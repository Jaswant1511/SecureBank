package com.securebank.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Represents an authorized, executed bill payment.
 */
public class BillPayment {

    private int paymentId;
    private String paymentRef;
    private int customerId;
    private int accountNumber;
    private int billerId;
    private String billerName;
    private String consumerNumber;
    private BigDecimal amount;
    private String status; // 'SUCCESS', 'FAILED'
    private LocalDateTime paidAt;

    public BillPayment() {
        this.status = "SUCCESS";
        this.paidAt = LocalDateTime.now();
    }

    public BillPayment(int paymentId, String paymentRef, int customerId, int accountNumber, int billerId,
                       String consumerNumber, BigDecimal amount, String status, LocalDateTime paidAt) {
        this.paymentId = paymentId;
        this.paymentRef = paymentRef;
        this.customerId = customerId;
        this.accountNumber = accountNumber;
        this.billerId = billerId;
        this.consumerNumber = consumerNumber;
        this.amount = amount;
        this.status = status != null ? status : "SUCCESS";
        this.paidAt = paidAt != null ? paidAt : LocalDateTime.now();
    }

    public int getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(int paymentId) {
        this.paymentId = paymentId;
    }

    public String getPaymentRef() {
        return paymentRef;
    }

    public void setPaymentRef(String paymentRef) {
        this.paymentRef = paymentRef;
    }

    public int getCustomerId() {
        return customerId;
    }

    public void setCustomerId(int customerId) {
        this.customerId = customerId;
    }

    public int getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(int accountNumber) {
        this.accountNumber = accountNumber;
    }

    public int getBillerId() {
        return billerId;
    }

    public void setBillerId(int billerId) {
        this.billerId = billerId;
    }

    public String getBillerName() {
        return billerName;
    }

    public void setBillerName(String billerName) {
        this.billerName = billerName;
    }

    public String getConsumerNumber() {
        return consumerNumber;
    }

    public void setConsumerNumber(String consumerNumber) {
        this.consumerNumber = consumerNumber;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(LocalDateTime paidAt) {
        this.paidAt = paidAt;
    }
}
