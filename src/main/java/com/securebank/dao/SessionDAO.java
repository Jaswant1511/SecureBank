package com.securebank.dao;

import com.securebank.model.Role;
import com.securebank.model.UserSession;
import com.securebank.util.DatabaseConfig;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SecureBank: Role-Based Core Banking & Transaction Management System
 * File: SessionDAO.java
 *
 * Safe database persistence operations for authentication sessions.
 * Supports MySQL 8 and Embedded H2 with parameterized prepared statements.
 */
public class SessionDAO {

    private static final Logger LOGGER = Logger.getLogger(SessionDAO.class.getName());

    /**
     * Creates a new session record in the database upon successful authentication.
     */
    public boolean createSession(UserSession session) {
        if (session == null || session.getSessionId() == null) {
            return false;
        }
        final String sql = "INSERT INTO user_sessions "
                + "(session_id, user_id, username, role, login_time, logout_time, last_activity_time, "
                + "session_status, session_end_reason, ip_address, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, session.getSessionId());
            ps.setInt(2, session.getUserId());
            ps.setString(3, session.getUsername());
            ps.setString(4, session.getRole() != null ? session.getRole().name() : Role.CUSTOMER.name());
            ps.setTimestamp(5, Timestamp.valueOf(session.getLoginTime() != null ? session.getLoginTime() : LocalDateTime.now()));
            ps.setTimestamp(6, session.getLogoutTime() != null ? Timestamp.valueOf(session.getLogoutTime()) : null);
            ps.setTimestamp(7, Timestamp.valueOf(session.getLastActivityTime() != null ? session.getLastActivityTime() : LocalDateTime.now()));
            ps.setString(8, session.getSessionStatus() != null ? session.getSessionStatus() : "ACTIVE");
            ps.setString(9, session.getSessionEndReason());
            ps.setString(10, session.getIpAddress() != null ? session.getIpAddress() : "127.0.0.1");
            ps.setTimestamp(11, Timestamp.valueOf(session.getCreatedAt() != null ? session.getCreatedAt() : LocalDateTime.now()));
            ps.setTimestamp(12, Timestamp.valueOf(session.getUpdatedAt() != null ? session.getUpdatedAt() : LocalDateTime.now()));

            int rows = ps.executeUpdate();
            return rows > 0;
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to create session record for user: " + session.getUsername(), ex);
            return false;
        }
    }

    /**
     * Updates an active session status (e.g. LOGGED_OUT, EXPIRED) with timestamp and reason.
     * Safe to repeat without overwriting original logout timestamp if already closed.
     */
    public boolean updateSessionStatus(String sessionId, String status, String reason) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return false;
        }
        final String sql = "UPDATE user_sessions SET logout_time = ?, session_status = ?, "
                + "session_end_reason = ?, updated_at = ? WHERE session_id = ? AND session_status = 'ACTIVE'";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            LocalDateTime now = LocalDateTime.now();
            ps.setTimestamp(1, Timestamp.valueOf(now));
            ps.setString(2, status != null ? status : "LOGGED_OUT");
            ps.setString(3, reason != null ? reason : status);
            ps.setTimestamp(4, Timestamp.valueOf(now));
            ps.setString(5, sessionId);

            int rows = ps.executeUpdate();
            return rows > 0;
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "Failed to update status for session: " + sessionId, ex);
            return false;
        }
    }

    public boolean updateSessionLogout(String sessionId, String reason) {
        return updateSessionStatus(sessionId, "LOGGED_OUT", reason != null ? reason : "USER_LOGOUT");
    }

    public boolean updateSessionExpired(String sessionId, String reason) {
        return updateSessionStatus(sessionId, "EXPIRED", reason != null ? reason : "TIMEOUT_EXPIRED");
    }

    /**
     * Updates the last activity timestamp for an active session.
     */
    public boolean updateSessionActivity(String sessionId, LocalDateTime activityTime) {
        if (sessionId == null) {
            return false;
        }
        final String sql = "UPDATE user_sessions SET last_activity_time = ?, updated_at = ? "
                + "WHERE session_id = ? AND session_status = 'ACTIVE'";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            LocalDateTime time = activityTime != null ? activityTime : LocalDateTime.now();
            ps.setTimestamp(1, Timestamp.valueOf(time));
            ps.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
            ps.setString(3, sessionId);

            return ps.executeUpdate() > 0;
        } catch (SQLException ex) {
            LOGGER.log(Level.FINE, "Failed to update activity for session: " + sessionId, ex);
            return false;
        }
    }

    /**
     * Reconciles expired sessions whose last activity exceeds the idle timeout threshold.
     */
    public int expireStaleSessions(Duration timeout) {
        LocalDateTime threshold = LocalDateTime.now().minus(timeout != null ? timeout : Duration.ofMinutes(15));
        final String sql = "UPDATE user_sessions SET session_status = 'EXPIRED', "
                + "session_end_reason = 'TIMEOUT_EXPIRED', logout_time = last_activity_time, updated_at = ? "
                + "WHERE session_status = 'ACTIVE' AND last_activity_time < ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            ps.setTimestamp(2, Timestamp.valueOf(threshold));

            return ps.executeUpdate();
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "Failed to reconcile stale sessions", ex);
            return 0;
        }
    }

    /**
     * Administrative termination of an active session.
     */
    public boolean terminateSession(String sessionId, String reason) {
        if (sessionId == null) {
            return false;
        }
        final String sql = "UPDATE user_sessions SET logout_time = ?, session_status = 'TERMINATED_BY_ADMIN', "
                + "session_end_reason = ?, updated_at = ? WHERE session_id = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            LocalDateTime now = LocalDateTime.now();
            ps.setTimestamp(1, Timestamp.valueOf(now));
            ps.setString(2, reason != null ? reason : "TERMINATED_BY_ADMIN");
            ps.setTimestamp(3, Timestamp.valueOf(now));
            ps.setString(4, sessionId);

            return ps.executeUpdate() > 0;
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "Failed to terminate session: " + sessionId, ex);
            return false;
        }
    }

    /**
     * Fetches a session record by its unique session ID.
     */
    public UserSession getSessionById(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        final String sql = "SELECT * FROM user_sessions WHERE session_id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRowToUserSession(rs);
                }
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "Failed to fetch session: " + sessionId, ex);
        }
        return null;
    }

    /**
     * Returns all currently active sessions ordered by login time descending.
     */
    public List<UserSession> getActiveSessions() {
        final String sql = "SELECT * FROM user_sessions WHERE session_status = 'ACTIVE' ORDER BY login_time DESC";
        List<UserSession> sessions = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                sessions.add(mapRowToUserSession(rs));
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to retrieve active sessions", ex);
        }
        return sessions;
    }

    /**
     * Returns summary counts of active sessions broken down by role.
     */
    public Map<String, Integer> getActiveSessionCountsByRole() {
        Map<String, Integer> counts = new HashMap<>();
        counts.put("totalActive", 0);
        counts.put("customersOnline", 0);
        counts.put("tellersOnline", 0);
        counts.put("adminsOnline", 0);

        final String sql = "SELECT role, COUNT(*) as cnt FROM user_sessions WHERE session_status = 'ACTIVE' GROUP BY role";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            int total = 0;
            while (rs.next()) {
                String roleStr = rs.getString("role");
                int cnt = rs.getInt("cnt");
                total += cnt;
                if ("CUSTOMER".equalsIgnoreCase(roleStr)) {
                    counts.put("customersOnline", cnt);
                } else if ("TELLER".equalsIgnoreCase(roleStr)) {
                    counts.put("tellersOnline", cnt);
                } else if ("ADMIN".equalsIgnoreCase(roleStr)) {
                    counts.put("adminsOnline", cnt);
                }
            }
            counts.put("totalActive", total);
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to retrieve active session counts by role", ex);
        }
        return counts;
    }

    /**
     * Retrieves paginated session history with parameterized search and filter controls.
     */
    public List<UserSession> getSessionHistory(
            String search,
            String roleFilter,
            String statusFilter,
            LocalDate fromDate,
            LocalDate toDate,
            int page,
            int pageSize
    ) {
        StringBuilder sql = new StringBuilder("SELECT * FROM user_sessions WHERE 1=1 ");
        List<Object> params = buildFilterParams(sql, search, roleFilter, statusFilter, fromDate, toDate);

        sql.append(" ORDER BY login_time DESC LIMIT ? OFFSET ?");
        int limit = Math.max(1, Math.min(pageSize, 100));
        int offset = Math.max(0, (page - 1) * limit);
        params.add(limit);
        params.add(offset);

        List<UserSession> sessions = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < params.size(); i++) {
                Object p = params.get(i);
                if (p instanceof String) {
                    ps.setString(i + 1, (String) p);
                } else if (p instanceof Integer) {
                    ps.setInt(i + 1, (Integer) p);
                } else if (p instanceof Timestamp) {
                    ps.setTimestamp(i + 1, (Timestamp) p);
                }
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    sessions.add(mapRowToUserSession(rs));
                }
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to retrieve session history", ex);
        }
        return sessions;
    }

    /**
     * Counts total session records matching the specified filters for pagination calculations.
     */
    public int countSessionHistory(
            String search,
            String roleFilter,
            String statusFilter,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM user_sessions WHERE 1=1 ");
        List<Object> params = buildFilterParams(sql, search, roleFilter, statusFilter, fromDate, toDate);

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < params.size(); i++) {
                Object p = params.get(i);
                if (p instanceof String) {
                    ps.setString(i + 1, (String) p);
                } else if (p instanceof Integer) {
                    ps.setInt(i + 1, (Integer) p);
                } else if (p instanceof Timestamp) {
                    ps.setTimestamp(i + 1, (Timestamp) p);
                }
            }

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to count session history records", ex);
        }
        return 0;
    }

    /**
     * Returns recent authentication events (logins, logouts, expirations) across all users.
     */
    public List<UserSession> getRecentActivity(int limit) {
        final String sql = "SELECT * FROM user_sessions ORDER BY updated_at DESC, login_time DESC LIMIT ?";
        List<UserSession> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, Math.max(1, Math.min(limit, 50)));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToUserSession(rs));
                }
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "Failed to retrieve recent session activity", ex);
        }
        return list;
    }

    /**
     * Retrieves recent login records specifically for an authenticated user.
     * Prevents IDOR and ensures customer/teller dashboards see only their own sessions.
     */
    public List<UserSession> getUserRecentSessions(int userId, int limit) {
        final String sql = "SELECT * FROM user_sessions WHERE user_id = ? ORDER BY login_time DESC LIMIT ?";
        List<UserSession> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);
            ps.setInt(2, Math.max(1, Math.min(limit, 20)));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToUserSession(rs));
                }
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "Failed to retrieve user recent sessions for user #" + userId, ex);
        }
        return list;
    }

    private List<Object> buildFilterParams(
            StringBuilder sql,
            String search,
            String roleFilter,
            String statusFilter,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        List<Object> params = new ArrayList<>();

        if (search != null && !search.trim().isEmpty()) {
            String s = search.trim();
            Integer idVal = null;
            try {
                idVal = Integer.parseInt(s);
            } catch (NumberFormatException ignored) {
            }

            if (idVal != null) {
                sql.append(" AND (LOWER(username) LIKE ? OR user_id = ?)");
                params.add("%" + s.toLowerCase() + "%");
                params.add(idVal);
            } else {
                sql.append(" AND LOWER(username) LIKE ?");
                params.add("%" + s.toLowerCase() + "%");
            }
        }

        if (roleFilter != null && !roleFilter.trim().isEmpty() && !"ALL".equalsIgnoreCase(roleFilter.trim())) {
            sql.append(" AND role = ?");
            params.add(roleFilter.trim().toUpperCase());
        }

        if (statusFilter != null && !statusFilter.trim().isEmpty() && !"ALL".equalsIgnoreCase(statusFilter.trim())) {
            sql.append(" AND session_status = ?");
            params.add(statusFilter.trim().toUpperCase());
        }

        if (fromDate != null) {
            sql.append(" AND login_time >= ?");
            params.add(Timestamp.valueOf(LocalDateTime.of(fromDate, LocalTime.MIN)));
        }

        if (toDate != null) {
            sql.append(" AND login_time <= ?");
            params.add(Timestamp.valueOf(LocalDateTime.of(toDate, LocalTime.MAX)));
        }

        return params;
    }

    private UserSession mapRowToUserSession(ResultSet rs) throws SQLException {
        UserSession s = new UserSession();
        s.setSessionId(rs.getString("session_id"));
        s.setUserId(rs.getInt("user_id"));
        s.setUsername(rs.getString("username"));
        String roleStr = rs.getString("role");
        if (roleStr != null) {
            s.setRole(Role.fromString(roleStr));
        }
        Timestamp loginTs = rs.getTimestamp("login_time");
        if (loginTs != null) {
            s.setLoginTime(loginTs.toLocalDateTime());
        }
        Timestamp logoutTs = rs.getTimestamp("logout_time");
        if (logoutTs != null) {
            s.setLogoutTime(logoutTs.toLocalDateTime());
        }
        Timestamp actTs = rs.getTimestamp("last_activity_time");
        if (actTs != null) {
            s.setLastActivityTime(actTs.toLocalDateTime());
        }
        s.setSessionStatus(rs.getString("session_status"));
        s.setSessionEndReason(rs.getString("session_end_reason"));
        s.setIpAddress(rs.getString("ip_address"));
        Timestamp crTs = rs.getTimestamp("created_at");
        if (crTs != null) {
            s.setCreatedAt(crTs.toLocalDateTime());
        }
        Timestamp upTs = rs.getTimestamp("updated_at");
        if (upTs != null) {
            s.setUpdatedAt(upTs.toLocalDateTime());
        }
        return s;
    }
}
