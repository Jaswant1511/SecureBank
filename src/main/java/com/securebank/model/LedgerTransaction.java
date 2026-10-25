package com.securebank.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a balanced, immutable Double-Entry Journal Transaction.
 * Mathematical Invariant: sum(DEBIT) == sum(CREDIT)
 */
public class LedgerTransaction {

    private int ledgerTxnId;
    private String transactionRef;
    private String txnType;
    private String description;
    private String status; // 'POSTED', 'REVERSED'
    private LocalDateTime createdAt;
    private List<LedgerEntry> entries = new ArrayList<>();

    public LedgerTransaction() {
        this.createdAt = LocalDateTime.now();
        this.status = "POSTED";
    }

    public LedgerTransaction(int ledgerTxnId, String transactionRef, String txnType, String description, String status, LocalDateTime createdAt) {
        this.ledgerTxnId = ledgerTxnId;
        this.transactionRef = transactionRef;
        this.txnType = txnType;
        this.description = description;
        this.status = status != null ? status : "POSTED";
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
    }

    public boolean isBalanced() {
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        for (LedgerEntry entry : entries) {
            if (entry.getEntryType() == EntryType.DEBIT) {
                totalDebit = totalDebit.add(entry.getAmount());
            } else if (entry.getEntryType() == EntryType.CREDIT) {
                totalCredit = totalCredit.add(entry.getAmount());
            }
        }
        return totalDebit.compareTo(totalCredit) == 0 && totalDebit.compareTo(BigDecimal.ZERO) > 0;
    }

    public int getLedgerTxnId() {
        return ledgerTxnId;
    }

    public void setLedgerTxnId(int ledgerTxnId) {
        this.ledgerTxnId = ledgerTxnId;
    }

    public String getTransactionRef() {
        return transactionRef;
    }

    public void setTransactionRef(String transactionRef) {
        this.transactionRef = transactionRef;
    }

    public String getTxnType() {
        return txnType;
    }

    public void setTxnType(String txnType) {
        this.txnType = txnType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public List<LedgerEntry> getEntries() {
        return entries;
    }

    public void setEntries(List<LedgerEntry> entries) {
        this.entries = entries != null ? entries : new ArrayList<>();
    }

    public void addEntry(LedgerEntry entry) {
        this.entries.add(entry);
    }
}
