package com.securebank.dao;

import com.securebank.model.Budget;
import com.securebank.util.DatabaseConfig;
import java.math.BigDecimal;
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
 * Persistence layer for customer spending budgets.
 */
public class BudgetDAO {

    public int setBudget(int customerId, String category, BigDecimal limit) throws SQLException {
        // Upsert logic compatible with H2 and MySQL: update if exists, insert otherwise
        final String selectSql = "SELECT budget_id FROM budgets WHERE customer_id = ? AND category = ?";
        try (Connection conn = DatabaseConfig.getConnection()) {
            Integer existingId = null;
            try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                ps.setInt(1, customerId);
                ps.setString(2, category);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        existingId = rs.getInt("budget_id");
                    }
                }
            }

            if (existingId != null) {
                final String updateSql = "UPDATE budgets SET monthly_limit = ? WHERE budget_id = ?";
                try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                    ps.setBigDecimal(1, limit);
                    ps.setInt(2, existingId);
                    ps.executeUpdate();
                    return existingId;
                }
            } else {
                final String insertSql = "INSERT INTO budgets (customer_id, category, monthly_limit, created_at) VALUES (?, ?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, customerId);
                    ps.setString(2, category);
                    ps.setBigDecimal(3, limit);
                    ps.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now()));
                    ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (rs.next()) {
                            return rs.getInt(1);
                        }
                    }
                }
            }
        }
        return 0;
    }

    public List<Budget> getBudgetsByCustomerId(int customerId) throws SQLException {
        final String sql = "SELECT budget_id, customer_id, category, monthly_limit, created_at FROM budgets WHERE customer_id = ? ORDER BY budget_id ASC";
        List<Budget> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Budget(
                            rs.getInt("budget_id"),
                            rs.getInt("customer_id"),
                            rs.getString("category"),
                            rs.getBigDecimal("monthly_limit"),
                            rs.getTimestamp("created_at").toLocalDateTime()
                    ));
                }
            }
        }
        return list;
    }

    public boolean deleteBudget(int budgetId, int customerId) throws SQLException {
        final String sql = "DELETE FROM budgets WHERE budget_id = ? AND customer_id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, budgetId);
            ps.setInt(2, customerId);
            return ps.executeUpdate() > 0;
        }
    }
}
