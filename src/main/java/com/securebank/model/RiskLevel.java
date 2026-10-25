package com.securebank.model;

/**
 * Severity classification for explainable risk engine evaluations.
 */
public enum RiskLevel {
    LOW(0, 30, "Low Risk — Normal execution"),
    MEDIUM(31, 60, "Medium Risk — Internal monitoring event recorded"),
    HIGH(61, 85, "High Risk — Requires secondary verification or elevated audit"),
    CRITICAL(86, 100, "Critical Risk — Suspicious anomaly flagged for compliance review");

    private final int minScore;
    private final int maxScore;
    private final String description;

    RiskLevel(int minScore, int maxScore, String description) {
        this.minScore = minScore;
        this.maxScore = maxScore;
        this.description = description;
    }

    public static RiskLevel fromScore(int score) {
        if (score >= 86) return CRITICAL;
        if (score >= 61) return HIGH;
        if (score >= 31) return MEDIUM;
        return LOW;
    }

    public int getMinScore() {
        return minScore;
    }

    public int getMaxScore() {
        return maxScore;
    }

    public String getDescription() {
        return description;
    }
}
