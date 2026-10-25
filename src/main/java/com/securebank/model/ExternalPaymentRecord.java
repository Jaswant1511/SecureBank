package com.securebank.model;

import com.securebank.service.payment.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity representing an external financial payment transaction.
 */
public class ExternalPaymentRecord {

    private int paymentId;
    private String clientReference;
    private String idempotencyKey;
    private int accountNumber;
    private BigDecimal amount;
    private String destinationAccount;
    private String destinationBank;
    private String providerName;
    private String providerReference;
    private PaymentStatus status;
    private boolean simulated;
    private String purpose;
    private String failureReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public ExternalPaymentRecord() {
    }

    public ExternalPaymentRecord(int paymentId, String clientReference, String idempotencyKey, int accountNumber,
                                 BigDecimal amount, String destinationAccount, String destinationBank,
                                 String providerName, String providerReference, PaymentStatus status,
                                 boolean simulated, String purpose, String failureReason,
                                 LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.paymentId = paymentId;
        this.clientReference = clientReference;
        this.idempotencyKey = idempotencyKey;
        this.accountNumber = accountNumber;
        this.amount = amount;
        this.destinationAccount = destinationAccount;
        this.destinationBank = destinationBank;
        this.providerName = providerName;
        this.providerReference = providerReference;
        this.status = status;
        this.simulated = simulated;
        this.purpose = purpose;
        this.failureReason = failureReason;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public int getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(int paymentId) {
        this.paymentId = paymentId;
    }

    public String getClientReference() {
        return clientReference;
    }

    public void setClientReference(String clientReference) {
        this.clientReference = clientReference;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public int getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(int accountNumber) {
        this.accountNumber = accountNumber;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getDestinationAccount() {
        return destinationAccount;
    }

    public void setDestinationAccount(String destinationAccount) {
        this.destinationAccount = destinationAccount;
    }

    public String getDestinationBank() {
        return destinationBank;
    }

    public void setDestinationBank(String destinationBank) {
        this.destinationBank = destinationBank;
    }

    public String getProviderName() {
        return providerName;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public void setProviderReference(String providerReference) {
        this.providerReference = providerReference;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public boolean isSimulated() {
        return simulated;
    }

    public void setSimulated(boolean simulated) {
        this.simulated = simulated;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
