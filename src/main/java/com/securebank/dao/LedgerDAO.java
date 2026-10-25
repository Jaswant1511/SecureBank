package com.securebank.dao;

import com.securebank.model.EntryType;
import com.securebank.model.LedgerEntry;
import com.securebank.model.LedgerTransaction;
import com.securebank.util.DatabaseConfig;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Double-Entry General Ledger Persistence Engine.
 * Enforces atomic posting of balanced debit and credit entries.
 */
public class LedgerDAO {

    /**
     * Atomically posts a double-entry transaction and its entries within the caller's connection.
     * Enforces the mathematical invariant: sum(DEBIT) == sum(CREDIT)
     */
    public int postTransaction(LedgerTransaction txn, Connection conn) throws SQLException {
        if (!txn.isBalanced()) {
            throw new IllegalArgumentException("Unbalanced double-entry ledger transaction: Debits must exactly equal Credits.");
        }

        final String insertTxnSql = "INSERT INTO ledger_transactions (transaction_ref, txn_type, description, status, created_at) VALUES (?, ?, ?, ?, ?)";
        int txnId = 0;
        try (PreparedStatement ps = conn.prepareStatement(insertTxnSql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, txn.getTransactionRef());
            ps.setString(2, txn.getTxnType());
            ps.setString(3, txn.getDescription());
            ps.setString(4, txn.getStatus());
            ps.setTimestamp(5, Timestamp.valueOf(txn.getCreatedAt()));
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    txnId = rs.getInt(1);
                    txn.setLedgerTxnId(txnId);
                }
            }
        }

        final String insertEntrySql = "INSERT INTO ledger_entries (ledger_txn_id, account_number, entry_type, amount, created_at) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(insertEntrySql, Statement.RETURN_GENERATED_KEYS)) {
            for (LedgerEntry entry : txn.getEntries()) {
                ps.setInt(1, txnId);
                ps.setInt(2, entry.getAccountNumber());
                ps.setString(3, entry.getEntryType().name());
                ps.setBigDecimal(4, entry.getAmount());
                ps.setTimestamp(5, Timestamp.valueOf(entry.getCreatedAt()));
                ps.addBatch();
            }
            ps.executeBatch();
        }
        return txnId;
    }

    public List<LedgerEntry> getEntriesForAccount(int accountNumber, int limit) throws SQLException {
        final String sql = "SELECT entry_id, ledger_txn_id, account_number, entry_type, amount, created_at "
                + "FROM ledger_entries WHERE account_number = ? ORDER BY created_at DESC, entry_id DESC LIMIT ?";
        List<LedgerEntry> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, accountNumber);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new LedgerEntry(
                            rs.getInt("entry_id"),
                            rs.getInt("ledger_txn_id"),
                            rs.getInt("account_number"),
                            EntryType.valueOf(rs.getString("entry_type")),
                            rs.getBigDecimal("amount"),
                            rs.getTimestamp("created_at").toLocalDateTime()
                    ));
                }
            }
        }
        return list;
    }

    public BigDecimal calculateLedgerBalance(int accountNumber) throws SQLException {
        final String sql = "SELECT "
                + "COALESCE(SUM(CASE WHEN entry_type = 'CREDIT' THEN amount ELSE 0 END), 0) - "
                + "COALESCE(SUM(CASE WHEN entry_type = 'DEBIT' THEN amount ELSE 0 END), 0) AS balance "
                + "FROM ledger_entries WHERE account_number = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, accountNumber);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBigDecimal("balance");
                }
            }
        }
        return BigDecimal.ZERO;
    }

    public int getUnbalancedTransactionCount() throws SQLException {
        final String sql = "SELECT COUNT(*) FROM ("
                + "SELECT ledger_txn_id, "
                + "SUM(CASE WHEN entry_type = 'DEBIT' THEN amount ELSE 0 END) AS total_debit, "
                + "SUM(CASE WHEN entry_type = 'CREDIT' THEN amount ELSE 0 END) AS total_credit "
                + "FROM ledger_entries GROUP BY ledger_txn_id "
                + "HAVING total_debit != total_credit"
                + ") t";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    public List<LedgerTransaction> getRecentTransactions(int limit) throws SQLException {
        final String sql = "SELECT ledger_txn_id, transaction_ref, txn_type, description, status, created_at "
                + "FROM ledger_transactions ORDER BY created_at DESC, ledger_txn_id DESC LIMIT ?";
        List<LedgerTransaction> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    LedgerTransaction lt = new LedgerTransaction(
                            rs.getInt("ledger_txn_id"),
                            rs.getString("transaction_ref"),
                            rs.getString("txn_type"),
                            rs.getString("description"),
                            rs.getString("status"),
                            rs.getTimestamp("created_at").toLocalDateTime()
                    );
                    list.add(lt);
                }
            }
        }
        return list;
    }
}
