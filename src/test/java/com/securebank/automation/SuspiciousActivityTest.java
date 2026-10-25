package com.securebank.automation;

import com.securebank.model.SuspiciousActivityAlert;
import com.securebank.service.SuspiciousActivityService;
import com.securebank.util.DatabaseConfig;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SuspiciousActivityTest {

    private SuspiciousActivityService fraudService;

    @BeforeEach
    public void setUp() {
        DatabaseConfig.switchToEmbeddedH2();
        DatabaseConfig.seedTestData();
        fraudService = new SuspiciousActivityService();
    }

    @Test
    public void testHighValueOutlierRule() {
        BigDecimal highAmount = new BigDecimal("75000.00");
        fraudService.checkHighValueTransaction(1001, highAmount, "DEPOSIT");

        List<SuspiciousActivityAlert> alerts = fraudService.getAllAlerts(10);
        assertFalse(alerts.isEmpty(), "Alert list should have generated items");

        boolean matched = alerts.stream().anyMatch(a ->
            "HIGH_VALUE_TRANSACTION".equals(a.getRuleTriggered()) &&
            Integer.valueOf(1001).equals(a.getAccountNumber())
        );
        assertTrue(matched, "High-value transaction rule must trigger an alert");
    }

    @Test
    public void testRepeatedFailedLoginsRule() {
        fraudService.checkFailedLogin(105, "rohan", 3);

        List<SuspiciousActivityAlert> alerts = fraudService.getAllAlerts(10);
        boolean matched = alerts.stream().anyMatch(a ->
            "REPEATED_FAILED_LOGINS".equals(a.getRuleTriggered()) &&
            Integer.valueOf(105).equals(a.getUserId())
        );
        assertTrue(matched, "Failed login threshold must generate security alert");
    }

    @Test
    public void testUnauthorizedAccessAttemptRule() {
        fraudService.checkUnauthorizedAccessAttempt(103, "aarav", 1002);

        List<SuspiciousActivityAlert> alerts = fraudService.getAllAlerts(10);
        boolean matched = alerts.stream().anyMatch(a ->
            "UNAUTHORIZED_ACCESS_ATTEMPT".equals(a.getRuleTriggered()) &&
            "CRITICAL".equals(a.getSeverity())
        );
        assertTrue(matched, "Unauthorized IDOR horizontal access attempt must trigger CRITICAL alert");
    }

    @Test
    public void testAlertLifecycleStatusUpdate() {
        fraudService.checkHighValueTransaction(1002, new BigDecimal("60000.00"), "TRANSFER");
        List<SuspiciousActivityAlert> alerts = fraudService.getAllAlerts(5);
        assertFalse(alerts.isEmpty());
        SuspiciousActivityAlert latest = alerts.get(0);

        boolean updated = fraudService.updateAlertStatus(latest.getAlertId(), "RESOLVED");
        assertTrue(updated, "Status update should succeed");
    }
}
