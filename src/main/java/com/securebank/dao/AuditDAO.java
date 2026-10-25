package com.securebank.dao;

import com.securebank.model.AuditLog;
import com.securebank.model.Role;
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
 * File: AuditDAO.java
 *
 * Persistence operations for Administrative and Security Audit Trails.
 */
public class AuditDAO {

    public int recordAuditLog(AuditLog log) throws SQLException {
        final String sql = "INSERT INTO audit_logs "
            + "(actor_username, role, action, entity_type, entity_id, details, status, ip_address, log_timestamp) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, log.getActorUsername());
            ps.setString(2, log.getRole() != null ? log.getRole().name() : null);
            ps.setString(3, log.getAction());
            ps.setString(4, log.getEntityType());
            ps.setString(5, log.getEntityId());
            ps.setString(6, log.getDetails());
            ps.setString(7, log.getStatus());
            ps.setString(8, log.getIpAddress());
            ps.setTimestamp(9, Timestamp.valueOf(log.getTimestamp() != null ? log.getTimestamp() : LocalDateTime.now()));

            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    log.setLogId(id);
                    return id;
                }
            }
            return 0;
        }
    }

    public List<AuditLog> getRecentAuditLogs(int limit) throws SQLException {
        final String sql = "SELECT log_id, actor_username, role, action, entity_type, entity_id, details, status, ip_address, log_timestamp "
            + "FROM audit_logs ORDER BY log_timestamp DESC, log_id DESC LIMIT ?";
        List<AuditLog> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToAuditLog(rs));
                }
            }
        }
        return list;
    }

    private AuditLog mapRowToAuditLog(ResultSet rs) throws SQLException {
        AuditLog al = new AuditLog();
        al.setLogId(rs.getInt("log_id"));
        al.setActorUsername(rs.getString("actor_username"));
        String roleStr = rs.getString("role");
        if (roleStr != null) {
            al.setRole(Role.fromString(roleStr));
        }
        al.setAction(rs.getString("action"));
        al.setEntityType(rs.getString("entity_type"));
        al.setEntityId(rs.getString("entity_id"));
        al.setDetails(rs.getString("details"));
        al.setStatus(rs.getString("status"));
        al.setIpAddress(rs.getString("ip_address"));
        Timestamp ts = rs.getTimestamp("log_timestamp");
        if (ts != null) {
            al.setTimestamp(ts.toLocalDateTime());
        }
        return al;
    }
}
