package com.securebank;

import com.securebank.automation.BackupServiceTest;
import com.securebank.automation.ReconciliationTest;
import com.securebank.automation.ReportServiceTest;
import com.securebank.automation.SuspiciousActivityTest;
import com.securebank.domain.AccountHierarchyTest;
import com.securebank.domain.ValidationTest;
import com.securebank.integration.BankingModulesTest;
import com.securebank.integration.BankingServiceIntegrationTest;
import com.securebank.integration.ConcurrencyTest;
import com.securebank.integration.DoubleEntryLedgerTest;
import com.securebank.integration.ExplainableFraudEngineTest;
import com.securebank.integration.FinancialIntegrationTest;
import com.securebank.integration.SpendingInsightsTest;
import com.securebank.security.AuthServiceTest;
import com.securebank.security.CustomerRegistrationTest;
import com.securebank.security.PasswordUtilTest;
import com.securebank.security.SecurityRemediationTest;
import com.securebank.security.SessionMonitoringTest;
import com.securebank.security.SessionSecurityTest;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Standalone Automated Test Orchestrator:
 * Executes the entire SecureBank verification suite across Domain, Security,
 * Database Integration, Concurrency, and Automation dimensions without requiring an external IDE or Maven.
 */
public class TestSuiteRunner {

    public static class TestResult {
        public final String suite;
        public final String testName;
        public final boolean passed;
        public final String details;

        public TestResult(String suite, String testName, boolean passed, String details) {
            this.suite = suite;
            this.testName = testName;
            this.passed = passed;
            this.details = details;
        }
    }

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("          SECUREBANK CAPSTONE — COMPREHENSIVE AUTOMATED TEST SUITE             ");
        System.out.println("================================================================================\n");

        List<Class<?>> testClasses = List.of(
            AccountHierarchyTest.class,
            ValidationTest.class,
            PasswordUtilTest.class,
            AuthServiceTest.class,
            CustomerRegistrationTest.class,
            SessionSecurityTest.class,
            SessionMonitoringTest.class,
            SecurityRemediationTest.class,
            BankingServiceIntegrationTest.class,
            DoubleEntryLedgerTest.class,
            ExplainableFraudEngineTest.class,
            SpendingInsightsTest.class,
            BankingModulesTest.class,
            ConcurrencyTest.class,
            ReconciliationTest.class,
            SuspiciousActivityTest.class,
            ReportServiceTest.class,
            BackupServiceTest.class,
            FinancialIntegrationTest.class
        );

        List<TestResult> results = new ArrayList<>();
        int passed = 0;
        int failed = 0;

        com.securebank.util.DatabaseConfig.switchToEmbeddedH2();
        com.securebank.util.DatabaseConfig.seedTestData();

        for (Class<?> clazz : testClasses) {
            String suiteName = clazz.getSimpleName();
            System.out.println(">> Running Test Suite: " + suiteName);

            Method[] methods = clazz.getDeclaredMethods();
            Method beforeEach = null;
            Method afterEach = null;
            for (Method m : methods) {
                if (m.isAnnotationPresent(BeforeEach.class)) {
                    beforeEach = m;
                }
                if (m.isAnnotationPresent(AfterEach.class)) {
                    afterEach = m;
                }
            }

            for (Method m : methods) {
                if (m.isAnnotationPresent(Test.class)) {
                    String testName = m.getName();
                    Object instance = null;
                    try {
                        instance = clazz.getDeclaredConstructor().newInstance();
                        if (beforeEach != null) {
                            beforeEach.invoke(instance);
                        }
                        m.invoke(instance);
                        results.add(new TestResult(suiteName, testName, true, "PASSED"));
                        System.out.println("   [PASS] " + testName);
                        passed++;
                    } catch (Throwable t) {
                        Throwable cause = t.getCause() != null ? t.getCause() : t;
                        results.add(new TestResult(suiteName, testName, false, cause.getMessage()));
                        System.err.println("   [FAIL] " + testName + " => " + cause.getMessage());
                        failed++;
                    } finally {
                        if (afterEach != null && instance != null) {
                            try {
                                afterEach.invoke(instance);
                            } catch (Throwable ignored) {
                            }
                        }
                    }
                }
            }
            System.out.println();
        }

        System.out.println("================================================================================");
        System.out.println("                           EXECUTIVE TEST SUMMARY                               ");
        System.out.println("================================================================================");
        int total = passed + failed;
        double pct = total > 0 ? (passed * 100.0 / total) : 0.0;
        System.out.printf(" Total Tests Executed : %d%n", total);
        System.out.printf(" Tests Passed         : %d [%.1f%%]%n", passed, pct);
        System.out.printf(" Tests Failed         : %d%n", failed);
        System.out.println(" Status               : " + (failed == 0 ? "ALL VERIFICATION CRITERIA MET" : "FAILURES DETECTED"));
        System.out.println("================================================================================\n");

        if (failed > 0) {
            System.exit(1);
        } else {
            System.exit(0);
        }
    }
}
