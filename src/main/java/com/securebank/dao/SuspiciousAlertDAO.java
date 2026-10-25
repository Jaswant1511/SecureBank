package com.securebank.dao;

import com.securebank.model.SuspiciousActivityAlert;
import com.securebank.util.DatabaseConfig;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Persistence operations for automated fraud and explainable risk activity alerts.
 */
public class SuspiciousAlertDAO {

    public long recordAlert(SuspiciousActivityAlert alert) throws SQLException {
        final String sql = "INSERT INTO suspicious_alerts "
            + "(event_timestamp, user_id, account_number, rule_triggered, severity, evidence, status, risk_score) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setTimestamp(1, Timestamp.valueOf(alert.getTimestamp() != null ? alert.getTimestamp() : LocalDateTime.now()));
            if (alert.getUserId() != null) {
                ps.setInt(2, alert.getUserId());
            } else {
                ps.setNull(2, java.sql.Types.INTEGER);
            }
            if (alert.getAccountNumber() != null) {
                ps.setInt(3, alert.getAccountNumber());
            } else {
                ps.setNull(3, java.sql.Types.INTEGER);
            }
            ps.setString(4, alert.getRuleTriggered());
            ps.setString(5, alert.getSeverity() != null ? alert.getSeverity() : "MEDIUM");
            ps.setString(6, alert.getEvidence());
            ps.setString(7, alert.getStatus() != null ? alert.getStatus() : "OPEN");
            ps.setInt(8, alert.getRiskScore());

            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    long id = rs.getLong(1);
                    alert.setAlertId(id);
                    return id;
                }
            }
            return 0;
        }
    }

    public List<SuspiciousActivityAlert> getAllAlerts(int limit) throws SQLException {
        final String sql = "SELECT alert_id, event_timestamp, user_id, account_number, rule_triggered, severity, evidence, status, risk_score, review_decision, reviewer_username, reviewed_at "
            + "FROM suspicious_alerts ORDER BY event_timestamp DESC, alert_id DESC LIMIT ?";
        List<SuspiciousActivityAlert> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(extractAlert(rs));
                }
            }
        }
        return list;
    }

    public List<SuspiciousActivityAlert> getAlertsByStatus(String status, int limit) throws SQLException {
        final String sql = "SELECT alert_id, event_timestamp, user_id, account_number, rule_triggered, severity, evidence, status, risk_score, review_decision, reviewer_username, reviewed_at "
            + "FROM suspicious_alerts WHERE status = ? ORDER BY event_timestamp DESC, alert_id DESC LIMIT ?";
        List<SuspiciousActivityAlert> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(extractAlert(rs));
                }
            }
        }
        return list;
    }

    public boolean updateAlertStatus(long alertId, String newStatus) throws SQLException {
        final String sql = "UPDATE suspicious_alerts SET status = ? WHERE alert_id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newStatus);
            ps.setLong(2, alertId);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean resolveAlert(long alertId, String decision, String reviewerUsername) throws SQLException {
        return resolveAlert(alertId, "RESOLVED", decision, reviewerUsername);
    }

    public boolean resolveAlert(long alertId, String newStatus, String decision, String reviewerUsername) throws SQLException {
        final String sql = "UPDATE suspicious_alerts SET status = ?, review_decision = ?, reviewer_username = ?, reviewed_at = ? WHERE alert_id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newStatus);
            ps.setString(2, decision);
            ps.setString(3, reviewerUsername);
            ps.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now()));
            ps.setLong(5, alertId);
            return ps.executeUpdate() > 0;
        }
    }

    private SuspiciousActivityAlert extractAlert(ResultSet rs) throws SQLException {
        Timestamp ts = rs.getTimestamp("event_timestamp");
        LocalDateTime time = ts != null ? ts.toLocalDateTime() : LocalDateTime.now();
        Integer userId = rs.getObject("user_id") != null ? rs.getInt("user_id") : null;
        Integer accNum = rs.getObject("account_number") != null ? rs.getInt("account_number") : null;
        Timestamp revTs = rs.getTimestamp("reviewed_at");
        LocalDateTime revTime = revTs != null ? revTs.toLocalDateTime() : null;

        return new SuspiciousActivityAlert(
            rs.getLong("alert_id"),
            time,
            userId,
            accNum,
            rs.getString("rule_triggered"),
            rs.getString("severity"),
            rs.getString("evidence"),
            rs.getString("status"),
            rs.getInt("risk_score"),
            rs.getString("review_decision"),
            rs.getString("reviewer_username"),
            revTime
        );
    }
}
