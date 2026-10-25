package com.securebank.model;

import com.securebank.exception.InsufficientFundsException;
import com.securebank.util.CurrencyFormatter;
import com.securebank.util.ValidationUtil;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * SecureBank: Role-Based Core Banking & Transaction Management System
 * File: SavingsAccount.java
 *
 * Concrete subclass representing a retail Savings Account.
 * Demonstrates:
 *  - Inheritance & super() constructor chaining
 *  - Polymorphic override of withdraw(): Enforces MIN_BALANCE = Rs. 1,000.00
 *  - Subclass-specific behavior: applyYearlyInterest() at 4% (0.04)
 *  - Exact BigDecimal financial calculations
 */
public class SavingsAccount extends Account {

    public static final BigDecimal MIN_BALANCE = BigDecimal.valueOf(1000.00).setScale(2, RoundingMode.HALF_UP);
    public static final BigDecimal INTEREST_RATE = BigDecimal.valueOf(0.04).setScale(4, RoundingMode.HALF_UP);

    public static final double MIN_BALANCE_DOUBLE = 1000.0;
    public static final double INTEREST_RATE_DOUBLE = 0.04;

    public SavingsAccount(String holderName, BigDecimal initialBalance, String pin) {
        super(holderName, initialBalance, pin);
        if (initialBalance.compareTo(MIN_BALANCE) < 0) {
            throw new IllegalArgumentException(
                String.format("Initial deposit for Savings Account must be at least %s", CurrencyFormatter.format(MIN_BALANCE))
            );
        }
    }

    public SavingsAccount(String holderName, double initialBalance, String pin) {
        this(holderName, BigDecimal.valueOf(initialBalance), pin);
    }

    public SavingsAccount(
        int accountNumber,
        int customerId,
        String holderName,
        BigDecimal balance,
        String pinHash,
        int failedAttempts,
        AccountStatus status,
        LocalDateTime createdAt
    ) {
        super(accountNumber, customerId, holderName, balance, pinHash, failedAttempts, status, createdAt);
    }

    public SavingsAccount(
        int accountNumber,
        String holderName,
        double balance,
        String pin,
        int failedAttempts,
        boolean isLocked
    ) {
        super(accountNumber, holderName, balance, pin, failedAttempts, isLocked);
    }

    @Override
    public void withdraw(BigDecimal amount) throws InsufficientFundsException {
        ValidationUtil.validatePositiveAmount(amount);

        if (!getStatus().canTransact()) {
            throw new IllegalStateException("Account #" + getAccountNumber() + " is " + getStatus() + ". Cannot withdraw.");
        }

        BigDecimal projectedBalance = getBalance().subtract(amount).setScale(2, RoundingMode.HALF_UP);
        if (projectedBalance.compareTo(MIN_BALANCE) < 0) {
            BigDecimal shortfall = amount.add(MIN_BALANCE).subtract(getBalance());
            throw new InsufficientFundsException(
                String.format("Savings account minimum balance policy violation. Must retain %s.", CurrencyFormatter.format(MIN_BALANCE)),
                shortfall
            );
        }

        setBalance(projectedBalance);
        recordLocalTransaction("WITHDRAW", amount, getBalance(), "Savings Withdrawal");
    }

    /**
     * Credits 4% yearly interest on current balance.
     */
    public BigDecimal applyYearlyInterest() {
        if (!getStatus().canTransact()) {
            throw new IllegalStateException("Cannot apply interest to inactive account.");
        }
        BigDecimal interest = getBalance().multiply(INTEREST_RATE).setScale(2, RoundingMode.HALF_UP);
        setBalance(getBalance().add(interest));
        recordLocalTransaction("INTEREST", interest, getBalance(), "4% Yearly Interest Credited");
        return interest;
    }

    public double applyYearlyInterestAsDouble() {
        return applyYearlyInterest().doubleValue();
    }

    @Override
    public String getAccountType() {
        return "SAVINGS";
    }

    @Override
    public void displayDetails() {
        System.out.println("================================================================================");
        System.out.printf("  ACCOUNT PROFILE       : #%d  [SAVINGS ACCOUNT]%n", getAccountNumber());
        System.out.printf("  Account Holder Name   : %s%n", getHolderName());
        System.out.printf("  Current Balance       : %s%n", CurrencyFormatter.format(getBalance()));
        System.out.printf("  Rule Policy           : Min Balance %s | Interest Rate 4.00%%%n",
            CurrencyFormatter.format(MIN_BALANCE));
        BigDecimal available = getBalance().subtract(MIN_BALANCE);
        if (available.compareTo(BigDecimal.ZERO) < 0) {
            available = BigDecimal.ZERO;
        }
        System.out.printf("  Withdrawable Funds    : %s%n", CurrencyFormatter.format(available));
        System.out.printf("  Operational Status    : %s (%d/%d PIN attempts)%n",
            getStatus(), getFailedAttempts(), MAX_FAILED_ATTEMPTS);
        System.out.println("================================================================================");
    }
}
