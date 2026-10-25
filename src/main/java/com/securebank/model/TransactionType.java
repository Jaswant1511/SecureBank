package com.securebank.model;

/**
 * SecureBank: Role-Based Core Banking & Transaction Management System
 * Enum: TransactionType
 *
 * Defines all allowable financial transaction types in the system.
 */
public enum TransactionType {
    OPEN("Account Opening Deposit"),
    DEPOSIT("Cash / Electronic Deposit"),
    WITHDRAW("Cash / ATM Withdrawal"),
    TRANSFER_IN("Fund Transfer Inbound"),
    TRANSFER_OUT("Fund Transfer Outbound"),
    INTEREST("Yearly Interest Credit"),
    REVERSAL("Compensating Transaction Reversal"),
    BILL_PAYMENT("Utility Bill Payment"),
    FIXED_DEPOSIT("Fixed Deposit Investment"),
    LOAN_DISBURSEMENT("Loan Credit Disbursement"),
    LOAN_REPAYMENT("Loan EMI Repayment");

    private final String description;

    TransactionType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean isCredit() {
        return this == OPEN || this == DEPOSIT || this == TRANSFER_IN || this == INTEREST
                || this == LOAN_DISBURSEMENT;
    }

    public boolean isDebit() {
        return this == WITHDRAW || this == TRANSFER_OUT || this == BILL_PAYMENT
                || this == FIXED_DEPOSIT || this == LOAN_REPAYMENT;
    }

    public static TransactionType fromString(String typeStr) {
        if (typeStr == null) {
            throw new IllegalArgumentException("Transaction type cannot be null.");
        }
        for (TransactionType t : TransactionType.values()) {
            if (t.name().equalsIgnoreCase(typeStr.trim())) {
                return t;
            }
        }
        throw new IllegalArgumentException("Unknown transaction type: " + typeStr);
    }
}
