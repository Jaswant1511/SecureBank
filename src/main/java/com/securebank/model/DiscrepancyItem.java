package com.securebank.model;

import java.math.BigDecimal;

/**
 * SecureBank: Complete Banking Automation System
 * File: DiscrepancyItem.java
 *
 * Represents an individual account ledger reconciliation anomaly.
 */
public class DiscrepancyItem {

    private final int accountNumber;
    private final String holderName;
    private final BigDecimal storedBalance;
    private final BigDecimal calculatedBalance;
    private final BigDecimal difference;
    private final String notes;

    public DiscrepancyItem(
        int accountNumber,
        String holderName,
        BigDecimal storedBalance,
        BigDecimal calculatedBalance,
        BigDecimal difference,
        String notes
    ) {
        this.accountNumber = accountNumber;
        this.holderName = holderName;
        this.storedBalance = storedBalance;
        this.calculatedBalance = calculatedBalance;
        this.difference = difference;
        this.notes = notes;
    }

    public int getAccountNumber() {
        return accountNumber;
    }

    public String getHolderName() {
        return holderName;
    }

    public BigDecimal getStoredBalance() {
        return storedBalance;
    }

    public BigDecimal getCalculatedBalance() {
        return calculatedBalance;
    }

    public BigDecimal getDifference() {
        return difference;
    }

    public String getNotes() {
        return notes;
    }

    @Override
    public String toString() {
        return String.format(
            "Account #%d (%s): Stored=%.2f, Calculated=%.2f, Diff=%.2f (%s)",
            accountNumber, holderName, storedBalance, calculatedBalance, difference, notes
        );
    }
}
