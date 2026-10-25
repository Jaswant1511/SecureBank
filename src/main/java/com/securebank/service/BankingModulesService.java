package com.securebank.service;

import com.securebank.dao.AuditDAO;
import com.securebank.dao.BankDAO;
import com.securebank.dao.BankingModulesDAO;
import com.securebank.dao.CustomerDAO;
import com.securebank.dao.TransactionDAO;
import com.securebank.exception.AccountNotFoundException;
import com.securebank.exception.AuthorizationException;
import com.securebank.exception.InactiveAccountException;
import com.securebank.exception.InsufficientFundsException;
import com.securebank.model.Account;
import com.securebank.model.AuditLog;
import com.securebank.model.Biller;
import com.securebank.model.BillPayment;
import com.securebank.model.Customer;
import com.securebank.model.DebitCard;
import com.securebank.model.FixedDeposit;
import com.securebank.model.Loan;
import com.securebank.model.LoanType;
import com.securebank.model.Role;
import com.securebank.model.Transaction;
import com.securebank.model.TransactionType;
import com.securebank.model.User;
import com.securebank.util.DatabaseConfig;
import com.securebank.util.IdGenerator;
import com.securebank.util.ValidationUtil;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * SecureBank: Role-Based Core Banking & Transaction Management System
 * File: BankingModulesService.java
 *
 * Orchestrates Section 13 banking modules:
 * 1. Bill Payments with verifiable merchant clearing
 * 2. Fixed Deposit portfolio management with compound interest
 * 3. Retail & Commercial Loan application, approval, disbursement, and EMI repayments
 * 4. Debit Card security controls (daily limits, freeze, contactless toggle)
 */
public class BankingModulesService {

    private final BankingModulesDAO bankingModulesDAO;
    private final BankDAO bankDAO;
    private final CustomerDAO customerDAO;
    private final TransactionDAO transactionDAO;
    private final AuditDAO auditDAO;
    private final LedgerService ledgerService;
    private final NotificationService notificationService;

    public BankingModulesService() {
        this.bankingModulesDAO = new BankingModulesDAO();
        this.bankDAO = new BankDAO();
        this.customerDAO = new CustomerDAO();
        this.transactionDAO = new TransactionDAO();
        this.auditDAO = new AuditDAO();
        this.ledgerService = new LedgerService();
        this.notificationService = new NotificationService();
    }

    public BankingModulesService(BankingModulesDAO bankingModulesDAO, BankDAO bankDAO, CustomerDAO customerDAO,
                                 TransactionDAO transactionDAO, AuditDAO auditDAO, LedgerService ledgerService,
                                 NotificationService notificationService) {
        this.bankingModulesDAO = bankingModulesDAO != null ? bankingModulesDAO : new BankingModulesDAO();
        this.bankDAO = bankDAO != null ? bankDAO : new BankDAO();
        this.customerDAO = customerDAO != null ? customerDAO : new CustomerDAO();
        this.transactionDAO = transactionDAO != null ? transactionDAO : new TransactionDAO();
        this.auditDAO = auditDAO != null ? auditDAO : new AuditDAO();
        this.ledgerService = ledgerService != null ? ledgerService : new LedgerService();
        this.notificationService = notificationService != null ? notificationService : new NotificationService();
    }

    // ==========================================
    // 1. BILL PAYMENTS
    // ==========================================

    public List<Biller> getAllBillers() throws SQLException {
        return bankingModulesDAO.getAllBillers();
    }

    public List<BillPayment> getCustomerBillPayments(int customerId, User actor) throws SQLException, AuthorizationException {
        if (actor == null) {
            throw new AuthorizationException("Unauthenticated request.");
        }
        verifyCustomerOwnership(customerId, actor);
        return bankingModulesDAO.getBillPaymentsByCustomerId(customerId);
    }

    public BillPayment payBill(
            int customerId,
            int accountNumber,
            String pin,
            int billerId,
            String consumerNumber,
            BigDecimal amount,
            User actor
    ) throws SQLException, AuthorizationException, InsufficientFundsException, InactiveAccountException, AccountNotFoundException {
        if (actor == null) {
            throw new AuthorizationException("Unauthenticated request. Please log in.");
        }
        verifyCustomerOwnership(customerId, actor);
        ValidationUtil.validateAccountNumber(accountNumber);
        ValidationUtil.validatePin(pin);
        ValidationUtil.validateMonetaryAmount(amount);

        Biller biller = bankingModulesDAO.getBillerById(billerId);
        if (biller == null) {
            throw new IllegalArgumentException("Invalid Biller ID #" + billerId);
        }
        if (consumerNumber == null || consumerNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Consumer reference number is required.");
        }

        Connection conn = null;
        try {
            conn = DatabaseConfig.getConnection();
            conn.setAutoCommit(false);

            Account account = bankDAO.getAccountForUpdate(conn, accountNumber);
            if (account == null) {
                throw new AccountNotFoundException("Account #" + accountNumber + " not found.");
            }
            if (account.getCustomerId() != customerId) {
                throw new AuthorizationException("Access Denied: Account #" + accountNumber + " does not belong to customer #" + customerId);
            }
            if (!account.getStatus().canTransact()) {
                throw new InactiveAccountException("Account #" + accountNumber + " is " + account.getStatus() + ". Bill payments rejected.");
            }

            if (!account.verifyPin(pin)) {
                conn.commit();
                throw new SecurityException("PIN verification failed for Account #" + accountNumber);
            }

            // Deduct funds
            account.withdraw(amount);

            final String updateSql = "UPDATE accounts SET balance = ? WHERE account_number = ?";
            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setBigDecimal(1, account.getBalance());
                ps.setInt(2, accountNumber);
                ps.executeUpdate();
            }

            String paymentRef = IdGenerator.generateTransactionRef();
            String desc = "Bill Payment: " + biller.getBillerName() + " (Ref: " + consumerNumber.trim() + ")";

            // Post transaction
            Transaction txn = new Transaction(
                    0,
                    paymentRef,
                    accountNumber,
                    TransactionType.BILL_PAYMENT,
                    amount,
                    account.getBalance(),
                    desc,
                    null,
                    LocalDateTime.now()
            );
            transactionDAO.recordTransaction(conn, txn);

            // Double-entry posting: Debit Customer Account, Credit Utility Settlement Vault #0
            ledgerService.postWithdrawal(paymentRef, accountNumber, amount, desc, conn);

            BillPayment bp = new BillPayment(
                    0,
                    paymentRef,
                    customerId,
                    accountNumber,
                    billerId,
                    consumerNumber.trim(),
                    amount,
                    "SUCCESS",
                    LocalDateTime.now()
            );
            bp.setBillerName(biller.getBillerName());
            bankingModulesDAO.recordBillPayment(bp, conn);

            conn.commit();

            recordAudit(actor.getUsername(), actor.getRole(), "BILL_PAYMENT", "ACCOUNT", String.valueOf(accountNumber),
                    String.format("Paid ₹%s to %s (Ref: %s)", amount.toPlainString(), biller.getBillerName(), paymentRef), "SUCCESS");

            if (actor.getUserId() > 0) {
                notificationService.notifyUser(actor.getUserId(), "Bill Payment Successful",
                        String.format("₹%s paid to %s for %s. New balance: ₹%s",
                                amount.toPlainString(), biller.getBillerName(), consumerNumber, account.getBalance().toPlainString()), "SUCCESS");
            }

            return bp;
        } catch (SQLException | InsufficientFundsException | InactiveAccountException | RuntimeException ex) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ignored) {}
            }
            throw ex;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException ignored) {}
            }
        }
    }

    // ==========================================
    // 2. FIXED DEPOSITS
    // ==========================================

    public List<FixedDeposit> getCustomerFixedDeposits(int customerId, User actor) throws SQLException, AuthorizationException {
        if (actor == null) {
            throw new AuthorizationException("Unauthenticated request.");
        }
        verifyCustomerOwnership(customerId, actor);
        return bankingModulesDAO.getFixedDepositsByCustomerId(customerId);
    }

    public FixedDeposit openFixedDeposit(
            int customerId,
            int fundingAccount,
            String pin,
            BigDecimal principal,
            int tenureMonths,
            User actor
    ) throws SQLException, AuthorizationException, InsufficientFundsException, InactiveAccountException, AccountNotFoundException {
        if (actor == null) {
            throw new AuthorizationException("Unauthenticated request. Please log in.");
        }
        verifyCustomerOwnership(customerId, actor);
        ValidationUtil.validateAccountNumber(fundingAccount);
        ValidationUtil.validatePin(pin);
        ValidationUtil.validateMonetaryAmount(principal);

        if (principal.compareTo(new BigDecimal("1000.00")) < 0) {
            throw new IllegalArgumentException("Minimum Fixed Deposit principal is ₹1,000.00.");
        }
        if (tenureMonths < 3) {
            throw new IllegalArgumentException("Minimum Fixed Deposit tenure is 3 months.");
        }

        // Tiered interest rates
        BigDecimal rate;
        if (tenureMonths < 6) {
            rate = new BigDecimal("5.50");
        } else if (tenureMonths <= 12) {
            rate = new BigDecimal("6.50");
        } else {
            rate = new BigDecimal("7.25");
        }

        BigDecimal maturityAmount = FixedDeposit.calculateMaturity(principal, rate, tenureMonths);

        Connection conn = null;
        try {
            conn = DatabaseConfig.getConnection();
            conn.setAutoCommit(false);

            Account account = bankDAO.getAccountForUpdate(conn, fundingAccount);
            if (account == null) {
                throw new AccountNotFoundException("Account #" + fundingAccount + " not found.");
            }
            if (account.getCustomerId() != customerId) {
                throw new AuthorizationException("Access Denied: Account #" + fundingAccount + " does not belong to customer #" + customerId);
            }
            if (!account.getStatus().canTransact()) {
                throw new InactiveAccountException("Account #" + fundingAccount + " is " + account.getStatus() + ". FD creation rejected.");
            }
            if (!account.verifyPin(pin)) {
                conn.commit();
                throw new SecurityException("PIN verification failed for Account #" + fundingAccount);
            }

            // Deduct principal
            account.withdraw(principal);

            final String updateSql = "UPDATE accounts SET balance = ? WHERE account_number = ?";
            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setBigDecimal(1, account.getBalance());
                ps.setInt(2, fundingAccount);
                ps.executeUpdate();
            }

            String fdRef = "FD-" + System.currentTimeMillis();
            String desc = "Fixed Deposit Creation (" + tenureMonths + " Months @ " + rate + "%)";

            Transaction txn = new Transaction(
                    0,
                    fdRef,
                    fundingAccount,
                    TransactionType.FIXED_DEPOSIT,
                    principal,
                    account.getBalance(),
                    desc,
                    null,
                    LocalDateTime.now()
            );
            transactionDAO.recordTransaction(conn, txn);

            // Double-entry posting: Debit Customer Account, Credit Fixed Deposit Liability Reserve #0
            ledgerService.postWithdrawal(fdRef, fundingAccount, principal, desc, conn);

            LocalDateTime startDate = LocalDateTime.now();
            LocalDateTime maturityDate = startDate.plusMonths(tenureMonths);

            FixedDeposit fd = new FixedDeposit(
                    0,
                    fdRef,
                    customerId,
                    fundingAccount,
                    principal,
                    tenureMonths,
                    rate,
                    maturityAmount,
                    "ACTIVE",
                    startDate,
                    maturityDate
            );
            bankingModulesDAO.createFixedDeposit(fd, conn);

            conn.commit();

            recordAudit(actor.getUsername(), actor.getRole(), "CREATE_FD", "FIXED_DEPOSIT", fdRef,
                    String.format("Opened FD %s for ₹%s (Tenure: %d months, Maturity: ₹%s)", fdRef, principal.toPlainString(), tenureMonths, maturityAmount.toPlainString()), "SUCCESS");

            if (actor.getUserId() > 0) {
                notificationService.notifyUser(actor.getUserId(), "Fixed Deposit Created",
                        String.format("FD %s created for ₹%s at %s%% p.a. Maturity value: ₹%s",
                                fdRef, principal.toPlainString(), rate.toPlainString(), maturityAmount.toPlainString()), "SUCCESS");
            }

            return fd;
        } catch (SQLException | InsufficientFundsException | InactiveAccountException | RuntimeException ex) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ignored) {}
            }
            throw ex;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException ignored) {}
            }
        }
    }

    public boolean closeFixedDeposit(int fdId, int customerId, User actor) throws SQLException, AuthorizationException, AccountNotFoundException {
        if (actor == null) {
            throw new AuthorizationException("Unauthenticated request.");
        }
        verifyCustomerOwnership(customerId, actor);

        FixedDeposit fd = bankingModulesDAO.getFixedDepositById(fdId);
        if (fd == null) {
            throw new IllegalArgumentException("Fixed Deposit #" + fdId + " not found.");
        }
        if (fd.getCustomerId() != customerId) {
            throw new AuthorizationException("Access Denied: You do not own Fixed Deposit #" + fdId);
        }
        if (!"ACTIVE".equalsIgnoreCase(fd.getStatus())) {
            throw new IllegalArgumentException("Fixed Deposit #" + fdId + " is already " + fd.getStatus());
        }

        Connection conn = null;
        try {
            conn = DatabaseConfig.getConnection();
            conn.setAutoCommit(false);

            Account account = bankDAO.getAccountForUpdate(conn, fd.getAccountNumber());
            if (account == null) {
                throw new AccountNotFoundException("Funding Account #" + fd.getAccountNumber() + " not found for FD liquidation.");
            }

            // Liquidation return principal + accrued interest if matured
            BigDecimal liquidationAmount = fd.getPrincipalAmount();
            boolean isMatured = LocalDateTime.now().isAfter(fd.getMaturityDate());
            if (isMatured) {
                liquidationAmount = fd.getMaturityAmount();
            }

            account.deposit(liquidationAmount);

            final String updateSql = "UPDATE accounts SET balance = ? WHERE account_number = ?";
            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setBigDecimal(1, account.getBalance());
                ps.setInt(2, fd.getAccountNumber());
                ps.executeUpdate();
            }

            String closeRef = IdGenerator.generateTransactionRef();
            String desc = isMatured ? "Fixed Deposit Maturity Credit" : "Premature Fixed Deposit Closure";

            Transaction txn = new Transaction(
                    0,
                    closeRef,
                    fd.getAccountNumber(),
                    TransactionType.DEPOSIT,
                    liquidationAmount,
                    account.getBalance(),
                    desc,
                    null,
                    LocalDateTime.now()
            );
            transactionDAO.recordTransaction(conn, txn);

            // Double-entry posting: Debit Reserve #0, Credit Customer Account
            ledgerService.postDeposit(closeRef, fd.getAccountNumber(), liquidationAmount, desc, conn);

            String finalStatus = isMatured ? "CLOSED_MATURED" : "CLOSED_PREMATURE";
            bankingModulesDAO.updateFixedDepositStatus(fdId, finalStatus);

            conn.commit();

            recordAudit(actor.getUsername(), actor.getRole(), "CLOSE_FD", "FIXED_DEPOSIT", fd.getFdRef(),
                    String.format("Closed FD %s with %s payout of ₹%s", fd.getFdRef(), finalStatus, liquidationAmount.toPlainString()), "SUCCESS");

            return true;
        } catch (SQLException | RuntimeException ex) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ignored) {}
            }
            throw ex;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException ignored) {}
            }
        }
    }

    // ==========================================
    // 3. LOAN MANAGEMENT
    // ==========================================

    public List<Loan> getCustomerLoans(int customerId, User actor) throws SQLException, AuthorizationException {
        if (actor == null) {
            throw new AuthorizationException("Unauthenticated request.");
        }
        verifyCustomerOwnership(customerId, actor);
        return bankingModulesDAO.getLoansByCustomerId(customerId);
    }

    public List<Loan> getAllLoansForStaff(User actor) throws SQLException, AuthorizationException {
        if (actor == null || (actor.getRole() != Role.TELLER && actor.getRole() != Role.ADMIN)) {
            throw new AuthorizationException("Access Denied: Only staff and administrators may review loan applications.");
        }
        return bankingModulesDAO.getAllLoans();
    }

    public Loan applyLoan(
            int customerId,
            String loanType,
            BigDecimal principal,
            int tenureMonths,
            Integer disbursedAccount,
            User actor
    ) throws SQLException, AuthorizationException, AccountNotFoundException {
        if (actor == null) {
            throw new AuthorizationException("Unauthenticated request.");
        }
        verifyCustomerOwnership(customerId, actor);
        ValidationUtil.validateMonetaryAmount(principal);

        if (tenureMonths <= 0 || tenureMonths > 360) {
            throw new IllegalArgumentException("Loan tenure must be between 1 and 360 months.");
        }
        if (disbursedAccount != null) {
            Account acc = bankDAO.getAccountByNumber(disbursedAccount);
            if (acc == null || acc.getCustomerId() != customerId) {
                throw new AuthorizationException("Disbursement account must belong to the applicant.");
            }
        }

        LoanType lt;
        try {
            lt = LoanType.valueOf(loanType.trim().toUpperCase());
        } catch (Exception e) {
            lt = LoanType.PERSONAL;
        }

        BigDecimal interestRate = BigDecimal.valueOf(lt.getDefaultRate());
        BigDecimal emi = Loan.calculateEMI(principal, interestRate, tenureMonths);
        String loanRef = "LN-" + System.currentTimeMillis();

        Loan loan = new Loan(
                0,
                loanRef,
                customerId,
                lt.name(),
                principal,
                interestRate,
                tenureMonths,
                emi,
                principal, // Outstanding balance initially principal
                disbursedAccount,
                "APPLIED",
                LocalDateTime.now()
        );

        Connection conn = null;
        try {
            conn = DatabaseConfig.getConnection();
            conn.setAutoCommit(false);
            bankingModulesDAO.applyLoan(loan, conn);
            conn.commit();

            recordAudit(actor.getUsername(), actor.getRole(), "APPLY_LOAN", "LOAN", loanRef,
                    String.format("Applied for %s Loan of ₹%s (EMI: ₹%s)", lt.name(), principal.toPlainString(), emi.toPlainString()), "SUCCESS");

            return loan;
        } catch (SQLException ex) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ignored) {}
            }
            throw ex;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException ignored) {}
            }
        }
    }

    public boolean reviewLoanApplication(int loanId, String decision, User actor) throws SQLException, AuthorizationException, AccountNotFoundException {
        if (actor == null || (actor.getRole() != Role.TELLER && actor.getRole() != Role.ADMIN)) {
            throw new AuthorizationException("Access Denied: Only authorized staff may approve or reject loans.");
        }

        Loan loan = bankingModulesDAO.getLoanById(loanId);
        if (loan == null) {
            throw new IllegalArgumentException("Loan #" + loanId + " not found.");
        }
        if (!"APPLIED".equalsIgnoreCase(loan.getStatus())) {
            throw new IllegalArgumentException("Loan #" + loanId + " is already " + loan.getStatus());
        }

        String dec = decision.trim().toUpperCase();
        if ("REJECTED".equals(dec)) {
            bankingModulesDAO.updateLoanStatus(loanId, "REJECTED");
            recordAudit(actor.getUsername(), actor.getRole(), "REJECT_LOAN", "LOAN", loan.getLoanRef(), "Loan rejected by " + actor.getUsername(), "SUCCESS");
            return true;
        }

        if ("APPROVED".equals(dec)) {
            if (loan.getDisbursedAccount() == null) {
                throw new IllegalArgumentException("Cannot approve loan without a designated disbursement account.");
            }

            Connection conn = null;
            try {
                conn = DatabaseConfig.getConnection();
                conn.setAutoCommit(false);

                Account acc = bankDAO.getAccountForUpdate(conn, loan.getDisbursedAccount());
                if (acc == null) {
                    throw new AccountNotFoundException("Disbursement Account #" + loan.getDisbursedAccount() + " not found.");
                }

                // Disburse principal funds into customer account
                acc.deposit(loan.getPrincipalAmount());

                final String updateSql = "UPDATE accounts SET balance = ? WHERE account_number = ?";
                try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                    ps.setBigDecimal(1, acc.getBalance());
                    ps.setInt(2, loan.getDisbursedAccount());
                    ps.executeUpdate();
                }

                String disburseRef = "DISB-" + loan.getLoanRef();
                String desc = "Retail Loan Disbursement (" + loan.getLoanType() + " Ref: " + loan.getLoanRef() + ")";

                Transaction txn = new Transaction(
                        0,
                        disburseRef,
                        loan.getDisbursedAccount(),
                        TransactionType.LOAN_DISBURSEMENT,
                        loan.getPrincipalAmount(),
                        acc.getBalance(),
                        desc,
                        null,
                        LocalDateTime.now()
                );
                transactionDAO.recordTransaction(conn, txn);

                // Double-entry posting: Debit Loan Assets Clearing #0, Credit Customer Account
                ledgerService.postDeposit(disburseRef, loan.getDisbursedAccount(), loan.getPrincipalAmount(), desc, conn);

                bankingModulesDAO.disburseLoan(loanId, loan.getDisbursedAccount(), conn);

                conn.commit();

                recordAudit(actor.getUsername(), actor.getRole(), "APPROVE_LOAN", "LOAN", loan.getLoanRef(),
                        String.format("Approved & disbursed ₹%s to Account #%d", loan.getPrincipalAmount().toPlainString(), loan.getDisbursedAccount()), "SUCCESS");

                return true;
            } catch (SQLException | RuntimeException ex) {
                if (conn != null) {
                    try {
                        conn.rollback();
                    } catch (SQLException ignored) {}
                }
                throw ex;
            } finally {
                if (conn != null) {
                    try {
                        conn.setAutoCommit(true);
                        conn.close();
                    } catch (SQLException ignored) {}
                }
            }
        }
        return false;
    }

    public boolean repayLoanEmi(int loanId, int customerId, int fromAccount, String pin, BigDecimal amount, User actor)
            throws SQLException, AuthorizationException, InsufficientFundsException, InactiveAccountException, AccountNotFoundException {
        if (actor == null) {
            throw new AuthorizationException("Unauthenticated request.");
        }
        verifyCustomerOwnership(customerId, actor);
        ValidationUtil.validateAccountNumber(fromAccount);
        ValidationUtil.validatePin(pin);
        ValidationUtil.validateMonetaryAmount(amount);

        Loan loan = bankingModulesDAO.getLoanById(loanId);
        if (loan == null) {
            throw new IllegalArgumentException("Loan #" + loanId + " not found.");
        }
        if (loan.getCustomerId() != customerId) {
            throw new AuthorizationException("Access Denied: You do not own Loan #" + loanId);
        }
        if (!"ACTIVE".equalsIgnoreCase(loan.getStatus()) && !"APPROVED".equalsIgnoreCase(loan.getStatus())) {
            throw new IllegalArgumentException("Loan #" + loanId + " is not currently active for repayment.");
        }

        Connection conn = null;
        try {
            conn = DatabaseConfig.getConnection();
            conn.setAutoCommit(false);

            Account account = bankDAO.getAccountForUpdate(conn, fromAccount);
            if (account == null) {
                throw new AccountNotFoundException("Repayment Account #" + fromAccount + " not found.");
            }
            if (!account.verifyPin(pin)) {
                conn.commit();
                throw new SecurityException("PIN verification failed for Account #" + fromAccount);
            }

            account.withdraw(amount);

            final String updateSql = "UPDATE accounts SET balance = ? WHERE account_number = ?";
            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setBigDecimal(1, account.getBalance());
                ps.setInt(2, fromAccount);
                ps.executeUpdate();
            }

            String repayRef = IdGenerator.generateTransactionRef();
            String desc = "Loan EMI Repayment for " + loan.getLoanRef();

            Transaction txn = new Transaction(
                    0,
                    repayRef,
                    fromAccount,
                    TransactionType.LOAN_REPAYMENT,
                    amount,
                    account.getBalance(),
                    desc,
                    null,
                    LocalDateTime.now()
            );
            transactionDAO.recordTransaction(conn, txn);

            // Double-entry posting: Debit Customer Account, Credit Loan Asset Reserve #0
            ledgerService.postWithdrawal(repayRef, fromAccount, amount, desc, conn);

            BigDecimal newOutstanding = loan.getOutstandingBalance().subtract(amount);
            if (newOutstanding.compareTo(BigDecimal.ZERO) < 0) {
                newOutstanding = BigDecimal.ZERO;
            }

            bankingModulesDAO.recordRepayment(loanId, amount, newOutstanding, conn);
            if (newOutstanding.compareTo(BigDecimal.ZERO) == 0) {
                bankingModulesDAO.updateLoanStatus(loanId, "CLOSED");
            }

            conn.commit();

            recordAudit(actor.getUsername(), actor.getRole(), "REPAY_LOAN", "LOAN", loan.getLoanRef(),
                    String.format("Repaid ₹%s for loan %s. Remaining: ₹%s", amount.toPlainString(), loan.getLoanRef(), newOutstanding.toPlainString()), "SUCCESS");

            return true;
        } catch (SQLException | InsufficientFundsException | RuntimeException ex) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ignored) {}
            }
            throw ex;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException ignored) {}
            }
        }
    }

    // ==========================================
    // 4. DEBIT CARDS
    // ==========================================

    public List<DebitCard> getCustomerCards(int customerId, User actor) throws SQLException, AuthorizationException {
        if (actor == null) {
            throw new AuthorizationException("Unauthenticated request.");
        }
        verifyCustomerOwnership(customerId, actor);
        return bankingModulesDAO.getCardsByCustomerId(customerId);
    }

    public boolean toggleCardBlock(int cardId, int customerId, boolean blocked, User actor) throws SQLException, AuthorizationException {
        if (actor == null) {
            throw new AuthorizationException("Unauthenticated request.");
        }
        verifyCustomerOwnership(customerId, actor);
        boolean success = bankingModulesDAO.updateCardStatus(cardId, blocked);
        if (success) {
            recordAudit(actor.getUsername(), actor.getRole(), "CARD_STATUS", "CARD", String.valueOf(cardId),
                    "Set card #" + cardId + " blocked=" + blocked, "SUCCESS");
        }
        return success;
    }

    public boolean updateCardDailyLimit(int cardId, int customerId, BigDecimal newLimit, User actor) throws SQLException, AuthorizationException {
        if (actor == null) {
            throw new AuthorizationException("Unauthenticated request.");
        }
        verifyCustomerOwnership(customerId, actor);
        ValidationUtil.validateMonetaryAmount(newLimit);
        if (newLimit.compareTo(new BigDecimal("200000.00")) > 0) {
            throw new IllegalArgumentException("Maximum allowed daily card limit is ₹200,000.00.");
        }
        boolean success = bankingModulesDAO.updateCardDailyLimit(cardId, newLimit);
        if (success) {
            recordAudit(actor.getUsername(), actor.getRole(), "CARD_LIMIT", "CARD", String.valueOf(cardId),
                    "Updated daily limit to ₹" + newLimit.toPlainString(), "SUCCESS");
        }
        return success;
    }

    public boolean toggleContactless(int cardId, int customerId, boolean enabled, User actor) throws SQLException, AuthorizationException {
        if (actor == null) {
            throw new AuthorizationException("Unauthenticated request.");
        }
        verifyCustomerOwnership(customerId, actor);
        boolean success = bankingModulesDAO.updateCardContactless(cardId, enabled);
        if (success) {
            recordAudit(actor.getUsername(), actor.getRole(), "CARD_CONTACTLESS", "CARD", String.valueOf(cardId),
                    "Set contactless=" + enabled, "SUCCESS");
        }
        return success;
    }

    private void verifyCustomerOwnership(int customerId, User actor) throws AuthorizationException, SQLException {
        if (actor.getRole() == Role.CUSTOMER) {
            Customer cust = customerDAO.getCustomerByUserId(actor.getUserId());
            if (cust == null || cust.getCustomerId() != customerId) {
                throw new AuthorizationException("Access Denied: You cannot view or modify another customer's banking facilities.");
            }
        }
    }

    private void recordAudit(String actor, Role role, String action, String type, String id, String details, String status) {
        try {
            AuditLog al = new AuditLog(0, actor, role, action, type, id, details, status, "LOCAL", LocalDateTime.now());
            auditDAO.recordAuditLog(al);
        } catch (Exception ignored) {}
    }
}
