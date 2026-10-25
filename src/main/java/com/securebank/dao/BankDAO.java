package com.securebank.dao;

import com.securebank.exception.InactiveAccountException;
import com.securebank.exception.InsufficientFundsException;
import com.securebank.model.Account;
import com.securebank.model.AccountStatus;
import com.securebank.model.CurrentAccount;
import com.securebank.model.SavingsAccount;
import com.securebank.model.Transaction;
import com.securebank.model.TransactionType;
import com.securebank.model.EntryType;
import com.securebank.model.LedgerEntry;
import com.securebank.model.LedgerTransaction;
import com.securebank.util.CurrencyFormatter;
import com.securebank.util.DatabaseConfig;
import com.securebank.util.IdGenerator;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * SecureBank: Role-Based Core Banking & Transaction Management System
 * File: BankDAO.java
 *
 * Primary Data Access Object orchestrating ACID financial transactions,
 * thread-safe row-level locking (SELECT ... FOR UPDATE), deadlock-safe transfer ordering,
 * and polymorphic account hydration.
 */
public class BankDAO {

    private final TransactionDAO transactionDAO = new TransactionDAO();
    private final LedgerDAO ledgerDAO = new LedgerDAO();

    public BankDAO() {
        // Uses centralized DatabaseConfig
    }

    public BankDAO(String dbUrl, String dbUser, String dbPassword) {
        DatabaseConfig.setConfig(dbUrl, dbUser, dbPassword);
    }

    public Connection getConnection() throws SQLException {
        return DatabaseConfig.getConnection();
    }

    // =========================================================================
    // 1. CREATE ACCOUNT
    // =========================================================================
    public int createAccount(Account account) throws SQLException {
        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false);
            int accNo = createAccount(account, conn);
            conn.commit();
            return accNo;
        } catch (SQLException ex) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ignored) {
                }
            }
            throw ex;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException ignored) {
                }
            }
        }
    }

    public int createAccount(Account account, Connection conn) throws SQLException {
        final String insertSql =
            "INSERT INTO accounts (customer_id, holder_name, account_type, balance, pin, failed_attempts, is_locked, status, created_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        int generatedAccNo;
        try (PreparedStatement ps = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, account.getCustomerId());
            ps.setString(2, account.getHolderName());
            ps.setString(3, account.getAccountType());
            ps.setBigDecimal(4, account.getBalance());
            ps.setString(5, account.getPinHash());
            ps.setInt(6, account.getFailedAttempts());
            ps.setBoolean(7, account.isLocked());
            ps.setString(8, account.getStatus() != null ? account.getStatus().name() : AccountStatus.ACTIVE.name());
            ps.setTimestamp(9, Timestamp.valueOf(account.getCreatedAt() != null ? account.getCreatedAt() : LocalDateTime.now()));

            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new SQLException("Creating account failed, no records inserted.");
            }

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    generatedAccNo = keys.getInt(1);
                    account.setAccountNumber(generatedAccNo);
                } else {
                    throw new SQLException("Creating account failed, no generated account_number obtained.");
                }
            }
        }

        // Record initial OPEN ledger transaction
        String txnRef = IdGenerator.generateTransactionRef();
        Transaction initialTxn = new Transaction(
            0,
            txnRef,
            generatedAccNo,
            TransactionType.OPEN,
            account.getBalance(),
            account.getBalance(),
            "Initial Account Opening (" + account.getAccountType() + ")",
            null,
            LocalDateTime.now()
        );
        transactionDAO.recordTransaction(conn, initialTxn);

        // Atomic Double-Entry Ledger posting for opening balance
        if (account.getBalance() != null && account.getBalance().compareTo(BigDecimal.ZERO) > 0) {
            postLedgerDeposit(conn, txnRef, generatedAccNo, account.getBalance(), "Initial Account Opening Deposit");
        }

        account.recordLocalTransaction("OPEN", account.getBalance(), account.getBalance(), "Initial Account Opening");
        return generatedAccNo;
    }

    // =========================================================================
    // 2. READ ACCOUNT
    // =========================================================================
    public Account getAccountByNumber(int accountNumber) throws SQLException {
        final String selectSql =
            "SELECT account_number, customer_id, holder_name, account_type, balance, pin, failed_attempts, is_locked, status, created_at "
            + "FROM accounts WHERE account_number = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(selectSql)) {

            ps.setInt(1, accountNumber);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Account account = mapRowToAccount(rs);
                String[] lastFive = transactionDAO.fetchLastFiveTransactions(conn, accountNumber);
                account.setRecentTransactions(lastFive);
                return account;
            }
        }
    }

    public List<Account> getAllAccounts() throws SQLException {
        final String sql =
            "SELECT account_number, customer_id, holder_name, account_type, balance, pin, failed_attempts, is_locked, status, created_at "
            + "FROM accounts ORDER BY account_number ASC";

        List<Account> accounts = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Account acc = mapRowToAccount(rs);
                acc.setRecentTransactions(transactionDAO.fetchLastFiveTransactions(conn, acc.getAccountNumber()));
                accounts.add(acc);
            }
        }
        return accounts;
    }

    public List<Account> getAccountsByCustomerId(int customerId) throws SQLException {
        final String sql =
            "SELECT account_number, customer_id, holder_name, account_type, balance, pin, failed_attempts, is_locked, status, created_at "
            + "FROM accounts WHERE customer_id = ? ORDER BY account_number ASC";

        List<Account> accounts = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Account acc = mapRowToAccount(rs);
                    acc.setRecentTransactions(transactionDAO.fetchLastFiveTransactions(conn, acc.getAccountNumber()));
                    accounts.add(acc);
                }
            }
        }
        return accounts;
    }

    public String[] fetchLastFiveTransactions(Connection conn, int accountNumber) throws SQLException {
        return transactionDAO.fetchLastFiveTransactions(conn, accountNumber);
    }

    // =========================================================================
    // 3. ATOMIC DEPOSIT WITH ROW-LEVEL LOCKING
    // =========================================================================
    public Transaction deposit(int accountNumber, BigDecimal amount, String remarks, String transactionRef)
        throws SQLException, InactiveAccountException {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Deposit amount must be strictly positive.");
        }

        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false);

            Account account = getAccountForUpdate(conn, accountNumber);
            if (account == null) {
                throw new SQLException("Account #" + accountNumber + " does not exist.");
            }
            if (!account.getStatus().canTransact()) {
                throw new InactiveAccountException("Account #" + accountNumber + " is " + account.getStatus() + ". Deposits rejected.");
            }

            account.deposit(amount);

            final String updateSql = "UPDATE accounts SET balance = ? WHERE account_number = ?";
            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setBigDecimal(1, account.getBalance());
                ps.setInt(2, accountNumber);
                ps.executeUpdate();
            }

            String ref = (transactionRef != null && !transactionRef.isEmpty()) ? transactionRef : IdGenerator.generateTransactionRef();
            Transaction txn = new Transaction(
                0,
                ref,
                accountNumber,
                TransactionType.DEPOSIT,
                amount,
                account.getBalance(),
                remarks != null ? remarks : "Cash / Counter Deposit",
                null,
                LocalDateTime.now()
            );
            transactionDAO.recordTransaction(conn, txn);

            // Atomic Double-Entry Ledger posting: Debit Vault Cash #0, Credit Customer Account
            postLedgerDeposit(conn, ref, accountNumber, amount, remarks != null ? remarks : "Cash / Counter Deposit");

            conn.commit();
            return txn;

        } catch (SQLException | InactiveAccountException | RuntimeException ex) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ignored) {
                }
            }
            throw ex;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException ignored) {
                }
            }
        }
    }

    // =========================================================================
    // 4. ATOMIC WITHDRAWAL WITH ROW-LEVEL LOCKING & PIN AUTHENTICATION
    // =========================================================================
    public Transaction withdraw(int accountNumber, String pin, BigDecimal amount, String remarks, String transactionRef)
        throws SQLException, InsufficientFundsException, InactiveAccountException {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Withdrawal amount must be strictly positive.");
        }

        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false);

            Account account = getAccountForUpdate(conn, accountNumber);
            if (account == null) {
                throw new SQLException("Account #" + accountNumber + " does not exist.");
            }
            if (!account.getStatus().canTransact()) {
                throw new InactiveAccountException("Account #" + accountNumber + " is " + account.getStatus() + ". Withdrawals rejected.");
            }

            // Verify PIN
            if (!account.verifyPin(pin)) {
                updatePinSecurityInTx(conn, accountNumber, account.getFailedAttempts(), account.getStatus());
                conn.commit();
                throw new SecurityException("PIN verification failed for Account #" + accountNumber);
            }

            // Reset failed attempts upon successful verification
            updatePinSecurityInTx(conn, accountNumber, 0, AccountStatus.ACTIVE);

            // Polymorphic withdrawal logic
            account.withdraw(amount);

            final String updateSql = "UPDATE accounts SET balance = ? WHERE account_number = ?";
            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setBigDecimal(1, account.getBalance());
                ps.setInt(2, accountNumber);
                ps.executeUpdate();
            }

            String ref = (transactionRef != null && !transactionRef.isEmpty()) ? transactionRef : IdGenerator.generateTransactionRef();
            Transaction txn = new Transaction(
                0,
                ref,
                accountNumber,
                TransactionType.WITHDRAW,
                amount,
                account.getBalance(),
                remarks != null ? remarks : (account.getAccountType() + " Cash Withdrawal"),
                null,
                LocalDateTime.now()
            );
            transactionDAO.recordTransaction(conn, txn);

            // Atomic Double-Entry Ledger posting: Debit Customer Account, Credit Vault Cash #0
            postLedgerWithdrawal(conn, ref, accountNumber, amount, remarks != null ? remarks : (account.getAccountType() + " Cash Withdrawal"));

            conn.commit();
            return txn;

        } catch (SQLException | InsufficientFundsException | InactiveAccountException | RuntimeException ex) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ignored) {
                }
            }
            throw ex;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException ignored) {
                }
            }
        }
    }

    // =========================================================================
    // 5. DEADLOCK-SAFE ACID FUND TRANSFER
    // =========================================================================
    public String transferFunds(
        int senderAccNo,
        String senderPin,
        int receiverAccNo,
        BigDecimal amount,
        String remarks,
        String transactionRef
    ) throws SQLException, InsufficientFundsException, InactiveAccountException {
        if (senderAccNo == receiverAccNo) {
            throw new IllegalArgumentException("Sender and receiver account numbers cannot be identical.");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transfer amount must be strictly positive.");
        }

        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false);

            // DEADLOCK PREVENTION: Lock accounts in strictly ascending order
            int firstLock = Math.min(senderAccNo, receiverAccNo);
            int secondLock = Math.max(senderAccNo, receiverAccNo);

            Account firstAcc = getAccountForUpdate(conn, firstLock);
            Account secondAcc = getAccountForUpdate(conn, secondLock);

            if (firstAcc == null || secondAcc == null) {
                throw new SQLException("One or both accounts (#" + senderAccNo + ", #" + receiverAccNo + ") do not exist.");
            }

            Account sender = (senderAccNo == firstLock) ? firstAcc : secondAcc;
            Account receiver = (receiverAccNo == firstLock) ? firstAcc : secondAcc;

            if (!sender.getStatus().canTransact()) {
                throw new InactiveAccountException("Sender Account #" + senderAccNo + " is " + sender.getStatus() + ". Transfer rejected.");
            }
            if (!receiver.getStatus().canTransact()) {
                throw new InactiveAccountException("Receiver Account #" + receiverAccNo + " is " + receiver.getStatus() + ". Transfer rejected.");
            }

            // Verify Sender PIN
            if (!sender.verifyPin(senderPin)) {
                updatePinSecurityInTx(conn, senderAccNo, sender.getFailedAttempts(), sender.getStatus());
                conn.commit();
                throw new SecurityException("PIN verification failed for Sender Account #" + senderAccNo);
            }
            updatePinSecurityInTx(conn, senderAccNo, 0, AccountStatus.ACTIVE);

            // Polymorphic debit & credit in memory
            sender.withdraw(amount);
            receiver.deposit(amount);

            // Persist balances
            final String updateSql = "UPDATE accounts SET balance = ? WHERE account_number = ?";
            try (PreparedStatement psSender = conn.prepareStatement(updateSql);
                 PreparedStatement psReceiver = conn.prepareStatement(updateSql)) {

                psSender.setBigDecimal(1, sender.getBalance());
                psSender.setInt(2, senderAccNo);
                psSender.executeUpdate();

                psReceiver.setBigDecimal(1, receiver.getBalance());
                psReceiver.setInt(2, receiverAccNo);
                psReceiver.executeUpdate();
            }

            String ref = (transactionRef != null && !transactionRef.isEmpty()) ? transactionRef : IdGenerator.generateTransactionRef();
            String transferDesc = (remarks != null && !remarks.isEmpty()) ? remarks : ("Transfer to #" + receiverAccNo);

            // Outbound transaction record
            Transaction outbound = new Transaction(
                0,
                ref,
                senderAccNo,
                TransactionType.TRANSFER_OUT,
                amount,
                sender.getBalance(),
                transferDesc,
                receiverAccNo,
                LocalDateTime.now()
            );
            transactionDAO.recordTransaction(conn, outbound);

            // Inbound transaction record
            Transaction inbound = new Transaction(
                0,
                ref,
                receiverAccNo,
                TransactionType.TRANSFER_IN,
                amount,
                receiver.getBalance(),
                "Transfer from #" + senderAccNo + ": " + transferDesc,
                senderAccNo,
                LocalDateTime.now()
            );
            transactionDAO.recordTransaction(conn, inbound);

            // Atomic Double-Entry Ledger posting: Debit Sender, Credit Receiver
            postLedgerTransfer(conn, ref, senderAccNo, receiverAccNo, amount, transferDesc);

            // Record transfer linkage if table exists
            recordTransferDetails(conn, ref, senderAccNo, receiverAccNo, amount, remarks);

            conn.commit();
            return ref;

        } catch (SQLException | InsufficientFundsException | InactiveAccountException | RuntimeException ex) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ignored) {
                }
            }
            throw ex;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException ignored) {
                }
            }
        }
    }

    // Backward-compatible transferFunds overload
    public void transferFunds(int senderAccNo, String senderPin, int receiverAccNo, double amount)
        throws SQLException, InsufficientFundsException {
        try {
            transferFunds(senderAccNo, senderPin, receiverAccNo, BigDecimal.valueOf(amount), null, null);
        } catch (InactiveAccountException e) {
            throw new SQLException(e.getMessage(), e);
        }
    }

    // =========================================================================
    // 6. APPLY YEARLY INTEREST (SAVINGS ONLY)
    // =========================================================================
    public BigDecimal applyYearlyInterest(int savingsAccNo, String transactionRef) throws SQLException {
        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false);

            Account account = getAccountForUpdate(conn, savingsAccNo);
            if (account == null) {
                throw new SQLException("Savings Account #" + savingsAccNo + " not found.");
            }
            if (!(account instanceof SavingsAccount)) {
                throw new IllegalArgumentException("Account #" + savingsAccNo + " is not a SAVINGS account.");
            }

            SavingsAccount sa = (SavingsAccount) account;
            BigDecimal interest = sa.applyYearlyInterest();

            final String updateSql = "UPDATE accounts SET balance = ? WHERE account_number = ?";
            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setBigDecimal(1, sa.getBalance());
                ps.setInt(2, savingsAccNo);
                ps.executeUpdate();
            }

            String ref = (transactionRef != null && !transactionRef.isEmpty()) ? transactionRef : IdGenerator.generateTransactionRef();
            Transaction txn = new Transaction(
                0,
                ref,
                savingsAccNo,
                TransactionType.INTEREST,
                interest,
                sa.getBalance(),
                "4% Yearly Savings Interest Credited",
                null,
                LocalDateTime.now()
            );
            transactionDAO.recordTransaction(conn, txn);

            // Atomic Double-Entry Ledger posting: Debit Vault Reserve #0, Credit Savings Account
            postLedgerDeposit(conn, ref, savingsAccNo, interest, "4% Yearly Savings Interest Credited");

            conn.commit();
            return interest;

        } catch (SQLException | RuntimeException ex) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ignored) {
                }
            }
            throw ex;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException ignored) {
                }
            }
        }
    }

    // =========================================================================
    // 7. SAFE ACCOUNT CLOSURE (NON-DESTRUCTIVE SOFT CLOSURE)
    // =========================================================================
    public boolean closeAccount(int accountNumber, String pin) throws SQLException {
        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false);

            Account acc = getAccountForUpdate(conn, accountNumber);
            if (acc == null) {
                throw new SQLException("Account #" + accountNumber + " not found.");
            }

            if (!acc.verifyPin(pin)) {
                updatePinSecurityInTx(conn, accountNumber, acc.getFailedAttempts(), acc.getStatus());
                conn.commit();
                throw new SecurityException("PIN verification failed for Account #" + accountNumber);
            }

            // Prevent closure if outstanding balance or overdraft exists
            if (acc.getBalance().compareTo(BigDecimal.ZERO) != 0) {
                throw new IllegalStateException(
                    String.format("Cannot close Account #%d with non-zero balance (%s). Please settle balance first.",
                        accountNumber, CurrencyFormatter.format(acc.getBalance()))
                );
            }

            final String closeSql = "UPDATE accounts SET status = 'CLOSED' WHERE account_number = ?";
            try (PreparedStatement ps = conn.prepareStatement(closeSql)) {
                ps.setInt(1, accountNumber);
                int rows = ps.executeUpdate();
                conn.commit();
                return rows > 0;
            }

        } catch (SQLException | RuntimeException ex) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ignored) {
                }
            }
            throw ex;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException ignored) {
                }
            }
        }
    }

    // Physical deletion maintained only for backward compatibility / tests
    public boolean deleteAccount(int accountNumber) throws SQLException {
        return closeAccountSoftOrHard(accountNumber);
    }

    private boolean closeAccountSoftOrHard(int accountNumber) throws SQLException {
        final String sql = "UPDATE accounts SET status = 'CLOSED' WHERE account_number = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, accountNumber);
            int rows = ps.executeUpdate();
            if (rows > 0) {
                return true;
            }
        }
        // Fallback physical delete if status column doesn't exist
        final String delSql = "DELETE FROM accounts WHERE account_number = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(delSql)) {
            ps.setInt(1, accountNumber);
            return ps.executeUpdate() > 0;
        }
    }

    // =========================================================================
    // 8. SECURITY & STATUS UPDATES
    // =========================================================================
    public void updatePinSecurityState(int accountNumber, int failedAttempts, boolean isLocked) throws SQLException {
        updatePinSecurityState(accountNumber, failedAttempts, isLocked ? AccountStatus.LOCKED : AccountStatus.ACTIVE);
    }

    public void updatePinSecurityState(int accountNumber, int failedAttempts, AccountStatus status) throws SQLException {
        final String sql = "UPDATE accounts SET failed_attempts = ?, is_locked = ?, status = ? WHERE account_number = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, failedAttempts);
            ps.setBoolean(2, status == AccountStatus.LOCKED);
            ps.setString(3, status.name());
            ps.setInt(4, accountNumber);
            ps.executeUpdate();
        }
    }

    public void updateAccountStatus(int accountNumber, AccountStatus status) throws SQLException {
        final String sql = "UPDATE accounts SET status = ?, is_locked = ? WHERE account_number = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setBoolean(2, status == AccountStatus.LOCKED);
            ps.setInt(3, accountNumber);
            ps.executeUpdate();
        }
    }

    public void updateBalanceAndLog(
        int accountNumber,
        double newBalance,
        String txnType,
        double amount,
        String remarks
    ) throws SQLException {
        try {
            if ("DEPOSIT".equalsIgnoreCase(txnType)) {
                deposit(accountNumber, BigDecimal.valueOf(amount), remarks, null);
            } else if ("WITHDRAW".equalsIgnoreCase(txnType)) {
                // Administrative balance log
                updateBalanceDirect(accountNumber, BigDecimal.valueOf(newBalance), txnType, BigDecimal.valueOf(amount), remarks);
            } else {
                updateBalanceDirect(accountNumber, BigDecimal.valueOf(newBalance), txnType, BigDecimal.valueOf(amount), remarks);
            }
        } catch (InactiveAccountException e) {
            throw new SQLException(e.getMessage(), e);
        }
    }

    private void updateBalanceDirect(int accNo, BigDecimal newBal, String txnType, BigDecimal amount, String remarks) throws SQLException {
        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false);

            final String updateSql = "UPDATE accounts SET balance = ? WHERE account_number = ?";
            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setBigDecimal(1, newBal);
                ps.setInt(2, accNo);
                ps.executeUpdate();
            }

            Transaction txn = new Transaction(
                0,
                IdGenerator.generateTransactionRef(),
                accNo,
                TransactionType.fromString(txnType),
                amount,
                newBal,
                remarks,
                null,
                LocalDateTime.now()
            );
            transactionDAO.recordTransaction(conn, txn);
            conn.commit();
        } catch (SQLException e) {
            if (conn != null) {
                conn.rollback();
            }
            throw e;
        } finally {
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
    }

    // =========================================================================
    // HELPERS & TRANSACTION LOCKING
    // =========================================================================
    public Account getAccountForUpdate(Connection conn, int accountNumber) throws SQLException {
        final String sql =
            "SELECT account_number, customer_id, holder_name, account_type, balance, pin, failed_attempts, is_locked, status, created_at "
            + "FROM accounts WHERE account_number = ? FOR UPDATE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, accountNumber);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRowToAccount(rs);
                }
                return null;
            }
        }
    }

    private void updatePinSecurityInTx(Connection conn, int accNo, int failedAttempts, AccountStatus status)
        throws SQLException {
        final String sql = "UPDATE accounts SET failed_attempts = ?, is_locked = ?, status = ? WHERE account_number = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, failedAttempts);
            ps.setBoolean(2, status == AccountStatus.LOCKED);
            ps.setString(3, status.name());
            ps.setInt(4, accNo);
            ps.executeUpdate();
        }
    }

    private void recordTransferDetails(Connection conn, String ref, int sender, int receiver, BigDecimal amount, String remarks) {
        final String sql = "INSERT INTO transfer_details (transfer_ref, sender_account, receiver_account, amount, remarks) "
            + "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ref);
            ps.setInt(2, sender);
            ps.setInt(3, receiver);
            ps.setBigDecimal(4, amount);
            ps.setString(5, remarks);
            ps.executeUpdate();
        } catch (SQLException ignored) {
            // Optional table in minimal migrations
        }
    }

    private Account mapRowToAccount(ResultSet rs) throws SQLException {
        int accNo = rs.getInt("account_number");
        int customerId = 0;
        try {
            customerId = rs.getInt("customer_id");
        } catch (SQLException ignored) {
        }
        String holder = rs.getString("holder_name");
        String type = rs.getString("account_type");
        BigDecimal bal = rs.getBigDecimal("balance");
        String pin = rs.getString("pin");
        int attempts = rs.getInt("failed_attempts");
        boolean locked = rs.getBoolean("is_locked");

        AccountStatus status = locked ? AccountStatus.LOCKED : AccountStatus.ACTIVE;
        try {
            String statusStr = rs.getString("status");
            if (statusStr != null) {
                status = AccountStatus.fromString(statusStr);
            }
        } catch (SQLException ignored) {
        }

        LocalDateTime createdAt = LocalDateTime.now();
        try {
            Timestamp ts = rs.getTimestamp("created_at");
            if (ts != null) {
                createdAt = ts.toLocalDateTime();
            }
        } catch (SQLException ignored) {
        }

        if ("SAVINGS".equalsIgnoreCase(type)) {
            return new SavingsAccount(accNo, customerId, holder, bal, pin, attempts, status, createdAt);
        } else if ("CURRENT".equalsIgnoreCase(type)) {
            return new CurrentAccount(accNo, customerId, holder, bal, pin, attempts, status, createdAt);
        } else {
            throw new SQLException("Unknown account_type: " + type);
        }
    }

    private void postLedgerDeposit(Connection conn, String txnRef, int customerAccount, BigDecimal amount, String description) throws SQLException {
        LedgerTransaction lt = new LedgerTransaction(0, txnRef, "DEPOSIT", description, "POSTED", LocalDateTime.now());
        lt.addEntry(new LedgerEntry(0, 0, 0, EntryType.DEBIT, amount, LocalDateTime.now()));
        lt.addEntry(new LedgerEntry(0, 0, customerAccount, EntryType.CREDIT, amount, LocalDateTime.now()));
        ledgerDAO.postTransaction(lt, conn);
    }

    private void postLedgerWithdrawal(Connection conn, String txnRef, int customerAccount, BigDecimal amount, String description) throws SQLException {
        LedgerTransaction lt = new LedgerTransaction(0, txnRef, "WITHDRAW", description, "POSTED", LocalDateTime.now());
        lt.addEntry(new LedgerEntry(0, 0, customerAccount, EntryType.DEBIT, amount, LocalDateTime.now()));
        lt.addEntry(new LedgerEntry(0, 0, 0, EntryType.CREDIT, amount, LocalDateTime.now()));
        ledgerDAO.postTransaction(lt, conn);
    }

    private void postLedgerTransfer(Connection conn, String txnRef, int fromAccount, int toAccount, BigDecimal amount, String description) throws SQLException {
        LedgerTransaction lt = new LedgerTransaction(0, txnRef, "TRANSFER", description, "POSTED", LocalDateTime.now());
        lt.addEntry(new LedgerEntry(0, 0, fromAccount, EntryType.DEBIT, amount, LocalDateTime.now()));
        lt.addEntry(new LedgerEntry(0, 0, toAccount, EntryType.CREDIT, amount, LocalDateTime.now()));
        ledgerDAO.postTransaction(lt, conn);
    }
}

