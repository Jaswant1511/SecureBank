package com.securebank.dao;

import com.securebank.model.Notification;
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
 * SecureBank: Complete Banking Automation System
 * File: NotificationDAO.java
 *
 * Persistence operations for user in-app notifications.
 */
public class NotificationDAO {

    public long createNotification(Notification n) throws SQLException {
        final String sql = "INSERT INTO notifications (user_id, title, message, type, is_read, created_at) "
            + "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, n.getUserId());
            ps.setString(2, n.getTitle());
            ps.setString(3, n.getMessage());
            ps.setString(4, n.getType() != null ? n.getType() : "INFO");
            ps.setBoolean(5, n.isRead());
            ps.setTimestamp(6, Timestamp.valueOf(n.getCreatedAt() != null ? n.getCreatedAt() : LocalDateTime.now()));

            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    long id = rs.getLong(1);
                    n.setNotificationId(id);
                    return id;
                }
            }
            return 0;
        }
    }

    public List<Notification> getNotificationsForUser(int userId, boolean unreadOnly) throws SQLException {
        String sql = "SELECT notification_id, user_id, title, message, type, is_read, created_at "
            + "FROM notifications WHERE user_id = ?";
        if (unreadOnly) {
            sql += " AND is_read = FALSE";
        }
        sql += " ORDER BY created_at DESC, notification_id DESC LIMIT 50";

        List<Notification> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Timestamp ts = rs.getTimestamp("created_at");
                    LocalDateTime createdAt = ts != null ? ts.toLocalDateTime() : LocalDateTime.now();
                    list.add(new Notification(
                        rs.getLong("notification_id"),
                        rs.getInt("user_id"),
                        rs.getString("title"),
                        rs.getString("message"),
                        rs.getString("type"),
                        rs.getBoolean("is_read"),
                        createdAt
                    ));
                }
            }
        }
        return list;
    }

    public int getUnreadCount(int userId) throws SQLException {
        final String sql = "SELECT COUNT(*) FROM notifications WHERE user_id = ? AND is_read = FALSE";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    public boolean markAsRead(long notificationId, int userId) throws SQLException {
        final String sql = "UPDATE notifications SET is_read = TRUE WHERE notification_id = ? AND user_id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, notificationId);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean markAllAsRead(int userId) throws SQLException {
        final String sql = "UPDATE notifications SET is_read = TRUE WHERE user_id = ? AND is_read = FALSE";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            return ps.executeUpdate() > 0;
        }
    }
}
