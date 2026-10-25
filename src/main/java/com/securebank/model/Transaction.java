package com.securebank.model;

import com.securebank.util.CurrencyFormatter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * SecureBank: Role-Based Core Banking & Transaction Management System
 * File: Transaction.java
 *
 * Represents an immutable ledger transaction record.
 */
public class Transaction {

    private int transactionId;
    private String transactionRef;
    private int accountNumber;
    private TransactionType txnType;
    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private String remarks;
    private Integer relatedAccountNumber;
    private LocalDateTime timestamp;

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public Transaction() {
        this.timestamp = LocalDateTime.now();
    }

    public Transaction(
        int transactionId,
        String transactionRef,
        int accountNumber,
        TransactionType txnType,
        BigDecimal amount,
        BigDecimal balanceAfter,
        String remarks,
        Integer relatedAccountNumber,
        LocalDateTime timestamp
    ) {
        this.transactionId = transactionId;
        this.transactionRef = transactionRef;
        this.accountNumber = accountNumber;
        this.txnType = txnType;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.remarks = remarks;
        this.relatedAccountNumber = relatedAccountNumber;
        this.timestamp = timestamp != null ? timestamp : LocalDateTime.now();
    }

    public int getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(int transactionId) {
        this.transactionId = transactionId;
    }

    public String getTransactionRef() {
        return transactionRef;
    }

    public void setTransactionRef(String transactionRef) {
        this.transactionRef = transactionRef;
    }

    public int getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(int accountNumber) {
        this.accountNumber = accountNumber;
    }

    public TransactionType getTxnType() {
        return txnType;
    }

    public void setTxnType(TransactionType txnType) {
        this.txnType = txnType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public void setBalanceAfter(BigDecimal balanceAfter) {
        this.balanceAfter = balanceAfter;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public Integer getRelatedAccountNumber() {
        return relatedAccountNumber;
    }

    public void setRelatedAccountNumber(Integer relatedAccountNumber) {
        this.relatedAccountNumber = relatedAccountNumber;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getFormattedTimestamp() {
        return timestamp != null ? timestamp.format(TS_FMT) : "N/A";
    }

    public String toFormattedString() {
        return String.format(
            "[%s] %-12s | Ref: %-18s | Amt: %12s | Bal: %12s | %s",
            getFormattedTimestamp(),
            txnType,
            transactionRef != null ? transactionRef : "N/A",
            CurrencyFormatter.format(amount),
            CurrencyFormatter.format(balanceAfter),
            remarks
        );
    }
}
