package com.securebank.service;

import com.securebank.dao.ReconciliationDAO;
import com.securebank.model.DiscrepancyItem;
import com.securebank.model.ReconciliationReport;
import com.securebank.model.SuspiciousActivityAlert;
import com.securebank.util.DatabaseConfig;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SecureBank: Complete Banking Automation System
 * File: ReconciliationService.java
 *
 * Automated Financial Ledger Reconciliation Engine.
 * Operates strictly in read-only audit mode by default.
 * Compares authoritative double-entry transaction ledgers against stored account balances.
 */
public class ReconciliationService {

    private static final Logger LOGGER = Logger.getLogger(ReconciliationService.class.getName());

    private final ReconciliationDAO reconciliationDAO;
    private final SuspiciousActivityService suspiciousActivityService;

    public ReconciliationService() {
        this.reconciliationDAO = new ReconciliationDAO();
        this.suspiciousActivityService = new SuspiciousActivityService();
    }

    public ReconciliationService(ReconciliationDAO reconciliationDAO, SuspiciousActivityService suspiciousActivityService) {
        this.reconciliationDAO = reconciliationDAO;
        this.suspiciousActivityService = suspiciousActivityService;
    }

    /**
     * Executes a complete read-only transaction reconciliation audit across all accounts.
     * Computes sum of ledger entries per account and compares against stored account balance.
     */
    public ReconciliationReport runReconciliation() {
        LOGGER.info("Starting automated ledger reconciliation audit...");
        List<DiscrepancyItem> discrepancies = new ArrayList<>();
        int totalAccounts = 0;
        int matchedAccounts = 0;

        final String accountSql = "SELECT account_number, holder_name, balance, status FROM accounts ORDER BY account_number ASC";
        final String ledgerSql = "SELECT txn_type, amount FROM transactions WHERE account_number = ? ORDER BY transaction_id ASC";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement psAcc = conn.prepareStatement(accountSql);
             ResultSet rsAcc = psAcc.executeQuery()) {

            while (rsAcc.next()) {
                totalAccounts++;
                int accNum = rsAcc.getInt("account_number");
                String holder = rsAcc.getString("holder_name");
                BigDecimal storedBalance = rsAcc.getBigDecimal("balance").setScale(2, RoundingMode.HALF_UP);

                BigDecimal expectedBalance = calculateLedgerBalance(conn, ledgerSql, accNum);

                if (storedBalance.compareTo(expectedBalance) == 0) {
                    matchedAccounts++;
                } else {
                    BigDecimal diff = storedBalance.subtract(expectedBalance);
                    String reason = String.format("Balance mismatch: Stored balance ₹%s != Ledger sum ₹%s (Diff: ₹%s)",
                        storedBalance.toPlainString(), expectedBalance.toPlainString(), diff.toPlainString());
                    discrepancies.add(new DiscrepancyItem(accNum, holder, storedBalance, expectedBalance, diff, reason));
                }
            }

            // Check for asymmetric transfer records
            checkTransferSymmetry(conn, discrepancies);

            // Check for unbalanced double-entry ledger transactions
            checkGeneralLedgerBalance(conn, discrepancies);

            // Check for missing transaction references
            checkMissingTransactionReferences(conn, discrepancies);

            // Check for external payment discrepancies (stuck pending payments)
            checkExternalPaymentAnomalies(conn, discrepancies);

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error during reconciliation run: " + e.getMessage(), e);
            return new ReconciliationReport(0, LocalDateTime.now(), totalAccounts, matchedAccounts,
                discrepancies.size(), discrepancies, "ERROR", "Reconciliation halted due to SQL failure: " + e.getMessage());
        }

        String status = discrepancies.isEmpty() ? "BALANCED" : "DISCREPANCIES_FOUND";
        String summary = String.format("Reconciled %d accounts. %d balanced, %d discrepancies detected.",
            totalAccounts, matchedAccounts, discrepancies.size());

        ReconciliationReport report = new ReconciliationReport(
            0,
            LocalDateTime.now(),
            totalAccounts,
            matchedAccounts,
            discrepancies.size(),
            discrepancies,
            status,
            summary
        );

        // Record execution outcome in reconciliation_logs
        try {
            reconciliationDAO.recordReconciliation(report);
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to persist reconciliation log: " + e.getMessage());
        }

        // If discrepancies detected, trigger automated administrator alert
        if (!discrepancies.isEmpty()) {
            LOGGER.warning("[RECONCILIATION_ANOMALY] " + summary);
            SuspiciousActivityAlert alert = new SuspiciousActivityAlert(
                0,
                LocalDateTime.now(),
                null,
                discrepancies.get(0).getAccountNumber(),
                "RECONCILIATION_DISCREPANCY",
                "CRITICAL",
                summary + " First anomaly on account #" + discrepancies.get(0).getAccountNumber(),
                "OPEN"
            );
            try {
                new com.securebank.dao.SuspiciousAlertDAO().recordAlert(alert);
            } catch (SQLException ignored) {
            }
        } else {
            LOGGER.info("[RECONCILIATION_SUCCESS] " + summary);
        }

        return report;
    }

    private BigDecimal calculateLedgerBalance(Connection conn, String ledgerSql, int accNum) throws SQLException {
        BigDecimal balance = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        try (PreparedStatement psLedger = conn.prepareStatement(ledgerSql)) {
            psLedger.setInt(1, accNum);
            try (ResultSet rs = psLedger.executeQuery()) {
                while (rs.next()) {
                    String type = rs.getString("txn_type");
                    BigDecimal amt = rs.getBigDecimal("amount");
                    if (amt == null) {
                        continue;
                    }
                    try {
                        com.securebank.model.TransactionType tt = com.securebank.model.TransactionType.valueOf(type.toUpperCase());
                        if (tt.isCredit()) {
                            balance = balance.add(amt);
                        } else if (tt.isDebit()) {
                            balance = balance.subtract(amt);
                        }
                    } catch (IllegalArgumentException e) {
                        if ("OPEN".equalsIgnoreCase(type) || "DEPOSIT".equalsIgnoreCase(type) ||
                            "TRANSFER_IN".equalsIgnoreCase(type) || "INTEREST".equalsIgnoreCase(type)) {
                            balance = balance.add(amt);
                        } else if ("WITHDRAW".equalsIgnoreCase(type) || "TRANSFER_OUT".equalsIgnoreCase(type) ||
                                   "FEE".equalsIgnoreCase(type)) {
                            balance = balance.subtract(amt);
                        }
                    }
                }
            }
        }
        return balance;
    }

    private void checkTransferSymmetry(Connection conn, List<DiscrepancyItem> discrepancies) {
        final String sql = "SELECT sender_account, receiver_account, amount FROM transfer_details";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int sender = rs.getInt("sender_account");
                int receiver = rs.getInt("receiver_account");
                BigDecimal amt = rs.getBigDecimal("amount");
                if (sender <= 0 || receiver <= 0 || amt == null || amt.compareTo(BigDecimal.ZERO) <= 0) {
                    discrepancies.add(new DiscrepancyItem(sender, "Transfer Record", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                            "Invalid transfer record detected between #" + sender + " and #" + receiver));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.FINE, "Transfer symmetry check notice: " + e.getMessage());
        }
    }

    private void checkGeneralLedgerBalance(Connection conn, List<DiscrepancyItem> discrepancies) {
        final String sql =
            "SELECT lt.ledger_txn_id, lt.transaction_ref, " +
            "SUM(CASE WHEN le.entry_type = 'DEBIT' THEN le.amount ELSE 0 END) AS total_debit, " +
            "SUM(CASE WHEN le.entry_type = 'CREDIT' THEN le.amount ELSE 0 END) AS total_credit " +
            "FROM ledger_transactions lt " +
            "JOIN ledger_entries le ON lt.ledger_txn_id = le.ledger_txn_id " +
            "GROUP BY lt.ledger_txn_id, lt.transaction_ref " +
            "HAVING SUM(CASE WHEN le.entry_type = 'DEBIT' THEN le.amount ELSE 0 END) != " +
            "       SUM(CASE WHEN le.entry_type = 'CREDIT' THEN le.amount ELSE 0 END)";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String ref = rs.getString("transaction_ref");
                BigDecimal debits = rs.getBigDecimal("total_debit");
                BigDecimal credits = rs.getBigDecimal("total_credit");
                discrepancies.add(new DiscrepancyItem(0, "General Ledger: " + ref, debits, credits,
                        debits.subtract(credits), "Unbalanced double-entry ledger transaction: Debits ₹" + debits + " != Credits ₹" + credits));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.FINE, "Ledger balance check notice: " + e.getMessage());
        }
    }

    private void checkMissingTransactionReferences(Connection conn, List<DiscrepancyItem> discrepancies) {
        final String sql = "SELECT transaction_id, account_number, amount, txn_type FROM transactions WHERE transaction_ref IS NULL OR TRIM(transaction_ref) = ''";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int txnId = rs.getInt("transaction_id");
                int acc = rs.getInt("account_number");
                BigDecimal amt = rs.getBigDecimal("amount");
                discrepancies.add(new DiscrepancyItem(acc, "Transaction #" + txnId, amt, BigDecimal.ZERO, amt,
                        "Missing transaction reference for transaction #" + txnId + " on account #" + acc));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.FINE, "Missing transaction reference check notice: " + e.getMessage());
        }
    }

    private void checkExternalPaymentAnomalies(Connection conn, List<DiscrepancyItem> discrepancies) {
        final String sql = "SELECT payment_id, client_reference, account_number, amount, status FROM external_payments WHERE status = 'PENDING'";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int pid = rs.getInt("payment_id");
                String ref = rs.getString("client_reference");
                int acc = rs.getInt("account_number");
                BigDecimal amt = rs.getBigDecimal("amount");
                discrepancies.add(new DiscrepancyItem(acc, "External Payment #" + pid, amt, BigDecimal.ZERO, amt,
                        "External payment " + ref + " in PENDING status requires provider inquiry/settlement."));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.FINE, "External payment anomaly check notice: " + e.getMessage());
        }
    }

    public List<ReconciliationReport> getRecentReports(int limit) {
        try {
            return reconciliationDAO.getRecentReconciliations(limit);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to retrieve reconciliation history: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public SuspiciousActivityService getSuspiciousActivityService() {
        return suspiciousActivityService;
    }
}
