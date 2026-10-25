package com.securebank.service;

import com.securebank.dao.BankDAO;
import com.securebank.dao.LedgerDAO;
import com.securebank.model.Account;
import com.securebank.model.EntryType;
import com.securebank.model.LedgerEntry;
import com.securebank.model.LedgerTransaction;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Double-Entry General Ledger Service.
 * Manages balanced, immutable ledger postings and financial integrity verification.
 * Standard Account #0 represents the Central Bank Reserve / Cash Vault Clearing Account.
 */
public class LedgerService {

    public static final int CENTRAL_RESERVE_ACCOUNT = 0;

    private final LedgerDAO ledgerDAO;
    private final BankDAO bankDAO;

    public LedgerService() {
        this.ledgerDAO = new LedgerDAO();
        this.bankDAO = new BankDAO();
    }

    public LedgerService(LedgerDAO ledgerDAO, BankDAO bankDAO) {
        this.ledgerDAO = ledgerDAO != null ? ledgerDAO : new LedgerDAO();
        this.bankDAO = bankDAO != null ? bankDAO : new BankDAO();
    }

    /**
     * Records an atomic double-entry deposit:
     * - Reserve Account #0 DEBIT amount (asset cash received into vault)
     * - Customer Account CREDIT amount (liability to customer increased)
     */
    public void postDeposit(String txnRef, int customerAccount, BigDecimal amount, String description, Connection conn) throws SQLException {
        LedgerTransaction lt = new LedgerTransaction(0, txnRef, "DEPOSIT", description, "POSTED", LocalDateTime.now());
        lt.addEntry(new LedgerEntry(0, 0, CENTRAL_RESERVE_ACCOUNT, EntryType.DEBIT, amount, LocalDateTime.now()));
        lt.addEntry(new LedgerEntry(0, 0, customerAccount, EntryType.CREDIT, amount, LocalDateTime.now()));
        ledgerDAO.postTransaction(lt, conn);
    }

    /**
     * Records an atomic double-entry withdrawal:
     * - Customer Account DEBIT amount (liability to customer reduced)
     * - Reserve Account #0 CREDIT amount (cash disbursed from vault)
     */
    public void postWithdrawal(String txnRef, int customerAccount, BigDecimal amount, String description, Connection conn) throws SQLException {
        LedgerTransaction lt = new LedgerTransaction(0, txnRef, "WITHDRAW", description, "POSTED", LocalDateTime.now());
        lt.addEntry(new LedgerEntry(0, 0, customerAccount, EntryType.DEBIT, amount, LocalDateTime.now()));
        lt.addEntry(new LedgerEntry(0, 0, CENTRAL_RESERVE_ACCOUNT, EntryType.CREDIT, amount, LocalDateTime.now()));
        ledgerDAO.postTransaction(lt, conn);
    }

    /**
     * Records an atomic double-entry inter-account transfer:
     * - Sender Account DEBIT amount
     * - Receiver Account CREDIT amount
     */
    public void postTransfer(String txnRef, int fromAccount, int toAccount, BigDecimal amount, String description, Connection conn) throws SQLException {
        LedgerTransaction lt = new LedgerTransaction(0, txnRef, "TRANSFER", description, "POSTED", LocalDateTime.now());
        lt.addEntry(new LedgerEntry(0, 0, fromAccount, EntryType.DEBIT, amount, LocalDateTime.now()));
        lt.addEntry(new LedgerEntry(0, 0, toAccount, EntryType.CREDIT, amount, LocalDateTime.now()));
        ledgerDAO.postTransaction(lt, conn);
    }

    /**
     * Records an atomic double-entry compensating reversal:
     * Inverts the original debits and credits.
     */
    public void postReversal(String revRef, int debitAccount, int creditAccount, BigDecimal amount, String description, Connection conn) throws SQLException {
        LedgerTransaction lt = new LedgerTransaction(0, revRef, "REVERSAL", description, "POSTED", LocalDateTime.now());
        lt.addEntry(new LedgerEntry(0, 0, debitAccount, EntryType.DEBIT, amount, LocalDateTime.now()));
        lt.addEntry(new LedgerEntry(0, 0, creditAccount, EntryType.CREDIT, amount, LocalDateTime.now()));
        ledgerDAO.postTransaction(lt, conn);
    }

    public List<LedgerEntry> getAccountLedger(int accountNumber, int limit) throws SQLException {
        return ledgerDAO.getEntriesForAccount(accountNumber, limit);
    }

    public BigDecimal getCalculatedLedgerBalance(int accountNumber) throws SQLException {
        return ledgerDAO.calculateLedgerBalance(accountNumber);
    }

    /**
     * Audits the integrity of the double-entry general ledger:
     * 1. Confirms 0 unbalanced journal transactions exist across the system.
     * 2. Compares stored balance vs calculated ledger balance for all active accounts.
     */
    public Map<String, Object> auditLedgerIntegrity() throws SQLException {
        Map<String, Object> report = new HashMap<>();
        int unbalancedTxns = ledgerDAO.getUnbalancedTransactionCount();
        report.put("unbalancedTransactions", unbalancedTxns);
        report.put("isGeneralLedgerBalanced", unbalancedTxns == 0);

        List<Account> allAccounts = bankDAO.getAllAccounts();
        int matched = 0;
        int discrepancies = 0;
        List<Map<String, Object>> discrepancyDetails = new ArrayList<>();

        for (Account acc : allAccounts) {
            BigDecimal ledgerBal = ledgerDAO.calculateLedgerBalance(acc.getAccountNumber());
            // Opening balance or difference check
            BigDecimal storedBal = acc.getBalance();
            BigDecimal diff = storedBal.subtract(ledgerBal).abs();
            if (diff.compareTo(new BigDecimal("0.01")) <= 0) {
                matched++;
            } else {
                discrepancies++;
                Map<String, Object> item = new HashMap<>();
                item.put("accountNumber", acc.getAccountNumber());
                item.put("holderName", acc.getHolderName());
                item.put("storedBalance", storedBal);
                item.put("ledgerBalance", ledgerBal);
                item.put("difference", storedBal.subtract(ledgerBal));
                discrepancyDetails.add(item);
            }
        }

        report.put("totalAccountsAudited", allAccounts.size());
        report.put("matchedAccounts", matched);
        report.put("discrepancyCount", discrepancies);
        report.put("discrepancies", discrepancyDetails);
        report.put("status", (unbalancedTxns == 0 && discrepancies == 0) ? "BALANCED" : "DISCREPANCY_DETECTED");
        return report;
    }
}
