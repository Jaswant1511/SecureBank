package com.securebank.service.payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Standard request payload for external payment initiation.
 */
public class PaymentRequest {

    private final String clientReference;
    private final String idempotencyKey;
    private final int accountNumber;
    private final BigDecimal amount;
    private final String destinationAccount;
    private final String destinationBank;
    private final String purpose;
    private final LocalDateTime timestamp;

    public PaymentRequest(String clientReference, String idempotencyKey, int accountNumber,
                          BigDecimal amount, String destinationAccount, String destinationBank,
                          String purpose) {
        this.clientReference = clientReference;
        this.idempotencyKey = idempotencyKey;
        this.accountNumber = accountNumber;
        this.amount = amount;
        this.destinationAccount = destinationAccount;
        this.destinationBank = destinationBank;
        this.purpose = purpose;
        this.timestamp = LocalDateTime.now();
    }

    public String getClientReference() {
        return clientReference;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public int getAccountNumber() {
        return accountNumber;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getDestinationAccount() {
        return destinationAccount;
    }

    public String getDestinationBank() {
        return destinationBank;
    }

    public String getPurpose() {
        return purpose;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "PaymentRequest{" +
                "clientRef='" + clientReference + '\'' +
                ", acc=#" + accountNumber +
                ", amount=" + amount +
                ", destAcc='" + destinationAccount + '\'' +
                ", destBank='" + destinationBank + '\'' +
                '}';
    }
}
