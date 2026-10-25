package com.securebank.model;

import java.math.BigDecimal;

/**
 * Debit Card management entity.
 * Supports viewing card, toggling card block/freeze status, updating daily spending limits,
 * and managing contactless NFC payment settings.
 */
public class DebitCard {

    private int cardId;
    private int accountNumber;
    private int customerId;
    private String cardNumberMasked;
    private String cardType; // 'VISA_PLATINUM', 'MASTERCARD_GOLD', 'RUPAY_CLASSIC'
    private BigDecimal dailyLimit;
    private boolean isBlocked;
    private boolean contactlessEnabled;
    private String expiryDate;

    public DebitCard() {
        this.cardType = "VISA_PLATINUM";
        this.dailyLimit = new BigDecimal("50000.00");
        this.isBlocked = false;
        this.contactlessEnabled = true;
        this.expiryDate = "12/29";
    }

    public DebitCard(int cardId, int accountNumber, int customerId, String cardNumberMasked, String cardType,
                     BigDecimal dailyLimit, boolean isBlocked, boolean contactlessEnabled, String expiryDate) {
        this.cardId = cardId;
        this.accountNumber = accountNumber;
        this.customerId = customerId;
        this.cardNumberMasked = cardNumberMasked;
        this.cardType = cardType != null ? cardType : "VISA_PLATINUM";
        this.dailyLimit = dailyLimit != null ? dailyLimit : new BigDecimal("50000.00");
        this.isBlocked = isBlocked;
        this.contactlessEnabled = contactlessEnabled;
        this.expiryDate = expiryDate != null ? expiryDate : "12/29";
    }

    public int getCardId() {
        return cardId;
    }

    public void setCardId(int cardId) {
        this.cardId = cardId;
    }

    public int getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(int accountNumber) {
        this.accountNumber = accountNumber;
    }

    public int getCustomerId() {
        return customerId;
    }

    public void setCustomerId(int customerId) {
        this.customerId = customerId;
    }

    public String getCardNumberMasked() {
        return cardNumberMasked;
    }

    public void setCardNumberMasked(String cardNumberMasked) {
        this.cardNumberMasked = cardNumberMasked;
    }

    public String getCardType() {
        return cardType;
    }

    public void setCardType(String cardType) {
        this.cardType = cardType;
    }

    public BigDecimal getDailyLimit() {
        return dailyLimit;
    }

    public void setDailyLimit(BigDecimal dailyLimit) {
        this.dailyLimit = dailyLimit;
    }

    public boolean isBlocked() {
        return isBlocked;
    }

    public void setBlocked(boolean blocked) {
        isBlocked = blocked;
    }

    public boolean isContactlessEnabled() {
        return contactlessEnabled;
    }

    public void setContactlessEnabled(boolean contactlessEnabled) {
        this.contactlessEnabled = contactlessEnabled;
    }

    public String getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(String expiryDate) {
        this.expiryDate = expiryDate;
    }
}
