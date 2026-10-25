package com.securebank.service;

import com.securebank.dao.SuspiciousAlertDAO;
import com.securebank.model.SuspiciousActivityAlert;
import com.securebank.util.DatabaseConfig;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SecureBank: Complete Banking Automation System
 * File: SuspiciousActivityService.java
 *
 * Automated, rule-based fraud and suspicious activity detection engine.
 * Generates auditable investigation alerts without silently blocking legitimate customers.
 */
public class SuspiciousActivityService {

    private static final Logger LOGGER = Logger.getLogger(SuspiciousActivityService.class.getName());
    private static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("50000.00");
    private static final int VELOCITY_COUNT_THRESHOLD = 5;
    private static final int VELOCITY_WINDOW_MINUTES = 10;

    private final SuspiciousAlertDAO alertDAO;

    public SuspiciousActivityService() {
        this.alertDAO = new SuspiciousAlertDAO();
    }

    public SuspiciousActivityService(SuspiciousAlertDAO alertDAO) {
        this.alertDAO = alertDAO;
    }

    /**
     * Rule: Repeated Failed Logins
     */
    public void checkFailedLogin(int userId, String username, int failedAttempts) {
        if (failedAttempts >= 3) {
            SuspiciousActivityAlert alert = new SuspiciousActivityAlert(
                0,
                LocalDateTime.now(),
                userId > 0 ? userId : null,
                null,
                "REPEATED_FAILED_LOGINS",
                "HIGH",
                "User '" + username + "' exceeded failed authentication threshold (" + failedAttempts + " failures). Account locked.",
                "OPEN"
            );
            recordAlertQuietly(alert);
        }
    }

    /**
     * Rule: High-Value Financial Outlier
     */
    public void checkHighValueTransaction(int accountNumber, BigDecimal amount, String txnType) {
        if (amount != null && amount.compareTo(HIGH_VALUE_THRESHOLD) >= 0) {
            SuspiciousActivityAlert alert = new SuspiciousActivityAlert(
                0,
                LocalDateTime.now(),
                null,
                accountNumber,
                "HIGH_VALUE_TRANSACTION",
                "MEDIUM",
                String.format("Unusual high-value %s transaction detected: ₹%s on account #%d (threshold: ₹%s)",
                    txnType, amount.toPlainString(), accountNumber, HIGH_VALUE_THRESHOLD.toPlainString()),
                "OPEN"
            );
            recordAlertQuietly(alert);
        }
    }

    /**
     * Rule: Rapid Velocity Burst (multiple transactions within a tight time window)
     */
    public void checkTransactionVelocity(int accountNumber) {
        try {
            int recentCount = countRecentTransactions(accountNumber, VELOCITY_WINDOW_MINUTES);
            if (recentCount >= VELOCITY_COUNT_THRESHOLD) {
                SuspiciousActivityAlert alert = new SuspiciousActivityAlert(
                    0,
                    LocalDateTime.now(),
                    null,
                    accountNumber,
                    "RAPID_TRANSACTION_BURST",
                    "HIGH",
                    String.format("Account #%d recorded %d transactions within %d minutes (threshold: %d).",
                        accountNumber, recentCount, VELOCITY_WINDOW_MINUTES, VELOCITY_COUNT_THRESHOLD),
                    "OPEN"
                );
                recordAlertQuietly(alert);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to evaluate transaction velocity for account " + accountNumber, e);
        }
    }

    /**
     * Rule: Repeated Rejected Withdrawals (Insufficient Funds Probe)
     */
    public void checkRejectedWithdrawal(int accountNumber, BigDecimal requestedAmount, BigDecimal availableBalance) {
        SuspiciousActivityAlert alert = new SuspiciousActivityAlert(
            0,
            LocalDateTime.now(),
            null,
            accountNumber,
            "REJECTED_WITHDRAWAL_PROBE",
            "LOW",
            String.format("Withdrawal rejected on account #%d: requested ₹%s with available balance ₹%s.",
                accountNumber, requestedAmount != null ? requestedAmount.toPlainString() : "0.00",
                availableBalance != null ? availableBalance.toPlainString() : "0.00"),
            "OPEN"
        );
        recordAlertQuietly(alert);
    }

    /**
     * Rule: Unauthorized IDOR Access Attempt (Horizontal Privilege Escalation)
     */
    public void checkUnauthorizedAccessAttempt(int userId, String username, int requestedAccountNumber) {
        SuspiciousActivityAlert alert = new SuspiciousActivityAlert(
            0,
            LocalDateTime.now(),
            userId,
            requestedAccountNumber,
            "UNAUTHORIZED_ACCESS_ATTEMPT",
            "CRITICAL",
            String.format("Security Violation: User '%s' (ID %d) attempted unauthorized direct access to account #%d.",
                username, userId, requestedAccountNumber),
            "OPEN"
        );
        recordAlertQuietly(alert);
    }

    public List<SuspiciousActivityAlert> getAllAlerts(int limit) {
        try {
            return alertDAO.getAllAlerts(limit);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to fetch suspicious alerts: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<SuspiciousActivityAlert> getOpenAlerts(int limit) {
        try {
            return alertDAO.getAlertsByStatus("OPEN", limit);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to fetch open suspicious alerts: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    public boolean updateAlertStatus(long alertId, String newStatus) {
        try {
            return alertDAO.updateAlertStatus(alertId, newStatus);
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to update alert status for alert " + alertId, e);
            return false;
        }
    }

    public void recordAlertQuietly(SuspiciousActivityAlert alert) {
        try {
            alertDAO.recordAlert(alert);
            LOGGER.info("[FRAUD_ALERT] Triggered " + alert.getRuleTriggered() + " for acc #" + alert.getAccountNumber() + ": " + alert.getEvidence());
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to persist fraud alert: " + e.getMessage());
        }
    }

    private int countRecentTransactions(int accountNumber, int minutes) throws SQLException {
        final String sql = "SELECT COUNT(*) FROM transactions WHERE account_number = ? AND txn_timestamp >= ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, accountNumber);
            ps.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now().minusMinutes(minutes)));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }
}
