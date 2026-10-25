package com.securebank.security;

import com.securebank.model.Role;
import com.securebank.model.User;
import com.securebank.service.SessionService;
import com.securebank.ui.web.BankWebServer;
import com.securebank.util.DatabaseConfig;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Session Management & API Security Test Suite:
 * Validates cryptographically random Bearer tokens, idle timeout, session invalidation,
 * role-based access control (RBAC), and IDOR prevention on banking endpoints.
 */
public class SessionSecurityTest {

    private SessionService sessionService;
    private BankWebServer testServer;
    private HttpClient httpClient;
    private String baseUrl;

    @BeforeEach
    public void setUp() throws Exception {
        DatabaseConfig.switchToEmbeddedH2();
        DatabaseConfig.seedTestData();
        sessionService = new SessionService();
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @AfterEach
    public void tearDown() {
        if (testServer != null) {
            testServer.stop();
        }
    }

    @Test
    @DisplayName("Session Token: Generates 32+ byte Base64URL cryptographically secure tokens")
    public void testSessionTokenGenerationAndRetrieval() {
        User user = new User(103, "aarav", "hash", Role.CUSTOMER, 0, false, true);
        String token = sessionService.createSession(user);

        Assertions.assertNotNull(token);
        Assertions.assertTrue(token.length() >= 42, "Token must be at least 32 bytes encoded in Base64URL");

        SessionService.SessionRecord session = sessionService.getSession(token);
        Assertions.assertNotNull(session);
        Assertions.assertEquals("aarav", session.getUser().getUsername());
        Assertions.assertEquals(Role.CUSTOMER, session.getRole());
        Assertions.assertNotNull(session.getCreatedAt());
        Assertions.assertNotNull(session.getLastSeen());
        Assertions.assertEquals(1, sessionService.getActiveSessionCount());
    }

    @Test
    @DisplayName("Idle Timeout: Sessions older than 15 minutes of inactivity expire automatically")
    public void testIdleTimeoutExpiration() {
        User user = new User(103, "aarav", "hash", Role.CUSTOMER, 0, false, true);
        String token = sessionService.createSession(user);

        SessionService.SessionRecord session = sessionService.getSession(token);
        Assertions.assertNotNull(session);

        // Artificially simulate 16 minutes of idle time
        session.setLastSeen(Instant.now().minus(Duration.ofMinutes(16)));

        SessionService.SessionRecord expired = sessionService.getSession(token);
        Assertions.assertNull(expired, "Session must expire after exceeding 15 minutes of idle time");
        Assertions.assertEquals(0, sessionService.getActiveSessionCount());
    }

    @Test
    @DisplayName("Invalidation: Explicit logout and user-wide invalidation purge active sessions")
    public void testSessionInvalidation() {
        User user1 = new User(103, "aarav", "hash", Role.CUSTOMER, 0, false, true);
        User user2 = new User(104, "priya", "hash", Role.CUSTOMER, 0, false, true);

        String token1 = sessionService.createSession(user1);
        String token2 = sessionService.createSession(user1); // Second device
        String token3 = sessionService.createSession(user2);

        Assertions.assertEquals(3, sessionService.getActiveSessionCount());

        // Single logout
        boolean loggedOut = sessionService.invalidateSession(token1);
        Assertions.assertTrue(loggedOut);
        Assertions.assertNull(sessionService.getSession(token1));
        Assertions.assertNotNull(sessionService.getSession(token2));
        Assertions.assertEquals(2, sessionService.getActiveSessionCount());

        // User-wide invalidation (e.g., on password change)
        sessionService.invalidateUserSessions(103);
        Assertions.assertNull(sessionService.getSession(token2));
        Assertions.assertNotNull(sessionService.getSession(token3));
        Assertions.assertEquals(1, sessionService.getActiveSessionCount());
    }

    @Test
    @DisplayName("Security Enforcement: Bearer Token, RBAC, IDOR, and Empty Password Blocking")
    public void testWebSecurityEnforcement() throws Exception {
        testServer = new BankWebServer(0);
        testServer.start();
        baseUrl = "http://localhost:" + testServer.getPort();

        // 1. Empty password login attempt must fail with 400 Bad Request
        HttpRequest emptyPassReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"aarav\",\"password\":\"\"}"))
                .build();
        HttpResponse<String> emptyPassResp = httpClient.send(emptyPassReq, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(400, emptyPassResp.statusCode(), "Empty password login must be rejected with 400");
        Assertions.assertTrue(emptyPassResp.body().contains("required"));

        // 2. Accessing protected endpoint without Bearer token must return 401 Unauthorized
        HttpRequest noAuthReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/customer/dashboard"))
                .GET()
                .build();
        HttpResponse<String> noAuthResp = httpClient.send(noAuthReq, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(401, noAuthResp.statusCode(), "Request without Bearer token must return 401");

        // 3. Valid Login receives Bearer token
        HttpRequest loginReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"aarav\",\"password\":\"Password@123\"}"))
                .build();
        HttpResponse<String> loginResp = httpClient.send(loginReq, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(200, loginResp.statusCode());
        String body = loginResp.body();
        Assertions.assertTrue(body.contains("\"token\":"));
        int tokenStart = body.indexOf("\"token\":\"") + 9;
        int tokenEnd = body.indexOf("\"", tokenStart);
        String token = body.substring(tokenStart, tokenEnd);

        // 4. Customer accessing Admin overview must return 403 Forbidden (RBAC violation)
        HttpRequest adminReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/admin/overview"))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        HttpResponse<String> adminResp = httpClient.send(adminReq, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(403, adminResp.statusCode(), "Customer calling Admin endpoint must return 403");

        // 5. IDOR Prevention: Customer Aarav attempting to withdraw from Priya's account #1002 must return 403
        HttpRequest idorReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/banking/withdraw"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"accountNumber\":1002,\"amount\":100.00,\"pin\":\"1234\"}"))
                .build();
        HttpResponse<String> idorResp = httpClient.send(idorReq, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(403, idorResp.statusCode(), "Customer accessing another user's account must return 403 IDOR");
        Assertions.assertTrue(idorResp.body().contains("only access your own accounts"));

        // 6. Logout terminates token
        HttpRequest logoutReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/logout"))
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();
        HttpResponse<String> logoutResp = httpClient.send(logoutReq, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(200, logoutResp.statusCode());

        // Subsequent call with invalidated token must return 401
        HttpRequest postLogoutReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/customer/dashboard"))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        HttpResponse<String> postLogoutResp = httpClient.send(postLogoutReq, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(401, postLogoutResp.statusCode(), "Revoked token must be rejected with 401");
    }
}
