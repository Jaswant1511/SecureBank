package com.securebank.service;

import com.securebank.dao.NotificationDAO;
import com.securebank.model.Notification;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SecureBank: Complete Banking Automation System
 * File: NotificationService.java
 *
 * Automated In-App Notification engine for transaction confirmations,
 * security alerts, and administrative status updates.
 */
public class NotificationService {

    private static final Logger LOGGER = Logger.getLogger(NotificationService.class.getName());
    private final NotificationDAO notificationDAO;

    public NotificationService() {
        this.notificationDAO = new NotificationDAO();
    }

    public NotificationService(NotificationDAO notificationDAO) {
        this.notificationDAO = notificationDAO;
    }

    /**
     * Dispatches an automated in-app notification to a specific user.
     * Never throws checked exceptions to caller so banking operations never fail
     * solely due to notification dispatch issues.
     */
    public boolean notifyUser(int userId, String title, String message, String type) {
        if (userId <= 0 || title == null || message == null) {
            return false;
        }
        try {
            Notification n = new Notification(0, userId, title, message, type != null ? type : "INFO", false, LocalDateTime.now());
            notificationDAO.createNotification(n);
            return true;
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to dispatch automated notification to user " + userId + ": " + e.getMessage());
            return false;
        }
    }

    public List<Notification> getUserNotifications(int userId, boolean unreadOnly) {
        try {
            return notificationDAO.getNotificationsForUser(userId, unreadOnly);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to retrieve notifications for user " + userId + ": " + e.getMessage());
            return Collections.emptyList();
        }
    }

    public int getUnreadCount(int userId) {
        try {
            return notificationDAO.getUnreadCount(userId);
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to get unread notification count for user " + userId, e);
            return 0;
        }
    }

    public boolean markAsRead(long notificationId, int userId) {
        try {
            return notificationDAO.markAsRead(notificationId, userId);
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to mark notification " + notificationId + " as read", e);
            return false;
        }
    }

    public boolean markAllAsRead(int userId) {
        try {
            return notificationDAO.markAllAsRead(userId);
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to mark all notifications as read for user " + userId, e);
            return false;
        }
    }
}
