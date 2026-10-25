package com.securebank.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Represents an individual debit or credit line entry in the immutable double-entry ledger.
 */
public class LedgerEntry {

    private int entryId;
    private int ledgerTxnId;
    private int accountNumber;
    private EntryType entryType;
    private BigDecimal amount;
    private LocalDateTime createdAt;

    public LedgerEntry() {
        this.createdAt = LocalDateTime.now();
    }

    public LedgerEntry(int entryId, int ledgerTxnId, int accountNumber, EntryType entryType, BigDecimal amount, LocalDateTime createdAt) {
        this.entryId = entryId;
        this.ledgerTxnId = ledgerTxnId;
        this.accountNumber = accountNumber;
        this.entryType = entryType;
        this.amount = amount;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
    }

    public int getEntryId() {
        return entryId;
    }

    public void setEntryId(int entryId) {
        this.entryId = entryId;
    }

    public int getLedgerTxnId() {
        return ledgerTxnId;
    }

    public void setLedgerTxnId(int ledgerTxnId) {
        this.ledgerTxnId = ledgerTxnId;
    }

    public int getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(int accountNumber) {
        this.accountNumber = accountNumber;
    }

    public EntryType getEntryType() {
        return entryType;
    }

    public void setEntryType(EntryType entryType) {
        this.entryType = entryType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
