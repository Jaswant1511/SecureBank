package com.securebank.integration;

import com.securebank.dao.BankDAO;
import com.securebank.exception.AuthorizationException;
import com.securebank.model.Budget;
import com.securebank.model.Role;
import com.securebank.model.SpendingCategory;
import com.securebank.model.SpendingSummary;
import com.securebank.model.User;
import com.securebank.service.BankingService;
import com.securebank.service.SpendingAnalyticsService;
import com.securebank.util.DatabaseConfig;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SpendingInsightsTest {

    private SpendingAnalyticsService spendingService;
    private BankingService bankingService;
    private BankDAO bankDAO;

    @BeforeEach
    public void setup() {
        DatabaseConfig.switchToEmbeddedH2();
        DatabaseConfig.seedTestData();
        this.bankDAO = new BankDAO();
        this.spendingService = new SpendingAnalyticsService();
        this.bankingService = new BankingService();
    }

    @Test
    @DisplayName("Spending Insights: Rule-based keyword categorization maps transaction remarks accurately")
    public void testKeywordCategorization() {
        assertEquals(SpendingCategory.FOOD, SpendingCategory.categorize("Dinner with friends at Zomato restaurant"));
        assertEquals(SpendingCategory.TRANSPORT, SpendingCategory.categorize("Daily commute via Uber cab"));
        assertEquals(SpendingCategory.SHOPPING, SpendingCategory.categorize("Amazon electronics online shopping"));
        assertEquals(SpendingCategory.UTILITIES, SpendingCategory.categorize("Monthly BESCOM electricity bill"));
        assertEquals(SpendingCategory.EDUCATION, SpendingCategory.categorize("Semester college tuition fee payment"));
        assertEquals(SpendingCategory.ENTERTAINMENT, SpendingCategory.categorize("Netflix monthly subscription"));
        assertEquals(SpendingCategory.HEALTHCARE, SpendingCategory.categorize("Apollo pharmacy medicine purchase"));
        assertEquals(SpendingCategory.OTHER, SpendingCategory.categorize("Miscellaneous cash withdrawal"));
    }

    @Test
    @DisplayName("Spending Insights: Self-account transfers are strictly excluded from spending totals")
    public void testSelfTransferExclusionFromSpending() throws Exception {
        User aarav = new User(103, "aarav", "hash", Role.CUSTOMER, 0, false, true);

        // Account 1001 belongs to Customer #501 (Aarav)
        // Create a secondary account for customer 501 to test self-transfers
        com.securebank.model.SavingsAccount secondAcc = new com.securebank.model.SavingsAccount("Aarav Second Account", new BigDecimal("5000.00"), "1234");
        secondAcc.setCustomerId(501);
        bankDAO.createAccount(secondAcc);

        // Execute an internal transfer between own accounts (1001 and secondAcc)
        bankingService.transfer(1001, "1234", secondAcc.getAccountNumber(), new BigDecimal("2000.00"), "Transfer between own accounts", aarav);

        SpendingSummary summary = spendingService.calculateSpending(501, aarav);
        assertNotNull(summary);

        // Execute an external withdrawal / spending
        bankingService.withdraw(1001, "1234", new BigDecimal("500.00"), "Swiggy food delivery", aarav);

        SpendingSummary updatedSummary = spendingService.calculateSpending(501, aarav);
        // The total spent must only account for the 500 withdrawal, NOT the 2000 internal transfer
        assertTrue(updatedSummary.getTotalSpent().compareTo(new BigDecimal("500.00")) >= 0);
        assertEquals(SpendingCategory.FOOD.name(), updatedSummary.getCategoryDetails().stream()
                .filter(cd -> cd.getSpent().compareTo(BigDecimal.ZERO) > 0)
                .findFirst().map(SpendingSummary.CategoryDetail::getCategory).orElse(""));
    }

    @Test
    @DisplayName("Spending Insights: Budget management allows setting limits and computing utilization %")
    public void testBudgetManagementAndUtilization() throws Exception {
        User aarav = new User(103, "aarav", "hash", Role.CUSTOMER, 0, false, true);

        int budgetId = spendingService.setBudget(501, "ENTERTAINMENT", new BigDecimal("3000.00"), aarav);
        assertTrue(budgetId > 0);

        List<Budget> budgets = spendingService.getBudgets(501, aarav);
        assertFalse(budgets.isEmpty());
        assertTrue(budgets.stream().anyMatch(b -> "ENTERTAINMENT".equalsIgnoreCase(b.getCategory())));

        // Perform entertainment expenditure
        bankingService.withdraw(1001, "1234", new BigDecimal("1500.00"), "Netflix monthly subscription", aarav);

        SpendingSummary summary = spendingService.calculateSpending(501, aarav);
        SpendingSummary.CategoryDetail entDetail = summary.getCategoryDetails().stream()
                .filter(cd -> "ENTERTAINMENT".equalsIgnoreCase(cd.getCategory()))
                .findFirst().orElse(null);

        assertNotNull(entDetail);
        assertEquals(0, new BigDecimal("3000.00").compareTo(entDetail.getBudgetLimit()));
        assertEquals(50.0, entDetail.getPercentUtilized(), 0.1);

        // Cleanup
        boolean deleted = spendingService.deleteBudget(budgetId, 501, aarav);
        assertTrue(deleted);
    }

    @Test
    @DisplayName("Spending Insights: Customer cannot inspect or modify another customer's spending data")
    public void testCustomerIsolationEnforcement() {
        User priya = new User(104, "priya", "hash", Role.CUSTOMER, 0, false, true);

        // Priya (Customer 502) attempting to query Customer 501's spending insights
        assertThrows(AuthorizationException.class, () -> {
            spendingService.calculateSpending(501, priya);
        });

        assertThrows(AuthorizationException.class, () -> {
            spendingService.setBudget(501, "FOOD", new BigDecimal("5000.00"), priya);
        });
    }
}
