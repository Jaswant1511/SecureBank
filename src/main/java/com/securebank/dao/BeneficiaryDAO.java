package com.securebank.dao;

import com.securebank.model.Beneficiary;
import com.securebank.util.DatabaseConfig;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Persistence layer for customer beneficiary records.
 */
public class BeneficiaryDAO {

    public int addBeneficiary(Beneficiary b) throws SQLException {
        final String sql = "INSERT INTO beneficiaries (customer_id, beneficiary_account, beneficiary_name, bank_name, ifsc_code, transfer_limit, status, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, b.getCustomerId());
            ps.setInt(2, b.getBeneficiaryAccount());
            ps.setString(3, b.getBeneficiaryName());
            ps.setString(4, b.getBankName());
            ps.setString(5, b.getIfscCode());
            ps.setBigDecimal(6, b.getTransferLimit());
            ps.setString(7, b.getStatus());
            ps.setTimestamp(8, Timestamp.valueOf(b.getCreatedAt()));
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    b.setBeneficiaryId(id);
                    return id;
                }
            }
        }
        return 0;
    }

    public List<Beneficiary> getBeneficiariesByCustomerId(int customerId) throws SQLException {
        final String sql = "SELECT beneficiary_id, customer_id, beneficiary_account, beneficiary_name, bank_name, ifsc_code, transfer_limit, status, created_at "
                + "FROM beneficiaries WHERE customer_id = ? AND status = 'ACTIVE' ORDER BY beneficiary_id DESC";
        List<Beneficiary> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Beneficiary(
                            rs.getInt("beneficiary_id"),
                            rs.getInt("customer_id"),
                            rs.getInt("beneficiary_account"),
                            rs.getString("beneficiary_name"),
                            rs.getString("bank_name"),
                            rs.getString("ifsc_code"),
                            rs.getBigDecimal("transfer_limit"),
                            rs.getString("status"),
                            rs.getTimestamp("created_at").toLocalDateTime()
                    ));
                }
            }
        }
        return list;
    }

    public Beneficiary getBeneficiaryById(int beneficiaryId) throws SQLException {
        final String sql = "SELECT beneficiary_id, customer_id, beneficiary_account, beneficiary_name, bank_name, ifsc_code, transfer_limit, status, created_at "
                + "FROM beneficiaries WHERE beneficiary_id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, beneficiaryId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Beneficiary(
                            rs.getInt("beneficiary_id"),
                            rs.getInt("customer_id"),
                            rs.getInt("beneficiary_account"),
                            rs.getString("beneficiary_name"),
                            rs.getString("bank_name"),
                            rs.getString("ifsc_code"),
                            rs.getBigDecimal("transfer_limit"),
                            rs.getString("status"),
                            rs.getTimestamp("created_at").toLocalDateTime()
                    );
                }
            }
        }
        return null;
    }

    public boolean deleteBeneficiary(int beneficiaryId, int customerId) throws SQLException {
        final String sql = "UPDATE beneficiaries SET status = 'INACTIVE' WHERE beneficiary_id = ? AND customer_id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, beneficiaryId);
            ps.setInt(2, customerId);
            return ps.executeUpdate() > 0;
        }
    }
}
