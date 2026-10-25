package com.securebank.model;

import com.securebank.exception.InsufficientFundsException;
import com.securebank.util.CurrencyFormatter;
import com.securebank.util.ValidationUtil;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * SecureBank: Role-Based Core Banking & Transaction Management System
 * File: CurrentAccount.java
 *
 * Concrete subclass representing a commercial / business Current Account.
 * Demonstrates:
 *  - Inheritance & super() constructor chaining
 *  - Polymorphic override of withdraw(): Permits negative balance down to -OVERDRAFT_LIMIT (-Rs. 5,000.00)
 *  - Overdraft utilization metrics for commercial liquidity
 *  - Exact BigDecimal arithmetic
 */
public class CurrentAccount extends Account {

    public static final BigDecimal OVERDRAFT_LIMIT = BigDecimal.valueOf(5000.00).setScale(2, RoundingMode.HALF_UP);
    public static final double OVERDRAFT_LIMIT_DOUBLE = 5000.0;

    public CurrentAccount(String holderName, BigDecimal initialBalance, String pin) {
        super(holderName, initialBalance, pin);
        if (initialBalance.compareTo(OVERDRAFT_LIMIT.negate()) < 0) {
            throw new IllegalArgumentException(
                String.format("Initial balance cannot be lower than negative overdraft limit of -%s", CurrencyFormatter.format(OVERDRAFT_LIMIT))
            );
        }
    }

    public CurrentAccount(String holderName, double initialBalance, String pin) {
        this(holderName, BigDecimal.valueOf(initialBalance), pin);
    }

    public CurrentAccount(
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

    public CurrentAccount(
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

        BigDecimal minAllowable = OVERDRAFT_LIMIT.negate();
        BigDecimal projectedBalance = getBalance().subtract(amount).setScale(2, RoundingMode.HALF_UP);

        if (projectedBalance.compareTo(minAllowable) < 0) {
            BigDecimal maxWithdrawable = getBalance().add(OVERDRAFT_LIMIT);
            BigDecimal shortfall = amount.subtract(maxWithdrawable);
            throw new InsufficientFundsException(
                String.format("Current account overdraft limit of %s exceeded.", CurrencyFormatter.format(OVERDRAFT_LIMIT)),
                shortfall
            );
        }

        setBalance(projectedBalance);
        recordLocalTransaction("WITHDRAW", amount, getBalance(), "Current Account Withdrawal");
    }

    /**
     * Calculates current overdraft facility drawn down.
     */
    public BigDecimal getOverdraftUsed() {
        if (getBalance().compareTo(BigDecimal.ZERO) < 0) {
            return getBalance().abs();
        }
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    public double getOverdraftUsedAsDouble() {
        return getOverdraftUsed().doubleValue();
    }

    /**
     * Total liquidity available (balance + overdraft limit).
     */
    public BigDecimal getAvailableLiquidity() {
        return getBalance().add(OVERDRAFT_LIMIT).setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public String getAccountType() {
        return "CURRENT";
    }

    @Override
    public void displayDetails() {
        BigDecimal odUsed = getOverdraftUsed();
        System.out.println("================================================================================");
        System.out.printf("  ACCOUNT PROFILE       : #%d  [CURRENT ACCOUNT]%n", getAccountNumber());
        System.out.printf("  Account Holder Name   : %s%n", getHolderName());
        System.out.printf("  Current Balance       : %s%n", CurrencyFormatter.format(getBalance()));
        System.out.printf("  Overdraft Facility    : Limit %s | Utilized %s%n",
            CurrencyFormatter.format(OVERDRAFT_LIMIT), CurrencyFormatter.format(odUsed));
        System.out.printf("  Total Liquidity Avail : %s%n", CurrencyFormatter.format(getAvailableLiquidity()));
        System.out.printf("  Operational Status    : %s (%d/%d PIN attempts)%n",
            getStatus(), getFailedAttempts(), MAX_FAILED_ATTEMPTS);
        System.out.println("================================================================================");
    }
}
