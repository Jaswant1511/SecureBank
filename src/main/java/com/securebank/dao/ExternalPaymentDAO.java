package com.securebank.dao;

import com.securebank.model.ExternalPaymentRecord;
import com.securebank.service.payment.PaymentStatus;
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
 * Data Access Object for external payment tracking and webhook callback deduplication.
 */
public class ExternalPaymentDAO {

    public int createPaymentRecord(ExternalPaymentRecord record) throws SQLException {
        final String sql =
            "INSERT INTO external_payments (client_reference, idempotency_key, account_number, amount, " +
            "destination_account, destination_bank, provider_name, provider_reference, status, is_simulated, " +
            "purpose, failure_reason, created_at, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, record.getClientReference());
            ps.setString(2, record.getIdempotencyKey());
            ps.setInt(3, record.getAccountNumber());
            ps.setBigDecimal(4, record.getAmount());
            ps.setString(5, record.getDestinationAccount());
            ps.setString(6, record.getDestinationBank());
            ps.setString(7, record.getProviderName());
            ps.setString(8, record.getProviderReference());
            ps.setString(9, record.getStatus().name());
            ps.setBoolean(10, record.isSimulated());
            ps.setString(11, record.getPurpose());
            ps.setString(12, record.getFailureReason());
            ps.setTimestamp(13, Timestamp.valueOf(record.getCreatedAt() != null ? record.getCreatedAt() : LocalDateTime.now()));
            ps.setTimestamp(14, Timestamp.valueOf(record.getUpdatedAt() != null ? record.getUpdatedAt() : LocalDateTime.now()));

            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    record.setPaymentId(id);
                    return id;
                }
            }
        }
        return record.getPaymentId();
    }

    public ExternalPaymentRecord findByIdempotencyKey(String idempotencyKey) throws SQLException {
        final String sql = "SELECT * FROM external_payments WHERE idempotency_key = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, idempotencyKey);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    public ExternalPaymentRecord findByClientReference(String clientReference) throws SQLException {
        final String sql = "SELECT * FROM external_payments WHERE client_reference = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, clientReference);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    public ExternalPaymentRecord findByProviderReference(String providerReference) throws SQLException {
        final String sql = "SELECT * FROM external_payments WHERE provider_reference = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, providerReference);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    public void updateStatus(int paymentId, PaymentStatus status, String providerRef, String failureReason) throws SQLException {
        final String sql =
            "UPDATE external_payments SET status = ?, provider_reference = COALESCE(?, provider_reference), " +
            "failure_reason = ?, updated_at = ? WHERE payment_id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setString(2, providerRef);
            ps.setString(3, failureReason);
            ps.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now()));
            ps.setInt(5, paymentId);
            ps.executeUpdate();
        }
    }

    public List<ExternalPaymentRecord> getPaymentsForAccount(int accountNumber, int limit) throws SQLException {
        final String sql = "SELECT * FROM external_payments WHERE account_number = ? ORDER BY payment_id DESC LIMIT ?";
        List<ExternalPaymentRecord> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, accountNumber);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    public List<ExternalPaymentRecord> getPendingPayments() throws SQLException {
        final String sql = "SELECT * FROM external_payments WHERE status = 'PENDING' ORDER BY payment_id ASC";
        List<ExternalPaymentRecord> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    public boolean isCallbackProcessed(String signature) throws SQLException {
        final String sql = "SELECT 1 FROM payment_callbacks WHERE signature = ? AND processed = TRUE";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, signature);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public void recordCallback(String providerRef, String clientRef, String signature,
                               String payload, String status, boolean isValidSignature, boolean processed) throws SQLException {
        final String sql =
            "INSERT INTO payment_callbacks (provider_reference, client_reference, signature, raw_payload, " +
            "status, is_valid_signature, processed, received_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, providerRef);
            ps.setString(2, clientRef);
            ps.setString(3, signature);
            ps.setString(4, payload);
            ps.setString(5, status);
            ps.setBoolean(6, isValidSignature);
            ps.setBoolean(7, processed);
            ps.setTimestamp(8, Timestamp.valueOf(LocalDateTime.now()));
            ps.executeUpdate();
        }
    }

    public void markCallbackProcessed(String signature) throws SQLException {
        final String sql = "UPDATE payment_callbacks SET processed = TRUE WHERE signature = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, signature);
            ps.executeUpdate();
        }
    }

    private ExternalPaymentRecord mapRow(ResultSet rs) throws SQLException {
        return new ExternalPaymentRecord(
            rs.getInt("payment_id"),
            rs.getString("client_reference"),
            rs.getString("idempotency_key"),
            rs.getInt("account_number"),
            rs.getBigDecimal("amount"),
            rs.getString("destination_account"),
            rs.getString("destination_bank"),
            rs.getString("provider_name"),
            rs.getString("provider_reference"),
            PaymentStatus.valueOf(rs.getString("status")),
            rs.getBoolean("is_simulated"),
            rs.getString("purpose"),
            rs.getString("failure_reason"),
            rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime() : null,
            rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toLocalDateTime() : null
        );
    }
}
