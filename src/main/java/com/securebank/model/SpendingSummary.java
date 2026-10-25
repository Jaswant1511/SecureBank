package com.securebank.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregated customer spending analytics covering category totals, largest outflows, and budget status.
 */
public class SpendingSummary {

    private BigDecimal totalSpent = BigDecimal.ZERO;
    private int transactionCount = 0;
    private Map<String, BigDecimal> categoryTotals = new HashMap<>();
    private List<CategoryDetail> categoryDetails = new ArrayList<>();
    private Transaction largestExpense;

    public static class CategoryDetail {
        private String category;
        private String displayName;
        private BigDecimal spent;
        private BigDecimal budgetLimit;
        private double percentUtilized;

        public CategoryDetail(String category, String displayName, BigDecimal spent, BigDecimal budgetLimit, double percentUtilized) {
            this.category = category;
            this.displayName = displayName;
            this.spent = spent;
            this.budgetLimit = budgetLimit;
            this.percentUtilized = percentUtilized;
        }

        public String getCategory() {
            return category;
        }

        public String getDisplayName() {
            return displayName;
        }

        public BigDecimal getSpent() {
            return spent;
        }

        public BigDecimal getBudgetLimit() {
            return budgetLimit;
        }

        public double getPercentUtilized() {
            return percentUtilized;
        }
    }

    public BigDecimal getTotalSpent() {
        return totalSpent;
    }

    public void setTotalSpent(BigDecimal totalSpent) {
        this.totalSpent = totalSpent;
    }

    public int getTransactionCount() {
        return transactionCount;
    }

    public void setTransactionCount(int transactionCount) {
        this.transactionCount = transactionCount;
    }

    public Map<String, BigDecimal> getCategoryTotals() {
        return categoryTotals;
    }

    public void setCategoryTotals(Map<String, BigDecimal> categoryTotals) {
        this.categoryTotals = categoryTotals != null ? categoryTotals : new HashMap<>();
    }

    public List<CategoryDetail> getCategoryDetails() {
        return categoryDetails;
    }

    public void setCategoryDetails(List<CategoryDetail> categoryDetails) {
        this.categoryDetails = categoryDetails != null ? categoryDetails : new ArrayList<>();
    }

    public Transaction getLargestExpense() {
        return largestExpense;
    }

    public void setLargestExpense(Transaction largestExpense) {
        this.largestExpense = largestExpense;
    }
}
