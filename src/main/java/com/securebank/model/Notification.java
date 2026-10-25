package com.securebank.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * SecureBank: Complete Banking Automation System
 * File: Notification.java
 *
 * Represents an in-app user notification for routine, transactional,
 * or security-critical banking events.
 */
public class Notification {

    private long notificationId;
    private int userId;
    private String title;
    private String message;
    private String type; // INFO, WARNING, ALERT, SUCCESS
    private boolean isRead;
    private LocalDateTime createdAt;

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public Notification() {
        this.type = "INFO";
        this.isRead = false;
        this.createdAt = LocalDateTime.now();
    }

    public Notification(long notificationId, int userId, String title, String message, String type, boolean isRead, LocalDateTime createdAt) {
        this.notificationId = notificationId;
        this.userId = userId;
        this.title = title;
        this.message = message;
        this.type = type != null ? type : "INFO";
        this.isRead = isRead;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
    }

    public long getNotificationId() {
        return notificationId;
    }

    public void setNotificationId(long notificationId) {
        this.notificationId = notificationId;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isRead() {
        return isRead;
    }

    public void setRead(boolean read) {
        isRead = read;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getFormattedTimestamp() {
        return createdAt != null ? createdAt.format(TS_FMT) : "";
    }
}
