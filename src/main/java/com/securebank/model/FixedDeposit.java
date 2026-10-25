package com.securebank.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * Fixed Deposit entity with compound interest calculation and tenure tracking.
 */
public class FixedDeposit {

    private int fdId;
    private String fdRef;
    private int customerId;
    private int accountNumber;
    private BigDecimal principalAmount;
    private int tenureMonths;
    private BigDecimal interestRate;
    private BigDecimal maturityAmount;
    private String status; // 'ACTIVE', 'MATURED', 'PREMATURE_CLOSED'
    private LocalDateTime startDate;
    private LocalDateTime maturityDate;

    public FixedDeposit() {
        this.status = "ACTIVE";
        this.startDate = LocalDateTime.now();
    }

    public FixedDeposit(int fdId, String fdRef, int customerId, int accountNumber, BigDecimal principalAmount,
                        int tenureMonths, BigDecimal interestRate, BigDecimal maturityAmount, String status,
                        LocalDateTime startDate, LocalDateTime maturityDate) {
        this.fdId = fdId;
        this.fdRef = fdRef;
        this.customerId = customerId;
        this.accountNumber = accountNumber;
        this.principalAmount = principalAmount;
        this.tenureMonths = tenureMonths;
        this.interestRate = interestRate;
        this.maturityAmount = maturityAmount;
        this.status = status != null ? status : "ACTIVE";
        this.startDate = startDate != null ? startDate : LocalDateTime.now();
        this.maturityDate = maturityDate != null ? maturityDate : this.startDate.plusMonths(tenureMonths);
    }

    /**
     * Calculates maturity amount using quarterly compound interest standard:
     * A = P * (1 + r/400)^(4 * t) where t = tenureMonths / 12.0
     */
    public static BigDecimal calculateMaturity(BigDecimal principal, BigDecimal rate, int months) {
        if (principal == null || rate == null || months <= 0) {
            return BigDecimal.ZERO;
        }
        double p = principal.doubleValue();
        double r = rate.doubleValue() / 100.0;
        double t = months / 12.0;
        int n = 4; // Quarterly compounding
        double a = p * Math.pow(1.0 + (r / n), n * t);
        return BigDecimal.valueOf(a).setScale(2, RoundingMode.HALF_UP);
    }

    public int getFdId() {
        return fdId;
    }

    public void setFdId(int fdId) {
        this.fdId = fdId;
    }

    public String getFdRef() {
        return fdRef;
    }

    public void setFdRef(String fdRef) {
        this.fdRef = fdRef;
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

    public BigDecimal getPrincipalAmount() {
        return principalAmount;
    }

    public void setPrincipalAmount(BigDecimal principalAmount) {
        this.principalAmount = principalAmount;
    }

    public int getTenureMonths() {
        return tenureMonths;
    }

    public void setTenureMonths(int tenureMonths) {
        this.tenureMonths = tenureMonths;
    }

    public BigDecimal getInterestRate() {
        return interestRate;
    }

    public void setInterestRate(BigDecimal interestRate) {
        this.interestRate = interestRate;
    }

    public BigDecimal getMaturityAmount() {
        return maturityAmount;
    }

    public void setMaturityAmount(BigDecimal maturityAmount) {
        this.maturityAmount = maturityAmount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDateTime startDate) {
        this.startDate = startDate;
    }

    public LocalDateTime getMaturityDate() {
        return maturityDate;
    }

    public void setMaturityDate(LocalDateTime maturityDate) {
        this.maturityDate = maturityDate;
    }
}
