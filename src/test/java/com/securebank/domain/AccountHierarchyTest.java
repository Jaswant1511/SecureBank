package com.securebank.domain;

import com.securebank.exception.InsufficientFundsException;
import com.securebank.model.CurrentAccount;
import com.securebank.model.SavingsAccount;
import java.math.BigDecimal;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Domain Test Suite: Verifies Java OOP Encapsulation, Inheritance,
 * Polymorphism, Minimum Balance, Overdraft Bounds, and Interest Rules.
 */
public class AccountHierarchyTest {

    private SavingsAccount savings;
    private CurrentAccount current;

    @BeforeEach
    public void setUp() {
        savings = new SavingsAccount("Test Customer", BigDecimal.valueOf(5000.00), "1234");
        current = new CurrentAccount("Test Business", BigDecimal.valueOf(10000.00), "5678");
    }

    @Test
    @DisplayName("Savings Account: Enforces opening minimum balance policy of Rs. 1,000.00")
    public void testSavingsMinimumOpeningDeposit() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            new SavingsAccount("Below Minimum", BigDecimal.valueOf(999.99), "1234");
        });
    }

    @Test
    @DisplayName("Current Account: Enforces opening overdraft boundary (-Rs. 5,000.00)")
    public void testCurrentAccountOpeningOverdraftBoundary() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            new CurrentAccount("Excessive Deficit", BigDecimal.valueOf(-5000.01), "5678");
        });
    }

    @Test
    @DisplayName("Polymorphism: withdraw() enforces minimum balance on SavingsAccount")
    public void testSavingsWithdrawalMinimumBalanceEnforcement() {
        // Balance is 5000. Minimum balance is 1000. Can withdraw up to 4000.
        Assertions.assertDoesNotThrow(() -> savings.withdraw(BigDecimal.valueOf(4000.00)));
        Assertions.assertEquals(new BigDecimal("1000.00"), savings.getBalance());

        // Attempting to withdraw another 1 Rupee should throw InsufficientFundsException with shortfall
        InsufficientFundsException ex = Assertions.assertThrows(InsufficientFundsException.class, () -> {
            savings.withdraw(BigDecimal.valueOf(1.00));
        });
        Assertions.assertTrue(ex.getShortfall().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    @DisplayName("Polymorphism: withdraw() permits overdraft down to -Rs. 5,000.00 on CurrentAccount")
    public void testCurrentAccountOverdraftFacility() throws InsufficientFundsException {
        // Balance is 10000. Can withdraw 10000 + 5000 = 15000.
        current.withdraw(BigDecimal.valueOf(12000.00));
        Assertions.assertEquals(new BigDecimal("-2000.00"), current.getBalance());
        Assertions.assertEquals(new BigDecimal("2000.00"), current.getOverdraftUsed());

        // Further withdrawal down to exact limit
        current.withdraw(BigDecimal.valueOf(3000.00));
        Assertions.assertEquals(new BigDecimal("-5000.00"), current.getBalance());
        Assertions.assertEquals(new BigDecimal("5000.00"), current.getOverdraftUsed());

        // Exceeding limit throws InsufficientFundsException
        Assertions.assertThrows(InsufficientFundsException.class, () -> {
            current.withdraw(BigDecimal.valueOf(0.01));
        });
    }

    @Test
    @DisplayName("Savings Account: applyYearlyInterest() correctly credits 4% on current balance")
    public void testSavingsInterestCalculation() {
        // Balance 5000.00 * 0.04 = 200.00
        BigDecimal interest = savings.applyYearlyInterest();
        Assertions.assertEquals(BigDecimal.valueOf(200.00).setScale(2), interest);
        Assertions.assertEquals(BigDecimal.valueOf(5200.00).setScale(2), savings.getBalance());
    }

    @Test
    @DisplayName("PIN Security: 3 failed attempts triggers automatic lockout")
    public void testPinLockoutStateMachine() {
        Assertions.assertFalse(savings.isLocked());

        // Attempt 1: Wrong PIN
        Assertions.assertFalse(savings.verifyPin("0000"));
        Assertions.assertEquals(1, savings.getFailedAttempts());
        Assertions.assertFalse(savings.isLocked());

        // Attempt 2: Wrong PIN
        Assertions.assertFalse(savings.verifyPin("0000"));
        Assertions.assertEquals(2, savings.getFailedAttempts());
        Assertions.assertFalse(savings.isLocked());

        // Attempt 3: Wrong PIN -> Account Locked
        Assertions.assertFalse(savings.verifyPin("0000"));
        Assertions.assertEquals(3, savings.getFailedAttempts());
        Assertions.assertTrue(savings.isLocked());

        // Even with correct PIN, locked account rejects operations
        Assertions.assertFalse(savings.verifyPin("1234"));
    }

    @Test
    @DisplayName("Circular Buffer: String[5] maintains fixed-size recent transaction history")
    public void testCircularTransactionBuffer() {
        savings.deposit(BigDecimal.valueOf(100.00));
        savings.deposit(BigDecimal.valueOf(200.00));
        savings.deposit(BigDecimal.valueOf(300.00));
        savings.deposit(BigDecimal.valueOf(400.00));
        savings.deposit(BigDecimal.valueOf(500.00));
        savings.deposit(BigDecimal.valueOf(600.00)); // Should drop the oldest 100.00

        String[] buffer = savings.getRecentTransactions();
        Assertions.assertEquals(5, buffer.length);
        Assertions.assertNotNull(buffer[0]);
        Assertions.assertTrue(buffer[0].contains("600.00")); // Newest
    }
}
