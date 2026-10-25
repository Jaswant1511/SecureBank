package com.securebank.security;

import com.securebank.dao.AuditDAO;
import com.securebank.model.AuditLog;
import com.securebank.model.Role;
import com.securebank.ui.web.BankWebServer;
import com.securebank.util.DatabaseConfig;
import com.securebank.util.PasswordUtil;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive Security Remediation Regression Test Suite:
 * Proves that:
 * 1. Clear-all-data endpoint requires authentication, admin role, confirmation payload,
 *    is disabled in production, and preserves regulatory audit logs.
 * 2. Administrators are strictly forbidden from initiating financial transactions (deposits/withdrawals/transfers).
 * 3. Customers cannot access other customers' accounts (IDOR prevention on deposits, withdrawals, transfers).
 * 4. Customer self-service deposit limit (max ₹50,000) is enforced while tellers can accept cash deposits.
 * 5. Transfers validate distinct source and destination accounts.
 * 6. Monetary amounts require strictly positive values and max 2 decimal places.
 * 7. Malformed JSON and oversized request payloads (>64KB) are safely rejected without leaking stack traces.
 * 8. PasswordUtil rejects legacy plaintext password and PIN matches.
 */
public class SecurityRemediationTest {

    private BankWebServer testServer;
    private HttpClient httpClient;
    private String baseUrl;

    @BeforeEach
    public void setUp() throws Exception {
        DatabaseConfig.switchToEmbeddedH2();
        DatabaseConfig.seedTestData();
        DatabaseConfig.setProductionModeOverride(false);

        testServer = new BankWebServer(0);
        testServer.start();
        baseUrl = "http://localhost:" + testServer.getPort();

        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @AfterEach
    public void tearDown() {
        DatabaseConfig.setProductionModeOverride(null);
        if (testServer != null) {
            testServer.stop();
        }
    }

    private String loginAndGetToken(String username, String password) throws Exception {
        String body = String.format("{\"username\":\"%s\",\"password\":\"%s\"}", username, password);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(200, resp.statusCode(), "Login failed for " + username + ": " + resp.body());

        String respBody = resp.body();
        int tokenStart = respBody.indexOf("\"token\":\"") + 9;
        int tokenEnd = respBody.indexOf("\"", tokenStart);
        return respBody.substring(tokenStart, tokenEnd);
    }

    // =========================================================================
    // STEP 2: Clear-All-Data Vulnerability Remediation
    // =========================================================================

    @Test
    @DisplayName("Clear-All-Data: Unauthenticated request returns 401 Unauthorized")
    public void testClearAllDataUnauthenticatedReturns401() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/admin/clear-all-data"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"confirmation\":\"CONFIRM_CLEAR_ALL_DATA\"}"))
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(401, resp.statusCode(), "Unauthenticated clear-all-data must return 401");
    }

    @Test
    @DisplayName("Clear-All-Data: Customer token returns 403 Forbidden")
    public void testClearAllDataCustomerForbidden403() throws Exception {
        String customerToken = loginAndGetToken("aarav", "Password@123");
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/admin/clear-all-data"))
                .header("Authorization", "Bearer " + customerToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"confirmation\":\"CONFIRM_CLEAR_ALL_DATA\"}"))
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(403, resp.statusCode(), "Customer calling clear-all-data must return 403");
    }

    @Test
    @DisplayName("Clear-All-Data: Teller token returns 403 Forbidden")
    public void testClearAllDataTellerForbidden403() throws Exception {
        String tellerToken = loginAndGetToken("teller_suresh", "Teller@123");
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/admin/clear-all-data"))
                .header("Authorization", "Bearer " + tellerToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"confirmation\":\"CONFIRM_CLEAR_ALL_DATA\"}"))
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(403, resp.statusCode(), "Teller calling clear-all-data must return 403");
    }

    @Test
    @DisplayName("Clear-All-Data: Admin without explicit confirmation returns 400 Bad Request")
    public void testClearAllDataAdminWithoutConfirmationReturns400() throws Exception {
        String adminToken = loginAndGetToken("admin_rajesh", "Admin@123");

        // Missing confirmation field
        HttpRequest req1 = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/admin/clear-all-data"))
                .header("Authorization", "Bearer " + adminToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();
        HttpResponse<String> resp1 = httpClient.send(req1, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(400, resp1.statusCode(), "Admin without confirmation must return 400");
        Assertions.assertTrue(resp1.body().contains("Explicit confirmation"));

        // Wrong confirmation value
        HttpRequest req2 = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/admin/clear-all-data"))
                .header("Authorization", "Bearer " + adminToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"confirmation\":\"yes\"}"))
                .build();
        HttpResponse<String> resp2 = httpClient.send(req2, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(400, resp2.statusCode(), "Admin with invalid confirmation must return 400");
    }

    @Test
    @DisplayName("Clear-All-Data: Admin request in Production mode is strictly blocked with 403")
    public void testClearAllDataAdminInProductionModeReturns403() throws Exception {
        String adminToken = loginAndGetToken("admin_rajesh", "Admin@123");
        DatabaseConfig.setProductionModeOverride(true);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/admin/clear-all-data"))
                .header("Authorization", "Bearer " + adminToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"confirmation\":\"CONFIRM_CLEAR_ALL_DATA\"}"))
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(403, resp.statusCode(), "Clear-all-data must be blocked in production mode");
        Assertions.assertTrue(resp.body().contains("disabled in production"));
    }

    @Test
    @DisplayName("Clear-All-Data: Authorized Admin in Dev mode succeeds, preserves audit logs, and logs action")
    public void testClearAllDataAdminDevPreservesAuditLogs() throws Exception {
        String adminToken = loginAndGetToken("admin_rajesh", "Admin@123");
        DatabaseConfig.setProductionModeOverride(false);

        AuditDAO auditDAO = new AuditDAO();
        AuditLog testLog = new AuditLog(0, "admin_rajesh", Role.ADMIN, "PRE_TEST_ACTION", "SYSTEM", "ALL", "Testing audit preservation", "SUCCESS", "127.0.0.1", null);
        auditDAO.recordAuditLog(testLog);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/admin/clear-all-data"))
                .header("Authorization", "Bearer " + adminToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"confirmation\":\"CONFIRM_CLEAR_ALL_DATA\"}"))
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(200, resp.statusCode(), "Valid admin clear-all-data in dev should return 200");
        Assertions.assertTrue(resp.body().contains("Audit trail preserved"));

        // Verify audit logs were NOT deleted
        List<AuditLog> logs = auditDAO.getRecentAuditLogs(10);
        Assertions.assertFalse(logs.isEmpty(), "Audit logs must be preserved after clear-all-data");

        boolean foundClearAction = logs.stream().anyMatch(l -> "CLEAR_ALL_DATA".equals(l.getAction()));
        Assertions.assertTrue(foundClearAction, "Audit log must contain CLEAR_ALL_DATA record");

        // Re-seed test data for subsequent tests
        DatabaseConfig.seedTestData();
    }

    // =========================================================================
    // STEP 3 & 4: Financial Operation Authorization (RBAC & IDOR)
    // =========================================================================

    @Test
    @DisplayName("Financial RBAC: Administrator cannot initiate deposits (403 Forbidden)")
    public void testAdminCannotDepositReturns403() throws Exception {
        String adminToken = loginAndGetToken("admin_rajesh", "Admin@123");
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/banking/deposit"))
                .header("Authorization", "Bearer " + adminToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"accountNumber\":1001,\"amount\":500.00}"))
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(403, resp.statusCode(), "Admin must not be authorized to deposit funds");
        Assertions.assertTrue(resp.body().contains("Administrators are not permitted"));
    }

    @Test
    @DisplayName("Financial RBAC: Administrator cannot initiate withdrawals (403 Forbidden)")
    public void testAdminCannotWithdrawReturns403() throws Exception {
        String adminToken = loginAndGetToken("admin_rajesh", "Admin@123");
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/banking/withdraw"))
                .header("Authorization", "Bearer " + adminToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"accountNumber\":1001,\"amount\":500.00,\"pin\":\"1234\"}"))
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(403, resp.statusCode(), "Admin must not be authorized to withdraw funds");
    }

    @Test
    @DisplayName("Financial RBAC: Administrator cannot initiate transfers (403 Forbidden)")
    public void testAdminCannotTransferReturns403() throws Exception {
        String adminToken = loginAndGetToken("admin_rajesh", "Admin@123");
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/banking/transfer"))
                .header("Authorization", "Bearer " + adminToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"accountNumber\":1001,\"targetAccount\":1002,\"amount\":500.00,\"pin\":\"1234\"}"))
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(403, resp.statusCode(), "Admin must not be authorized to transfer funds");
    }

    @Test
    @DisplayName("Deposit IDOR: Customer Aarav cannot deposit into Priya's account #1002 (403 Forbidden)")
    public void testCustomerCannotDepositIntoOtherCustomerAccountReturns403() throws Exception {
        String customerToken = loginAndGetToken("aarav", "Password@123");
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/banking/deposit"))
                .header("Authorization", "Bearer " + customerToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"accountNumber\":1002,\"amount\":500.00}"))
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(403, resp.statusCode(), "Customer depositing to unowned account must return 403");
        Assertions.assertTrue(resp.body().contains("only access your own accounts"));
    }

    @Test
    @DisplayName("Deposit Limit: Customer deposit exceeding ₹50,000 is rejected with 400 Bad Request")
    public void testCustomerDepositOverLimitRejectedReturns400() throws Exception {
        String customerToken = loginAndGetToken("aarav", "Password@123");
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/banking/deposit"))
                .header("Authorization", "Bearer " + customerToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"accountNumber\":1001,\"amount\":50000.01}"))
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(400, resp.statusCode(), "Customer self-service deposit over ₹50,000 must return 400");
        Assertions.assertTrue(resp.body().contains("limit exceeded"));
    }

    @Test
    @DisplayName("Deposit Flow: Customer deposit within ₹50,000 succeeds with digital self-service audit remarks")
    public void testCustomerSelfServiceDepositWithinLimitSucceeds() throws Exception {
        String customerToken = loginAndGetToken("aarav", "Password@123");
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/banking/deposit"))
                .header("Authorization", "Bearer " + customerToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"accountNumber\":1001,\"amount\":1000.00}"))
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(200, resp.statusCode(), "Valid self-service customer deposit should succeed");
        Assertions.assertTrue(resp.body().contains("\"success\":true"));
    }

    @Test
    @DisplayName("Teller Deposit Flow: Authorized Teller can deposit amounts over ₹50,000 as cash counter deposit")
    public void testTellerCanDepositCashIntoCustomerAccount() throws Exception {
        String tellerToken = loginAndGetToken("teller_suresh", "Teller@123");
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/banking/deposit"))
                .header("Authorization", "Bearer " + tellerToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"accountNumber\":1001,\"amount\":60000.00}"))
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(200, resp.statusCode(), "Authorized teller cash counter deposit should succeed");
        Assertions.assertTrue(resp.body().contains("\"success\":true"));
    }

    @Test
    @DisplayName("Transfer IDOR: Customer cannot transfer from another customer's account (403 Forbidden)")
    public void testCustomerCannotTransferFromOtherAccountReturns403() throws Exception {
        String customerToken = loginAndGetToken("aarav", "Password@123");
        // Aarav attempts to transfer money OUT of Priya's account 1002 into his account 1001
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/banking/transfer"))
                .header("Authorization", "Bearer " + customerToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"accountNumber\":1002,\"targetAccount\":1001,\"amount\":500.00,\"pin\":\"1234\"}"))
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(403, resp.statusCode(), "Customer transferring from unowned account must return 403");
    }

    @Test
    @DisplayName("Transfer Validation: Same source and destination account is rejected with 400 Bad Request")
    public void testTransferSameSourceAndDestinationReturns400() throws Exception {
        String customerToken = loginAndGetToken("aarav", "Password@123");
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/banking/transfer"))
                .header("Authorization", "Bearer " + customerToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"accountNumber\":1001,\"targetAccount\":1001,\"amount\":100.00,\"pin\":\"1234\"}"))
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(400, resp.statusCode(), "Transfer to same account must return 400");
        Assertions.assertTrue(resp.body().contains("Source and destination accounts must be distinct"));
    }

    // =========================================================================
    // STEP 6: Input Validation, Monetary Constraints, and JSON Parser Hardening
    // =========================================================================

    @Test
    @DisplayName("Monetary Precision: Negative amount or >2 decimal places rejected with 400 Bad Request")
    public void testInvalidMonetaryAmountsRejected() throws Exception {
        String customerToken = loginAndGetToken("aarav", "Password@123");

        // Negative amount
        HttpRequest reqNeg = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/banking/deposit"))
                .header("Authorization", "Bearer " + customerToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"accountNumber\":1001,\"amount\":-50.00}"))
                .build();
        HttpResponse<String> respNeg = httpClient.send(reqNeg, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(400, respNeg.statusCode(), "Negative amount must return 400");

        // Zero amount
        HttpRequest reqZero = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/banking/deposit"))
                .header("Authorization", "Bearer " + customerToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"accountNumber\":1001,\"amount\":0.00}"))
                .build();
        HttpResponse<String> respZero = httpClient.send(reqZero, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(400, respZero.statusCode(), "Zero amount must return 400");

        // Too many decimal places (3 decimals)
        HttpRequest reqDec = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/banking/deposit"))
                .header("Authorization", "Bearer " + customerToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"accountNumber\":1001,\"amount\":10.555}"))
                .build();
        HttpResponse<String> respDec = httpClient.send(reqDec, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(400, respDec.statusCode(), "Amount with 3 decimal places must return 400");
    }

    @Test
    @DisplayName("Input Hardening: Malformed JSON returns 400 without leaking server stack trace")
    public void testMalformedJsonReturns400WithoutStackTrace() throws Exception {
        String customerToken = loginAndGetToken("aarav", "Password@123");
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/banking/deposit"))
                .header("Authorization", "Bearer " + customerToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{malformed json here!}"))
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(400, resp.statusCode(), "Malformed JSON must return 400");
        String body = resp.body();
        Assertions.assertTrue(body.contains("Invalid JSON request"));
        Assertions.assertFalse(body.contains("at com.securebank"), "Response must never leak Java stack trace");
        Assertions.assertFalse(body.contains("SQLException"), "Response must never leak internal SQL details");
    }

    @Test
    @DisplayName("DoS Protection: Request payload exceeding 64KB returns 400 Bad Request")
    public void testOversizedRequestBodyRejectedWith400() throws Exception {
        String customerToken = loginAndGetToken("aarav", "Password@123");
        StringBuilder hugeBody = new StringBuilder("{\"accountNumber\":1001,\"amount\":100.00,\"junk\":\"");
        for (int i = 0; i < 70000; i++) {
            hugeBody.append("A");
        }
        hugeBody.append("\"}");

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/banking/deposit"))
                .header("Authorization", "Bearer " + customerToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(hugeBody.toString()))
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(400, resp.statusCode(), "Oversized request body must return 400");
        Assertions.assertTrue(resp.body().contains("exceeds maximum allowable size"));
    }

    // =========================================================================
    // STEP 5: Password Security (No Plaintext Fallback)
    // =========================================================================

    @Test
    @DisplayName("Password Security: Plaintext password or PIN match is strictly rejected")
    public void testPasswordUtilRejectsPlaintextFallback() {
        // Plaintext match should fail immediately
        boolean passMatch = PasswordUtil.checkPassword("plainPassword123", "plainPassword123");
        Assertions.assertFalse(passMatch, "Plaintext password equality check must be rejected");

        boolean pinMatch = PasswordUtil.checkPin("1234", "1234");
        Assertions.assertFalse(pinMatch, "Plaintext PIN equality check must be rejected");

        // Correct BCrypt hash must still succeed
        String hashedPass = PasswordUtil.hashPassword("Secure@Pass1");
        Assertions.assertTrue(PasswordUtil.checkPassword("Secure@Pass1", hashedPass));

        String hashedPin = PasswordUtil.hashPin("4321");
        Assertions.assertTrue(PasswordUtil.checkPin("4321", hashedPin));
    }
}
