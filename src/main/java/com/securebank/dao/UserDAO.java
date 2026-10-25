package com.securebank.dao;

import com.securebank.model.Role;
import com.securebank.model.User;
import com.securebank.util.DatabaseConfig;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * SecureBank: Role-Based Core Banking & Transaction Management System
 * File: UserDAO.java
 *
 * Persistence operations for Users and Authentication credentials.
 */
public class UserDAO {

    public int createUser(User user, Connection conn) throws SQLException {
        final String sql = "INSERT INTO users (username, password_hash, role, failed_logins, is_locked, active, created_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        boolean isLocalConn = false;
        if (conn == null) {
            conn = DatabaseConfig.getConnection();
            isLocalConn = true;
        }
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getPasswordHash());
            ps.setString(3, user.getRole().name());
            ps.setInt(4, user.getFailedLogins());
            ps.setBoolean(5, user.isLocked());
            ps.setBoolean(6, user.isActive());
            ps.setTimestamp(7, Timestamp.valueOf(user.getCreatedAt() != null ? user.getCreatedAt() : LocalDateTime.now()));

            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    user.setUserId(id);
                    return id;
                }
            }
            throw new SQLException("Creating user failed, no ID obtained.");
        } finally {
            if (isLocalConn && conn != null) {
                conn.close();
            }
        }
    }

    public User getUserByUsername(String username) throws SQLException {
        final String sql = "SELECT user_id, username, password_hash, role, failed_logins, is_locked, active, created_at, last_login "
            + "FROM users WHERE username = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRowToUser(rs);
                }
                return null;
            }
        }
    }

    public User getUserById(int userId) throws SQLException {
        final String sql = "SELECT user_id, username, password_hash, role, failed_logins, is_locked, active, created_at, last_login "
            + "FROM users WHERE user_id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRowToUser(rs);
                }
                return null;
            }
        }
    }

    public User getUserByEmail(String email) throws SQLException {
        if (email == null || email.trim().isEmpty()) {
            return null;
        }
        final String sql = "SELECT u.user_id, u.username, u.password_hash, u.role, u.failed_logins, u.is_locked, u.active, u.created_at, u.last_login "
            + "FROM users u INNER JOIN customers c ON u.user_id = c.user_id WHERE LOWER(c.email) = LOWER(?)";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRowToUser(rs);
                }
                return null;
            }
        }
    }

    public User getUserByFullName(String fullName) throws SQLException {
        if (fullName == null || fullName.trim().isEmpty()) {
            return null;
        }
        final String sql = "SELECT u.user_id, u.username, u.password_hash, u.role, u.failed_logins, u.is_locked, u.active, u.created_at, u.last_login "
            + "FROM users u INNER JOIN customers c ON u.user_id = c.user_id WHERE LOWER(c.full_name) = LOWER(?)";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, fullName.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRowToUser(rs);
                }
                return null;
            }
        }
    }

    public User getUserByAccountNumber(long accountNumber) throws SQLException {
        final String sql = "SELECT u.user_id, u.username, u.password_hash, u.role, u.failed_logins, u.is_locked, u.active, u.created_at, u.last_login "
            + "FROM users u INNER JOIN customers c ON u.user_id = c.user_id INNER JOIN accounts a ON c.customer_id = a.customer_id WHERE a.account_number = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, accountNumber);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRowToUser(rs);
                }
                return null;
            }
        }
    }

    public User getUserByEmailOrUsername(String identifier) throws SQLException {
        if (identifier == null || identifier.trim().isEmpty()) {
            return null;
        }
        String trimmed = identifier.trim();

        // 1. Check exact username match
        User byUsername = getUserByUsername(trimmed);
        if (byUsername != null) {
            return byUsername;
        }

        // 2. Check email match
        User byEmail = getUserByEmail(trimmed);
        if (byEmail != null) {
            return byEmail;
        }

        // 3. Check customer full name match
        User byFullName = getUserByFullName(trimmed);
        if (byFullName != null) {
            return byFullName;
        }

        // 4. If numeric, check account number match
        if (trimmed.matches("^\\d{4,12}$")) {
            try {
                long accNum = Long.parseLong(trimmed);
                User byAcc = getUserByAccountNumber(accNum);
                if (byAcc != null) {
                    return byAcc;
                }
            } catch (NumberFormatException ignored) {}
        }

        return null;
    }

    public void updatePasswordHash(int userId, String newPasswordHash) throws SQLException {
        final String sql = "UPDATE users SET password_hash = ? WHERE user_id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newPasswordHash);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
    }

    public boolean hasAdminUser() throws SQLException {
        final String sql = "SELECT COUNT(*) FROM users WHERE role = 'ADMIN'";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
            return false;
        }
    }

    public void updateLoginSecurity(int userId, int failedLogins, boolean isLocked, LocalDateTime lastLogin) throws SQLException {
        final String sql = "UPDATE users SET failed_logins = ?, is_locked = ?, last_login = ? WHERE user_id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, failedLogins);
            ps.setBoolean(2, isLocked);
            ps.setTimestamp(3, lastLogin != null ? Timestamp.valueOf(lastLogin) : null);
            ps.setInt(4, userId);
            ps.executeUpdate();
        }
    }

    public void updateUserStatus(int userId, boolean active) throws SQLException {
        final String sql = "UPDATE users SET active = ? WHERE user_id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, active);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
    }

    public void unlockUser(int userId) throws SQLException {
        final String sql = "UPDATE users SET failed_logins = 0, is_locked = FALSE WHERE user_id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.executeUpdate();
        }
    }

    public List<User> getAllUsers() throws SQLException {
        final String sql = "SELECT user_id, username, password_hash, role, failed_logins, is_locked, active, created_at, last_login "
            + "FROM users ORDER BY user_id ASC";
        List<User> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRowToUser(rs));
            }
        }
        return list;
    }

    public int countAdmins() throws SQLException {
        final String sql = "SELECT COUNT(*) FROM users WHERE role = 'ADMIN'";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        }
    }

    public int countUsers() throws SQLException {
        final String sql = "SELECT COUNT(*) FROM users";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        }
    }

    public void clearAllUserData() throws SQLException {
        try (Connection conn = DatabaseConfig.getConnection();
             Statement st = conn.createStatement()) {
            st.executeUpdate("DELETE FROM scheduled_job_logs");
            st.executeUpdate("DELETE FROM reconciliation_logs");
            st.executeUpdate("DELETE FROM suspicious_alerts");
            st.executeUpdate("DELETE FROM notifications");
            st.executeUpdate("DELETE FROM transfer_details");
            st.executeUpdate("DELETE FROM transactions");
            st.executeUpdate("DELETE FROM accounts");
            st.executeUpdate("DELETE FROM customers");
            // NOTE: audit_logs are intentionally preserved to ensure regulatory and forensic audit integrity
            st.executeUpdate("DELETE FROM users");
        }
    }

    public User createStaffUser(String username, String passwordHash, Role role) throws SQLException {
        User u = new User();
        u.setUsername(username);
        u.setPasswordHash(passwordHash);
        u.setRole(role);
        u.setFailedLogins(0);
        u.setLocked(false);
        u.setActive(true);
        u.setCreatedAt(LocalDateTime.now());
        int id = createUser(u, null);
        u.setUserId(id);
        return u;
    }

    private User mapRowToUser(ResultSet rs) throws SQLException {
        User u = new User();
        u.setUserId(rs.getInt("user_id"));
        u.setUsername(rs.getString("username"));
        u.setPasswordHash(rs.getString("password_hash"));
        u.setRole(Role.fromString(rs.getString("role")));
        u.setFailedLogins(rs.getInt("failed_logins"));
        u.setLocked(rs.getBoolean("is_locked"));
        u.setActive(rs.getBoolean("active"));
        Timestamp cat = rs.getTimestamp("created_at");
        if (cat != null) {
            u.setCreatedAt(cat.toLocalDateTime());
        }
        Timestamp llt = rs.getTimestamp("last_login");
        if (llt != null) {
            u.setLastLogin(llt.toLocalDateTime());
        }
        return u;
    }
}
