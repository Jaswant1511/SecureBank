package com.securebank.dao;

import com.securebank.model.Customer;
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
 * File: CustomerDAO.java
 *
 * Persistence operations for Customer Profiles.
 */
public class CustomerDAO {

    public int createCustomer(Customer customer, Connection conn) throws SQLException {
        final String sql = "INSERT INTO customers (user_id, full_name, email, phone, address, created_at) "
            + "VALUES (?, ?, ?, ?, ?, ?)";
        boolean isLocalConn = false;
        if (conn == null) {
            conn = DatabaseConfig.getConnection();
            isLocalConn = true;
        }
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, customer.getUserId());
            ps.setString(2, customer.getFullName());
            ps.setString(3, customer.getEmail());
            ps.setString(4, customer.getPhone());
            ps.setString(5, customer.getAddress());
            ps.setTimestamp(6, Timestamp.valueOf(customer.getCreatedAt() != null ? customer.getCreatedAt() : LocalDateTime.now()));

            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    customer.setCustomerId(id);
                    return id;
                }
            }
            throw new SQLException("Creating customer failed, no ID obtained.");
        } finally {
            if (isLocalConn && conn != null) {
                conn.close();
            }
        }
    }

    public Customer getCustomerById(int customerId) throws SQLException {
        final String sql = "SELECT customer_id, user_id, full_name, email, phone, address, created_at "
            + "FROM customers WHERE customer_id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRowToCustomer(rs);
                }
                return null;
            }
        }
    }

    public Customer getCustomerByUserId(int userId) throws SQLException {
        final String sql = "SELECT customer_id, user_id, full_name, email, phone, address, created_at "
            + "FROM customers WHERE user_id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRowToCustomer(rs);
                }
                return null;
            }
        }
    }

    public Customer getCustomerByEmail(String email) throws SQLException {
        if (email == null || email.trim().isEmpty()) {
            return null;
        }
        final String sql = "SELECT customer_id, user_id, full_name, email, phone, address, created_at "
            + "FROM customers WHERE LOWER(email) = LOWER(?)";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRowToCustomer(rs);
                }
                return null;
            }
        }
    }

    public void updateCustomer(Customer customer) throws SQLException {
        final String sql = "UPDATE customers SET full_name = ?, email = ?, phone = ?, address = ? WHERE customer_id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, customer.getFullName());
            ps.setString(2, customer.getEmail());
            ps.setString(3, customer.getPhone());
            ps.setString(4, customer.getAddress());
            ps.setInt(5, customer.getCustomerId());
            ps.executeUpdate();
        }
    }

    public List<Customer> searchCustomers(String query) throws SQLException {
        final String sql = "SELECT customer_id, user_id, full_name, email, phone, address, created_at "
            + "FROM customers WHERE LOWER(full_name) LIKE ? OR email LIKE ? OR phone LIKE ? ORDER BY customer_id ASC";
        List<Customer> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            String param = "%" + (query != null ? query.trim().toLowerCase() : "") + "%";
            ps.setString(1, param);
            ps.setString(2, param);
            ps.setString(3, param);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToCustomer(rs));
                }
            }
        }
        return list;
    }

    private Customer mapRowToCustomer(ResultSet rs) throws SQLException {
        Customer c = new Customer();
        c.setCustomerId(rs.getInt("customer_id"));
        c.setUserId(rs.getInt("user_id"));
        c.setFullName(rs.getString("full_name"));
        c.setEmail(rs.getString("email"));
        c.setPhone(rs.getString("phone"));
        c.setAddress(rs.getString("address"));
        Timestamp cat = rs.getTimestamp("created_at");
        if (cat != null) {
            c.setCreatedAt(cat.toLocalDateTime());
        }
        return c;
    }
}
