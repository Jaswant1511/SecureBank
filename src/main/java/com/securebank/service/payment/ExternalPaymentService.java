package com.securebank.service.payment;

import com.securebank.dao.AuditDAO;
import com.securebank.dao.BankDAO;
import com.securebank.dao.CustomerDAO;
import com.securebank.dao.ExternalPaymentDAO;
import com.securebank.dao.LedgerDAO;
import com.securebank.exception.AccountNotFoundException;
import com.securebank.exception.AuthorizationException;
import com.securebank.exception.InactiveAccountException;
import com.securebank.exception.InsufficientFundsException;
import com.securebank.model.Account;
import com.securebank.model.AuditLog;
import com.securebank.model.Customer;
import com.securebank.model.EntryType;
import com.securebank.model.ExternalPaymentRecord;
import com.securebank.model.LedgerEntry;
import com.securebank.model.LedgerTransaction;
import com.securebank.model.Role;
import com.securebank.model.Transaction;
import com.securebank.model.TransactionType;
import com.securebank.model.User;
import com.securebank.service.LedgerService;
import com.securebank.service.NotificationService;
import com.securebank.service.SuspiciousActivityService;
import com.securebank.util.DatabaseConfig;
import com.securebank.util.IdGenerator;
import com.securebank.util.ValidationUtil;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service orchestrating external financial payment processing.
 *
 * ARCHITECTURAL PRINCIPLES:
 * 1. External HTTP/network calls are NEVER executed while holding database transaction locks.
 * 2. Strict idempotency enforcement prevents duplicate debits on retried submissions.
 * 3. Incoming callbacks require cryptographic signature validation and replay defense.
 * 4. Double-entry general ledger entries are atomically posted upon authoritative confirmation.
 * 5. Simulation mode is transparently documented and segregated from real financial accounts.
 */
public class ExternalPaymentService {

    private static final Logger LOGGER = Logger.getLogger(ExternalPaymentService.class.getName());

    private final PaymentGateway paymentGateway;
    private final ExternalPaymentDAO paymentDAO;
    private final BankDAO bankDAO;
    private final LedgerDAO ledgerDAO;
    private final CustomerDAO customerDAO;
    private final AuditDAO auditDAO;
    private final NotificationService notificationService;
    private final SuspiciousActivityService suspiciousActivityService;

    public ExternalPaymentService() {
        this(new SimulatedPaymentGateway(), new ExternalPaymentDAO(), new BankDAO(), new LedgerDAO(),
             new CustomerDAO(), new AuditDAO(), new NotificationService(), new SuspiciousActivityService());
    }

    public ExternalPaymentService(PaymentGateway paymentGateway, ExternalPaymentDAO paymentDAO, BankDAO bankDAO,
                                  LedgerDAO ledgerDAO, CustomerDAO customerDAO, AuditDAO auditDAO,
                                  NotificationService notificationService, SuspiciousActivityService suspiciousActivityService) {
        this.paymentGateway = paymentGateway != null ? paymentGateway : new SimulatedPaymentGateway();
        this.paymentDAO = paymentDAO != null ? paymentDAO : new ExternalPaymentDAO();
        this.bankDAO = bankDAO != null ? bankDAO : new BankDAO();
        this.ledgerDAO = ledgerDAO != null ? ledgerDAO : new LedgerDAO();
        this.customerDAO = customerDAO != null ? customerDAO : new CustomerDAO();
        this.auditDAO = auditDAO != null ? auditDAO : new AuditDAO();
        this.notificationService = notificationService != null ? notificationService : new NotificationService();
        this.suspiciousActivityService = suspiciousActivityService != null ? suspiciousActivityService : new SuspiciousActivityService();
    }

    public PaymentGateway getPaymentGateway() {
        return paymentGateway;
    }

    /**
     * Initiates an external payment request with strict separation between API calls and DB locks.
     */
    public ExternalPaymentRecord initiateExternalPayment(
            int accountNumber,
            String pin,
            BigDecimal amount,
            String destinationAccount,
            String destinationBank,
            String purpose,
            String idempotencyKey,
            User actor
    ) throws SQLException, AuthorizationException, AccountNotFoundException, InactiveAccountException, InsufficientFundsException {

        if (actor == null) {
            throw new AuthorizationException("Unauthenticated request. Please log in.");
        }
        if (actor.getRole() == Role.ADMIN) {
            throw new AuthorizationException("Access Denied: Administrators cannot initiate customer payment operations.");
        }

        ValidationUtil.validateAccountNumber(accountNumber);
        ValidationUtil.validatePin(pin);
        ValidationUtil.validateMonetaryAmount(amount);
        if (destinationAccount == null || destinationAccount.trim().isEmpty()) {
            throw new IllegalArgumentException("Destination account identifier cannot be empty.");
        }
        if (destinationBank == null || destinationBank.trim().isEmpty()) {
            throw new IllegalArgumentException("Destination bank/provider code cannot be empty.");
        }

        // 1. Idempotency Check: Return existing record if already processed
        if (idempotencyKey != null && !idempotencyKey.trim().isEmpty()) {
            ExternalPaymentRecord existing = paymentDAO.findByIdempotencyKey(idempotencyKey.trim());
            if (existing != null) {
                LOGGER.info("Idempotent request detected. Returning existing external payment record #" + existing.getPaymentId());
                return existing;
            }
        } else {
            idempotencyKey = "IDEMP-" + IdGenerator.generateTransactionRef();
        }

        // 2. Pre-flight Account Validation & PIN verification
        Account account = bankDAO.getAccountByNumber(accountNumber);
        if (account == null) {
            throw new AccountNotFoundException("Account #" + accountNumber + " does not exist.");
        }
        if (account.getStatus() == null || !account.getStatus().canTransact() || account.isLocked()) {
            throw new InactiveAccountException("Account #" + accountNumber + " is " + account.getStatus() + " or locked. Payments rejected.");
        }

        // Ownership enforcement for retail customers
        if (actor.getRole() == Role.CUSTOMER) {
            Customer cust = customerDAO.getCustomerByUserId(actor.getUserId());
            if (cust == null || (account.getCustomerId() > 0 && account.getCustomerId() != cust.getCustomerId())) {
                suspiciousActivityService.checkUnauthorizedAccessAttempt(actor.getUserId(), actor.getUsername(), accountNumber);
                throw new AuthorizationException("Access Denied: Unauthorized access to Account #" + accountNumber);
            }
        }

        if (!account.verifyPin(pin)) {
            suspiciousActivityService.recordAlertQuietly(
                new com.securebank.model.SuspiciousActivityAlert(0, LocalDateTime.now(), actor.getUserId(),
                    accountNumber, "INVALID_PIN_PAYMENT", "HIGH", "Incorrect PIN entered during external payment initiation", "OPEN")
            );
            throw new SecurityException("PIN verification failed for Account #" + accountNumber);
        }

        if (account.getBalance().compareTo(amount) < 0) {
            suspiciousActivityService.checkRejectedWithdrawal(accountNumber, amount, account.getBalance());
            BigDecimal shortfall = amount.subtract(account.getBalance());
            throw new InsufficientFundsException("Insufficient funds on Account #" + accountNumber +
                    ": Requested ₹" + amount.toPlainString() + ", Available ₹" + account.getBalance().toPlainString(), shortfall);
        }

        // 3. Persist initial payment record in PENDING state
        String clientRef = "EXT-PAY-" + IdGenerator.generateTransactionRef();
        ExternalPaymentRecord record = new ExternalPaymentRecord(
            0,
            clientRef,
            idempotencyKey,
            accountNumber,
            amount,
            destinationAccount.trim(),
            destinationBank.trim(),
            paymentGateway.getProviderName(),
            null,
            PaymentStatus.PENDING,
            paymentGateway.isSimulated(),
            purpose != null ? purpose.trim() : "External Payment",
            null,
            LocalDateTime.now(),
            LocalDateTime.now()
        );
        int paymentId = paymentDAO.createPaymentRecord(record);
        record.setPaymentId(paymentId);

        // 4. External Gateway Invocation — OUTSIDE ANY DATABASE TRANSACTION!
        PaymentRequest request = new PaymentRequest(
            clientRef,
            idempotencyKey,
            accountNumber,
            amount,
            destinationAccount.trim(),
            destinationBank.trim(),
            purpose
        );

        PaymentResponse response;
        try {
            response = paymentGateway.initiatePayment(request);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "External payment gateway call failed with network error: " + e.getMessage(), e);
            paymentDAO.updateStatus(paymentId, PaymentStatus.TIMEOUT, null, "Gateway network failure: " + e.getMessage());
            record.setStatus(PaymentStatus.TIMEOUT);
            record.setFailureReason("Gateway network failure: " + e.getMessage());
            return record;
        }

        // 5. Authoritative State Transition based on Gateway Response
        if (response.getStatus() == PaymentStatus.SUCCESS) {
            // Atomically debit account and post double-entry ledger entry
            executeInternalDebitAndLedger(accountNumber, amount, clientRef, response.getProviderReference(),
                    "External Transfer to " + destinationAccount + " (" + destinationBank + ")");
            paymentDAO.updateStatus(paymentId, PaymentStatus.SUCCESS, response.getProviderReference(), null);
            record.setStatus(PaymentStatus.SUCCESS);
            record.setProviderReference(response.getProviderReference());

            recordAudit(actor.getUsername(), actor.getRole(), "EXTERNAL_PAYMENT_INITIATE", "PAYMENT",
                    String.valueOf(paymentId), "External payment successful. ProviderRef: " + response.getProviderReference(), "SUCCESS");

            if (actor.getUserId() > 0) {
                notificationService.notifyUser(actor.getUserId(), "Payment Dispatched",
                    String.format("₹%s sent to %s via %s (Ref: %s)", amount.toPlainString(), destinationAccount, destinationBank, clientRef), "SUCCESS");
            }

        } else if (response.getStatus() == PaymentStatus.PENDING) {
            paymentDAO.updateStatus(paymentId, PaymentStatus.PENDING, response.getProviderReference(), "Awaiting asynchronous provider settlement");
            record.setStatus(PaymentStatus.PENDING);
            record.setProviderReference(response.getProviderReference());

            recordAudit(actor.getUsername(), actor.getRole(), "EXTERNAL_PAYMENT_INITIATE", "PAYMENT",
                    String.valueOf(paymentId), "External payment pending with provider. Ref: " + response.getProviderReference(), "PENDING");

        } else if (response.getStatus() == PaymentStatus.TIMEOUT) {
            paymentDAO.updateStatus(paymentId, PaymentStatus.TIMEOUT, response.getProviderReference(), response.getMessage());
            record.setStatus(PaymentStatus.TIMEOUT);
            record.setProviderReference(response.getProviderReference());
            record.setFailureReason(response.getMessage());

        } else {
            // FAILED / REJECTED
            paymentDAO.updateStatus(paymentId, PaymentStatus.FAILED, response.getProviderReference(), response.getMessage());
            record.setStatus(PaymentStatus.FAILED);
            record.setProviderReference(response.getProviderReference());
            record.setFailureReason(response.getMessage());

            recordAudit(actor.getUsername(), actor.getRole(), "EXTERNAL_PAYMENT_FAILED", "PAYMENT",
                    String.valueOf(paymentId), "External payment declined by provider: " + response.getMessage(), "FAILED");
        }

        return record;
    }

    /**
     * Processes an asynchronous webhook callback with signature verification, replay protection,
     * and idempotent ledger settlement.
     */
    public boolean processProviderCallback(String rawPayload, String signature, String providerRef,
                                            String clientRef, PaymentStatus status) throws SQLException {
        if (signature == null || signature.trim().isEmpty()) {
            paymentDAO.recordCallback(providerRef, clientRef, signature != null ? signature : "", rawPayload,
                    status != null ? status.name() : "UNKNOWN", false, false);
            throw new SecurityException("Webhook rejected: Missing digital signature header.");
        }

        // 1. Replay & Duplicate Delivery Protection at DB Persistence Layer:
        // If this exact callback signature was already processed, ignore safely and return true (idempotent 200 OK)
        if (paymentDAO.isCallbackProcessed(signature)) {
            LOGGER.info("Duplicate callback delivery ignored for already processed signature: " + signature);
            return true;
        }

        // 2. Signature Authenticity & Replay Protection at Gateway Layer
        boolean validSignature = paymentGateway.verifyCallbackSignature(rawPayload, signature);
        if (!validSignature) {
            paymentDAO.recordCallback(providerRef, clientRef, signature, rawPayload,
                    status != null ? status.name() : "UNKNOWN", false, false);
            recordAudit("WEBHOOK_INGRESS", Role.ADMIN, "PAYMENT_CALLBACK_REJECTED", "WEBHOOK",
                    clientRef, "Invalid signature or replay attack detected for providerRef: " + providerRef, "SECURITY_ALERT");
            throw new SecurityException("Webhook rejected: Invalid signature or replay attack detected.");
        }

        // 3. Correlate with existing payment record
        ExternalPaymentRecord record = paymentDAO.findByClientReference(clientRef);
        if (record == null && providerRef != null) {
            record = paymentDAO.findByProviderReference(providerRef);
        }

        if (record == null) {
            paymentDAO.recordCallback(providerRef, clientRef, signature, rawPayload, status.name(), true, false);
            throw new IllegalArgumentException("Unknown payment reference in callback: " + clientRef + " / " + providerRef);
        }

        // 4. Ensure payment is not already in a terminal state (Prevents double debiting)
        if (record.getStatus() == PaymentStatus.SUCCESS) {
            LOGGER.info("Payment #" + record.getPaymentId() + " already settled. Callback marked processed.");
            paymentDAO.recordCallback(providerRef, clientRef, signature, rawPayload, status.name(), true, true);
            return true;
        }

        // 5. Authoritative State Transition
        if (status == PaymentStatus.SUCCESS) {
            try {
                executeInternalDebitAndLedger(record.getAccountNumber(), record.getAmount(), record.getClientReference(),
                        providerRef, "Settled external transfer via webhook to " + record.getDestinationAccount());
                paymentDAO.updateStatus(record.getPaymentId(), PaymentStatus.SUCCESS, providerRef, null);
            } catch (InsufficientFundsException ife) {
                LOGGER.severe("Settlement failed: Insufficient funds upon webhook execution for payment #" + record.getPaymentId());
                paymentDAO.updateStatus(record.getPaymentId(), PaymentStatus.FAILED, providerRef, "Settlement failed: Insufficient funds");
            }
        } else if (status == PaymentStatus.FAILED) {
            paymentDAO.updateStatus(record.getPaymentId(), PaymentStatus.FAILED, providerRef, "Provider callback confirmed failure");
        }

        paymentDAO.recordCallback(providerRef, clientRef, signature, rawPayload, status.name(), true, true);
        paymentDAO.markCallbackProcessed(signature);

        recordAudit("WEBHOOK_INGRESS", Role.ADMIN, "PAYMENT_CALLBACK_PROCESSED", "PAYMENT",
                String.valueOf(record.getPaymentId()), "Settled via verified provider callback. Status: " + status, "SUCCESS");

        return true;
    }

    /**
     * Inquires the status of a pending payment from the external provider and synchronizes database state.
     */
    public ExternalPaymentRecord inquireAndSyncPayment(int paymentId) throws SQLException {
        List<ExternalPaymentRecord> all = paymentDAO.getPendingPayments();
        ExternalPaymentRecord record = null;
        for (ExternalPaymentRecord r : all) {
            if (r.getPaymentId() == paymentId) {
                record = r;
                break;
            }
        }
        if (record == null) {
            return null;
        }

        String providerRef = record.getProviderReference();
        if (providerRef == null || providerRef.trim().isEmpty()) {
            return record;
        }

        PaymentResponse response = paymentGateway.inquirePaymentStatus(providerRef);
        if (response.getStatus() == PaymentStatus.SUCCESS && record.getStatus() != PaymentStatus.SUCCESS) {
            try {
                executeInternalDebitAndLedger(record.getAccountNumber(), record.getAmount(), record.getClientReference(),
                        providerRef, "Inquiry settled external transfer to " + record.getDestinationAccount());
                paymentDAO.updateStatus(record.getPaymentId(), PaymentStatus.SUCCESS, providerRef, null);
                record.setStatus(PaymentStatus.SUCCESS);
            } catch (InsufficientFundsException ife) {
                paymentDAO.updateStatus(record.getPaymentId(), PaymentStatus.FAILED, providerRef, "Settlement failed: Insufficient funds");
                record.setStatus(PaymentStatus.FAILED);
            }
        } else if (response.getStatus() == PaymentStatus.FAILED) {
            paymentDAO.updateStatus(record.getPaymentId(), PaymentStatus.FAILED, providerRef, response.getMessage());
            record.setStatus(PaymentStatus.FAILED);
        }

        return record;
    }

    /**
     * Executes atomic double-entry debit from account and posts balanced ledger entries.
     */
    private void executeInternalDebitAndLedger(int accountNumber, BigDecimal amount, String clientRef,
                                               String providerRef, String description) throws SQLException, InsufficientFundsException {
        Connection conn = null;
        try {
            conn = DatabaseConfig.getConnection();
            conn.setAutoCommit(false);

            Account acc = bankDAO.getAccountForUpdate(conn, accountNumber);
            if (acc == null) {
                throw new SQLException("Source account #" + accountNumber + " not found during settlement.");
            }
            if (acc.getBalance().compareTo(amount) < 0) {
                throw new SQLException("Insufficient funds on Account #" + accountNumber + " during settlement.");
            }

            // Debit internal account
            acc.withdraw(amount);
            final String updateSql = "UPDATE accounts SET balance = ? WHERE account_number = ?";
            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setBigDecimal(1, acc.getBalance());
                ps.setInt(2, accountNumber);
                ps.executeUpdate();
            }

            // Post internal transaction
            Transaction txn = new Transaction(
                0,
                clientRef,
                accountNumber,
                TransactionType.WITHDRAW,
                amount,
                acc.getBalance(),
                description + (providerRef != null ? " [ProviderRef: " + providerRef + "]" : ""),
                null,
                LocalDateTime.now()
            );
            new com.securebank.dao.TransactionDAO().recordTransaction(conn, txn);

            // Double-entry general ledger: Customer DEBIT, Central Bank Clearing #0 CREDIT
            LedgerTransaction lt = new LedgerTransaction(0, clientRef, "WITHDRAW", description, "POSTED", LocalDateTime.now());
            lt.addEntry(new LedgerEntry(0, 0, accountNumber, EntryType.DEBIT, amount, LocalDateTime.now()));
            lt.addEntry(new LedgerEntry(0, 0, LedgerService.CENTRAL_RESERVE_ACCOUNT, EntryType.CREDIT, amount, LocalDateTime.now()));
            ledgerDAO.postTransaction(lt, conn);

            conn.commit();
        } catch (SQLException ex) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ignored) {}
            }
            throw ex;
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ignored) {}
            }
        }
    }

    /**
     * Reconciles external provider payments against internal ledger records.
     */
    public Map<String, Object> reconcileExternalPayments() throws SQLException {
        Map<String, Object> report = new HashMap<>();
        List<ExternalPaymentRecord> pending = paymentDAO.getPendingPayments();
        int synced = 0;
        int failed = 0;
        List<Map<String, Object>> details = new ArrayList<>();

        for (ExternalPaymentRecord rec : pending) {
            Map<String, Object> item = new HashMap<>();
            item.put("paymentId", rec.getPaymentId());
            item.put("clientReference", rec.getClientReference());
            item.put("amount", rec.getAmount());
            item.put("statusBefore", rec.getStatus());

            if (rec.getProviderReference() != null) {
                try {
                    PaymentResponse resp = paymentGateway.inquirePaymentStatus(rec.getProviderReference());
                    item.put("providerStatus", resp.getStatus());
                    if (resp.getStatus() == PaymentStatus.SUCCESS) {
                        inquireAndSyncPayment(rec.getPaymentId());
                        synced++;
                        item.put("resolution", "SETTLED");
                    } else if (resp.getStatus() == PaymentStatus.FAILED) {
                        paymentDAO.updateStatus(rec.getPaymentId(), PaymentStatus.FAILED, rec.getProviderReference(), resp.getMessage());
                        failed++;
                        item.put("resolution", "MARKED_FAILED");
                    } else {
                        item.put("resolution", "STILL_PENDING");
                    }
                } catch (Exception e) {
                    item.put("resolution", "INQUIRY_ERROR: " + e.getMessage());
                }
            } else {
                item.put("resolution", "MISSING_PROVIDER_REF");
            }
            details.add(item);
        }

        report.put("totalPendingEvaluated", pending.size());
        report.put("settledCount", synced);
        report.put("failedCount", failed);
        report.put("details", details);
        return report;
    }

    public List<ExternalPaymentRecord> getPaymentsForAccount(int accountNumber, int limit) throws SQLException {
        return paymentDAO.getPaymentsForAccount(accountNumber, limit);
    }

    private void recordAudit(String actor, Role role, String action, String type, String id, String details, String status) {
        try {
            AuditLog al = new AuditLog(0, actor, role, action, type, id, details, status, "LOCAL", LocalDateTime.now());
            auditDAO.recordAuditLog(al);
        } catch (Exception ignored) {
        }
    }
}
