package com.securebank.model;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * SecureBank: Role-Based Core Banking & Transaction Management System
 * File: UserSession.java
 *
 * Represents a persistent database-backed authentication session record.
 * Used for login/logout tracking, activity auditing, and administrator session monitoring.
 */
public class UserSession {

    private String sessionId;
    private int userId;
    private String username;
    private Role role;
    private LocalDateTime loginTime;
    private LocalDateTime logoutTime;
    private LocalDateTime lastActivityTime;
    private String sessionStatus; // ACTIVE, LOGGED_OUT, EXPIRED, TERMINATED_BY_ADMIN
    private String sessionEndReason;
    private String ipAddress;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public UserSession() {
        this.loginTime = LocalDateTime.now();
        this.lastActivityTime = LocalDateTime.now();
        this.sessionStatus = "ACTIVE";
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public UserSession(
            String sessionId,
            int userId,
            String username,
            Role role,
            LocalDateTime loginTime,
            LocalDateTime logoutTime,
            LocalDateTime lastActivityTime,
            String sessionStatus,
            String sessionEndReason,
            String ipAddress,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.username = username;
        this.role = role;
        this.loginTime = loginTime != null ? loginTime : LocalDateTime.now();
        this.logoutTime = logoutTime;
        this.lastActivityTime = lastActivityTime != null ? lastActivityTime : this.loginTime;
        this.sessionStatus = sessionStatus != null ? sessionStatus : "ACTIVE";
        this.sessionEndReason = sessionEndReason;
        this.ipAddress = ipAddress != null ? ipAddress : "127.0.0.1";
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
        this.updatedAt = updatedAt != null ? updatedAt : LocalDateTime.now();
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public LocalDateTime getLoginTime() {
        return loginTime;
    }

    public void setLoginTime(LocalDateTime loginTime) {
        this.loginTime = loginTime;
    }

    public LocalDateTime getLogoutTime() {
        return logoutTime;
    }

    public void setLogoutTime(LocalDateTime logoutTime) {
        this.logoutTime = logoutTime;
    }

    public LocalDateTime getLastActivityTime() {
        return lastActivityTime;
    }

    public void setLastActivityTime(LocalDateTime lastActivityTime) {
        this.lastActivityTime = lastActivityTime;
    }

    public String getSessionStatus() {
        return sessionStatus;
    }

    public void setSessionStatus(String sessionStatus) {
        this.sessionStatus = sessionStatus;
    }

    public String getSessionEndReason() {
        return sessionEndReason;
    }

    public void setSessionEndReason(String sessionEndReason) {
        this.sessionEndReason = sessionEndReason;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(this.sessionStatus);
    }

    /**
     * Calculates the session duration in a consistent, user-friendly format (e.g. "15 min", "2h 10m", "< 1 min").
     */
    public String getDurationFormatted() {
        LocalDateTime start = this.loginTime;
        LocalDateTime end = this.logoutTime != null ? this.logoutTime : (isActive() ? LocalDateTime.now() : this.lastActivityTime);
        if (start == null || end == null || end.isBefore(start)) {
            return "< 1 min";
        }
        Duration duration = Duration.between(start, end);
        long minutes = duration.toMinutes();
        if (minutes < 1) {
            return "< 1 min";
        } else if (minutes < 60) {
            return minutes + " min";
        } else {
            long hours = minutes / 60;
            long remMinutes = minutes % 60;
            return hours + "h " + remMinutes + "m";
        }
    }

    @Override
    public String toString() {
        return "UserSession{" +
                "sessionId='" + sessionId + '\'' +
                ", userId=" + userId +
                ", username='" + username + '\'' +
                ", role=" + role +
                ", loginTime=" + (loginTime != null ? loginTime.format(TS_FMT) : null) +
                ", logoutTime=" + (logoutTime != null ? logoutTime.format(TS_FMT) : null) +
                ", sessionStatus='" + sessionStatus + '\'' +
                ", ipAddress='" + ipAddress + '\'' +
                '}';
    }
}
