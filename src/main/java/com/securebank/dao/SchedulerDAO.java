package com.securebank.dao;

import com.securebank.util.DatabaseConfig;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SecureBank: Complete Banking Automation System
 * File: SchedulerDAO.java
 *
 * Persistence operations for recording scheduled background job runs.
 */
public class SchedulerDAO {

    public long recordJobExecution(String jobName, String status, String details) throws SQLException {
        final String sql = "INSERT INTO scheduled_job_logs (job_name, executed_at, status, details) VALUES (?, ?, ?, ?)";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, jobName);
            ps.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
            ps.setString(3, status);
            ps.setString(4, details);

            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
            return 0;
        }
    }

    public List<Map<String, Object>> getRecentJobLogs(int limit) throws SQLException {
        final String sql = "SELECT job_id, job_name, executed_at, status, details FROM scheduled_job_logs ORDER BY executed_at DESC LIMIT ?";
        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("jobId", rs.getInt("job_id"));
                    map.put("jobName", rs.getString("job_name"));
                    map.put("executedAt", rs.getTimestamp("executed_at"));
                    map.put("status", rs.getString("status"));
                    map.put("details", rs.getString("details"));
                    list.add(map);
                }
            }
        }
        return list;
    }
}
