package com.securebank.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Represents a saved, pre-authorized transfer beneficiary belonging to a Customer.
 */
public class Beneficiary {

    private int beneficiaryId;
    private int customerId;
    private int beneficiaryAccount;
    private String beneficiaryName;
    private String bankName;
    private String ifscCode;
    private BigDecimal transferLimit;
    private String status; // 'ACTIVE', 'INACTIVE'
    private LocalDateTime createdAt;

    public Beneficiary() {
        this.status = "ACTIVE";
        this.bankName = "SecureBank";
        this.ifscCode = "SCBK0001001";
        this.transferLimit = new BigDecimal("100000.00");
        this.createdAt = LocalDateTime.now();
    }

    public Beneficiary(int beneficiaryId, int customerId, int beneficiaryAccount, String beneficiaryName,
                       String bankName, String ifscCode, BigDecimal transferLimit, String status, LocalDateTime createdAt) {
        this.beneficiaryId = beneficiaryId;
        this.customerId = customerId;
        this.beneficiaryAccount = beneficiaryAccount;
        this.beneficiaryName = beneficiaryName;
        this.bankName = (bankName != null && !bankName.isEmpty()) ? bankName : "SecureBank";
        this.ifscCode = (ifscCode != null && !ifscCode.isEmpty()) ? ifscCode : "SCBK0001001";
        this.transferLimit = transferLimit != null ? transferLimit : new BigDecimal("100000.00");
        this.status = (status != null && !status.isEmpty()) ? status : "ACTIVE";
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
    }

    public int getBeneficiaryId() {
        return beneficiaryId;
    }

    public void setBeneficiaryId(int beneficiaryId) {
        this.beneficiaryId = beneficiaryId;
    }

    public int getCustomerId() {
        return customerId;
    }

    public void setCustomerId(int customerId) {
        this.customerId = customerId;
    }

    public int getBeneficiaryAccount() {
        return beneficiaryAccount;
    }

    public void setBeneficiaryAccount(int beneficiaryAccount) {
        this.beneficiaryAccount = beneficiaryAccount;
    }

    public String getBeneficiaryName() {
        return beneficiaryName;
    }

    public void setBeneficiaryName(String beneficiaryName) {
        this.beneficiaryName = beneficiaryName;
    }

    public String getBankName() {
        return bankName;
    }

    public void setBankName(String bankName) {
        this.bankName = bankName;
    }

    public String getIfscCode() {
        return ifscCode;
    }

    public void setIfscCode(String ifscCode) {
        this.ifscCode = ifscCode;
    }

    public BigDecimal getTransferLimit() {
        return transferLimit;
    }

    public void setTransferLimit(BigDecimal transferLimit) {
        this.transferLimit = transferLimit;
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
