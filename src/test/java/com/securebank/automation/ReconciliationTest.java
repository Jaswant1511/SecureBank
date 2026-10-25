package com.securebank.automation;

import com.securebank.model.DiscrepancyItem;
import com.securebank.model.ReconciliationReport;
import com.securebank.service.ReconciliationService;
import com.securebank.util.DatabaseConfig;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ReconciliationTest {

    private ReconciliationService reconciliationService;

    @BeforeEach
    public void setUp() {
        DatabaseConfig.switchToEmbeddedH2();
        DatabaseConfig.seedTestData();
        reconciliationService = new ReconciliationService();
    }

    @Test
    public void testReconciliationBalancedLedgers() {
        ReconciliationReport report = reconciliationService.runReconciliation();
        assertNotNull(report, "Reconciliation report must not be null");
        assertTrue(report.getTotalAccountsAudited() > 0, "Should audit existing accounts");
        assertNotNull(report.getStatus());
        assertNotNull(report.getSummary());
    }

    @Test
    public void testReconciliationDetectsInducedDiscrepancy() throws Exception {
        // Induce artificial tampering on an account balance without a corresponding ledger entry
        try (Connection conn = DatabaseConfig.getConnection();
             Statement st = conn.createStatement()) {
            st.executeUpdate("UPDATE accounts SET balance = balance + 500.00 WHERE account_number = 1001");
        }

        ReconciliationReport report = reconciliationService.runReconciliation();
        assertEquals("DISCREPANCIES_FOUND", report.getStatus(), "Should detect induced imbalance");
        assertTrue(report.getDiscrepancyCount() > 0, "Discrepancy count should be positive");

        boolean foundAcc1001 = false;
        for (DiscrepancyItem item : report.getDiscrepancies()) {
            if (item.getAccountNumber() == 1001) {
                foundAcc1001 = true;
                assertEquals(new BigDecimal("500.00"), item.getDifference().setScale(2));
                break;
            }
        }
        assertTrue(foundAcc1001, "Account #1001 must be flagged in discrepancy items");

        // Restore balance so subsequent tests are clean
        try (Connection conn = DatabaseConfig.getConnection();
             Statement st = conn.createStatement()) {
            st.executeUpdate("UPDATE accounts SET balance = balance - 500.00 WHERE account_number = 1001");
        }
    }
}
