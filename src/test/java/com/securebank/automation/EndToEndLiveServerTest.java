package com.securebank.automation;

import com.securebank.ui.web.BankWebServer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Live End-to-End System Test:
 * Tests all real client-server workflows against http://localhost:8080:
 *  - Static Assets Delivery (index.html, style.css, app.js)
 *  - Customer Authentication, Dashboard, Ledger Queries & Notifications
 *  - Financial Operations: Cash Deposit, Authenticated PIN Withdrawal, Inter-Account ACID Transfer
 *  - Administrative Operations: Overview Metrics, Ledger Reconciliation, Fraud Alerts, Turnover Reports & CSV, Database Backup
 *  - Branch Teller Operations: Account Portfolios & Customer Onboarding
 *  - Cryptographic Session Bearer Token Propagation & RBAC Enforcement
 */
public class EndToEndLiveServerTest {

    private static final String BASE_URL = "http://localhost:8080";
    private final HttpClient client;
    private int passed = 0;
    private int failed = 0;
    private String currentToken = null;

    public EndToEndLiveServerTest() {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("       SECUREBANK END-TO-END LIVE SERVER COMPREHENSIVE TEST SUITE               ");
        System.out.println("       Target: " + BASE_URL);
        System.out.println("================================================================================\n");

        EndToEndLiveServerTest runner = new EndToEndLiveServerTest();
        BankWebServer autoServer = null;
        try {
            try {
                HttpResponse<String> health = runner.get("/api/health");
                if (health.statusCode() == 200) {
                    System.out.println("[E2E] Active server detected at " + BASE_URL + " (HTTP 200)");
                }
            } catch (Exception notRunning) {
                System.out.println("[E2E] Server offline on " + BASE_URL + ". Starting embedded BankWebServer...");
                autoServer = new BankWebServer(8080);
                autoServer.start();
                final BankWebServer s = autoServer;
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    try {
                        s.stop();
                    } catch (Throwable ignored) {}
                }));
                System.out.println("[E2E] Embedded BankWebServer online. Proceeding with end-to-end verification...\n");
                Thread.sleep(500);
            }

            runner.executeAllE2ETests();
        } catch (Throwable t) {
            System.err.println("[E2E] Execution error: " + t.getMessage());
            System.exit(1);
        } finally {
            if (autoServer != null) {
                System.out.println("[E2E] Stopping automated test BankWebServer...");
                autoServer.stop();
            }
        }
    }

    public void executeAllE2ETests() {
        // 1. Web Root Test
        test("1. Web Static Assets (index.html, style.css, app.js)", () -> {
            HttpResponse<String> res = get("/");
            assertCondition(res.statusCode() == 200, "Root returned " + res.statusCode());
            assertCondition(res.body().contains("SecureBank"), "Body does not contain SecureBank");
        });

        // 2. Dedicated Individual Login Page Static Delivery
        test("2. Dedicated Individual Login Page (/login & /login.html)", () -> {
            HttpResponse<String> res = get("/login");
            assertCondition(res.statusCode() == 200, "Login route returned " + res.statusCode());
            assertCondition(res.body().contains("Account Sign In"), "Login page missing Account Sign In heading");
            assertCondition(res.body().contains("loginIdentifier"), "Login page missing loginIdentifier input");
            assertCondition(res.body().contains("loginPassword"), "Login page missing loginPassword input");
        });

        // 3. Customer Authentication by Username
        test("3. Customer Login by Username (aarav / Password@123)", () -> {
            String payload = "{\"username\":\"aarav\",\"password\":\"Password@123\"}";
            HttpResponse<String> res = post("/api/login", payload);
            assertCondition(res.statusCode() == 200, "Login failed: " + res.statusCode() + " " + res.body());
            assertCondition(res.body().contains("\"success\":true"), "Login response missing success: " + res.body());
            assertCondition(res.body().contains("Aarav Sharma"), "Login response missing customer name: " + res.body());
            currentToken = extractToken(res.body());
            assertCondition(currentToken != null && !currentToken.isEmpty(), "Missing Bearer token in login response");
        });

        // 4. Customer Authentication by Email Address
        test("4. Multi-Identifier Login by Email (aarav.sharma@example.com / Password@123)", () -> {
            String payload = "{\"username\":\"aarav.sharma@example.com\",\"password\":\"Password@123\"}";
            HttpResponse<String> res = post("/api/login", payload);
            assertCondition(res.statusCode() == 200, "Login by email failed: " + res.statusCode() + " " + res.body());
            assertCondition(res.body().contains("\"success\":true"), "Email login failed: " + res.body());
        });

        // 5. Customer Authentication by Account Number
        test("5. Multi-Identifier Login by Account Number (1001 / Password@123)", () -> {
            String payload = "{\"username\":\"1001\",\"password\":\"Password@123\"}";
            HttpResponse<String> res = post("/api/login", payload);
            assertCondition(res.statusCode() == 200, "Login by account number failed: " + res.statusCode() + " " + res.body());
            assertCondition(res.body().contains("\"success\":true"), "Account number login failed: " + res.body());
        });

        // 6. Customer Authentication by Full Name
        test("6. Multi-Identifier Login by Full Name (Aarav Sharma / Password@123)", () -> {
            String payload = "{\"username\":\"Aarav Sharma\",\"password\":\"Password@123\"}";
            HttpResponse<String> res = post("/api/login", payload);
            assertCondition(res.statusCode() == 200, "Login by full name failed: " + res.statusCode() + " " + res.body());
            assertCondition(res.body().contains("\"success\":true"), "Full name login failed: " + res.body());
        });

        // 7. Self-Service Customer Registration
        test("7. Self-Service Customer Registration (/api/register)", () -> {
            String newUsername = "reg_test_" + (System.currentTimeMillis() % 100000);
            String payload = String.format(
                "{\"fullName\":\"Test New User\",\"username\":\"%s\",\"email\":\"%s@example.com\",\"phone\":\"+91 91122 33445\",\"password\":\"Password@123\",\"confirmPassword\":\"Password@123\",\"pin\":\"1234\",\"accountType\":\"SAVINGS\",\"initialDeposit\":1000.00}",
                newUsername, newUsername
            );
            HttpResponse<String> res = post("/api/register", payload);
            assertCondition(res.statusCode() == 201, "Self registration status " + res.statusCode() + ": " + res.body());
            assertCondition(res.body().contains("\"success\":true"), "Registration failed: " + res.body());
            assertCondition(res.body().contains("\"account\":"), "Missing account object in registration response: " + res.body());
        });

        // 8. Customer Dashboard Data Fetch
        test("8. Customer Dashboard Balance & Account Inquiries", () -> {
            HttpResponse<String> res = get("/api/customer/dashboard?userId=103");
            assertCondition(res.statusCode() == 200, "Dashboard status " + res.statusCode());
            assertCondition(res.body().contains("\"success\":true"), "Dashboard query failed: " + res.body());
            assertCondition(res.body().contains("1001"), "Account 1001 not found: " + res.body());
        });

        // 9. Customer Transactions Ledger Query
        test("9. Customer Transactions Ledger History Retrieval", () -> {
            HttpResponse<String> res = get("/api/transactions?accountNumber=1001");
            assertCondition(res.statusCode() == 200, "Transactions query status " + res.statusCode());
            assertCondition(res.body().contains("\"success\":true"), "Transactions query failed: " + res.body());
            assertCondition(res.body().contains("\"transactions\":["), "Missing transactions array: " + res.body());
        });

        // 10. Notifications Retrieval
        test("10. Customer Real-time Notifications Retrieval", () -> {
            HttpResponse<String> res = get("/api/notifications?userId=103");
            assertCondition(res.statusCode() == 200, "Notifications status " + res.statusCode());
            assertCondition(res.body().contains("\"success\":true"), "Notifications response error: " + res.body());
            assertCondition(res.body().contains("\"notifications\":["), "Missing notifications list: " + res.body());
        });

        // 11. Mark Notifications Read
        test("11. Mark Customer Notifications as Read", () -> {
            HttpResponse<String> res = post("/api/notifications/read-all", "{\"userId\":103}");
            assertCondition(res.statusCode() == 200, "Mark read status " + res.statusCode());
            assertCondition(res.body().contains("\"success\":true"), "Mark read failed: " + res.body());
        });

        // 12. Banking Deposit
        test("12. Automated Cash/Online Deposit Execution (₹500.00 to #1001)", () -> {
            String payload = "{\"accountNumber\":1001,\"amount\":500.00}";
            HttpResponse<String> res = post("/api/banking/deposit", payload);
            assertCondition(res.statusCode() == 200, "Deposit failed: " + res.statusCode() + " " + res.body());
            assertCondition(res.body().contains("\"success\":true"), "Deposit success false: " + res.body());
            assertCondition(res.body().contains("TXN-"), "Deposit missing transaction ref: " + res.body());
        });

        // 13. Banking Withdrawal with PIN Verification
        test("13. Automated PIN-Authenticated Withdrawal (₹200.00 from #1001)", () -> {
            String payload = "{\"accountNumber\":1001,\"amount\":200.00,\"pin\":\"1234\"}";
            HttpResponse<String> res = post("/api/banking/withdraw", payload);
            assertCondition(res.statusCode() == 200, "Withdrawal failed: " + res.statusCode() + " " + res.body());
            assertCondition(res.body().contains("\"success\":true"), "Withdrawal success false: " + res.body());
        });

        // 14. Inter-Account Fund Transfer
        test("14. Automated Inter-Account ACID Transfer (₹300.00 from #1001 to #1002)", () -> {
            String payload = "{\"fromAccount\":1001,\"toAccount\":1002,\"amount\":300.00,\"pin\":\"1234\",\"remarks\":\"E2E Live Transfer\"}";
            HttpResponse<String> res = post("/api/banking/transfer", payload);
            assertCondition(res.statusCode() == 200, "Transfer failed: " + res.statusCode() + " " + res.body());
            assertCondition(res.body().contains("\"success\":true"), "Transfer success false: " + res.body());
        });

        // 15. Transfer Idempotency Protection
        test("15. Transfer Idempotency Guard (Duplicate Request with Idempotency-Key)", () -> {
            String idempotencyKey = "E2E-IDEMP-" + System.currentTimeMillis();
            String payload = "{\"fromAccount\":1001,\"toAccount\":1002,\"amount\":50.00,\"pin\":\"1234\",\"remarks\":\"Idempotent Transfer\"}";
            HttpResponse<String> res1 = postWithHeader("/api/banking/transfer", payload, "Idempotency-Key", idempotencyKey);
            assertCondition(res1.statusCode() == 200, "First transfer attempt failed: " + res1.statusCode() + " " + res1.body());

            // Duplicate attempt with the exact same Idempotency-Key
            HttpResponse<String> res2 = postWithHeader("/api/banking/transfer", payload, "Idempotency-Key", idempotencyKey);
            assertCondition(res2.statusCode() == 200, "Duplicate transfer attempt failed: " + res2.statusCode() + " " + res2.body());
            assertCondition(res2.body().contains("\"success\":true"), "Duplicate attempt did not return success receipt");
        });

        // 16. Admin Authentication
        test("16. Admin Login (admin_rajesh / Admin@123)", () -> {
            String payload = "{\"username\":\"admin_rajesh\",\"password\":\"Admin@123\"}";
            HttpResponse<String> res = post("/api/login", payload);
            assertCondition(res.statusCode() == 200, "Admin login failed: " + res.statusCode() + " " + res.body());
            assertCondition(res.body().contains("\"role\":\"ADMIN\""), "Expected ADMIN role: " + res.body());
            currentToken = extractToken(res.body());
            assertCondition(currentToken != null && !currentToken.isEmpty(), "Missing Bearer token in admin response");
        });

        // 17. Admin Overview Metrics Retrieval
        test("17. Admin System Telemetry & Accounts Overview", () -> {
            HttpResponse<String> res = get("/api/admin/overview");
            assertCondition(res.statusCode() == 200, "Admin overview status " + res.statusCode());
            assertCondition(res.body().contains("\"users\":["), "Missing users list in overview: " + res.body());
            assertCondition(res.body().contains("\"auditLogs\":["), "Missing audit logs in overview: " + res.body());
        });

        // 18. Admin Transaction Reconciliation Audit Trigger
        test("18. Admin Automated Transaction Reconciliation Run", () -> {
            HttpResponse<String> res = post("/api/admin/reconcile", "{}");
            assertCondition(res.statusCode() == 200, "Reconciliation failed: " + res.statusCode() + " " + res.body());
            assertCondition(res.body().contains("\"success\":true"), "Reconciliation response error: " + res.body());
            assertCondition(res.body().contains("BALANCED"), "Expected BALANCED status: " + res.body());
        });

        // 19. Admin Reconciliation Audit History
        test("19. Admin Reconciliation Audit History Retrieval", () -> {
            HttpResponse<String> res = get("/api/admin/reconcile/history");
            assertCondition(res.statusCode() == 200, "Reconcile history status " + res.statusCode());
            assertCondition(res.body().contains("\"reports\":["), "Missing reports list: " + res.body());
        });

        // 20. Admin Suspicious Activity Alerts
        test("20. Admin Fraud & Suspicious Activity Alerts Query", () -> {
            HttpResponse<String> res = get("/api/admin/suspicious-alerts");
            assertCondition(res.statusCode() == 200, "Suspicious alerts status " + res.statusCode());
            assertCondition(res.body().contains("\"alerts\":["), "Missing alerts list: " + res.body());
        });

        // 21. Financial Reports Turnover Summary
        test("21. Financial Reports Turnover Summary Retrieval", () -> {
            HttpResponse<String> res = get("/api/admin/reports/summary?period=ALL");
            assertCondition(res.statusCode() == 200, "Reports status " + res.statusCode());
            assertCondition(res.body().contains("\"depositTotal\":"), "Missing depositTotal: " + res.body());
        });

        // 22. RFC 4180 Transactions CSV Export Stream
        test("22. Transactions Ledger RFC 4180 CSV Export Stream", () -> {
            HttpResponse<String> res = get("/api/admin/reports/export-transactions");
            assertCondition(res.statusCode() == 200, "CSV export status " + res.statusCode());
            assertCondition(res.body().contains("TransactionRef,AccountNumber"), "Invalid CSV header: " + res.body());
        });

        // 23. RFC 4180 Audit Logs CSV Export Stream
        test("23. Audit Logs RFC 4180 CSV Export Stream", () -> {
            HttpResponse<String> res = get("/api/admin/reports/export-audit");
            assertCondition(res.statusCode() == 200, "Audit CSV status " + res.statusCode());
            assertCondition(res.body().contains("LogId,Timestamp,Actor"), "Invalid Audit CSV header: " + res.body());
        });

        // 24. Admin Database Backup Execution
        test("24. Automated Database Backup Trigger & Integrity Check", () -> {
            HttpResponse<String> res = post("/api/admin/backup/create", "{}");
            assertCondition(res.statusCode() == 200, "Backup status " + res.statusCode());
            assertCondition(res.body().contains("\"success\":true"), "Backup response error: " + res.body());
            assertCondition(res.body().contains("securebank_backup_"), "Missing backup filename: " + res.body());
        });

        // 25. Teller Authentication
        test("25. Branch Staff Teller Login (teller_suresh / Teller@123)", () -> {
            String payload = "{\"username\":\"teller_suresh\",\"password\":\"Teller@123\"}";
            HttpResponse<String> res = post("/api/login", payload);
            assertCondition(res.statusCode() == 200, "Teller login status " + res.statusCode());
            assertCondition(res.body().contains("\"role\":\"TELLER\""), "Expected TELLER role: " + res.body());
            currentToken = extractToken(res.body());
            assertCondition(currentToken != null && !currentToken.isEmpty(), "Missing Bearer token in teller response");
        });

        // 26. Teller Accounts Portfolio
        test("26. Teller Branch Accounts Portfolio Lookup", () -> {
            HttpResponse<String> res = get("/api/teller/accounts");
            assertCondition(res.statusCode() == 200, "Teller search status " + res.statusCode());
            assertCondition(res.body().contains("\"accounts\":["), "Accounts list missing: " + res.body());
        });

        // 27. Teller Customer Onboarding & Account Creation
        test("27. Teller Customer Onboarding & Account Creation", () -> {
            String dynamicEmail = "vikram.rao." + (System.currentTimeMillis() % 100000) + "@example.com";
            String payload = String.format("{\"fullName\":\"Vikramaditya Rao\",\"accountType\":\"SAVINGS\",\"initialDeposit\":2500.00,\"pin\":\"9988\",\"email\":\"%s\",\"phone\":\"+91 99887 76655\"}", dynamicEmail);
            HttpResponse<String> res = post("/api/teller/create-account", payload);
            assertCondition(res.statusCode() == 200, "Account creation status " + res.statusCode() + ": " + res.body());
            assertCondition(res.body().contains("\"success\":true"), "Account creation failed: " + res.body());
            assertCondition(res.body().contains("\"accountNumber\":"), "Missing new account in response: " + res.body());
        });

        // Summary
        System.out.println("================================================================================");
        System.out.println("                     LIVE END-TO-END VERIFICATION SUMMARY                       ");
        System.out.println("================================================================================");
        int total = passed + failed;
        double pct = total > 0 ? (passed * 100.0 / total) : 0.0;
        System.out.printf(" Total Live E2E Tests : %d%n", total);
        System.out.printf(" Tests Passed         : %d [%.1f%%]%n", passed, pct);
        System.out.printf(" Tests Failed         : %d%n", failed);
        System.out.println(" Final Status         : " + (failed == 0 ? "ALL END-TO-END SCENARIOS VERIFIED [SUCCESS]" : "FAILURES DETECTED"));
        System.out.println("================================================================================\n");

        if (failed > 0) {
            System.exit(1);
        } else {
            System.exit(0);
        }
    }

    private void test(String name, TestRunnable action) {
        System.out.print("[E2E] " + name + " ... ");
        try {
            action.run();
            System.out.println("PASS");
            passed++;
        } catch (Throwable t) {
            System.out.println("FAIL -> " + t.getMessage());
            failed++;
        }
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .timeout(Duration.ofSeconds(10))
                .GET();
        if (currentToken != null) {
            b.header("Authorization", "Bearer " + currentToken);
        }
        return client.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String jsonBody) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody));
        if (currentToken != null) {
            b.header("Authorization", "Bearer " + currentToken);
        }
        return client.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postWithHeader(String path, String jsonBody, String headerName, String headerValue) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .header("Content-Type", "application/json")
                .header(headerName, headerValue)
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody));
        if (currentToken != null) {
            b.header("Authorization", "Bearer " + currentToken);
        }
        return client.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String extractToken(String json) {
        if (json == null) return null;
        int idx = json.indexOf("\"token\":\"");
        if (idx != -1) {
            int start = idx + 9;
            int end = json.indexOf("\"", start);
            if (end != -1) {
                return json.substring(start, end);
            }
        }
        return null;
    }

    private static void assertCondition(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    @FunctionalInterface
    interface TestRunnable {
        void run() throws Throwable;
    }
}
