package com.securebank.dao;

import com.securebank.model.ReconciliationReport;
import com.securebank.util.DatabaseConfig;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * SecureBank: Complete Banking Automation System
 * File: ReconciliationDAO.java
 *
 * Persistence operations for automated ledger reconciliation runs.
 */
public class ReconciliationDAO {

    public long recordReconciliation(ReconciliationReport report) throws SQLException {
        final String sql = "INSERT INTO reconciliation_logs "
            + "(run_timestamp, total_accounts, matched_accounts, discrepancies, status, summary) "
            + "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setTimestamp(1, Timestamp.valueOf(report.getRunTimestamp() != null ? report.getRunTimestamp() : LocalDateTime.now()));
            ps.setInt(2, report.getTotalAccountsAudited());
            ps.setInt(3, report.getMatchedAccounts());
            ps.setInt(4, report.getDiscrepancyCount());
            ps.setString(5, report.getStatus());
            ps.setString(6, report.getSummary());

            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    long id = rs.getLong(1);
                    report.setReconId(id);
                    return id;
                }
            }
            return 0;
        }
    }

    public List<ReconciliationReport> getRecentReconciliations(int limit) throws SQLException {
        final String sql = "SELECT recon_id, run_timestamp, total_accounts, matched_accounts, discrepancies, status, summary "
            + "FROM reconciliation_logs ORDER BY run_timestamp DESC, recon_id DESC LIMIT ?";
        List<ReconciliationReport> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Timestamp ts = rs.getTimestamp("run_timestamp");
                    LocalDateTime runTime = ts != null ? ts.toLocalDateTime() : LocalDateTime.now();
                    list.add(new ReconciliationReport(
                        rs.getLong("recon_id"),
                        runTime,
                        rs.getInt("total_accounts"),
                        rs.getInt("matched_accounts"),
                        rs.getInt("discrepancies"),
                        Collections.emptyList(), // summary contains detailed items for storage efficiency
                        rs.getString("status"),
                        rs.getString("summary")
                    ));
                }
            }
        }
        return list;
    }

    public ReconciliationReport getLatestReconciliation() throws SQLException {
        List<ReconciliationReport> list = getRecentReconciliations(1);
        return list.isEmpty() ? null : list.get(0);
    }
}
