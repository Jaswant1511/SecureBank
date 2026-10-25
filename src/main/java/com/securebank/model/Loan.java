package com.securebank.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * Banking Loan Facility Entity.
 * Tracks application, approval, disbursement, and EMI repayment schedule.
 */
public class Loan {

    private int loanId;
    private String loanRef;
    private int customerId;
    private String loanType; // 'PERSONAL', 'HOME', 'EDUCATION', 'AUTO'
    private BigDecimal principalAmount;
    private BigDecimal interestRate;
    private int tenureMonths;
    private BigDecimal emiAmount;
    private BigDecimal outstandingBalance;
    private Integer disbursedAccount;
    private String status; // 'PENDING_APPROVAL', 'APPROVED', 'DISBURSED', 'REJECTED', 'CLOSED'
    private LocalDateTime createdAt;

    public Loan() {
        this.status = "PENDING_APPROVAL";
        this.createdAt = LocalDateTime.now();
    }

    public Loan(int loanId, String loanRef, int customerId, String loanType, BigDecimal principalAmount,
                BigDecimal interestRate, int tenureMonths, BigDecimal emiAmount, BigDecimal outstandingBalance,
                Integer disbursedAccount, String status, LocalDateTime createdAt) {
        this.loanId = loanId;
        this.loanRef = loanRef;
        this.customerId = customerId;
        this.loanType = loanType;
        this.principalAmount = principalAmount;
        this.interestRate = interestRate;
        this.tenureMonths = tenureMonths;
        this.emiAmount = emiAmount;
        this.outstandingBalance = outstandingBalance;
        this.disbursedAccount = disbursedAccount;
        this.status = status != null ? status : "PENDING_APPROVAL";
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
    }

    /**
     * Calculates Equated Monthly Installment (EMI) using standard formula:
     * EMI = [P * r * (1 + r)^n] / [(1 + r)^n - 1]
     * where r = annualRate / (12 * 100), n = tenureMonths
     */
    public static BigDecimal calculateEMI(BigDecimal principal, BigDecimal annualRate, int months) {
        if (principal == null || annualRate == null || months <= 0) {
            return BigDecimal.ZERO;
        }
        double p = principal.doubleValue();
        double r = (annualRate.doubleValue() / 12.0) / 100.0;
        double factor = Math.pow(1.0 + r, months);
        double emi = (p * r * factor) / (factor - 1.0);
        return BigDecimal.valueOf(emi).setScale(2, RoundingMode.HALF_UP);
    }

    public int getLoanId() {
        return loanId;
    }

    public void setLoanId(int loanId) {
        this.loanId = loanId;
    }

    public String getLoanRef() {
        return loanRef;
    }

    public void setLoanRef(String loanRef) {
        this.loanRef = loanRef;
    }

    public int getCustomerId() {
        return customerId;
    }

    public void setCustomerId(int customerId) {
        this.customerId = customerId;
    }

    public String getLoanType() {
        return loanType;
    }

    public void setLoanType(String loanType) {
        this.loanType = loanType;
    }

    public BigDecimal getPrincipalAmount() {
        return principalAmount;
    }

    public void setPrincipalAmount(BigDecimal principalAmount) {
        this.principalAmount = principalAmount;
    }

    public BigDecimal getInterestRate() {
        return interestRate;
    }

    public void setInterestRate(BigDecimal interestRate) {
        this.interestRate = interestRate;
    }

    public int getTenureMonths() {
        return tenureMonths;
    }

    public void setTenureMonths(int tenureMonths) {
        this.tenureMonths = tenureMonths;
    }

    public BigDecimal getEmiAmount() {
        return emiAmount;
    }

    public void setEmiAmount(BigDecimal emiAmount) {
        this.emiAmount = emiAmount;
    }

    public BigDecimal getOutstandingBalance() {
        return outstandingBalance;
    }

    public void setOutstandingBalance(BigDecimal outstandingBalance) {
        this.outstandingBalance = outstandingBalance;
    }

    public Integer getDisbursedAccount() {
        return disbursedAccount;
    }

    public void setDisbursedAccount(Integer disbursedAccount) {
        this.disbursedAccount = disbursedAccount;
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
}
