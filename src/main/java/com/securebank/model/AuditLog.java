package com.securebank.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * SecureBank: Role-Based Core Banking & Transaction Management System
 * File: AuditLog.java
 *
 * Represents an immutable administrative or security audit trail record.
 */
public class AuditLog {

    private int logId;
    private String actorUsername;
    private Role role;
    private String action;
    private String entityType;
    private String entityId;
    private String details;
    private String status; // SUCCESS, FAILURE, REJECTED
    private String ipAddress;
    private LocalDateTime timestamp;

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public AuditLog() {
        this.timestamp = LocalDateTime.now();
    }

    public AuditLog(
        int logId,
        String actorUsername,
        Role role,
        String action,
        String entityType,
        String entityId,
        String details,
        String status,
        String ipAddress,
        LocalDateTime timestamp
    ) {
        this.logId = logId;
        this.actorUsername = actorUsername;
        this.role = role;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.details = details;
        this.status = status;
        this.ipAddress = ipAddress;
        this.timestamp = timestamp != null ? timestamp : LocalDateTime.now();
    }

    public int getLogId() {
        return logId;
    }

    public void setLogId(int logId) {
        this.logId = logId;
    }

    public String getActorUsername() {
        return actorUsername;
    }

    public void setActorUsername(String actorUsername) {
        this.actorUsername = actorUsername;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getFormattedTimestamp() {
        return timestamp != null ? timestamp.format(TS_FMT) : "N/A";
    }
}
