package com.securebank.model;

import com.securebank.exception.InsufficientFundsException;
import com.securebank.util.CurrencyFormatter;
import com.securebank.util.PasswordUtil;
import com.securebank.util.ValidationUtil;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * SecureBank: Role-Based Core Banking & Transaction Management System
 * File: Account.java
 *
 * Abstract parent class representing a generic bank account.
 * Demonstrates:
 *  1. Abstraction: Contract enforced via abstract methods.
 *  2. Encapsulation: State fields strictly private, validated modifications only.
 *  3. Financial Precision: Exact BigDecimal monetary balance.
 *  4. Security: Salted BCrypt PIN hashing with 3-attempt lockout.
 *  5. Fixed Buffer: 1D String[5] circular buffer for quick mini-statements.
 */
public abstract class Account {

    private int accountNumber;
    private int customerId;
    private String holderName;
    private BigDecimal balance;
    private String pinHash;
    private int failedAttempts;
    private AccountStatus status;
    private LocalDateTime createdAt;

    // Fixed-size circular buffer for the most recent 5 transaction summaries
    private final String[] recentTransactions = new String[5];

    public static final int MAX_FAILED_ATTEMPTS = 3;
    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Constructor for creating a new Account prior to persistence.
     */
    public Account(String holderName, BigDecimal initialBalance, String plainPin) {
        this(0, 0, holderName, initialBalance, PasswordUtil.hashPin(plainPin), 0, AccountStatus.ACTIVE, LocalDateTime.now());
    }

    /**
     * Overload accepting double balance for backward compatibility.
     */
    public Account(String holderName, double initialBalance, String plainPin) {
        this(holderName, BigDecimal.valueOf(initialBalance), plainPin);
    }

    /**
     * Full constructor for hydrating account from database.
     */
    public Account(
        int accountNumber,
        int customerId,
        String holderName,
        BigDecimal balance,
        String pinHash,
        int failedAttempts,
        AccountStatus status,
        LocalDateTime createdAt
    ) {
        ValidationUtil.validateHolderName(holderName);
        this.accountNumber = accountNumber;
        this.customerId = customerId;
        this.holderName = holderName.trim();
        this.balance = balance != null ? balance.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        this.pinHash = pinHash;
        this.failedAttempts = failedAttempts;
        this.status = status != null ? status : AccountStatus.ACTIVE;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
    }

    /**
     * Backward-compatible constructor for existing tests/code.
     */
    public Account(
        int accountNumber,
        String holderName,
        double balance,
        String pin,
        int failedAttempts,
        boolean isLocked
    ) {
        this(
            accountNumber,
            0,
            holderName,
            BigDecimal.valueOf(balance),
            pin != null && pin.length() == 4 ? PasswordUtil.hashPin(pin) : pin,
            failedAttempts,
            isLocked ? AccountStatus.LOCKED : AccountStatus.ACTIVE,
            LocalDateTime.now()
        );
    }

    /**
     * Verifies the 4-digit PIN before sensitive operations.
     * Automatically locks account if failed attempts reach MAX_FAILED_ATTEMPTS.
     */
    public boolean verifyPin(String inputPin) {
        if (this.status == AccountStatus.LOCKED || this.status == AccountStatus.CLOSED || this.status == AccountStatus.FROZEN) {
            System.out.println("[SECURITY ALERT] Account #" + accountNumber
                + " is " + status + ". Transactions not permitted.");
            return false;
        }

        if (PasswordUtil.checkPin(inputPin, this.pinHash)) {
            this.failedAttempts = 0;
            return true;
        } else {
            this.failedAttempts++;
            if (this.failedAttempts >= MAX_FAILED_ATTEMPTS) {
                this.status = AccountStatus.LOCKED;
                System.out.println("[SECURITY LOCKOUT] 3 consecutive wrong PIN attempts! Account #"
                    + accountNumber + " is now LOCKED.");
            } else {
                System.out.printf("[PIN ERROR] Invalid PIN! Attempt %d/%d used.%n",
                    this.failedAttempts, MAX_FAILED_ATTEMPTS);
            }
            return false;
        }
    }

    /**
     * Credits a positive deposit to the balance.
     */
    public void deposit(BigDecimal amount) {
        ValidationUtil.validatePositiveAmount(amount);
        if (!status.canTransact()) {
            throw new IllegalStateException("Cannot deposit to account in status: " + status);
        }
        this.balance = this.balance.add(amount).setScale(2, RoundingMode.HALF_UP);
        recordLocalTransaction("DEPOSIT", amount, this.balance, "Cash/Electronic Deposit");
    }

    /**
     * Backward-compatible deposit overload.
     */
    public void deposit(double amount) {
        deposit(BigDecimal.valueOf(amount));
    }

    /**
     * Pushes a formatted line into the fixed-size String[5] circular buffer.
     */
    public void recordLocalTransaction(String type, BigDecimal amount, BigDecimal balanceAfter, String remarks) {
        String entry = String.format(
            "[%s] %-12s | Amt: %s | Bal: %s | %s",
            LocalDateTime.now().format(TS_FORMAT),
            type,
            CurrencyFormatter.format(amount),
            CurrencyFormatter.format(balanceAfter),
            remarks
        );
        for (int i = recentTransactions.length - 1; i > 0; i--) {
            recentTransactions[i] = recentTransactions[i - 1];
        }
        recentTransactions[0] = entry;
    }

    public void recordLocalTransaction(String type, double amount, double balanceAfter, String remarks) {
        recordLocalTransaction(type, BigDecimal.valueOf(amount), BigDecimal.valueOf(balanceAfter), remarks);
    }

    public void setRecentTransactions(String[] dbTransactions) {
        for (int i = 0; i < recentTransactions.length; i++) {
            if (dbTransactions != null && i < dbTransactions.length) {
                recentTransactions[i] = dbTransactions[i];
            } else {
                recentTransactions[i] = null;
            }
        }
    }

    public String[] getRecentTransactions() {
        String[] copy = new String[5];
        System.arraycopy(recentTransactions, 0, copy, 0, recentTransactions.length);
        return copy;
    }

    public void printMiniStatement() {
        System.out.println("--------------------------------------------------------------------------------");
        System.out.printf("MINI-STATEMENT (Last 5 Transactions) — Account #%d (%s)%n",
            accountNumber, holderName);
        System.out.println("--------------------------------------------------------------------------------");
        boolean hasEntries = false;
        for (int i = 0; i < recentTransactions.length; i++) {
            if (recentTransactions[i] != null) {
                System.out.printf("  %d. %s%n", (i + 1), recentTransactions[i]);
                hasEntries = true;
            }
        }
        if (!hasEntries) {
            System.out.println("  [No recent transactions recorded in buffer]");
        }
        System.out.println("--------------------------------------------------------------------------------");
    }

    // Abstract methods overridden polymorphically
    public abstract void withdraw(BigDecimal amount) throws InsufficientFundsException;

    public void withdraw(double amount) throws InsufficientFundsException {
        withdraw(BigDecimal.valueOf(amount));
    }

    public abstract void displayDetails();

    public abstract String getAccountType();

    // Strictly Encapsulated Getters & Setters
    public int getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(int accountNumber) {
        this.accountNumber = accountNumber;
    }

    public int getCustomerId() {
        return customerId;
    }

    public void setCustomerId(int customerId) {
        this.customerId = customerId;
    }

    public String getHolderName() {
        return holderName;
    }

    public void setHolderName(String holderName) {
        ValidationUtil.validateHolderName(holderName);
        this.holderName = holderName.trim();
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public double getBalanceAsDouble() {
        return balance.doubleValue();
    }

    protected void setBalance(BigDecimal balance) {
        this.balance = balance != null ? balance.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
    }

    protected void setBalance(double balance) {
        setBalance(BigDecimal.valueOf(balance));
    }

    public String getPinHash() {
        return pinHash;
    }

    public void setPin(String plainPin) {
        ValidationUtil.validatePin(plainPin);
        this.pinHash = PasswordUtil.hashPin(plainPin);
    }

    public void setPinHash(String pinHash) {
        this.pinHash = pinHash;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public void setFailedAttempts(int failedAttempts) {
        this.failedAttempts = failedAttempts;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public void setStatus(AccountStatus status) {
        this.status = status;
    }

    public boolean isLocked() {
        return status == AccountStatus.LOCKED;
    }

    public void setLocked(boolean locked) {
        this.status = locked ? AccountStatus.LOCKED : AccountStatus.ACTIVE;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
