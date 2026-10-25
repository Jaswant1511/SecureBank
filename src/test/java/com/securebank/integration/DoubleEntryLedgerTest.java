package com.securebank.integration;

import com.securebank.dao.BankDAO;
import com.securebank.dao.LedgerDAO;
import com.securebank.model.Account;
import com.securebank.model.EntryType;
import com.securebank.model.LedgerEntry;
import com.securebank.model.LedgerTransaction;
import com.securebank.model.Role;
import com.securebank.model.SavingsAccount;
import com.securebank.model.User;
import com.securebank.service.BankingService;
import com.securebank.service.LedgerService;
import com.securebank.util.DatabaseConfig;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DoubleEntryLedgerTest {

    private LedgerDAO ledgerDAO;
    private LedgerService ledgerService;
    private BankDAO bankDAO;
    private BankingService bankingService;

    @BeforeEach
    public void setup() {
        DatabaseConfig.switchToEmbeddedH2();
        DatabaseConfig.seedTestData();
        this.ledgerDAO = new LedgerDAO();
        this.bankDAO = new BankDAO();
        this.ledgerService = new LedgerService(ledgerDAO, bankDAO);
        this.bankingService = new BankingService();
    }

    @Test
    @DisplayName("General Ledger: Transaction balancing invariant sum(DEBIT) == sum(CREDIT)")
    public void testLedgerBalancingInvariant() {
        LedgerTransaction lt = new LedgerTransaction(0, "TXN-TEST-BAL", "TRANSFER", "Test balanced entry", "POSTED", LocalDateTime.now());
        lt.addEntry(new LedgerEntry(0, 0, 1001, EntryType.DEBIT, new BigDecimal("1500.00"), LocalDateTime.now()));
        lt.addEntry(new LedgerEntry(0, 0, 1002, EntryType.CREDIT, new BigDecimal("1500.00"), LocalDateTime.now()));

        assertTrue(lt.isBalanced(), "Ledger transaction with equal debits and credits must be balanced.");

        LedgerTransaction unbalanced = new LedgerTransaction(0, "TXN-TEST-UNBAL", "DEPOSIT", "Unbalanced test", "POSTED", LocalDateTime.now());
        unbalanced.addEntry(new LedgerEntry(0, 0, 0, EntryType.DEBIT, new BigDecimal("1000.00"), LocalDateTime.now()));
        unbalanced.addEntry(new LedgerEntry(0, 0, 1001, EntryType.CREDIT, new BigDecimal("800.00"), LocalDateTime.now()));

        assertFalse(unbalanced.isBalanced(), "Ledger transaction with unequal debits and credits must not be balanced.");
    }

    @Test
    @DisplayName("General Ledger: Account creation atomically records opening balanced ledger entry")
    public void testAccountOpeningLedgerPosting() throws Exception {
        Account acc = new SavingsAccount("Rohan Ledger Test", new BigDecimal("5000.00"), "1234");
        acc.setCustomerId(1);
        int newAccNo = bankDAO.createAccount(acc);

        List<LedgerEntry> entries = ledgerDAO.getEntriesForAccount(newAccNo, 10);
        assertFalse(entries.isEmpty(), "Opening balance must produce a ledger entry for the new account.");
        assertEquals(EntryType.CREDIT, entries.get(0).getEntryType(), "Opening deposit should credit customer account (liability).");
        assertEquals(0, new BigDecimal("5000.00").compareTo(entries.get(0).getAmount()));

        BigDecimal calcBalance = ledgerDAO.calculateLedgerBalance(newAccNo);
        assertEquals(0, new BigDecimal("5000.00").compareTo(calcBalance), "Calculated ledger balance must equal opening balance.");
    }

    @Test
    @DisplayName("General Ledger: Inter-account transfer atomically posts balanced debit and credit entries")
    public void testTransferLedgerPosting() throws Exception {
        User teller = new User(2, "teller1", "hash", Role.TELLER, 0, false, true);
        BigDecimal transferAmount = new BigDecimal("1000.00");

        BigDecimal senderBalBefore = bankDAO.getAccountByNumber(1001).getBalance();
        BigDecimal receiverBalBefore = bankDAO.getAccountByNumber(1002).getBalance();

        bankingService.transfer(1001, "1234", 1002, transferAmount, "Double-entry transfer test", teller);

        BigDecimal senderBalAfter = bankDAO.getAccountByNumber(1001).getBalance();
        BigDecimal receiverBalAfter = bankDAO.getAccountByNumber(1002).getBalance();

        assertEquals(0, senderBalBefore.subtract(transferAmount).compareTo(senderBalAfter));
        assertEquals(0, receiverBalBefore.add(transferAmount).compareTo(receiverBalAfter));

        List<LedgerEntry> senderEntries = ledgerDAO.getEntriesForAccount(1001, 1);
        assertEquals(EntryType.DEBIT, senderEntries.get(0).getEntryType(), "Sender should have a DEBIT ledger entry.");
        assertEquals(0, transferAmount.compareTo(senderEntries.get(0).getAmount()));

        List<LedgerEntry> receiverEntries = ledgerDAO.getEntriesForAccount(1002, 1);
        assertEquals(EntryType.CREDIT, receiverEntries.get(0).getEntryType(), "Receiver should have a CREDIT ledger entry.");
        assertEquals(0, transferAmount.compareTo(receiverEntries.get(0).getAmount()));
    }

    @Test
    @DisplayName("General Ledger: Audit confirms zero unbalanced journal entries across the system")
    public void testGeneralLedgerAuditIntegrity() throws Exception {
        Map<String, Object> audit = ledgerService.auditLedgerIntegrity();
        assertNotNull(audit);
        assertEquals(0, audit.get("unbalancedTransactions"), "No unbalanced journal entries should exist in ledger.");
        assertTrue((Boolean) audit.get("isGeneralLedgerBalanced"), "General ledger must report balanced status.");
    }
}
