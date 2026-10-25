package com.securebank.integration;

import com.securebank.dao.BankDAO;
import com.securebank.dao.CustomerDAO;
import com.securebank.dao.ExternalPaymentDAO;
import com.securebank.dao.LedgerDAO;
import com.securebank.exception.AuthorizationException;
import com.securebank.model.Account;
import com.securebank.model.ExternalPaymentRecord;
import com.securebank.model.ReconciliationReport;
import com.securebank.model.Role;
import com.securebank.model.User;
import com.securebank.service.ReconciliationService;
import com.securebank.service.payment.ExternalPaymentService;
import com.securebank.service.payment.PaymentRequest;
import com.securebank.service.payment.PaymentStatus;
import com.securebank.service.payment.SimulatedPaymentGateway;
import com.securebank.util.DatabaseConfig;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 11: Real-World Banking Engineering & Financial Integration Readiness Verification.
 */
public class FinancialIntegrationTest {

    private SimulatedPaymentGateway gateway;
    private ExternalPaymentDAO paymentDAO;
    private ExternalPaymentService paymentService;
    private BankDAO bankDAO;
    private CustomerDAO customerDAO;
    private ReconciliationService reconciliationService;
    private User aaravUser; // Customer owner of Account #1001
    private User priyaUser; // Customer owner of Account #1002

    @BeforeEach
    public void setup() throws Exception {
        DatabaseConfig.setProductionModeOverride(false);
        DatabaseConfig.switchToEmbeddedH2();
        DatabaseConfig.seedTestData();

        this.gateway = new SimulatedPaymentGateway();
        this.paymentDAO = new ExternalPaymentDAO();
        this.bankDAO = new BankDAO();
        this.customerDAO = new CustomerDAO();
        this.paymentService = new ExternalPaymentService(gateway, paymentDAO, bankDAO, new LedgerDAO(),
                customerDAO, new com.securebank.dao.AuditDAO(), new com.securebank.service.NotificationService(),
                new com.securebank.service.SuspiciousActivityService());
        this.reconciliationService = new ReconciliationService();

        this.aaravUser = new User(103, "aarav", "", Role.CUSTOMER, 0, false, true);
        this.priyaUser = new User(104, "priya", "", Role.CUSTOMER, 0, false, true);
    }

    @AfterEach
    public void tearDown() {
        DatabaseConfig.setProductionModeOverride(null);
    }

    @Test
    @DisplayName("Phase 11: Duplicate payment requests with same idempotency key are rejected/cached")
    public void testDuplicatePaymentRequestsIdempotency() throws Exception {
        int accNo = 1001;
        Account before = bankDAO.getAccountByNumber(accNo);
        BigDecimal initialBal = before.getBalance();
        String idempotencyKey = "IDEMP-TEST-" + UUID.randomUUID();

        // First submission
        ExternalPaymentRecord first = paymentService.initiateExternalPayment(
            accNo, "1234", new BigDecimal("1000.00"), "ICIC0001234", "ICICI Bank",
            "Supplier Invoice #1", idempotencyKey, aaravUser
        );
        assertNotNull(first);
        assertEquals(PaymentStatus.SUCCESS, first.getStatus());

        Account afterFirst = bankDAO.getAccountByNumber(accNo);
        assertEquals(initialBal.subtract(new BigDecimal("1000.00")), afterFirst.getBalance());

        // Duplicate submission with same idempotency key
        ExternalPaymentRecord duplicate = paymentService.initiateExternalPayment(
            accNo, "1234", new BigDecimal("1000.00"), "ICIC0001234", "ICICI Bank",
            "Supplier Invoice #1", idempotencyKey, aaravUser
        );

        // Must return the exact same payment record
        assertEquals(first.getPaymentId(), duplicate.getPaymentId());
        assertEquals(first.getClientReference(), duplicate.getClientReference());

        // Crucial invariant: Balance must NOT be debited a second time
        Account afterSecond = bankDAO.getAccountByNumber(accNo);
        assertEquals(afterFirst.getBalance(), afterSecond.getBalance(),
                "Duplicate payment submission must not cause double-debiting.");
    }

    @Test
    @DisplayName("Phase 11: Repeated provider callbacks deduplication prevents double-crediting/debiting")
    public void testRepeatedProviderCallbacksDeduplication() throws Exception {
        int accNo = 1001;
        Account before = bankDAO.getAccountByNumber(accNo);
        BigDecimal initialBal = before.getBalance();

        // 1. Initiate pending payment via TEST_PENDING flag
        String idempotencyKey = "IDEMP-PENDING-" + UUID.randomUUID();
        ExternalPaymentRecord pendingPayment = paymentService.initiateExternalPayment(
            accNo, "1234", new BigDecimal("500.00"), "HDFC0005678", "HDFC Bank",
            "Pending test payment [TEST_PENDING]", idempotencyKey, aaravUser
        );
        assertEquals(PaymentStatus.PENDING, pendingPayment.getStatus());

        // Balance should not be debited yet during PENDING state
        Account afterPending = bankDAO.getAccountByNumber(accNo);
        assertEquals(initialBal, afterPending.getBalance());

        // 2. Prepare genuine signed webhook callback
        String rawPayload = "{\"clientReference\":\"" + pendingPayment.getClientReference() +
                "\",\"providerReference\":\"" + pendingPayment.getProviderReference() + "\",\"status\":\"SUCCESS\"}";
        String signature = gateway.generateSignature(rawPayload);

        // First callback execution -> transitions to SUCCESS and executes internal debit
        boolean processedFirst = paymentService.processProviderCallback(
            rawPayload, signature, pendingPayment.getProviderReference(),
            pendingPayment.getClientReference(), PaymentStatus.SUCCESS
        );
        assertTrue(processedFirst);

        Account afterFirstCallback = bankDAO.getAccountByNumber(accNo);
        assertEquals(initialBal.subtract(new BigDecimal("500.00")), afterFirstCallback.getBalance());

        // Repeated delivery of the exact same callback (replay / network retry)
        boolean processedSecond = paymentService.processProviderCallback(
            rawPayload, signature, pendingPayment.getProviderReference(),
            pendingPayment.getClientReference(), PaymentStatus.SUCCESS
        );
        assertTrue(processedSecond);

        // Invariant: Balance must NOT be debited twice
        Account afterSecondCallback = bankDAO.getAccountByNumber(accNo);
        assertEquals(afterFirstCallback.getBalance(), afterSecondCallback.getBalance(),
                "Repeated provider webhook callbacks must be deduplicated and never double-debit.");
    }

    @Test
    @DisplayName("Phase 11: Invalid callback signature is strictly rejected with SecurityException")
    public void testInvalidCallbackSignatureRejection() {
        String rawPayload = "{\"clientReference\":\"EXT-FAKE-123\",\"providerReference\":\"SIM-FAKE\",\"status\":\"SUCCESS\"}";
        String forgedSignature = "forged_invalid_hmac_hex_signature_999999999999";

        assertThrows(SecurityException.class, () -> {
            paymentService.processProviderCallback(rawPayload, forgedSignature, "SIM-FAKE", "EXT-FAKE-123", PaymentStatus.SUCCESS);
        }, "Tampered or forged webhook callback signatures must be rejected immediately.");
    }

    @Test
    @DisplayName("Phase 11: Failed provider request does not debit customer account balance")
    public void testFailedProviderRequestDoesNotDebitAccount() throws Exception {
        int accNo = 1001;
        Account before = bankDAO.getAccountByNumber(accNo);
        BigDecimal initialBal = before.getBalance();

        // Amount ending in .50 triggers deterministic provider rejection
        BigDecimal testAmount = new BigDecimal("150.50");
        ExternalPaymentRecord result = paymentService.initiateExternalPayment(
            accNo, "1234", testAmount, "SBIN0001111", "SBI",
            "Payment destined to fail", "IDEMP-FAIL-" + UUID.randomUUID(), aaravUser
        );

        assertEquals(PaymentStatus.FAILED, result.getStatus());
        assertNotNull(result.getFailureReason());

        Account after = bankDAO.getAccountByNumber(accNo);
        assertEquals(initialBal, after.getBalance(),
                "Customer account balance must remain untouched when payment provider rejects request.");
    }

    @Test
    @DisplayName("Phase 11: Payment timeout handling records TIMEOUT without debiting account")
    public void testPaymentTimeoutHandling() throws Exception {
        int accNo = 1001;
        Account before = bankDAO.getAccountByNumber(accNo);
        BigDecimal initialBal = before.getBalance();

        // Amount ending in .99 triggers deterministic gateway network timeout
        BigDecimal testAmount = new BigDecimal("250.99");
        ExternalPaymentRecord result = paymentService.initiateExternalPayment(
            accNo, "1234", testAmount, "AXIS0009999", "Axis Bank",
            "Payment destined to timeout", "IDEMP-TIMEOUT-" + UUID.randomUUID(), aaravUser
        );

        assertEquals(PaymentStatus.TIMEOUT, result.getStatus());

        Account after = bankDAO.getAccountByNumber(accNo);
        assertEquals(initialBal, after.getBalance(),
                "Customer balance must not be debited when gateway request times out.");
    }

    @Test
    @DisplayName("Phase 11: Development payment simulator is strictly prohibited in PRODUCTION configuration")
    public void testDevelopmentSimulatorRestrictionsInProduction() {
        DatabaseConfig.setProductionModeOverride(true);
        assertTrue(DatabaseConfig.isProduction());

        SimulatedPaymentGateway prodGateway = new SimulatedPaymentGateway();
        assertTrue(prodGateway.isSimulated());

        PaymentRequest request = new PaymentRequest("REF-PROD", "IDEMP-PROD", 1001,
                new BigDecimal("500.00"), "ACC123", "BANK", "Production test");

        assertThrows(IllegalStateException.class, () -> {
            prodGateway.initiatePayment(request);
        }, "Simulated payment gateway must throw IllegalStateException when accessed in production mode.");

        assertThrows(IllegalStateException.class, () -> {
            prodGateway.inquirePaymentStatus("SIM-TXN-123");
        }, "Simulated payment status inquiry must throw IllegalStateException in production mode.");
    }

    @Test
    @DisplayName("Phase 11: External payment inquiry synchronizes pending payment and triggers ledger settlement")
    public void testExternalPaymentStatusInquiryAndSync() throws Exception {
        int accNo = 1001;
        Account before = bankDAO.getAccountByNumber(accNo);
        BigDecimal initialBal = before.getBalance();

        ExternalPaymentRecord pending = paymentService.initiateExternalPayment(
            accNo, "1234", new BigDecimal("400.00"), "KOTAK0001", "Kotak Mahindra Bank",
            "Pending test for sync [TEST_PENDING]", "IDEMP-SYNC-" + UUID.randomUUID(), aaravUser
        );
        assertEquals(PaymentStatus.PENDING, pending.getStatus());

        // Inquire and sync — default inquiry returns SUCCESS
        ExternalPaymentRecord synced = paymentService.inquireAndSyncPayment(pending.getPaymentId());
        assertNotNull(synced);
        assertEquals(PaymentStatus.SUCCESS, synced.getStatus());

        Account after = bankDAO.getAccountByNumber(accNo);
        assertEquals(initialBal.subtract(new BigDecimal("400.00")), after.getBalance(),
                "Account balance must be debited upon authoritative inquiry settlement.");
    }

    @Test
    @DisplayName("Phase 11: Unauthorized payment initiation enforces strict ownership (IDOR defense)")
    public void testUnauthorizedPaymentInitiationEnforcesOwnership() {
        // User 'priya' (customer #502) attempts to initiate payment from account #1001 (owned by customer #501 'aarav')
        int unownedAccount = 1001;
        assertThrows(AuthorizationException.class, () -> {
            paymentService.initiateExternalPayment(
                unownedAccount, "1234", new BigDecimal("300.00"), "DEST123", "BANK",
                "Unauthorized payment", "IDEMP-IDOR-" + UUID.randomUUID(), priyaUser
            );
        }, "Customer must not be allowed to initiate payments from accounts they do not own.");
    }

    @Test
    @DisplayName("Phase 11: Reconciliation engine detects general ledger balance anomalies")
    public void testReconciliationDetectsAllAnomalies() throws Exception {
        // Run standard reconciliation — should be balanced
        ReconciliationReport cleanReport = reconciliationService.runReconciliation();
        assertEquals("BALANCED", cleanReport.getStatus());
        assertEquals(0, cleanReport.getDiscrepancyCount());

        // Induce an unbalanced double-entry ledger entry directly in the database
        try (Connection conn = DatabaseConfig.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("INSERT INTO ledger_transactions (transaction_ref, txn_type, description, status) " +
                    "VALUES ('TXN-INDUCED-UNBAL', 'TRANSFER', 'Induced discrepancy for reconciliation audit', 'POSTED')");
            stmt.executeUpdate("INSERT INTO ledger_entries (ledger_txn_id, account_number, entry_type, amount) " +
                    "VALUES ((SELECT MAX(ledger_txn_id) FROM ledger_transactions), 1001, 'DEBIT', 999.00)");
            // Omit the matching credit entry!
        }

        ReconciliationReport anomalyReport = reconciliationService.runReconciliation();
        assertEquals("DISCREPANCIES_FOUND", anomalyReport.getStatus());
        assertTrue(anomalyReport.getDiscrepancyCount() > 0,
                "Reconciliation engine must detect unbalanced journal entries in the double-entry general ledger.");
    }

    @Test
    @DisplayName("Phase 11: Production configuration strictly disables in-memory H2 fallback on DB failure")
    public void testDatabaseUnavailabilityInProduction() {
        DatabaseConfig.setProductionModeOverride(true);
        DatabaseConfig.setConfig("jdbc:mysql://localhost:9999/nonexistent_db?connectTimeout=500", "invalid_user", "wrong_pass");

        assertThrows(SQLException.class, () -> {
            DatabaseConfig.getConnection();
        }, "Production mode must immediately throw SQLException on database connection failure without falling back to embedded H2.");
    }
}
