package com.securebank.automation;

import com.securebank.service.ReportService;
import com.securebank.util.DatabaseConfig;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ReportServiceTest {

    private ReportService reportService;

    @BeforeEach
    public void setUp() {
        DatabaseConfig.switchToEmbeddedH2();
        DatabaseConfig.seedTestData();
        reportService = new ReportService();
    }

    @Test
    public void testFinancialSummaryGeneration() {
        Map<String, Object> summary = reportService.generateSummaryReport("ALL");
        assertNotNull(summary, "Report summary must not be null");
        assertEquals("ALL", summary.get("period"));
        assertNotNull(summary.get("depositTotal"));
        assertNotNull(summary.get("withdrawTotal"));
        assertNotNull(summary.get("transferTotal"));
        assertNotNull(summary.get("netVolume"));
    }

    @Test
    public void testTransactionsCSVExport() {
        String csv = reportService.exportTransactionsToCSV(1001, null, null);
        assertNotNull(csv);
        assertTrue(csv.startsWith("TransactionRef,AccountNumber,Type,Amount,BalanceAfter,Timestamp,Remarks"));
        assertTrue(csv.contains("1001"), "CSV export must contain account 1001 records");
    }

    @Test
    public void testAuditLogCSVExport() {
        String csv = reportService.exportAuditLogsToCSV(null, null);
        assertNotNull(csv);
        assertTrue(csv.startsWith("LogId,Timestamp,Actor,Role,Action,EntityType,EntityId,Status,Details,IpAddress"));
    }

    @Test
    public void testCSVFormulaInjectionSanitization() {
        // Values starting with =, +, -, @, \t, \r must be prefixed with single quote
        assertEquals("'=SUM(1+1)", ReportService.escapeCSV("=SUM(1+1)"));
        assertEquals("'+cmd|'/c calc'!A0", ReportService.escapeCSV("+cmd|'/c calc'!A0"));
        assertEquals("'-200", ReportService.escapeCSV("-200"));
        assertEquals("'@test_handle", ReportService.escapeCSV("@test_handle"));
        assertEquals("'\tTabbed", ReportService.escapeCSV("\tTabbed"));

        // Benign text should not be altered with formula prefix
        assertEquals("Regular Remarks", ReportService.escapeCSV("Regular Remarks"));
        assertEquals("\"With, Comma\"", ReportService.escapeCSV("With, Comma"));
    }
}
