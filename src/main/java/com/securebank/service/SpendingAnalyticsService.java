package com.securebank.service;

import com.securebank.dao.AuditDAO;
import com.securebank.dao.BankDAO;
import com.securebank.dao.BudgetDAO;
import com.securebank.dao.CustomerDAO;
import com.securebank.dao.TransactionDAO;
import com.securebank.exception.AuthorizationException;
import com.securebank.model.Account;
import com.securebank.model.AuditLog;
import com.securebank.model.Budget;
import com.securebank.model.Customer;
import com.securebank.model.Role;
import com.securebank.model.SpendingCategory;
import com.securebank.model.SpendingSummary;
import com.securebank.model.Transaction;
import com.securebank.model.TransactionType;
import com.securebank.model.User;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * SecureBank: Role-Based Core Banking & Transaction Management System
 * File: SpendingAnalyticsService.java
 *
 * Innovation 3: Smart Spending Insights & Budget Management.
 * Dynamically computes real customer expenditure breakdown from authoritative ledger transactions,
 * categorizes outflows, tracks budget thresholds, and prevents self-transfers from inflating spending metrics.
 */
public class SpendingAnalyticsService {

    private final BankDAO bankDAO;
    private final TransactionDAO transactionDAO;
    private final BudgetDAO budgetDAO;
    private final CustomerDAO customerDAO;
    private final AuditDAO auditDAO;

    public SpendingAnalyticsService() {
        this.bankDAO = new BankDAO();
        this.transactionDAO = new TransactionDAO();
        this.budgetDAO = new BudgetDAO();
        this.customerDAO = new CustomerDAO();
        this.auditDAO = new AuditDAO();
    }

    public SpendingAnalyticsService(BankDAO bankDAO, TransactionDAO transactionDAO, BudgetDAO budgetDAO, CustomerDAO customerDAO, AuditDAO auditDAO) {
        this.bankDAO = bankDAO != null ? bankDAO : new BankDAO();
        this.transactionDAO = transactionDAO != null ? transactionDAO : new TransactionDAO();
        this.budgetDAO = budgetDAO != null ? budgetDAO : new BudgetDAO();
        this.customerDAO = customerDAO != null ? customerDAO : new CustomerDAO();
        this.auditDAO = auditDAO != null ? auditDAO : new AuditDAO();
    }

    /**
     * Calculates customer spending analytics from authentic account transactions.
     */
    public SpendingSummary calculateSpending(int customerId, User actor) throws SQLException, AuthorizationException {
        if (actor == null) {
            throw new AuthorizationException("Unauthenticated request.");
        }
        verifyCustomerOwnership(customerId, actor);

        List<Account> accounts = bankDAO.getAccountsByCustomerId(customerId);
        Set<Integer> ownAccountNumbers = new HashSet<>();
        for (Account a : accounts) {
            ownAccountNumbers.add(a.getAccountNumber());
        }

        SpendingSummary summary = new SpendingSummary();
        BigDecimal totalSpent = BigDecimal.ZERO;
        int eligibleTxnCount = 0;
        Map<String, BigDecimal> categoryMap = new HashMap<>();
        Transaction largestExpense = null;

        for (Account acc : accounts) {
            List<Transaction> txns = transactionDAO.getRecentTransactions(acc.getAccountNumber(), 100);
            for (Transaction txn : txns) {
                TransactionType type = txn.getTxnType();
                // Include genuine expenditure types
                boolean isSpending = (type == TransactionType.WITHDRAW || type == TransactionType.TRANSFER_OUT ||
                        type == TransactionType.BILL_PAYMENT);

                if (!isSpending) {
                    continue;
                }

                // Per Section 9.2: Exclude transfers between customer's own accounts
                if (type == TransactionType.TRANSFER_OUT && txn.getRelatedAccountNumber() != null &&
                        ownAccountNumbers.contains(txn.getRelatedAccountNumber())) {
                    continue;
                }

                BigDecimal amt = txn.getAmount();
                totalSpent = totalSpent.add(amt);
                eligibleTxnCount++;

                if (largestExpense == null || amt.compareTo(largestExpense.getAmount()) > 0) {
                    largestExpense = txn;
                }

                String categoryKey = SpendingCategory.categorize(txn.getRemarks()).name();
                categoryMap.put(categoryKey, categoryMap.getOrDefault(categoryKey, BigDecimal.ZERO).add(amt));
            }
        }

        summary.setTotalSpent(totalSpent);
        summary.setTransactionCount(eligibleTxnCount);
        summary.setCategoryTotals(categoryMap);
        summary.setLargestExpense(largestExpense);

        // Fetch customer budgets to calculate percentage utilization
        List<Budget> budgets = budgetDAO.getBudgetsByCustomerId(customerId);
        Map<String, BigDecimal> budgetMap = new HashMap<>();
        for (Budget b : budgets) {
            budgetMap.put(b.getCategory().toUpperCase(), b.getMonthlyLimit());
        }

        List<SpendingSummary.CategoryDetail> details = new ArrayList<>();
        // Populate standard categories
        for (SpendingCategory sc : SpendingCategory.values()) {
            BigDecimal spent = categoryMap.getOrDefault(sc.name(), BigDecimal.ZERO);
            BigDecimal limit = budgetMap.get(sc.name());
            double percent = 0.0;
            if (limit != null && limit.compareTo(BigDecimal.ZERO) > 0) {
                percent = spent.multiply(new BigDecimal("100")).divide(limit, 2, RoundingMode.HALF_UP).doubleValue();
            }
            details.add(new SpendingSummary.CategoryDetail(sc.name(), sc.getDisplayName(), spent, limit, percent));
        }
        summary.setCategoryDetails(details);

        return summary;
    }

    /**
     * Sets or updates a spending budget limit for a category.
     */
    public int setBudget(int customerId, String category, BigDecimal monthlyLimit, User actor) throws SQLException, AuthorizationException {
        if (actor == null) {
            throw new AuthorizationException("Unauthenticated request.");
        }
        verifyCustomerOwnership(customerId, actor);

        if (category == null || category.trim().isEmpty()) {
            throw new IllegalArgumentException("Budget category is required.");
        }
        if (monthlyLimit == null || monthlyLimit.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Monthly budget limit must be strictly positive.");
        }

        String catUpper = category.trim().toUpperCase();
        int budgetId = budgetDAO.setBudget(customerId, catUpper, monthlyLimit);

        recordAudit(
                actor.getUsername(),
                actor.getRole(),
                "SET_BUDGET",
                "BUDGET",
                String.valueOf(budgetId),
                String.format("Set ₹%s monthly budget for %s (Customer #%d)", monthlyLimit.toPlainString(), catUpper, customerId),
                "SUCCESS"
        );

        return budgetId;
    }

    /**
     * Lists current budgets for a customer.
     */
    public List<Budget> getBudgets(int customerId, User actor) throws SQLException, AuthorizationException {
        if (actor == null) {
            throw new AuthorizationException("Unauthenticated request.");
        }
        verifyCustomerOwnership(customerId, actor);
        return budgetDAO.getBudgetsByCustomerId(customerId);
    }

    /**
     * Removes a spending budget.
     */
    public boolean deleteBudget(int budgetId, int customerId, User actor) throws SQLException, AuthorizationException {
        if (actor == null) {
            throw new AuthorizationException("Unauthenticated request.");
        }
        verifyCustomerOwnership(customerId, actor);
        boolean deleted = budgetDAO.deleteBudget(budgetId, customerId);
        if (deleted) {
            recordAudit(
                    actor.getUsername(),
                    actor.getRole(),
                    "DELETE_BUDGET",
                    "BUDGET",
                    String.valueOf(budgetId),
                    "Removed budget #" + budgetId + " for customer #" + customerId,
                    "SUCCESS"
            );
        }
        return deleted;
    }

    private void verifyCustomerOwnership(int customerId, User actor) throws AuthorizationException, SQLException {
        if (actor.getRole() == Role.CUSTOMER) {
            Customer cust = customerDAO.getCustomerByUserId(actor.getUserId());
            if (cust == null || cust.getCustomerId() != customerId) {
                throw new AuthorizationException("Access Denied: You cannot view or modify another customer's spending data.");
            }
        }
    }

    private void recordAudit(String actor, Role role, String action, String type, String id, String details, String status) {
        try {
            AuditLog al = new AuditLog(0, actor, role, action, type, id, details, status, "LOCAL", LocalDateTime.now());
            auditDAO.recordAuditLog(al);
        } catch (Exception ignored) {
        }
    }
}
