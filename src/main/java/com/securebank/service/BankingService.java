package com.securebank.service;

import com.securebank.dao.AuditDAO;
import com.securebank.dao.BankDAO;
import com.securebank.dao.CustomerDAO;
import com.securebank.dao.TransactionDAO;
import com.securebank.exception.AccountNotFoundException;
import com.securebank.exception.AuthorizationException;
import com.securebank.exception.InactiveAccountException;
import com.securebank.exception.InsufficientFundsException;
import com.securebank.model.Account;
import com.securebank.model.AuditLog;
import com.securebank.model.Customer;
import com.securebank.model.Role;
import com.securebank.model.Transaction;
import com.securebank.model.TransactionReceipt;
import com.securebank.model.TransactionType;
import com.securebank.model.User;
import com.securebank.util.CurrencyFormatter;
import com.securebank.util.IdGenerator;
import com.securebank.util.ValidationUtil;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * SecureBank: Role-Based Core Banking & Transaction Management System
 * File: BankingService.java
 *
 * Primary Financial Orchestration Service.
 * Implements strict RBAC authorization, transaction receipt generation,
 * validation, audit trail generation, and ACID execution.
 */
public class BankingService {

    private final BankDAO bankDAO;
    private final TransactionDAO transactionDAO;
    private final CustomerDAO customerDAO;
    private final AuditDAO auditDAO;
    private final NotificationService notificationService;
    private final SuspiciousActivityService suspiciousActivityService;
    private final java.util.Map<String, TransactionReceipt> idempotencyMap = new java.util.concurrent.ConcurrentHashMap<>();

    public BankingService() {
        this.bankDAO = new BankDAO();
        this.transactionDAO = new TransactionDAO();
        this.customerDAO = new CustomerDAO();
        this.auditDAO = new AuditDAO();
        this.notificationService = new NotificationService();
        this.suspiciousActivityService = new SuspiciousActivityService();
    }

    public BankingService(BankDAO bankDAO, TransactionDAO transactionDAO, CustomerDAO customerDAO, AuditDAO auditDAO) {
        this.bankDAO = bankDAO;
        this.transactionDAO = transactionDAO;
        this.customerDAO = customerDAO;
        this.auditDAO = auditDAO;
        this.notificationService = new NotificationService();
        this.suspiciousActivityService = new SuspiciousActivityService();
    }

    public BankingService(BankDAO bankDAO, TransactionDAO transactionDAO, CustomerDAO customerDAO, AuditDAO auditDAO,
                          NotificationService notificationService, SuspiciousActivityService suspiciousActivityService) {
        this.bankDAO = bankDAO;
        this.transactionDAO = transactionDAO;
        this.customerDAO = customerDAO;
        this.auditDAO = auditDAO;
        this.notificationService = notificationService != null ? notificationService : new NotificationService();
        this.suspiciousActivityService = suspiciousActivityService != null ? suspiciousActivityService : new SuspiciousActivityService();
    }

    /**
     * Executes a deposit with ACID persistence and receipt generation.
     */
    public TransactionReceipt deposit(
        int accountNumber,
        BigDecimal amount,
        String remarks,
        User actor
    ) throws SQLException, InactiveAccountException, AccountNotFoundException, AuthorizationException {
        if (actor == null) {
            throw new AuthorizationException("Unauthenticated request. Please log in.");
        }
        if (actor.getRole() == Role.ADMIN) {
            throw new AuthorizationException("Access Denied: Administrators are not authorized to initiate customer financial operations.");
        }
        ValidationUtil.validateAccountNumber(accountNumber);
        ValidationUtil.validateMonetaryAmount(amount);

        Account acc = bankDAO.getAccountByNumber(accountNumber);
        if (acc == null) {
            throw new AccountNotFoundException("Account #" + accountNumber + " not found.");
        }
        if (acc.getStatus() == null || !acc.getStatus().canTransact() || acc.isLocked()) {
            throw new InactiveAccountException("Account #" + accountNumber + " is " + acc.getStatus() + " or locked. Deposits rejected.");
        }

        // Distinct workflow rules for customer self-service vs teller branch cash
        String defaultDesc;
        if (actor.getRole() == Role.CUSTOMER) {
            verifyAccountOwnership(acc, actor);
            if (amount.compareTo(new BigDecimal("50000.00")) > 0) {
                throw new IllegalArgumentException("Customer self-service digital deposit limit exceeded (Maximum ₹50,000.00 per transaction). Branch counter cash deposits must be processed by an authorized teller.");
            }
            defaultDesc = "Simulated Electronic Deposit (Self-Service)";
        } else {
            defaultDesc = "Branch Counter Cash Deposit (Teller: " + actor.getUsername() + ")";
        }

        String txnRef = IdGenerator.generateTransactionRef();
        String desc = (remarks != null && !remarks.trim().isEmpty()) ? remarks.trim() : defaultDesc;

        Transaction txn = bankDAO.deposit(accountNumber, amount, desc, txnRef);

        recordAudit(
            actor.getUsername(),
            actor.getRole(),
            "DEPOSIT",
            "ACCOUNT",
            String.valueOf(accountNumber),
            String.format("Deposited %s (Ref: %s)", CurrencyFormatter.format(amount), txnRef),
            "SUCCESS"
        );

        // Automated notification & fraud monitoring
        if (actor.getUserId() > 0) {
            notificationService.notifyUser(actor.getUserId(), "Deposit Successful",
                String.format("₹%s credited to Account #%d. New balance: ₹%s (Ref: %s)",
                    amount.toPlainString(), accountNumber, txn.getBalanceAfter().toPlainString(), txnRef), "SUCCESS");
        }
        suspiciousActivityService.checkHighValueTransaction(accountNumber, amount, "DEPOSIT");
        suspiciousActivityService.checkTransactionVelocity(accountNumber);

        return new TransactionReceipt(
            txnRef,
            TransactionType.DEPOSIT,
            accountNumber,
            acc.getHolderName(),
            null,
            amount,
            txn.getBalanceAfter(),
            "SUCCESS",
            desc
        );
    }

    /**
     * Executes a polymorphic withdrawal with PIN security and receipt generation.
     */
    public TransactionReceipt withdraw(
        int accountNumber,
        String pin,
        BigDecimal amount,
        String remarks,
        User actor
    ) throws SQLException, InsufficientFundsException, InactiveAccountException, AccountNotFoundException, AuthorizationException {
        if (actor == null) {
            throw new AuthorizationException("Unauthenticated request. Please log in.");
        }
        if (actor.getRole() == Role.ADMIN) {
            throw new AuthorizationException("Access Denied: Administrators are not authorized to initiate customer financial operations.");
        }
        ValidationUtil.validateAccountNumber(accountNumber);
        ValidationUtil.validatePin(pin);
        ValidationUtil.validateMonetaryAmount(amount);

        Account acc = bankDAO.getAccountByNumber(accountNumber);
        if (acc == null) {
            throw new AccountNotFoundException("Account #" + accountNumber + " not found.");
        }
        if (acc.getStatus() == null || !acc.getStatus().canTransact() || acc.isLocked()) {
            throw new InactiveAccountException("Account #" + accountNumber + " is " + acc.getStatus() + " or locked. Withdrawals rejected.");
        }

        // Ownership enforcement for Customers
        verifyAccountOwnership(acc, actor);

        String txnRef = IdGenerator.generateTransactionRef();
        String desc = (remarks != null && !remarks.trim().isEmpty()) ? remarks.trim() : (acc.getAccountType() + " Cash Withdrawal");

        Transaction txn;
        try {
            txn = bankDAO.withdraw(accountNumber, pin, amount, desc, txnRef);
        } catch (InsufficientFundsException ife) {
            suspiciousActivityService.checkRejectedWithdrawal(accountNumber, amount, acc.getBalance());
            if (actor.getUserId() > 0) {
                notificationService.notifyUser(actor.getUserId(), "Withdrawal Declined",
                    String.format("Withdrawal of ₹%s failed on Account #%d: Insufficient funds.",
                        amount.toPlainString(), accountNumber), "ALERT");
            }
            throw ife;
        }

        recordAudit(
            actor.getUsername(),
            actor.getRole(),
            "WITHDRAW",
            "ACCOUNT",
            String.valueOf(accountNumber),
            String.format("Withdrew %s (Ref: %s)", CurrencyFormatter.format(amount), txnRef),
            "SUCCESS"
        );

        if (actor.getUserId() > 0) {
            notificationService.notifyUser(actor.getUserId(), "Withdrawal Successful",
                String.format("₹%s debited from Account #%d. New balance: ₹%s (Ref: %s)",
                    amount.toPlainString(), accountNumber, txn.getBalanceAfter().toPlainString(), txnRef), "SUCCESS");
        }
        suspiciousActivityService.checkHighValueTransaction(accountNumber, amount, "WITHDRAWAL");
        suspiciousActivityService.checkTransactionVelocity(accountNumber);

        return new TransactionReceipt(
            txnRef,
            TransactionType.WITHDRAW,
            accountNumber,
            acc.getHolderName(),
            null,
            amount,
            txn.getBalanceAfter(),
            "SUCCESS",
            desc
        );
    }

    /**
     * Executes an atomic transfer between two accounts with deadlock protection.
     */
    public TransactionReceipt transfer(
        int senderAccNo,
        String senderPin,
        int receiverAccNo,
        BigDecimal amount,
        String remarks,
        User actor
    ) throws SQLException, InsufficientFundsException, InactiveAccountException, AccountNotFoundException, AuthorizationException {
        return transfer(senderAccNo, senderPin, receiverAccNo, amount, remarks, actor, null);
    }

    /**
     * Executes an atomic transfer between two accounts with deadlock protection and idempotency key caching.
     * If an identical idempotencyKey was previously processed, returns the cached receipt to prevent double-debiting.
     */
    public TransactionReceipt transfer(
        int senderAccNo,
        String senderPin,
        int receiverAccNo,
        BigDecimal amount,
        String remarks,
        User actor,
        String idempotencyKey
    ) throws SQLException, InsufficientFundsException, InactiveAccountException, AccountNotFoundException, AuthorizationException {
        if (idempotencyKey != null && !idempotencyKey.trim().isEmpty()) {
            TransactionReceipt cached = idempotencyMap.get(idempotencyKey.trim());
            if (cached != null) {
                return cached;
            }
        }

        if (actor == null) {
            throw new AuthorizationException("Unauthenticated request. Please log in.");
        }
        if (actor.getRole() == Role.ADMIN) {
            throw new AuthorizationException("Access Denied: Administrators are not authorized to initiate customer financial operations.");
        }
        if (senderAccNo == receiverAccNo) {
            throw new IllegalArgumentException("Source and destination accounts must be distinct.");
        }
        ValidationUtil.validateAccountNumber(senderAccNo);
        ValidationUtil.validateAccountNumber(receiverAccNo);
        ValidationUtil.validatePin(senderPin);
        ValidationUtil.validateMonetaryAmount(amount);

        Account sender = bankDAO.getAccountByNumber(senderAccNo);
        if (sender == null) {
            throw new AccountNotFoundException("Sender Account #" + senderAccNo + " does not exist.");
        }
        if (sender.getStatus() == null || !sender.getStatus().canTransact() || sender.isLocked()) {
            throw new InactiveAccountException("Sender Account #" + senderAccNo + " is " + sender.getStatus() + " or locked. Transfers rejected.");
        }
        Account receiver = bankDAO.getAccountByNumber(receiverAccNo);
        if (receiver == null) {
            throw new AccountNotFoundException("Receiver Account #" + receiverAccNo + " does not exist.");
        }
        if (receiver.getStatus() == null || !receiver.getStatus().canTransact() || receiver.isLocked()) {
            throw new InactiveAccountException("Receiver Account #" + receiverAccNo + " is " + receiver.getStatus() + " or locked. Transfers rejected.");
        }

        verifyAccountOwnership(sender, actor);

        String desc = (remarks != null && !remarks.trim().isEmpty()) ? remarks.trim() : ("Transfer to #" + receiverAccNo);
        String txnRef = IdGenerator.generateTransactionRef();

        bankDAO.transferFunds(senderAccNo, senderPin, receiverAccNo, amount, desc, txnRef);

        Account updatedSender = bankDAO.getAccountByNumber(senderAccNo);

        recordAudit(
            actor.getUsername(),
            actor.getRole(),
            "TRANSFER",
            "ACCOUNT",
            String.valueOf(senderAccNo),
            String.format("Transferred %s to #%d (Ref: %s)", CurrencyFormatter.format(amount), receiverAccNo, txnRef),
            "SUCCESS"
        );

        // Automated notification for sender & receiver
        if (actor.getUserId() > 0) {
            notificationService.notifyUser(actor.getUserId(), "Transfer Sent",
                String.format("₹%s transferred to Account #%d. Reference: %s",
                    amount.toPlainString(), receiverAccNo, txnRef), "SUCCESS");
        }
        try {
            Customer receiverCust = customerDAO.getCustomerById(receiver.getCustomerId());
            if (receiverCust != null && receiverCust.getUserId() > 0) {
                notificationService.notifyUser(receiverCust.getUserId(), "Funds Received",
                    String.format("₹%s received from Account #%d. Reference: %s",
                        amount.toPlainString(), senderAccNo, txnRef), "INFO");
            }
        } catch (Exception ignored) {}

        suspiciousActivityService.checkHighValueTransaction(senderAccNo, amount, "TRANSFER");
        suspiciousActivityService.checkTransactionVelocity(senderAccNo);

        TransactionReceipt receipt = new TransactionReceipt(
            txnRef,
            TransactionType.TRANSFER_OUT,
            senderAccNo,
            sender.getHolderName(),
            receiverAccNo,
            amount,
            updatedSender != null ? updatedSender.getBalance() : null,
            "SUCCESS",
            desc
        );

        if (idempotencyKey != null && !idempotencyKey.trim().isEmpty()) {
            idempotencyMap.put(idempotencyKey.trim(), receipt);
        }

        return receipt;
    }

    /**
     * Credits 4% yearly interest to a Savings Account.
     */
    public TransactionReceipt applyYearlyInterest(int savingsAccNo, User actor)
        throws SQLException, AccountNotFoundException, AuthorizationException {
        if (actor == null) {
            throw new AuthorizationException("Unauthenticated request.");
        }

        Account acc = bankDAO.getAccountByNumber(savingsAccNo);
        if (acc == null) {
            throw new AccountNotFoundException("Account #" + savingsAccNo + " not found.");
        }

        String txnRef = IdGenerator.generateTransactionRef();
        BigDecimal interestCredited = bankDAO.applyYearlyInterest(savingsAccNo, txnRef);

        Account updated = bankDAO.getAccountByNumber(savingsAccNo);

        recordAudit(
            actor.getUsername(),
            actor.getRole(),
            "INTEREST_CREDIT",
            "ACCOUNT",
            String.valueOf(savingsAccNo),
            String.format("Credited interest %s (Ref: %s)", CurrencyFormatter.format(interestCredited), txnRef),
            "SUCCESS"
        );

        return new TransactionReceipt(
            txnRef,
            TransactionType.INTEREST,
            savingsAccNo,
            acc.getHolderName(),
            null,
            interestCredited,
            updated != null ? updated.getBalance() : null,
            "SUCCESS",
            "4% Yearly Savings Interest Credited"
        );
    }

    /**
     * Retrieves recent transactions for an authorized account.
     */
    public List<Transaction> getRecentTransactions(int accountNumber, int limit, User actor)
        throws SQLException, AccountNotFoundException, AuthorizationException {
        Account acc = bankDAO.getAccountByNumber(accountNumber);
        if (acc == null) {
            throw new AccountNotFoundException("Account #" + accountNumber + " not found.");
        }
        verifyAccountOwnership(acc, actor);
        return transactionDAO.getRecentTransactions(accountNumber, limit);
    }

    /**
     * Retrieves filtered and paginated transaction history.
     */
    public List<Transaction> getFilteredTransactions(
        int accountNumber,
        LocalDateTime fromDate,
        LocalDateTime toDate,
        TransactionType type,
        int offset,
        int limit,
        User actor
    ) throws SQLException, AccountNotFoundException, AuthorizationException {
        Account acc = bankDAO.getAccountByNumber(accountNumber);
        if (acc == null) {
            throw new AccountNotFoundException("Account #" + accountNumber + " not found.");
        }
        verifyAccountOwnership(acc, actor);
        return transactionDAO.filterTransactions(accountNumber, fromDate, toDate, type, offset, limit);
    }

    private void verifyAccountOwnership(Account account, User actor) throws AuthorizationException, SQLException {
        if (actor.getRole() == Role.CUSTOMER) {
            Customer cust = customerDAO.getCustomerByUserId(actor.getUserId());
            if (cust == null || (account.getCustomerId() > 0 && account.getCustomerId() != cust.getCustomerId())) {
                suspiciousActivityService.checkUnauthorizedAccessAttempt(actor.getUserId(), actor.getUsername(), account.getAccountNumber());
                if (actor.getUserId() > 0) {
                    notificationService.notifyUser(actor.getUserId(), "Security Warning",
                        "Unauthorized access attempt detected for Account #" + account.getAccountNumber(), "ALERT");
                }
                throw new AuthorizationException("Access Denied: You do not have authority over Account #" + account.getAccountNumber());
            }
        }
    }

    private void recordAudit(String actor, Role role, String action, String type, String id, String details, String status) {
        try {
            AuditLog al = new AuditLog(0, actor, role, action, type, id, details, status, "LOCAL", LocalDateTime.now());
            auditDAO.recordAuditLog(al);
        } catch (Exception ignored) {
        }
    }
}
