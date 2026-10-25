package com.securebank.service;

import com.securebank.dao.AuditDAO;
import com.securebank.dao.BankDAO;
import com.securebank.dao.CustomerDAO;
import com.securebank.exception.AccountNotFoundException;
import com.securebank.exception.AuthorizationException;
import com.securebank.model.Account;
import com.securebank.model.AccountStatus;
import com.securebank.model.AccountType;
import com.securebank.model.AuditLog;
import com.securebank.model.CurrentAccount;
import com.securebank.model.Customer;
import com.securebank.model.Role;
import com.securebank.model.SavingsAccount;
import com.securebank.model.User;
import com.securebank.util.CurrencyFormatter;
import com.securebank.util.ValidationUtil;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * SecureBank: Role-Based Core Banking & Transaction Management System
 * File: AccountService.java
 *
 * Manages Account Lifecycles, Customer Association, RBAC Authorization checks,
 * and Soft Account Closure.
 */
public class AccountService {

    private final BankDAO bankDAO;
    private final CustomerDAO customerDAO;
    private final AuditDAO auditDAO;

    public AccountService() {
        this.bankDAO = new BankDAO();
        this.customerDAO = new CustomerDAO();
        this.auditDAO = new AuditDAO();
    }

    public AccountService(BankDAO bankDAO, CustomerDAO customerDAO, AuditDAO auditDAO) {
        this.bankDAO = bankDAO;
        this.customerDAO = customerDAO;
        this.auditDAO = auditDAO;
    }

    /**
     * Creates a new banking account linked to a customer.
     * Tellers and Administrators are authorized.
     */
    public Account createAccount(
        int customerId,
        String holderName,
        AccountType type,
        BigDecimal initialDeposit,
        String plainPin,
        User actor
    ) throws SQLException, AuthorizationException {
        // Enforce Teller or Admin authorization
        AuthService.enforceRole(actor, Role.TELLER);

        ValidationUtil.validateHolderName(holderName);
        ValidationUtil.validatePin(plainPin);
        ValidationUtil.validatePositiveAmount(initialDeposit);

        Account account;
        if (type == AccountType.SAVINGS) {
            account = new SavingsAccount(holderName, initialDeposit, plainPin);
        } else if (type == AccountType.CURRENT) {
            account = new CurrentAccount(holderName, initialDeposit, plainPin);
        } else {
            throw new IllegalArgumentException("Unsupported account type: " + type);
        }

        account.setCustomerId(customerId);
        account.setStatus(AccountStatus.ACTIVE);

        int accNo = bankDAO.createAccount(account);
        account.setAccountNumber(accNo);

        recordAudit(
            actor.getUsername(),
            actor.getRole(),
            "ACCOUNT_CREATED",
            "ACCOUNT",
            String.valueOf(accNo),
            String.format("Opened %s with opening deposit %s for '%s' (Customer #%d)",
                type, CurrencyFormatter.format(initialDeposit), holderName, customerId),
            "SUCCESS"
        );

        try {
            Customer cust = customerDAO.getCustomerById(customerId);
            if (cust != null && cust.getUserId() > 0) {
                new NotificationService().notifyUser(cust.getUserId(), "Account Created",
                    String.format("Your new %s Account #%d has been activated with opening balance %s.",
                        type, accNo, CurrencyFormatter.format(initialDeposit)), "SUCCESS");
            }
        } catch (Exception ignored) {}

        return account;
    }

    /**
     * Retrieves an account enforcing Customer data privacy.
     * Customers may only inspect their own account; Tellers and Admins can view any account.
     */
    public Account getAccount(int accountNumber, User actor)
        throws SQLException, AccountNotFoundException, AuthorizationException {
        if (actor == null) {
            throw new AuthorizationException("Unauthenticated request. Please log in.");
        }

        Account acc = bankDAO.getAccountByNumber(accountNumber);
        if (acc == null) {
            throw new AccountNotFoundException("Account #" + accountNumber + " does not exist.");
        }

        // Ownership enforcement for Customers
        if (actor.getRole() == Role.CUSTOMER) {
            Customer cust = customerDAO.getCustomerByUserId(actor.getUserId());
            if (cust == null || (acc.getCustomerId() > 0 && acc.getCustomerId() != cust.getCustomerId())) {
                recordAudit(actor.getUsername(), actor.getRole(), "UNAUTHORIZED_ACCESS_ATTEMPT", "ACCOUNT",
                    String.valueOf(accountNumber), "Customer attempted to access foreign account", "REJECTED");
                throw new AuthorizationException("Access Denied: You are not authorized to view Account #" + accountNumber);
            }
        }

        return acc;
    }

    /**
     * Lists all accounts accessible to the logged-in user.
     * Customers see only their accounts; Tellers and Admins see all accounts.
     */
    public List<Account> getAccessibleAccounts(User actor) throws SQLException, AuthorizationException {
        if (actor == null) {
            throw new AuthorizationException("Unauthenticated request.");
        }

        if (actor.getRole() == Role.CUSTOMER) {
            Customer cust = customerDAO.getCustomerByUserId(actor.getUserId());
            if (cust == null) {
                return Collections.emptyList();
            }
            return bankDAO.getAccountsByCustomerId(cust.getCustomerId());
        } else {
            return bankDAO.getAllAccounts();
        }
    }

    /**
     * Closes an account safely (soft-close with status = CLOSED).
     * Prevents closure if non-zero balance or overdraft remains.
     */
    public boolean closeAccount(int accountNumber, String pin, User actor)
        throws SQLException, AccountNotFoundException, AuthorizationException {
        Account acc = getAccount(accountNumber, actor);

        boolean closed = bankDAO.closeAccount(accountNumber, pin);
        if (closed) {
            recordAudit(
                actor.getUsername(),
                actor.getRole(),
                "ACCOUNT_CLOSED",
                "ACCOUNT",
                String.valueOf(accountNumber),
                "Account closed (" + acc.getAccountType() + ", financial ledger preserved)",
                "SUCCESS"
            );
        }
        return closed;
    }

    /**
     * Administratively freezes, locks, or activates an account (Admin only).
     */
    public void updateStatus(int accountNumber, AccountStatus newStatus, User adminUser)
        throws SQLException, AuthorizationException {
        AuthService.enforceRole(adminUser, Role.ADMIN);
        bankDAO.updateAccountStatus(accountNumber, newStatus);
        recordAudit(
            adminUser.getUsername(),
            adminUser.getRole(),
            "ACCOUNT_STATUS_CHANGE",
            "ACCOUNT",
            String.valueOf(accountNumber),
            "Status changed to " + newStatus,
            "SUCCESS"
        );
    }

    private void recordAudit(String actor, Role role, String action, String type, String id, String details, String status) {
        try {
            AuditLog al = new AuditLog(0, actor, role, action, type, id, details, status, "LOCAL", LocalDateTime.now());
            auditDAO.recordAuditLog(al);
        } catch (Exception ignored) {
        }
    }
}
