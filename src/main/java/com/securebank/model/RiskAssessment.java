package com.securebank.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Transparent, explainable fraud and risk assessment evaluation result.
 * Demonstrates transparent rule triggers and score breakdown without opaque black-box claims.
 */
public class RiskAssessment {

    private int riskScore; // 0 to 100
    private RiskLevel riskLevel;
    private List<String> triggeredRules = new ArrayList<>();
    private List<String> ruleExplanations = new ArrayList<>();
    private String recommendedAction;

    public RiskAssessment() {
        this.riskScore = 0;
        this.riskLevel = RiskLevel.LOW;
        this.recommendedAction = "PROCEED";
    }

    public RiskAssessment(int riskScore, RiskLevel riskLevel, String recommendedAction) {
        this.riskScore = Math.min(100, Math.max(0, riskScore));
        this.riskLevel = riskLevel != null ? riskLevel : RiskLevel.fromScore(this.riskScore);
        this.recommendedAction = recommendedAction;
    }

    public void addRuleTrigger(String ruleName, int scoreWeight, String explanation) {
        this.triggeredRules.add(ruleName);
        this.ruleExplanations.add(explanation + " (+ " + scoreWeight + " pts)");
        this.riskScore = Math.min(100, this.riskScore + scoreWeight);
        this.riskLevel = RiskLevel.fromScore(this.riskScore);

        if (this.riskLevel == RiskLevel.CRITICAL) {
            this.recommendedAction = "FLAG_FOR_ADMIN_REVIEW";
        } else if (this.riskLevel == RiskLevel.HIGH) {
            this.recommendedAction = "REQUIRE_SECONDARY_VERIFICATION";
        } else if (this.riskLevel == RiskLevel.MEDIUM) {
            this.recommendedAction = "LOG_MONITORING_ALERT";
        } else {
            this.recommendedAction = "PROCEED";
        }
    }

    public int getRiskScore() {
        return riskScore;
    }

    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    public List<String> getTriggeredRules() {
        return triggeredRules;
    }

    public List<String> getRuleExplanations() {
        return ruleExplanations;
    }

    public String getRecommendedAction() {
        return recommendedAction;
    }

    public String getConsolidatedExplanation() {
        if (ruleExplanations.isEmpty()) {
            return "No suspicious risk patterns detected. Transaction cleared standard safety thresholds.";
        }
        return String.join(" | ", ruleExplanations);
    }
}
