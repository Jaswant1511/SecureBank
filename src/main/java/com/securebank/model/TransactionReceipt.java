package com.securebank.model;

import com.securebank.util.CurrencyFormatter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * SecureBank: Role-Based Core Banking & Transaction Management System
 * File: TransactionReceipt.java
 *
 * Immutable Value Object representing an official banking transaction receipt.
 */
public class TransactionReceipt {

    private final String receiptNumber;
    private final String transactionRef;
    private final TransactionType transactionType;
    private final int accountNumber;
    private final String accountHolder;
    private final Integer beneficiaryAccountNumber;
    private final BigDecimal amount;
    private final BigDecimal balanceAfter;
    private final LocalDateTime timestamp;
    private final String status;
    private final String remarks;

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss");

    public TransactionReceipt(
        String transactionRef,
        TransactionType transactionType,
        int accountNumber,
        String accountHolder,
        Integer beneficiaryAccountNumber,
        BigDecimal amount,
        BigDecimal balanceAfter,
        String status,
        String remarks
    ) {
        this.transactionRef = transactionRef;
        this.receiptNumber = "REC-" + (transactionRef != null ? transactionRef.replace("TXN-", "") : "GEN");
        this.transactionType = transactionType;
        this.accountNumber = accountNumber;
        this.accountHolder = accountHolder;
        this.beneficiaryAccountNumber = beneficiaryAccountNumber;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.timestamp = LocalDateTime.now();
        this.status = status;
        this.remarks = remarks;
    }

    public String getReceiptNumber() {
        return receiptNumber;
    }

    public String getTransactionRef() {
        return transactionRef;
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public int getAccountNumber() {
        return accountNumber;
    }

    public String getAccountHolder() {
        return accountHolder;
    }

    public Integer getBeneficiaryAccountNumber() {
        return beneficiaryAccountNumber;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getStatus() {
        return status;
    }

    public String getRemarks() {
        return remarks;
    }

    public String toPrintableText() {
        StringBuilder sb = new StringBuilder();
        sb.append("================================================================================\n");
        sb.append("                       SECUREBANK OFFICIAL TRANSACTION RECEIPT                  \n");
        sb.append("================================================================================\n");
        sb.append(String.format(" Receipt Number      : %s%n", receiptNumber));
        sb.append(String.format(" Reference Code      : %s%n", transactionRef));
        sb.append(String.format(" Date & Time         : %s%n", timestamp.format(TS_FMT)));
        sb.append(String.format(" Transaction Type    : %s%n", transactionType.getDescription()));
        sb.append(String.format(" Primary Account     : #%04d (%s)%n", accountNumber, maskName(accountHolder)));
        if (beneficiaryAccountNumber != null && beneficiaryAccountNumber > 0) {
            sb.append(String.format(" Beneficiary Account : #%04d%n", beneficiaryAccountNumber));
        }
        sb.append(String.format(" Transacted Amount   : %s%n", CurrencyFormatter.format(amount)));
        if (balanceAfter != null) {
            sb.append(String.format(" Updated Balance     : %s%n", CurrencyFormatter.format(balanceAfter)));
        }
        sb.append(String.format(" Transaction Status  : %s%n", status));
        sb.append(String.format(" Description/Remarks : %s%n", remarks));
        sb.append("--------------------------------------------------------------------------------\n");
        sb.append(" Security Notice: Computer-generated electronic receipt. No signature required. \n");
        sb.append("================================================================================\n");
        return sb.toString();
    }

    private String maskName(String name) {
        if (name == null || name.length() <= 3) {
            return name;
        }
        return name.substring(0, 2) + "***" + name.charAt(name.length() - 1);
    }
}
