package com.securebank.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Represents a monthly spending limit target set by a Customer for a specific category.
 */
public class Budget {

    private int budgetId;
    private int customerId;
    private String category;
    private BigDecimal monthlyLimit;
    private LocalDateTime createdAt;

    public Budget() {
        this.createdAt = LocalDateTime.now();
    }

    public Budget(int budgetId, int customerId, String category, BigDecimal monthlyLimit, LocalDateTime createdAt) {
        this.budgetId = budgetId;
        this.customerId = customerId;
        this.category = category;
        this.monthlyLimit = monthlyLimit;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
    }

    public int getBudgetId() {
        return budgetId;
    }

    public void setBudgetId(int budgetId) {
        this.budgetId = budgetId;
    }

    public int getCustomerId() {
        return customerId;
    }

    public void setCustomerId(int customerId) {
        this.customerId = customerId;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public BigDecimal getMonthlyLimit() {
        return monthlyLimit;
    }

    public void setMonthlyLimit(BigDecimal monthlyLimit) {
        this.monthlyLimit = monthlyLimit;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
