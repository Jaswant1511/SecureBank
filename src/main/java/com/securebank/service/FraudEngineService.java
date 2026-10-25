package com.securebank.service;

import com.securebank.dao.SuspiciousAlertDAO;
import com.securebank.dao.TransactionDAO;
import com.securebank.model.Account;
import com.securebank.model.RiskAssessment;
import com.securebank.model.SuspiciousActivityAlert;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Explainable, Configurable Rule-Based Fraud Detection & Risk Scoring Engine.
 * Transparently computes risk scores (0-100) based on observable financial behaviors,
 * providing evidence trails for administrative compliance audits.
 */
public class FraudEngineService {

    private static final Logger LOGGER = Logger.getLogger(FraudEngineService.class.getName());

    public static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("50000.00");
    public static final BigDecimal CRITICAL_VALUE_THRESHOLD = new BigDecimal("200000.00");
    public static final int VELOCITY_BURST_THRESHOLD = 5;

    private final SuspiciousAlertDAO alertDAO;
    private final TransactionDAO transactionDAO;

    public FraudEngineService() {
        this.alertDAO = new SuspiciousAlertDAO();
        this.transactionDAO = new TransactionDAO();
    }

    public FraudEngineService(SuspiciousAlertDAO alertDAO, TransactionDAO transactionDAO) {
        this.alertDAO = alertDAO != null ? alertDAO : new SuspiciousAlertDAO();
        this.transactionDAO = transactionDAO != null ? transactionDAO : new TransactionDAO();
    }

    /**
     * Evaluates transaction risk and creates an explainable assessment.
     */
    public RiskAssessment evaluateTransactionRisk(Account account, BigDecimal amount, String txnType, int userId) {
        RiskAssessment assessment = new RiskAssessment();

        // Rule 1: High Value Threshold
        if (amount != null && amount.compareTo(HIGH_VALUE_THRESHOLD) > 0) {
            assessment.addRuleTrigger("HIGH_VALUE_TRANSACTION", 35,
                    String.format("Transaction amount ₹%s exceeds standard single-operation threshold ₹%s",
                            amount.toPlainString(), HIGH_VALUE_THRESHOLD.toPlainString()));
        }

        // Rule 2: Critical Value Outlier
        if (amount != null && amount.compareTo(CRITICAL_VALUE_THRESHOLD) > 0) {
            assessment.addRuleTrigger("CRITICAL_VALUE_OUTLIER", 30,
                    String.format("Transaction amount ₹%s exceeds critical threshold ₹%s",
                            amount.toPlainString(), CRITICAL_VALUE_THRESHOLD.toPlainString()));
        }

        // Rule 3: Account Liquidity Drain (>80% of balance)
        if (account != null && amount != null && account.getBalance().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal eightyPercent = account.getBalance().multiply(new BigDecimal("0.80"));
            if (amount.compareTo(eightyPercent) > 0 && ("WITHDRAW".equalsIgnoreCase(txnType) || "TRANSFER".equalsIgnoreCase(txnType))) {
                assessment.addRuleTrigger("LIQUIDITY_DRAIN", 25,
                        String.format("Outflow ₹%s drains over 80%% of available balance ₹%s",
                                amount.toPlainString(), account.getBalance().toPlainString()));
            }
        }

        // Rule 4: Velocity Burst Check
        if (account != null) {
            try {
                int recentCount = transactionDAO.getRecentTransactions(account.getAccountNumber(), 10).size();
                if (recentCount >= VELOCITY_BURST_THRESHOLD) {
                    assessment.addRuleTrigger("RAPID_TRANSACTION_BURST", 25,
                            String.format("High transaction velocity: %d operations in recent window (threshold: %d)",
                                    recentCount, VELOCITY_BURST_THRESHOLD));
                }
            } catch (Exception ignored) {
            }
        }

        // Record alert if risk is elevated (score >= 31, Medium+)
        if (assessment.getRiskScore() >= 31 && account != null) {
            recordAssessmentAlertQuietly(userId, account.getAccountNumber(), assessment);
        }

        return assessment;
    }

    private void recordAssessmentAlertQuietly(Integer userId, Integer accountNumber, RiskAssessment assessment) {
        try {
            SuspiciousActivityAlert alert = new SuspiciousActivityAlert(
                    0,
                    LocalDateTime.now(),
                    userId,
                    accountNumber,
                    String.join(", ", assessment.getTriggeredRules()),
                    assessment.getRiskLevel().name(),
                    assessment.getConsolidatedExplanation(),
                    "OPEN",
                    assessment.getRiskScore(),
                    null,
                    null,
                    null
            );
            alertDAO.recordAlert(alert);
            LOGGER.info(String.format("[FRAUD_ENGINE] Logged %s risk alert (Score: %d) for account #%s",
                    assessment.getRiskLevel(), assessment.getRiskScore(), accountNumber));
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to record risk alert: " + e.getMessage());
        }
    }

    public List<SuspiciousActivityAlert> getAllAlerts(int limit) throws SQLException {
        return alertDAO.getAllAlerts(limit);
    }

    public List<SuspiciousActivityAlert> getAlertsByStatus(String status, int limit) throws SQLException {
        return alertDAO.getAlertsByStatus(status, limit);
    }

    public boolean resolveAlert(long alertId, String decision, String reviewerUsername) throws SQLException {
        String newStatus = "CONFIRMED_FRAUD".equalsIgnoreCase(decision) ? "CONFIRMED_FRAUD" : "RESOLVED";
        return alertDAO.resolveAlert(alertId, newStatus, decision, reviewerUsername);
    }
}
