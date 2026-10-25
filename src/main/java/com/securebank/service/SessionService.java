package com.securebank.service;

import com.securebank.dao.SessionDAO;
import com.securebank.model.Role;
import com.securebank.model.User;
import com.securebank.model.UserSession;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SecureBank: Role-Based Core Banking & Transaction Management System
 * File: SessionService.java
 *
 * Cryptographically Secure Server-Side Session Manager with Database Persistence.
 * Features:
 *  - 32-byte SecureRandom cryptographically random Base64URL session tokens.
 *  - In-memory ConcurrentHashMap storage (user, role, createdAt, lastSeen, sessionId, ipAddress).
 *  - Dual-layer database tracking (user_sessions) for login/logout audits and admin monitoring.
 *  - 15-minute idle timeout enforcement with automated session expiration reconciliation.
 *  - Single session invalidation (logout), admin termination, and user-wide invalidation.
 */
public class SessionService {

    public static final Duration IDLE_TIMEOUT = Duration.ofMinutes(15);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public static class SessionRecord {
        private final String token;
        private final String sessionId;
        private final User user;
        private final Role role;
        private final String ipAddress;
        private final Instant createdAt;
        private volatile Instant lastSeen;

        public SessionRecord(String token, User user, Role role, Instant createdAt, Instant lastSeen) {
            this(token, UUID.randomUUID().toString(), user, role, "127.0.0.1", createdAt, lastSeen);
        }

        public SessionRecord(String token, String sessionId, User user, Role role, String ipAddress, Instant createdAt, Instant lastSeen) {
            this.token = token;
            this.sessionId = sessionId != null ? sessionId : UUID.randomUUID().toString();
            this.user = user;
            this.role = role;
            this.ipAddress = ipAddress != null ? ipAddress : "127.0.0.1";
            this.createdAt = createdAt;
            this.lastSeen = lastSeen;
        }

        public String getToken() { return token; }
        public String getSessionId() { return sessionId; }
        public User getUser() { return user; }
        public Role getRole() { return role; }
        public String getIpAddress() { return ipAddress; }
        public Instant getCreatedAt() { return createdAt; }
        public Instant getLastSeen() { return lastSeen; }
        public void setLastSeen(Instant lastSeen) { this.lastSeen = lastSeen; }
    }

    private final Map<String, SessionRecord> activeSessions = new ConcurrentHashMap<>();
    private final SessionDAO sessionDAO;

    public SessionService() {
        this(new SessionDAO());
    }

    public SessionService(SessionDAO sessionDAO) {
        this.sessionDAO = sessionDAO != null ? sessionDAO : new SessionDAO();
    }

    public SessionDAO getSessionDAO() {
        return sessionDAO;
    }

    public String createSession(User user) {
        return createSession(user, "127.0.0.1");
    }

    public String createSession(User user, String ipAddress) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null for session creation.");
        }
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        String sessionId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        SessionRecord session = new SessionRecord(token, sessionId, user, user.getRole(), ipAddress, now, now);
        activeSessions.put(token, session);

        // Safe database session persistence
        try {
            UserSession us = new UserSession();
            us.setSessionId(sessionId);
            us.setUserId(user.getUserId());
            us.setUsername(user.getUsername());
            us.setRole(user.getRole());
            us.setLoginTime(LocalDateTime.now());
            us.setLastActivityTime(LocalDateTime.now());
            us.setSessionStatus("ACTIVE");
            us.setIpAddress(ipAddress != null ? ipAddress : "127.0.0.1");
            sessionDAO.createSession(us);
        } catch (Exception ignored) {
        }

        return token;
    }

    public SessionRecord getSession(String token) {
        if (token == null || token.trim().isEmpty()) {
            return null;
        }
        SessionRecord session = activeSessions.get(token);
        if (session == null) {
            return null;
        }
        Instant now = Instant.now();
        if (Duration.between(session.getLastSeen(), now).compareTo(IDLE_TIMEOUT) > 0) {
            activeSessions.remove(token);
            try {
                sessionDAO.updateSessionExpired(session.getSessionId(), "TIMEOUT_EXPIRED");
            } catch (Exception ignored) {
            }
            return null;
        }
        session.setLastSeen(now);
        try {
            sessionDAO.updateSessionActivity(session.getSessionId(), LocalDateTime.now());
        } catch (Exception ignored) {
        }
        return session;
    }

    public boolean invalidateSession(String token) {
        return invalidateSession(token, "USER_LOGOUT");
    }

    public boolean invalidateSession(String token, String reason) {
        if (token == null) return false;
        SessionRecord session = activeSessions.remove(token);
        if (session != null) {
            try {
                sessionDAO.updateSessionLogout(session.getSessionId(), reason != null ? reason : "USER_LOGOUT");
            } catch (Exception ignored) {
            }
            return true;
        }
        return false;
    }

    public void invalidateUserSessions(int userId) {
        invalidateUserSessions(userId, "USER_PASSWORD_CHANGED");
    }

    public void invalidateUserSessions(int userId, String reason) {
        activeSessions.entrySet().removeIf(entry -> {
            if (entry.getValue().getUser().getUserId() == userId) {
                try {
                    sessionDAO.updateSessionLogout(entry.getValue().getSessionId(), reason != null ? reason : "USER_PASSWORD_CHANGED");
                } catch (Exception ignored) {
                }
                return true;
            }
            return false;
        });
    }

    public boolean terminateSessionByAdmin(String sessionId, String adminUsername) {
        if (sessionId == null) return false;
        // Invalidate in-memory server session immediately
        activeSessions.entrySet().removeIf(entry -> sessionId.equals(entry.getValue().getSessionId()));
        // Record administrative termination in database
        return sessionDAO.terminateSession(sessionId, "TERMINATED_BY_ADMIN: " + (adminUsername != null ? adminUsername : "Admin"));
    }

    public void cleanExpiredSessions() {
        Instant now = Instant.now();
        activeSessions.entrySet().removeIf(entry -> {
            if (Duration.between(entry.getValue().getLastSeen(), now).compareTo(IDLE_TIMEOUT) > 0) {
                try {
                    sessionDAO.updateSessionExpired(entry.getValue().getSessionId(), "TIMEOUT_EXPIRED");
                } catch (Exception ignored) {
                }
                return true;
            }
            return false;
        });
        try {
            sessionDAO.expireStaleSessions(IDLE_TIMEOUT);
        } catch (Exception ignored) {
        }
    }

    public int getActiveSessionCount() {
        return activeSessions.size();
    }

    public void clearAllSessions() {
        for (SessionRecord record : activeSessions.values()) {
            try {
                sessionDAO.updateSessionLogout(record.getSessionId(), "SERVER_SHUTDOWN");
            } catch (Exception ignored) {
            }
        }
        activeSessions.clear();
    }
}
