package com.securebank.integration;

import com.securebank.dao.AuditDAO;
import com.securebank.dao.BankDAO;
import com.securebank.dao.CustomerDAO;
import com.securebank.dao.TransactionDAO;
import com.securebank.exception.InsufficientFundsException;
import com.securebank.model.Account;
import com.securebank.model.AccountType;
import com.securebank.model.Role;
import com.securebank.model.Transaction;
import com.securebank.model.TransactionReceipt;
import com.securebank.model.User;
import com.securebank.service.AccountService;
import com.securebank.service.BankingService;
import com.securebank.util.DatabaseConfig;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Integration Test Suite: Verifies end-to-end database transactions,
 * ACID rollback guarantees, and financial ledger persistence.
 */
public class BankingServiceIntegrationTest {

    private BankDAO bankDAO;
    private TransactionDAO transactionDAO;
    private AccountService accountService;
    private BankingService bankingService;

    private User adminUser;
    private User customerAarav;

    @BeforeEach
    public void setUp() {
        DatabaseConfig.switchToEmbeddedH2();
        DatabaseConfig.seedTestData();
        bankDAO = new BankDAO();
        transactionDAO = new TransactionDAO();
        CustomerDAO customerDAO = new CustomerDAO();
        AuditDAO auditDAO = new AuditDAO();

        accountService = new AccountService(bankDAO, customerDAO, auditDAO);
        bankingService = new BankingService(bankDAO, transactionDAO, customerDAO, auditDAO);

        adminUser = new User(101, "admin_rajesh", "hash", Role.ADMIN, 0, false, true);
        customerAarav = new User(103, "aarav", "hash", Role.CUSTOMER, 0, false, true);
        tellerUser = new User(102, "teller_suresh", "hash", Role.TELLER, 0, false, true);
    }

    private User tellerUser;

    @Test
    @DisplayName("Integration: Atomically creates account and inserts initial OPEN transaction")
    public void testAccountCreationAndOpenLedger() throws Exception {
        Account created = accountService.createAccount(
            501,
            "Integration Test User",
            AccountType.SAVINGS,
            BigDecimal.valueOf(2500.00),
            "1234",
            adminUser
        );

        Assertions.assertTrue(created.getAccountNumber() >= 1001);
        Account fetched = bankDAO.getAccountByNumber(created.getAccountNumber());
        Assertions.assertNotNull(fetched);
        Assertions.assertEquals(BigDecimal.valueOf(2500.00).setScale(2), fetched.getBalance());

        List<Transaction> txns = transactionDAO.getRecentTransactions(created.getAccountNumber(), 5);
        Assertions.assertFalse(txns.isEmpty());
        Assertions.assertEquals("OPEN", txns.get(0).getTxnType().name());
    }

    @Test
    @DisplayName("Integration: Atomic Deposit increases balance and records ledger transaction")
    public void testAtomicDeposit() throws Exception {
        Account initial = bankDAO.getAccountByNumber(1001);
        BigDecimal startingBal = initial.getBalance();

        TransactionReceipt receipt = bankingService.deposit(
            1001,
            BigDecimal.valueOf(1500.00),
            "Integration Deposit Test",
            tellerUser
        );

        Assertions.assertNotNull(receipt.getTransactionRef());
        Account updated = bankDAO.getAccountByNumber(1001);
        Assertions.assertEquals(startingBal.add(BigDecimal.valueOf(1500.00)), updated.getBalance());
    }

    @Test
    @DisplayName("Integration: Atomic Withdrawal decreases balance and updates audit ledger")
    public void testAtomicWithdrawal() throws Exception {
        Account initial = bankDAO.getAccountByNumber(1001);
        BigDecimal startingBal = initial.getBalance();

        TransactionReceipt receipt = bankingService.withdraw(
            1001,
            "1234",
            BigDecimal.valueOf(500.00),
            "ATM Cash Test",
            customerAarav
        );

        Assertions.assertNotNull(receipt.getTransactionRef());
        Account updated = bankDAO.getAccountByNumber(1001);
        Assertions.assertEquals(startingBal.subtract(BigDecimal.valueOf(500.00)), updated.getBalance());
    }

    @Test
    @DisplayName("Integration: ACID Fund Transfer debits sender, credits receiver, and generates dual ledger rows")
    public void testAtomicFundTransfer() throws Exception {
        Account senderBefore = bankDAO.getAccountByNumber(1001);
        Account receiverBefore = bankDAO.getAccountByNumber(1002);

        BigDecimal transferAmt = BigDecimal.valueOf(1000.00);

        TransactionReceipt receipt = bankingService.transfer(
            1001,
            "1234",
            1002,
            transferAmt,
            "Test NEFT Transfer",
            customerAarav
        );

        Assertions.assertNotNull(receipt.getTransactionRef());

        Account senderAfter = bankDAO.getAccountByNumber(1001);
        Account receiverAfter = bankDAO.getAccountByNumber(1002);

        Assertions.assertEquals(senderBefore.getBalance().subtract(transferAmt), senderAfter.getBalance());
        Assertions.assertEquals(receiverBefore.getBalance().add(transferAmt), receiverAfter.getBalance());
    }

    @Test
    @DisplayName("Integration: Transfer Rollback on Insufficient Funds leaves both accounts unmodified")
    public void testTransferRollbackOnFailure() throws Exception {
        Account senderBefore = bankDAO.getAccountByNumber(1001);
        Account receiverBefore = bankDAO.getAccountByNumber(1002);

        // Attempt transfer exceeding sender balance
        BigDecimal excessiveAmt = senderBefore.getBalance().add(BigDecimal.valueOf(100000.00));

        Assertions.assertThrows(InsufficientFundsException.class, () -> {
            bankingService.transfer(
                1001,
                "1234",
                1002,
                excessiveAmt,
                "Impossible Transfer",
                customerAarav
            );
        });

        Account senderAfter = bankDAO.getAccountByNumber(1001);
        Account receiverAfter = bankDAO.getAccountByNumber(1002);

        // Balances must be 100% untouched
        Assertions.assertEquals(senderBefore.getBalance(), senderAfter.getBalance());
        Assertions.assertEquals(receiverBefore.getBalance(), receiverAfter.getBalance());
    }

    @Test
    @DisplayName("Integration: Soft Account Closure preserves financial ledger records")
    public void testAccountClosurePreservesHistory() throws Exception {
        // Open a temporary account with 1000
        Account acc = accountService.createAccount(
            501,
            "Closure Test Holder",
            AccountType.SAVINGS,
            BigDecimal.valueOf(1000.00),
            "1234",
            adminUser
        );
        int accNo = acc.getAccountNumber();

        // Withdraw all funds so balance is 0.00 before closing
        // Note: For Savings, withdrawing to 0.00 requires withdrawing down to 0 for account close
        // Let's set balance to 0 for closing test
        bankDAO.updateBalanceAndLog(accNo, 0.00, "WITHDRAW", 1000.00, "Pre-closure withdrawal");

        boolean closed = accountService.closeAccount(accNo, "1234", adminUser);
        Assertions.assertTrue(closed);

        Account closedAcc = bankDAO.getAccountByNumber(accNo);
        Assertions.assertEquals("CLOSED", closedAcc.getStatus().name());

        // Transactions MUST still be present in database!
        List<Transaction> txns = transactionDAO.getRecentTransactions(accNo, 10);
        Assertions.assertFalse(txns.isEmpty());
    }

    @Test
    @DisplayName("Integration: Duplicate transfer with identical Idempotency-Key returns cached receipt without double-debiting")
    public void testTransferWithIdempotencyProtection() throws Exception {
        Account senderBefore = bankDAO.getAccountByNumber(1001);
        Account receiverBefore = bankDAO.getAccountByNumber(1002);
        BigDecimal transferAmt = BigDecimal.valueOf(350.00);
        String idempotencyKey = "IDEM-TEST-" + System.currentTimeMillis();

        // 1st Transfer call
        TransactionReceipt receipt1 = bankingService.transfer(
            1001,
            "1234",
            1002,
            transferAmt,
            "Idempotency Initial Transfer",
            customerAarav,
            idempotencyKey
        );
        Assertions.assertNotNull(receipt1);
        Assertions.assertEquals("SUCCESS", receipt1.getStatus());

        Account senderAfter1 = bankDAO.getAccountByNumber(1001);
        Account receiverAfter1 = bankDAO.getAccountByNumber(1002);
        Assertions.assertEquals(senderBefore.getBalance().subtract(transferAmt), senderAfter1.getBalance());
        Assertions.assertEquals(receiverBefore.getBalance().add(transferAmt), receiverAfter1.getBalance());

        // 2nd Transfer call with SAME idempotency key (simulating network timeout retry)
        TransactionReceipt receipt2 = bankingService.transfer(
            1001,
            "1234",
            1002,
            transferAmt,
            "Idempotency Retry Transfer",
            customerAarav,
            idempotencyKey
        );
        Assertions.assertNotNull(receipt2);
        Assertions.assertEquals(receipt1.getTransactionRef(), receipt2.getTransactionRef());

        // Balances MUST NOT change on duplicate request!
        Account senderAfter2 = bankDAO.getAccountByNumber(1001);
        Account receiverAfter2 = bankDAO.getAccountByNumber(1002);
        Assertions.assertEquals(senderAfter1.getBalance(), senderAfter2.getBalance(), "Sender balance must not be debited twice!");
        Assertions.assertEquals(receiverAfter1.getBalance(), receiverAfter2.getBalance(), "Receiver balance must not be credited twice!");
    }
}
