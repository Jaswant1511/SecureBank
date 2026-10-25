package com.securebank.integration;

import com.securebank.dao.AuditDAO;
import com.securebank.dao.BankDAO;
import com.securebank.dao.CustomerDAO;
import com.securebank.dao.TransactionDAO;
import com.securebank.model.Account;
import com.securebank.model.AccountType;
import com.securebank.model.Role;
import com.securebank.model.User;
import com.securebank.service.AccountService;
import com.securebank.service.BankingService;
import com.securebank.util.DatabaseConfig;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Concurrency Test Suite: Simulates simultaneous multithreaded operations
 * to verify Row-Level Locking, Race Condition Elimination, and Deadlock-Free Transfers.
 */
public class ConcurrencyTest {

    private BankDAO bankDAO;
    private BankingService bankingService;
    private AccountService accountService;
    private User adminUser;
    private User tellerUser;

    @BeforeEach
    public void setUp() {
        DatabaseConfig.switchToEmbeddedH2();
        DatabaseConfig.seedTestData();
        bankDAO = new BankDAO();
        CustomerDAO customerDAO = new CustomerDAO();
        TransactionDAO transactionDAO = new TransactionDAO();
        AuditDAO auditDAO = new AuditDAO();

        accountService = new AccountService(bankDAO, customerDAO, auditDAO);
        bankingService = new BankingService(bankDAO, transactionDAO, customerDAO, auditDAO);
        adminUser = new User(101, "admin_rajesh", "hash", Role.ADMIN, 0, false, true);
        tellerUser = new User(102, "teller_suresh", "hash", Role.TELLER, 0, false, true);
    }

    @Test
    @DisplayName("Concurrency: Simultaneous deposits preserve exact mathematical balance without lost updates")
    public void testConcurrentDeposits() throws Exception {
        Account acc = accountService.createAccount(
            501,
            "Concurrent Deposit Holder",
            AccountType.SAVINGS,
            BigDecimal.valueOf(1000.00),
            "1234",
            adminUser
        );
        int accNo = acc.getAccountNumber();

        int numThreads = 10;
        BigDecimal depositPerThread = BigDecimal.valueOf(100.00);

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);

        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    bankingService.deposit(accNo, depositPerThread, "Thread Deposit", tellerUser);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Fire all threads simultaneously
        startLatch.countDown();
        boolean finished = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        Assertions.assertTrue(finished);
        Account finalAcc = bankDAO.getAccountByNumber(accNo);
        // Expected: 1000.00 + (10 * 100.00) = 2000.00
        BigDecimal expected = BigDecimal.valueOf(1000.00).add(depositPerThread.multiply(BigDecimal.valueOf(numThreads)));
        Assertions.assertEquals(expected.setScale(2), finalAcc.getBalance());
    }

    @Test
    @DisplayName("Concurrency: Simultaneous withdrawals cannot overspend the account balance")
    public void testConcurrentWithdrawalsOverdraftProtection() throws Exception {
        // Savings Account with Rs. 2,000.00. Minimum balance is Rs. 1,000.00.
        // Therefore, exactly Rs. 1,000.00 can be withdrawn.
        Account acc = accountService.createAccount(
            501,
            "Concurrent Withdrawal Holder",
            AccountType.SAVINGS,
            BigDecimal.valueOf(2000.00),
            "1234",
            adminUser
        );
        int accNo = acc.getAccountNumber();

        int numThreads = 5;
        // Each thread attempts to withdraw Rs. 500.00.
        // Exactly 2 threads should succeed (2 * 500 = 1000, leaving 1000). The other 3 must fail!
        BigDecimal withdrawAmt = BigDecimal.valueOf(500.00);

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger rejectCount = new AtomicInteger(0);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < numThreads; i++) {
            futures.add(executor.submit(() -> {
                try {
                    startLatch.await();
                    bankingService.withdraw(accNo, "1234", withdrawAmt, "Concurrent ATM", tellerUser);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    rejectCount.incrementAndGet();
                }
            }));
        }

        startLatch.countDown();
        for (Future<?> f : futures) {
            f.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();

        Account finalAcc = bankDAO.getAccountByNumber(accNo);
        Assertions.assertEquals(2, successCount.get(), "Exactly two withdrawals of Rs. 500 should succeed");
        Assertions.assertEquals(3, rejectCount.get(), "Three withdrawals should be rejected due to minimum balance");
        Assertions.assertEquals(BigDecimal.valueOf(1000.00).setScale(2), finalAcc.getBalance());
    }

    @Test
    @DisplayName("Concurrency: Bidirectional simultaneous transfers between Account A and Account B do not deadlock")
    public void testBidirectionalConcurrentTransfersNoDeadlock() throws Exception {
        Account accA = accountService.createAccount(501, "Holder A", AccountType.SAVINGS, BigDecimal.valueOf(5000.00), "1234", adminUser);
        Account accB = accountService.createAccount(502, "Holder B", AccountType.SAVINGS, BigDecimal.valueOf(5000.00), "1234", adminUser);

        int accNoA = accA.getAccountNumber();
        int accNoB = accB.getAccountNumber();

        int totalTxns = 8;
        ExecutorService executor = Executors.newFixedThreadPool(totalTxns);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalTxns);

        for (int i = 0; i < totalTxns; i++) {
            final boolean aToB = (i % 2 == 0);
            executor.submit(() -> {
                try {
                    startLatch.await();
                    if (aToB) {
                        bankingService.transfer(accNoA, "1234", accNoB, BigDecimal.valueOf(50.00), "A to B", tellerUser);
                    } else {
                        bankingService.transfer(accNoB, "1234", accNoA, BigDecimal.valueOf(50.00), "B to A", tellerUser);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        Assertions.assertTrue(completed, "All bidirectional concurrent transfers must complete without deadlocking");

        Account finalA = bankDAO.getAccountByNumber(accNoA);
        Account finalB = bankDAO.getAccountByNumber(accNoB);

        // Sum of balances must remain constant (10,000.00)
        BigDecimal totalSum = finalA.getBalance().add(finalB.getBalance());
        Assertions.assertEquals(BigDecimal.valueOf(10000.00).setScale(2), totalSum);
    }
}
