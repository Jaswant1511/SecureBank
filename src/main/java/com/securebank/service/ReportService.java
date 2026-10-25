package com.securebank.service;

import com.securebank.util.DatabaseConfig;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SecureBank: Complete Banking Automation System
 * File: ReportService.java
 *
 * Automated Financial & Administrative Reporting Engine.
 * Aggregates verified database metrics and exports RFC 4180 compliant CSV reports.
 */
public class ReportService {

    private static final Logger LOGGER = Logger.getLogger(ReportService.class.getName());
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Generates an aggregated financial summary for a given time window.
     * @param period "DAILY", "WEEKLY", "MONTHLY", or "ALL"
     */
    public Map<String, Object> generateSummaryReport(String period) {
        LocalDateTime start;
        LocalDateTime now = LocalDateTime.now();
        if ("DAILY".equalsIgnoreCase(period)) {
            start = LocalDate.now().atStartOfDay();
        } else if ("WEEKLY".equalsIgnoreCase(period)) {
            start = now.minusDays(7);
        } else if ("MONTHLY".equalsIgnoreCase(period)) {
            start = now.minusDays(30);
        } else {
            start = LocalDateTime.of(2000, 1, 1, 0, 0); // All time
        }

        Map<String, Object> report = new HashMap<>();
        report.put("period", period != null ? period.toUpperCase() : "ALL");
        report.put("generatedAt", now.format(TS_FMT));
        report.put("startDate", start.format(TS_FMT));
        report.put("endDate", now.format(TS_FMT));

        BigDecimal depositTotal = BigDecimal.ZERO;
        int depositCount = 0;
        BigDecimal withdrawTotal = BigDecimal.ZERO;
        int withdrawCount = 0;
        BigDecimal transferTotal = BigDecimal.ZERO;
        int transferCount = 0;

        final String sql = "SELECT txn_type, COUNT(*) as txn_cnt, COALESCE(SUM(amount), 0) as txn_sum "
            + "FROM transactions WHERE txn_timestamp >= ? AND txn_timestamp <= ? "
            + "GROUP BY txn_type";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(start));
            ps.setTimestamp(2, Timestamp.valueOf(now));

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String type = rs.getString("txn_type");
                    int count = rs.getInt("txn_cnt");
                    BigDecimal sum = rs.getBigDecimal("txn_sum").setScale(2, RoundingMode.HALF_UP);

                    if ("DEPOSIT".equalsIgnoreCase(type) || "OPEN".equalsIgnoreCase(type)) {
                        depositTotal = depositTotal.add(sum);
                        depositCount += count;
                    } else if ("WITHDRAW".equalsIgnoreCase(type)) {
                        withdrawTotal = withdrawTotal.add(sum);
                        withdrawCount += count;
                    } else if ("TRANSFER_OUT".equalsIgnoreCase(type) || "TRANSFER_IN".equalsIgnoreCase(type)) {
                        // Transfer out represents debited transfer amount
                        if ("TRANSFER_OUT".equalsIgnoreCase(type)) {
                            transferTotal = transferTotal.add(sum);
                            transferCount += count;
                        }
                    }
                }
            }

            report.put("depositTotal", depositTotal);
            report.put("depositCount", depositCount);
            report.put("withdrawTotal", withdrawTotal);
            report.put("withdrawCount", withdrawCount);
            report.put("transferTotal", transferTotal);
            report.put("transferCount", transferCount);
            report.put("netVolume", depositTotal.add(withdrawTotal).add(transferTotal));

            // Count total active accounts
            final String accSql = "SELECT COUNT(*) FROM accounts WHERE status = 'ACTIVE'";
            try (PreparedStatement psAcc = conn.prepareStatement(accSql);
                 ResultSet rsAcc = psAcc.executeQuery()) {
                if (rsAcc.next()) {
                    report.put("activeAccounts", rsAcc.getInt(1));
                }
            }

            // Count failed audit security events in this period
            final String failSql = "SELECT COUNT(*) FROM audit_logs WHERE status != 'SUCCESS' AND log_timestamp >= ?";
            try (PreparedStatement psFail = conn.prepareStatement(failSql)) {
                psFail.setTimestamp(1, Timestamp.valueOf(start));
                try (ResultSet rsFail = psFail.executeQuery()) {
                    if (rsFail.next()) {
                        report.put("securityIncidents", rsFail.getInt(1));
                    }
                }
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to generate summary report: " + e.getMessage(), e);
            report.put("error", e.getMessage());
        }

        return report;
    }

    /**
     * Exports transactions for an account (or all accounts if null) to RFC 4180 compliant CSV.
     */
    public String exportTransactionsToCSV(Integer accountNumber, LocalDateTime from, LocalDateTime to) {
        StringWriter sw = new StringWriter();
        sw.append("TransactionRef,AccountNumber,Type,Amount,BalanceAfter,Timestamp,Remarks\n");

        StringBuilder sql = new StringBuilder("SELECT transaction_ref, account_number, txn_type, amount, balance_after, txn_timestamp, remarks FROM transactions WHERE 1=1 ");
        if (accountNumber != null && accountNumber > 0) {
            sql.append("AND account_number = ? ");
        }
        if (from != null) {
            sql.append("AND txn_timestamp >= ? ");
        }
        if (to != null) {
            sql.append("AND txn_timestamp <= ? ");
        }
        sql.append("ORDER BY txn_timestamp DESC, transaction_id DESC");

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int p = 1;
            if (accountNumber != null && accountNumber > 0) {
                ps.setInt(p++, accountNumber);
            }
            if (from != null) {
                ps.setTimestamp(p++, Timestamp.valueOf(from));
            }
            if (to != null) {
                ps.setTimestamp(p++, Timestamp.valueOf(to));
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String ref = escapeCSV(rs.getString("transaction_ref"));
                    int acc = rs.getInt("account_number");
                    String type = escapeCSV(rs.getString("txn_type"));
                    BigDecimal amt = rs.getBigDecimal("amount");
                    BigDecimal bal = rs.getBigDecimal("balance_after");
                    Timestamp ts = rs.getTimestamp("txn_timestamp");
                    String remarks = escapeCSV(rs.getString("remarks"));

                    sw.append(String.format("%s,%d,%s,%s,%s,%s,%s\n",
                        ref, acc, type,
                        amt != null ? amt.toPlainString() : "0.00",
                        bal != null ? bal.toPlainString() : "0.00",
                        ts != null ? ts.toLocalDateTime().format(TS_FMT) : "",
                        remarks));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "CSV export failed: " + e.getMessage(), e);
        }

        return sw.toString();
    }

    /**
     * Exports complete security audit log to RFC 4180 compliant CSV.
     */
    public String exportAuditLogsToCSV(LocalDateTime from, LocalDateTime to) {
        StringWriter sw = new StringWriter();
        sw.append("LogId,Timestamp,Actor,Role,Action,EntityType,EntityId,Status,Details,IpAddress\n");

        String sql = "SELECT log_id, log_timestamp, actor_username, role, action, entity_type, entity_id, status, details, ip_address FROM audit_logs WHERE 1=1 ";
        if (from != null) sql += "AND log_timestamp >= ? ";
        if (to != null) sql += "AND log_timestamp <= ? ";
        sql += "ORDER BY log_timestamp DESC LIMIT 1000";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int p = 1;
            if (from != null) ps.setTimestamp(p++, Timestamp.valueOf(from));
            if (to != null) ps.setTimestamp(p++, Timestamp.valueOf(to));

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    sw.append(String.format("%d,%s,%s,%s,%s,%s,%s,%s,%s,%s\n",
                        rs.getInt("log_id"),
                        rs.getTimestamp("log_timestamp").toLocalDateTime().format(TS_FMT),
                        escapeCSV(rs.getString("actor_username")),
                        escapeCSV(rs.getString("role")),
                        escapeCSV(rs.getString("action")),
                        escapeCSV(rs.getString("entity_type")),
                        escapeCSV(rs.getString("entity_id")),
                        escapeCSV(rs.getString("status")),
                        escapeCSV(rs.getString("details")),
                        escapeCSV(rs.getString("ip_address"))
                    ));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Audit CSV export failed: " + e.getMessage(), e);
        }
        return sw.toString();
    }

    /**
     * Escapes CSV values conforming to RFC 4180 and neutralizes CSV Formula Injection (CWE-1236).
     * Values beginning with =, +, -, @, \t, or \r are prefixed with a single quote to prevent
     * spreadsheet formula execution in Microsoft Excel, LibreOffice, and Google Sheets.
     */
    public static String escapeCSV(String value) {
        if (value == null) return "";
        String sanitized = value;
        if (!sanitized.isEmpty()) {
            char first = sanitized.charAt(0);
            if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
                sanitized = "'" + sanitized;
            }
        }
        if (sanitized.contains(",") || sanitized.contains("\"") || sanitized.contains("\n") || sanitized.contains("\r")) {
            return "\"" + sanitized.replace("\"", "\"\"") + "\"";
        }
        return sanitized;
    }
}
