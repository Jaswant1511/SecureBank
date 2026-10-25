package com.securebank.dao;

import com.securebank.model.Transaction;
import com.securebank.model.TransactionType;
import com.securebank.util.CurrencyFormatter;
import com.securebank.util.DatabaseConfig;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * SecureBank: Role-Based Core Banking & Transaction Management System
 * File: TransactionDAO.java
 *
 * Persistence operations for Financial Ledger Transactions.
 */
public class TransactionDAO {

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public int recordTransaction(Connection conn, Transaction txn) throws SQLException {
        final String sql = "INSERT INTO transactions "
            + "(transaction_ref, account_number, txn_type, amount, balance_after, remarks, related_account_number, txn_timestamp) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        boolean isLocalConn = false;
        if (conn == null) {
            conn = DatabaseConfig.getConnection();
            isLocalConn = true;
        }
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, txn.getTransactionRef());
            ps.setInt(2, txn.getAccountNumber());
            ps.setString(3, txn.getTxnType().name());
            ps.setBigDecimal(4, txn.getAmount());
            ps.setBigDecimal(5, txn.getBalanceAfter());
            ps.setString(6, txn.getRemarks());
            if (txn.getRelatedAccountNumber() != null && txn.getRelatedAccountNumber() > 0) {
                ps.setInt(7, txn.getRelatedAccountNumber());
            } else {
                ps.setNull(7, java.sql.Types.INTEGER);
            }
            ps.setTimestamp(8, Timestamp.valueOf(txn.getTimestamp() != null ? txn.getTimestamp() : LocalDateTime.now()));

            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    txn.setTransactionId(id);
                    return id;
                }
            }
            return 0;
        } finally {
            if (isLocalConn && conn != null) {
                conn.close();
            }
        }
    }

    public List<Transaction> getRecentTransactions(int accountNumber, int limit) throws SQLException {
        final String sql = "SELECT transaction_id, transaction_ref, account_number, txn_type, amount, balance_after, remarks, related_account_number, txn_timestamp "
            + "FROM transactions WHERE account_number = ? ORDER BY txn_timestamp DESC, transaction_id DESC LIMIT ?";
        List<Transaction> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, accountNumber);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToTransaction(rs));
                }
            }
        }
        return list;
    }

    public String[] fetchLastFiveTransactions(Connection conn, int accountNumber) throws SQLException {
        final String sql = "SELECT transaction_id, transaction_ref, txn_type, amount, balance_after, remarks, txn_timestamp "
            + "FROM transactions WHERE account_number = ? ORDER BY txn_timestamp DESC, transaction_id DESC LIMIT 5";
        String[] buffer = new String[5];
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, accountNumber);
            try (ResultSet rs = ps.executeQuery()) {
                int idx = 0;
                while (rs.next() && idx < 5) {
                    Timestamp ts = rs.getTimestamp("txn_timestamp");
                    String timeStr = (ts != null) ? ts.toLocalDateTime().format(TS_FMT) : "N/A";
                    String ref = rs.getString("transaction_ref");
                    buffer[idx++] = String.format(
                        "[%s] %-12s | Ref: %-16s | Amt: %s | Bal: %s | %s",
                        timeStr,
                        rs.getString("txn_type"),
                        ref != null ? ref : ("#" + rs.getInt("transaction_id")),
                        CurrencyFormatter.format(rs.getBigDecimal("amount")),
                        CurrencyFormatter.format(rs.getBigDecimal("balance_after")),
                        rs.getString("remarks")
                    );
                }
            }
        }
        return buffer;
    }

    public List<Transaction> filterTransactions(
        int accountNumber,
        LocalDateTime fromDate,
        LocalDateTime toDate,
        TransactionType type,
        int offset,
        int limit
    ) throws SQLException {
        StringBuilder sql = new StringBuilder(
            "SELECT transaction_id, transaction_ref, account_number, txn_type, amount, balance_after, remarks, related_account_number, txn_timestamp "
            + "FROM transactions WHERE account_number = ?"
        );
        List<Object> params = new ArrayList<>();
        params.add(accountNumber);

        if (fromDate != null) {
            sql.append(" AND txn_timestamp >= ?");
            params.add(Timestamp.valueOf(fromDate));
        }
        if (toDate != null) {
            sql.append(" AND txn_timestamp <= ?");
            params.add(Timestamp.valueOf(toDate));
        }
        if (type != null) {
            sql.append(" AND txn_type = ?");
            params.add(type.name());
        }

        sql.append(" ORDER BY txn_timestamp DESC, transaction_id DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        List<Transaction> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToTransaction(rs));
                }
            }
        }
        return list;
    }

    private Transaction mapRowToTransaction(ResultSet rs) throws SQLException {
        Transaction t = new Transaction();
        t.setTransactionId(rs.getInt("transaction_id"));
        t.setTransactionRef(rs.getString("transaction_ref"));
        t.setAccountNumber(rs.getInt("account_number"));
        t.setTxnType(TransactionType.fromString(rs.getString("txn_type")));
        t.setAmount(rs.getBigDecimal("amount"));
        t.setBalanceAfter(rs.getBigDecimal("balance_after"));
        t.setRemarks(rs.getString("remarks"));
        int related = rs.getInt("related_account_number");
        if (!rs.wasNull()) {
            t.setRelatedAccountNumber(related);
        }
        Timestamp ts = rs.getTimestamp("txn_timestamp");
        if (ts != null) {
            t.setTimestamp(ts.toLocalDateTime());
        }
        return t;
    }
}
