package com.securebank.service;

import com.securebank.dao.AuditDAO;
import com.securebank.exception.AuthorizationException;
import com.securebank.model.AuditLog;
import com.securebank.model.Role;
import com.securebank.model.User;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * SecureBank: Role-Based Core Banking & Transaction Management System
 * File: AuditService.java
 *
 * Centralized Audit Log Service for security compliance, operational oversight,
 * and fraud investigation.
 */
public class AuditService {

    private final AuditDAO auditDAO;

    public AuditService() {
        this.auditDAO = new AuditDAO();
    }

    public AuditService(AuditDAO auditDAO) {
        this.auditDAO = auditDAO;
    }

    public void logEvent(
        String actorUsername,
        Role role,
        String action,
        String entityType,
        String entityId,
        String details,
        String status,
        String ipAddress
    ) {
        try {
            AuditLog log = new AuditLog(0, actorUsername, role, action, entityType, entityId, details, status, ipAddress, LocalDateTime.now());
            auditDAO.recordAuditLog(log);
        } catch (SQLException ignored) {
            // Audit logging should never bring down the primary financial workflow
        }
    }

    public List<AuditLog> getRecentAuditLogs(int limit, User adminUser) throws SQLException, AuthorizationException {
        AuthService.enforceRole(adminUser, Role.ADMIN);
        return auditDAO.getRecentAuditLogs(limit);
    }
}
