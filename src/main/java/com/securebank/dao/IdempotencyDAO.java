package com.securebank.dao;

import com.securebank.util.DatabaseConfig;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * Persistence layer for API and Transaction Idempotency Keys.
 * Guarantees that duplicate requests with the same key execute once and return the cached receipt.
 */
public class IdempotencyDAO {

    public static class IdempotencyRecord {
        private final String key;
        private final String operationType;
        private final String payloadHash;
        private final String responseJson;
        private final String status;
        private final LocalDateTime createdAt;

        public IdempotencyRecord(String key, String operationType, String payloadHash, String responseJson, String status, LocalDateTime createdAt) {
            this.key = key;
            this.operationType = operationType;
            this.payloadHash = payloadHash;
            this.responseJson = responseJson;
            this.status = status;
            this.createdAt = createdAt;
        }

        public String getKey() {
            return key;
        }

        public String getOperationType() {
            return operationType;
        }

        public String getPayloadHash() {
            return payloadHash;
        }

        public String getResponseJson() {
            return responseJson;
        }

        public String getStatus() {
            return status;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }
    }

    public IdempotencyRecord findRecord(String key) throws SQLException {
        if (key == null || key.trim().isEmpty()) {
            return null;
        }
        final String sql = "SELECT idempotency_key, operation_type, request_payload_hash, response_json, status, created_at "
                + "FROM idempotency_keys WHERE idempotency_key = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new IdempotencyRecord(
                            rs.getString("idempotency_key"),
                            rs.getString("operation_type"),
                            rs.getString("request_payload_hash"),
                            rs.getString("response_json"),
                            rs.getString("status"),
                            rs.getTimestamp("created_at").toLocalDateTime()
                    );
                }
            }
        }
        return null;
    }

    public boolean saveRecord(String key, String opType, String payloadHash, String responseJson) throws SQLException {
        if (key == null || key.trim().isEmpty()) {
            return false;
        }
        final String sql = "INSERT INTO idempotency_keys (idempotency_key, operation_type, request_payload_hash, response_json, status, created_at) "
                + "VALUES (?, ?, ?, ?, 'COMPLETED', ?)";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key.trim());
            ps.setString(2, opType);
            ps.setString(3, payloadHash != null ? payloadHash : "");
            ps.setString(4, responseJson);
            ps.setTimestamp(5, Timestamp.valueOf(LocalDateTime.now()));
            return ps.executeUpdate() > 0;
        }
    }
}
