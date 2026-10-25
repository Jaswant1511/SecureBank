package com.securebank.integration;

import com.securebank.model.Account;
import com.securebank.model.RiskAssessment;
import com.securebank.model.RiskLevel;
import com.securebank.model.SavingsAccount;
import com.securebank.service.FraudEngineService;
import com.securebank.util.DatabaseConfig;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ExplainableFraudEngineTest {

    private FraudEngineService fraudEngine;
    private Account testAccount;

    @BeforeEach
    public void setup() {
        DatabaseConfig.switchToEmbeddedH2();
        DatabaseConfig.seedTestData();
        this.fraudEngine = new FraudEngineService();
        this.testAccount = new SavingsAccount("Test User", new BigDecimal("100000.00"), "1234");
        this.testAccount.setAccountNumber(9901);
    }

    @Test
    @DisplayName("Fraud Engine: Normal low-value transaction evaluates to LOW risk with zero flags")
    public void testLowRiskTransaction() {
        RiskAssessment assessment = fraudEngine.evaluateTransactionRisk(testAccount, new BigDecimal("1000.00"), "TRANSFER", 103);
        assertNotNull(assessment);
        assertEquals(0, assessment.getRiskScore());
        assertEquals(RiskLevel.LOW, assessment.getRiskLevel());
        assertTrue(assessment.getTriggeredRules().isEmpty());
    }

    @Test
    @DisplayName("Fraud Engine: High-value transaction rule triggers +35 risk score")
    public void testHighValueRiskRule() {
        RiskAssessment assessment = fraudEngine.evaluateTransactionRisk(testAccount, new BigDecimal("75000.00"), "TRANSFER", 103);
        assertNotNull(assessment);
        assertTrue(assessment.getRiskScore() >= 35, "Transaction exceeding ₹50,000 threshold must trigger high value points.");
        assertTrue(assessment.getTriggeredRules().contains("HIGH_VALUE_TRANSACTION"));
        assertEquals(RiskLevel.MEDIUM, assessment.getRiskLevel());
    }

    @Test
    @DisplayName("Fraud Engine: Near total liquidity drain triggers +25 risk score")
    public void testLiquidityDrainRule() {
        // ₹90,000 withdrawn from ₹100,000 balance is 90% drain (>80%)
        RiskAssessment assessment = fraudEngine.evaluateTransactionRisk(testAccount, new BigDecimal("90000.00"), "WITHDRAW", 103);
        assertNotNull(assessment);
        assertTrue(assessment.getRiskScore() >= 25, "Draining >80% of account balance must trigger liquidity drain points.");
        assertTrue(assessment.getTriggeredRules().contains("LIQUIDITY_DRAIN"));
    }

    @Test
    @DisplayName("Fraud Engine: Critical value outlier (> ₹200,000) compounds risk score")
    public void testCriticalValueOutlierRule() {
        Account largeAcc = new SavingsAccount("Wealth User", new BigDecimal("500000.00"), "1234");
        largeAcc.setAccountNumber(9902);
        RiskAssessment assessment = fraudEngine.evaluateTransactionRisk(largeAcc, new BigDecimal("250000.00"), "TRANSFER", 103);
        assertNotNull(assessment);
        assertTrue(assessment.getTriggeredRules().contains("HIGH_VALUE_TRANSACTION"));
        assertTrue(assessment.getTriggeredRules().contains("CRITICAL_VALUE_OUTLIER"));
        assertTrue(assessment.getRiskScore() >= 65);
        assertEquals(RiskLevel.HIGH, assessment.getRiskLevel());
    }

    @Test
    @DisplayName("Fraud Engine: Multiple simultaneous signals compound to CRITICAL risk capped at 100")
    public void testCompoundCriticalRisk() {
        // Amount 250,000 (>50k: 35 pts, >200k: 30 pts, >80% of 260,000 balance: 25 pts) = 90 pts (CRITICAL)
        Account acc = new SavingsAccount("Drain User", new BigDecimal("260000.00"), "1234");
        acc.setAccountNumber(9903);
        RiskAssessment assessment = fraudEngine.evaluateTransactionRisk(acc, new BigDecimal("250000.00"), "WITHDRAW", 103);
        assertNotNull(assessment);
        assertTrue(assessment.getRiskScore() >= 80, "Multiple combined indicators must compound score.");
        assertEquals(RiskLevel.CRITICAL, assessment.getRiskLevel(), "Score >= 76 must result in CRITICAL severity.");
        assertTrue(assessment.getTriggeredRules().size() >= 3);
        assertNotNull(assessment.getConsolidatedExplanation());
        assertFalse(assessment.getConsolidatedExplanation().isEmpty());
    }
}
