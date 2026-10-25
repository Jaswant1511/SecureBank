package com.securebank.security;

import com.securebank.dao.SessionDAO;
import com.securebank.model.Role;
import com.securebank.model.User;
import com.securebank.model.UserSession;
import com.securebank.service.SessionService;
import com.securebank.ui.web.BankWebServer;
import com.securebank.util.DatabaseConfig;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive Automated Verification Suite for Login, Logout & Session Monitoring.
 * Validates:
 *  - Persistent DB session tracking on Customer, Teller, and Admin logins.
 *  - Rejection of invalid credentials without session creation.
 *  - Correct logout timestamp, reason, and status transitions.
 *  - Idle timeout expiration reconciliation and statistics exclusion.
 *  - Role-based authorization on admin session endpoints (Customers and Tellers blocked with 403).
 *  - Administrative session termination and self-termination prevention.
 *  - Search, role, and date-range filters with pagination.
 *  - Scoped user recent session privacy (no IDOR).
 */
public class SessionMonitoringTest {

    private SessionService sessionService;
    private SessionDAO sessionDAO;
    private BankWebServer testServer;
    private HttpClient httpClient;
    private String baseUrl;

    @BeforeEach
    public void setUp() throws Exception {
        DatabaseConfig.switchToEmbeddedH2();
        DatabaseConfig.initializeSchema();
        DatabaseConfig.seedTestData();

        sessionDAO = new SessionDAO();
        sessionService = new SessionService(sessionDAO);
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
    @DisplayName("Login Tracking: Successful Customer, Teller, and Admin logins create persistent DB records")
    public void testSuccessfulLoginsCreateSessionRecords() {
        User customer = new User(103, "aarav", "hash", Role.CUSTOMER, 0, false, true);
        User teller = new User(102, "teller_suresh", "hash", Role.TELLER, 0, false, true);
        User admin = new User(101, "admin_rajesh", "hash", Role.ADMIN, 0, false, true);

        String custToken = sessionService.createSession(customer, "192.168.1.50");
        String tellerToken = sessionService.createSession(teller, "192.168.1.60");
        String adminToken = sessionService.createSession(admin, "192.168.1.70");

        Assertions.assertNotNull(custToken);
        Assertions.assertNotNull(tellerToken);
        Assertions.assertNotNull(adminToken);

        String custSessId = sessionService.getSession(custToken).getSessionId();
        String tellerSessId = sessionService.getSession(tellerToken).getSessionId();
        String adminSessId = sessionService.getSession(adminToken).getSessionId();

        List<UserSession> activeSessions = sessionDAO.getActiveSessions();
        Assertions.assertTrue(activeSessions.size() >= 3);

        boolean foundCust = false;
        boolean foundTeller = false;
        boolean foundAdmin = false;

        for (UserSession s : activeSessions) {
            if (custSessId.equals(s.getSessionId())) {
                foundCust = true;
                Assertions.assertEquals(103, s.getUserId());
                Assertions.assertEquals(Role.CUSTOMER, s.getRole());
                Assertions.assertEquals("ACTIVE", s.getSessionStatus());
                Assertions.assertEquals("192.168.1.50", s.getIpAddress());
                Assertions.assertNotNull(s.getLoginTime());
                Assertions.assertNull(s.getLogoutTime());
            } else if (tellerSessId.equals(s.getSessionId())) {
                foundTeller = true;
                Assertions.assertEquals(102, s.getUserId());
                Assertions.assertEquals(Role.TELLER, s.getRole());
                Assertions.assertEquals("ACTIVE", s.getSessionStatus());
                Assertions.assertEquals("192.168.1.60", s.getIpAddress());
            } else if (adminSessId.equals(s.getSessionId())) {
                foundAdmin = true;
                Assertions.assertEquals(101, s.getUserId());
                Assertions.assertEquals(Role.ADMIN, s.getRole());
                Assertions.assertEquals("ACTIVE", s.getSessionStatus());
                Assertions.assertEquals("192.168.1.70", s.getIpAddress());
            }
        }

        Assertions.assertTrue(foundCust, "Customer session record must be persisted");
        Assertions.assertTrue(foundTeller, "Teller session record must be persisted");
        Assertions.assertTrue(foundAdmin, "Admin session record must be persisted");

        Map<String, Integer> counts = sessionDAO.getActiveSessionCountsByRole();
        Assertions.assertTrue(counts.get("totalActive") >= 3);
        Assertions.assertTrue(counts.get("customersOnline") >= 1);
        Assertions.assertTrue(counts.get("tellersOnline") >= 1);
        Assertions.assertTrue(counts.get("adminsOnline") >= 1);
    }

    @Test
    @DisplayName("Invalid Credentials: Failed login attempts must NOT create session records")
    public void testFailedLoginDoesNotCreateSessionRecord() throws Exception {
        testServer = new BankWebServer(0);
        testServer.start();
        baseUrl = "http://localhost:" + testServer.getPort();

        int initialCount = testServer.getSessionService().getSessionDAO().countSessionHistory(null, null, null, null, null);

        // Attempt login with invalid password
        HttpRequest badLoginReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"aarav\",\"password\":\"WrongPassword!\"}"))
                .build();
        HttpResponse<String> resp = httpClient.send(badLoginReq, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(400, resp.statusCode());

        int postCount = testServer.getSessionService().getSessionDAO().countSessionHistory(null, null, null, null, null);
        Assertions.assertEquals(initialCount, postCount, "Failed login must never create a session record in the database");
    }

    @Test
    @DisplayName("Logout Tracking: Logout updates status to LOGGED_OUT with timestamp and reason, safe to repeat")
    public void testLogoutUpdatesSessionRecord() {
        User user = new User(103, "aarav", "hash", Role.CUSTOMER, 0, false, true);
        String token = sessionService.createSession(user, "10.0.0.1");

        SessionService.SessionRecord sessionRecord = sessionService.getSession(token);
        Assertions.assertNotNull(sessionRecord);
        String sessionId = sessionRecord.getSessionId();

        UserSession dbSessionBefore = sessionDAO.getSessionById(sessionId);
        Assertions.assertEquals("ACTIVE", dbSessionBefore.getSessionStatus());
        Assertions.assertNull(dbSessionBefore.getLogoutTime());

        // Perform Logout
        boolean loggedOut = sessionService.invalidateSession(token, "USER_LOGOUT");
        Assertions.assertTrue(loggedOut);

        UserSession dbSessionAfter = sessionDAO.getSessionById(sessionId);
        Assertions.assertEquals("LOGGED_OUT", dbSessionAfter.getSessionStatus());
        Assertions.assertEquals("USER_LOGOUT", dbSessionAfter.getSessionEndReason());
        Assertions.assertNotNull(dbSessionAfter.getLogoutTime());

        // Repeated logout should be idempotent and not crash or alter status
        boolean repeatLogout = sessionService.invalidateSession(token, "USER_LOGOUT");
        Assertions.assertFalse(repeatLogout);
        UserSession dbSessionRepeat = sessionDAO.getSessionById(sessionId);
        Assertions.assertEquals("LOGGED_OUT", dbSessionRepeat.getSessionStatus());
    }

    @Test
    @DisplayName("Session Expiration: Expired sessions are marked EXPIRED and excluded from active counts")
    public void testSessionExpirationTracking() {
        User user = new User(104, "priya", "hash", Role.CUSTOMER, 0, false, true);
        String token = sessionService.createSession(user, "10.0.0.2");

        SessionService.SessionRecord record = sessionService.getSession(token);
        String sessionId = record.getSessionId();

        // Simulate 16 minutes idle timeout
        record.setLastSeen(Instant.now().minus(Duration.ofMinutes(16)));

        // getSession detects expiration and reconciles database
        SessionService.SessionRecord expired = sessionService.getSession(token);
        Assertions.assertNull(expired, "Expired token must return null");

        UserSession dbSession = sessionDAO.getSessionById(sessionId);
        Assertions.assertEquals("EXPIRED", dbSession.getSessionStatus());
        Assertions.assertEquals("TIMEOUT_EXPIRED", dbSession.getSessionEndReason());

        Map<String, Integer> counts = sessionDAO.getActiveSessionCountsByRole();
        List<UserSession> activeSessions = sessionDAO.getActiveSessions();
        for (UserSession s : activeSessions) {
            Assertions.assertNotEquals(sessionId, s.getSessionId(), "Expired session must not appear in active sessions");
        }
    }

    @Test
    @DisplayName("Admin Termination: Admin can terminate active session; cannot terminate own session")
    public void testAdminSessionTermination() throws Exception {
        testServer = new BankWebServer(0);
        testServer.start();
        baseUrl = "http://localhost:" + testServer.getPort();

        // 1. Login as Admin
        HttpRequest adminLoginReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"admin_rajesh\",\"password\":\"Admin@123\"}"))
                .build();
        HttpResponse<String> adminLoginResp = httpClient.send(adminLoginReq, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(200, adminLoginResp.statusCode());
        String adminToken = extractJsonToken(adminLoginResp.body());

        // 2. Login as Customer
        HttpRequest custLoginReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"aarav\",\"password\":\"Password@123\"}"))
                .build();
        HttpResponse<String> custLoginResp = httpClient.send(custLoginReq, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(200, custLoginResp.statusCode());
        String custToken = extractJsonToken(custLoginResp.body());

        // Find customer's session ID from active sessions API
        HttpRequest activeReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/admin/sessions/active"))
                .header("Authorization", "Bearer " + adminToken)
                .GET()
                .build();
        HttpResponse<String> activeResp = httpClient.send(activeReq, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(200, activeResp.statusCode());
        String activeBody = activeResp.body();
        Assertions.assertTrue(activeBody.contains("aarav"));

        int custIdx = activeBody.indexOf("\"username\":\"aarav\"");
        int sessStart = activeBody.lastIndexOf("\"sessionId\":\"", custIdx) + 13;
        int sessEnd = activeBody.indexOf("\"", sessStart);
        String custSessionId = activeBody.substring(sessStart, sessEnd);

        // 3. Admin terminates customer session
        HttpRequest termReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/admin/sessions/terminate"))
                .header("Authorization", "Bearer " + adminToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"sessionId\":\"" + custSessionId + "\"}"))
                .build();
        HttpResponse<String> termResp = httpClient.send(termReq, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(200, termResp.statusCode());
        Assertions.assertTrue(termResp.body().contains("successfully terminated"));

        // 4. Customer token must now be rejected
        HttpRequest custApiReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/customer/dashboard"))
                .header("Authorization", "Bearer " + custToken)
                .GET()
                .build();
        HttpResponse<String> custApiResp = httpClient.send(custApiReq, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(401, custApiResp.statusCode(), "Terminated session must immediately be 401 Unauthorized");

        // 5. Admin attempting to terminate own active session must be rejected with 400
        int adminIdx = activeBody.indexOf("\"username\":\"admin_rajesh\"");
        int adminSessStart = activeBody.lastIndexOf("\"sessionId\":\"", adminIdx) + 13;
        int adminSessEnd = activeBody.indexOf("\"", adminSessStart);
        String adminSessionId = activeBody.substring(adminSessStart, adminSessEnd);

        HttpRequest termSelfReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/admin/sessions/terminate"))
                .header("Authorization", "Bearer " + adminToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"sessionId\":\"" + adminSessionId + "\"}"))
                .build();
        HttpResponse<String> termSelfResp = httpClient.send(termSelfReq, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(400, termSelfResp.statusCode(), "Admin self-termination must be blocked with 400");
    }

    @Test
    @DisplayName("Role Isolation: Customers and Tellers are strictly blocked (403) from Admin session endpoints")
    public void testRoleIsolationOnAdminSessionEndpoints() throws Exception {
        testServer = new BankWebServer(0);
        testServer.start();
        baseUrl = "http://localhost:" + testServer.getPort();

        // Customer Login
        HttpRequest custLoginReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"aarav\",\"password\":\"Password@123\"}"))
                .build();
        HttpResponse<String> custLoginResp = httpClient.send(custLoginReq, HttpResponse.BodyHandlers.ofString());
        String custToken = extractJsonToken(custLoginResp.body());

        // Teller Login
        HttpRequest tellerLoginReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"teller_suresh\",\"password\":\"Teller@123\"}"))
                .build();
        HttpResponse<String> tellerLoginResp = httpClient.send(tellerLoginReq, HttpResponse.BodyHandlers.ofString());
        String tellerToken = extractJsonToken(tellerLoginResp.body());

        // 1. Customer calling /api/admin/sessions/stats -> 403
        HttpRequest custStatsReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/admin/sessions/stats"))
                .header("Authorization", "Bearer " + custToken)
                .GET()
                .build();
        HttpResponse<String> custStatsResp = httpClient.send(custStatsReq, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(403, custStatsResp.statusCode(), "Customer must receive 403 on session stats");

        // 2. Customer calling /api/admin/sessions/history -> 403
        HttpRequest custHistReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/admin/sessions/history"))
                .header("Authorization", "Bearer " + custToken)
                .GET()
                .build();
        HttpResponse<String> custHistResp = httpClient.send(custHistReq, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(403, custHistResp.statusCode(), "Customer must receive 403 on session history");

        // 3. Teller calling /api/admin/sessions/active -> 403
        HttpRequest tellerActiveReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/admin/sessions/active"))
                .header("Authorization", "Bearer " + tellerToken)
                .GET()
                .build();
        HttpResponse<String> tellerActiveResp = httpClient.send(tellerActiveReq, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(403, tellerActiveResp.statusCode(), "Teller must receive 403 on active sessions");

        // 4. Teller calling /api/admin/sessions/terminate -> 403
        HttpRequest tellerTermReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/admin/sessions/terminate"))
                .header("Authorization", "Bearer " + tellerToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"sessionId\":\"dummy\"}"))
                .build();
        HttpResponse<String> tellerTermResp = httpClient.send(tellerTermReq, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(403, tellerTermResp.statusCode(), "Teller must receive 403 on session terminate");
    }

    @Test
    @DisplayName("Search & Filtering: Session history supports search by username, role filter, status, and pagination")
    public void testSessionHistorySearchAndFilters() {
        User u1 = new User(103, "aarav", "hash", Role.CUSTOMER, 0, false, true);
        User u2 = new User(104, "priya", "hash", Role.CUSTOMER, 0, false, true);
        User u3 = new User(102, "teller_suresh", "hash", Role.TELLER, 0, false, true);

        String t1 = sessionService.createSession(u1, "127.0.0.1");
        String t2 = sessionService.createSession(u2, "127.0.0.2");
        String t3 = sessionService.createSession(u3, "127.0.0.3");

        sessionService.invalidateSession(t1, "USER_LOGOUT"); // aarav logged out

        // 1. Search by username "aarav"
        List<UserSession> searchAarav = sessionDAO.getSessionHistory("aarav", null, null, null, null, 1, 10);
        Assertions.assertFalse(searchAarav.isEmpty());
        for (UserSession s : searchAarav) {
            Assertions.assertEquals("aarav", s.getUsername());
        }

        // 2. Filter by Role TELLER
        List<UserSession> tellerSessions = sessionDAO.getSessionHistory(null, "TELLER", null, null, null, 1, 10);
        Assertions.assertFalse(tellerSessions.isEmpty());
        for (UserSession s : tellerSessions) {
            Assertions.assertEquals(Role.TELLER, s.getRole());
        }

        // 3. Filter by Status LOGGED_OUT
        List<UserSession> loggedOutSessions = sessionDAO.getSessionHistory(null, null, "LOGGED_OUT", null, null, 1, 10);
        Assertions.assertFalse(loggedOutSessions.isEmpty());
        for (UserSession s : loggedOutSessions) {
            Assertions.assertEquals("LOGGED_OUT", s.getSessionStatus());
        }

        // 4. Date Range Filter
        LocalDate today = LocalDate.now();
        List<UserSession> todaySessions = sessionDAO.getSessionHistory(null, null, null, today, today, 1, 10);
        Assertions.assertFalse(todaySessions.isEmpty());

        // 5. Pagination count
        int count = sessionDAO.countSessionHistory(null, null, null, null, null);
        Assertions.assertTrue(count >= 3);
    }

    @Test
    @DisplayName("User Recent Sessions: Authenticated user sees only their own login activity")
    public void testUserRecentSessionsPrivacy() {
        User u1 = new User(103, "aarav", "hash", Role.CUSTOMER, 0, false, true);
        User u2 = new User(104, "priya", "hash", Role.CUSTOMER, 0, false, true);

        sessionService.createSession(u1, "10.0.0.1");
        sessionService.createSession(u1, "10.0.0.2");
        sessionService.createSession(u2, "10.0.0.3");

        List<UserSession> aaravSessions = sessionDAO.getUserRecentSessions(103, 5);
        Assertions.assertFalse(aaravSessions.isEmpty());
        for (UserSession s : aaravSessions) {
            Assertions.assertEquals(103, s.getUserId(), "User recent sessions must strictly contain only caller's records");
            Assertions.assertEquals("aarav", s.getUsername());
        }
    }

    private String extractJsonToken(String json) {
        int tokenStart = json.indexOf("\"token\":\"") + 9;
        int tokenEnd = json.indexOf("\"", tokenStart);
        return json.substring(tokenStart, tokenEnd);
    }
}
