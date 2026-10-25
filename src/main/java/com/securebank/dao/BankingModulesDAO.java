package com.securebank.dao;

import com.securebank.model.Biller;
import com.securebank.model.BillPayment;
import com.securebank.model.DebitCard;
import com.securebank.model.FixedDeposit;
import com.securebank.model.Loan;
import com.securebank.util.DatabaseConfig;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Persistence layer for extended banking facilities:
 * 1. Billers and Utility Payments
 * 2. Fixed Deposit Portfolio
 * 3. Retail & Commercial Loan Facilities
 * 4. Debit Card Management
 */
public class BankingModulesDAO {

    // ==========================================
    // 1. BILLERS & BILL PAYMENTS
    // ==========================================

    public List<Biller> getAllBillers() throws SQLException {
        final String sql = "SELECT biller_id, biller_name, category, biller_code FROM billers ORDER BY category, biller_name";
        List<Biller> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new Biller(
                        rs.getInt("biller_id"),
                        rs.getString("biller_name"),
                        rs.getString("category"),
                        rs.getString("biller_code")
                ));
            }
        }
        return list;
    }

    public Biller getBillerById(int billerId) throws SQLException {
        final String sql = "SELECT biller_id, biller_name, category, biller_code FROM billers WHERE biller_id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, billerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Biller(
                            rs.getInt("biller_id"),
                            rs.getString("biller_name"),
                            rs.getString("category"),
                            rs.getString("biller_code")
                    );
                }
            }
        }
        return null;
    }

    public int recordBillPayment(BillPayment bp, Connection conn) throws SQLException {
        final String sql = "INSERT INTO bill_payments (payment_ref, customer_id, account_number, biller_id, consumer_number, amount, status, paid_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, bp.getPaymentRef());
            ps.setInt(2, bp.getCustomerId());
            ps.setInt(3, bp.getAccountNumber());
            ps.setInt(4, bp.getBillerId());
            ps.setString(5, bp.getConsumerNumber());
            ps.setBigDecimal(6, bp.getAmount());
            ps.setString(7, bp.getStatus());
            ps.setTimestamp(8, Timestamp.valueOf(bp.getPaidAt()));
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    bp.setPaymentId(id);
                    return id;
                }
            }
        }
        return 0;
    }

    public List<BillPayment> getBillPaymentsByCustomerId(int customerId) throws SQLException {
        final String sql = "SELECT bp.payment_id, bp.payment_ref, bp.customer_id, bp.account_number, bp.biller_id, "
                + "b.biller_name, bp.consumer_number, bp.amount, bp.status, bp.paid_at "
                + "FROM bill_payments bp "
                + "JOIN billers b ON bp.biller_id = b.biller_id "
                + "WHERE bp.customer_id = ? ORDER BY bp.paid_at DESC";
        List<BillPayment> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    BillPayment p = new BillPayment(
                            rs.getInt("payment_id"),
                            rs.getString("payment_ref"),
                            rs.getInt("customer_id"),
                            rs.getInt("account_number"),
                            rs.getInt("biller_id"),
                            rs.getString("consumer_number"),
                            rs.getBigDecimal("amount"),
                            rs.getString("status"),
                            rs.getTimestamp("paid_at").toLocalDateTime()
                    );
                    p.setBillerName(rs.getString("biller_name"));
                    list.add(p);
                }
            }
        }
        return list;
    }

    // ==========================================
    // 2. FIXED DEPOSITS
    // ==========================================

    public int createFixedDeposit(FixedDeposit fd, Connection conn) throws SQLException {
        final String sql = "INSERT INTO fixed_deposits (fd_ref, customer_id, account_number, principal_amount, tenure_months, interest_rate, maturity_amount, status, start_date, maturity_date) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, fd.getFdRef());
            ps.setInt(2, fd.getCustomerId());
            ps.setInt(3, fd.getAccountNumber());
            ps.setBigDecimal(4, fd.getPrincipalAmount());
            ps.setInt(5, fd.getTenureMonths());
            ps.setBigDecimal(6, fd.getInterestRate());
            ps.setBigDecimal(7, fd.getMaturityAmount());
            ps.setString(8, fd.getStatus());
            ps.setTimestamp(9, Timestamp.valueOf(fd.getStartDate()));
            ps.setTimestamp(10, Timestamp.valueOf(fd.getMaturityDate()));
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    fd.setFdId(id);
                    return id;
                }
            }
        }
        return 0;
    }

    public List<FixedDeposit> getFixedDepositsByCustomerId(int customerId) throws SQLException {
        final String sql = "SELECT fd_id, fd_ref, customer_id, account_number, principal_amount, tenure_months, interest_rate, maturity_amount, status, start_date, maturity_date "
                + "FROM fixed_deposits WHERE customer_id = ? ORDER BY fd_id DESC";
        List<FixedDeposit> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new FixedDeposit(
                            rs.getInt("fd_id"),
                            rs.getString("fd_ref"),
                            rs.getInt("customer_id"),
                            rs.getInt("account_number"),
                            rs.getBigDecimal("principal_amount"),
                            rs.getInt("tenure_months"),
                            rs.getBigDecimal("interest_rate"),
                            rs.getBigDecimal("maturity_amount"),
                            rs.getString("status"),
                            rs.getTimestamp("start_date").toLocalDateTime(),
                            rs.getTimestamp("maturity_date").toLocalDateTime()
                    ));
                }
            }
        }
        return list;
    }

    public FixedDeposit getFixedDepositById(int fdId) throws SQLException {
        final String sql = "SELECT fd_id, fd_ref, customer_id, account_number, principal_amount, tenure_months, interest_rate, maturity_amount, status, start_date, maturity_date "
                + "FROM fixed_deposits WHERE fd_id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, fdId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new FixedDeposit(
                            rs.getInt("fd_id"),
                            rs.getString("fd_ref"),
                            rs.getInt("customer_id"),
                            rs.getInt("account_number"),
                            rs.getBigDecimal("principal_amount"),
                            rs.getInt("tenure_months"),
                            rs.getBigDecimal("interest_rate"),
                            rs.getBigDecimal("maturity_amount"),
                            rs.getString("status"),
                            rs.getTimestamp("start_date").toLocalDateTime(),
                            rs.getTimestamp("maturity_date").toLocalDateTime()
                    );
                }
            }
        }
        return null;
    }

    public boolean updateFixedDepositStatus(int fdId, String status) throws SQLException {
        final String sql = "UPDATE fixed_deposits SET status = ? WHERE fd_id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, fdId);
            return ps.executeUpdate() > 0;
        }
    }

    // ==========================================
    // 3. LOAN MANAGEMENT
    // ==========================================

    public int applyLoan(Loan loan, Connection conn) throws SQLException {
        final String sql = "INSERT INTO loans (loan_ref, customer_id, loan_type, principal_amount, interest_rate, tenure_months, emi_amount, outstanding_balance, disbursed_account, status, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, loan.getLoanRef());
            ps.setInt(2, loan.getCustomerId());
            ps.setString(3, loan.getLoanType());
            ps.setBigDecimal(4, loan.getPrincipalAmount());
            ps.setBigDecimal(5, loan.getInterestRate());
            ps.setInt(6, loan.getTenureMonths());
            ps.setBigDecimal(7, loan.getEmiAmount());
            ps.setBigDecimal(8, loan.getOutstandingBalance());
            if (loan.getDisbursedAccount() != null) {
                ps.setInt(9, loan.getDisbursedAccount());
            } else {
                ps.setNull(9, java.sql.Types.INTEGER);
            }
            ps.setString(10, loan.getStatus());
            ps.setTimestamp(11, Timestamp.valueOf(loan.getCreatedAt()));
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    loan.setLoanId(id);
                    return id;
                }
            }
        }
        return 0;
    }

    public List<Loan> getLoansByCustomerId(int customerId) throws SQLException {
        final String sql = "SELECT loan_id, loan_ref, customer_id, loan_type, principal_amount, interest_rate, tenure_months, emi_amount, outstanding_balance, disbursed_account, status, created_at "
                + "FROM loans WHERE customer_id = ? ORDER BY loan_id DESC";
        List<Loan> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(extractLoan(rs));
                }
            }
        }
        return list;
    }

    public List<Loan> getAllLoans() throws SQLException {
        final String sql = "SELECT loan_id, loan_ref, customer_id, loan_type, principal_amount, interest_rate, tenure_months, emi_amount, outstanding_balance, disbursed_account, status, created_at "
                + "FROM loans ORDER BY loan_id DESC";
        List<Loan> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(extractLoan(rs));
            }
        }
        return list;
    }

    public Loan getLoanById(int loanId) throws SQLException {
        final String sql = "SELECT loan_id, loan_ref, customer_id, loan_type, principal_amount, interest_rate, tenure_months, emi_amount, outstanding_balance, disbursed_account, status, created_at "
                + "FROM loans WHERE loan_id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, loanId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return extractLoan(rs);
                }
            }
        }
        return null;
    }

    public boolean updateLoanStatus(int loanId, String status) throws SQLException {
        try (Connection conn = DatabaseConfig.getConnection()) {
            return updateLoanStatus(loanId, status, null, conn);
        }
    }

    public boolean updateLoanStatus(int loanId, String status, Connection conn) throws SQLException {
        return updateLoanStatus(loanId, status, null, conn);
    }

    public boolean updateLoanStatus(int loanId, String status, Integer disbursedAccount, Connection conn) throws SQLException {
        final String sql = "UPDATE loans SET status = ?, disbursed_account = ? WHERE loan_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            if (disbursedAccount != null) {
                ps.setInt(2, disbursedAccount);
            } else {
                ps.setNull(2, java.sql.Types.INTEGER);
            }
            ps.setInt(3, loanId);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean disburseLoan(int loanId, Integer disbursedAccount, Connection conn) throws SQLException {
        return updateLoanStatus(loanId, "ACTIVE", disbursedAccount, conn);
    }

    public boolean recordRepayment(int loanId, BigDecimal repaymentAmount, BigDecimal newBalance, Connection conn) throws SQLException {
        final String sql = "UPDATE loans SET outstanding_balance = ? WHERE loan_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBigDecimal(1, newBalance);
            ps.setInt(2, loanId);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean recordLoanRepayment(int loanId, BigDecimal repaymentAmount, Connection conn) throws SQLException {
        final String sql = "UPDATE loans SET outstanding_balance = GREATEST(0, outstanding_balance - ?) WHERE loan_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBigDecimal(1, repaymentAmount);
            ps.setInt(2, loanId);
            return ps.executeUpdate() > 0;
        }
    }

    private Loan extractLoan(ResultSet rs) throws SQLException {
        Integer disbursedAcc = rs.getInt("disbursed_account");
        if (rs.wasNull()) {
            disbursedAcc = null;
        }
        return new Loan(
                rs.getInt("loan_id"),
                rs.getString("loan_ref"),
                rs.getInt("customer_id"),
                rs.getString("loan_type"),
                rs.getBigDecimal("principal_amount"),
                rs.getBigDecimal("interest_rate"),
                rs.getInt("tenure_months"),
                rs.getBigDecimal("emi_amount"),
                rs.getBigDecimal("outstanding_balance"),
                disbursedAcc,
                rs.getString("status"),
                rs.getTimestamp("created_at").toLocalDateTime()
        );
    }

    // ==========================================
    // 4. DEBIT CARDS
    // ==========================================

    public DebitCard getCardByAccountNumber(int accountNumber) throws SQLException {
        final String sql = "SELECT card_id, account_number, customer_id, card_number_masked, card_type, daily_limit, is_blocked, contactless_enabled, expiry_date "
                + "FROM cards WHERE account_number = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, accountNumber);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new DebitCard(
                            rs.getInt("card_id"),
                            rs.getInt("account_number"),
                            rs.getInt("customer_id"),
                            rs.getString("card_number_masked"),
                            rs.getString("card_type"),
                            rs.getBigDecimal("daily_limit"),
                            rs.getBoolean("is_blocked"),
                            rs.getBoolean("contactless_enabled"),
                            rs.getString("expiry_date")
                    );
                }
            }
        }
        return null;
    }

    public List<DebitCard> getCardsByCustomerId(int customerId) throws SQLException {
        final String sql = "SELECT card_id, account_number, customer_id, card_number_masked, card_type, daily_limit, is_blocked, contactless_enabled, expiry_date "
                + "FROM cards WHERE customer_id = ?";
        List<DebitCard> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new DebitCard(
                            rs.getInt("card_id"),
                            rs.getInt("account_number"),
                            rs.getInt("customer_id"),
                            rs.getString("card_number_masked"),
                            rs.getString("card_type"),
                            rs.getBigDecimal("daily_limit"),
                            rs.getBoolean("is_blocked"),
                            rs.getBoolean("contactless_enabled"),
                            rs.getString("expiry_date")
                    ));
                }
            }
        }
        return list;
    }

    public int createCard(DebitCard card) throws SQLException {
        final String sql = "INSERT INTO cards (account_number, customer_id, card_number_masked, card_type, daily_limit, is_blocked, contactless_enabled, expiry_date) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, card.getAccountNumber());
            ps.setInt(2, card.getCustomerId());
            ps.setString(3, card.getCardNumberMasked());
            ps.setString(4, card.getCardType());
            ps.setBigDecimal(5, card.getDailyLimit());
            ps.setBoolean(6, card.isBlocked());
            ps.setBoolean(7, card.isContactlessEnabled());
            ps.setString(8, card.getExpiryDate());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    card.setCardId(id);
                    return id;
                }
            }
        }
        return 0;
    }

    public boolean updateCardStatus(int cardId, boolean isBlocked) throws SQLException {
        final String sql = "UPDATE cards SET is_blocked = ? WHERE card_id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, isBlocked);
            ps.setInt(2, cardId);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean updateCardDailyLimit(int cardId, BigDecimal newLimit) throws SQLException {
        final String sql = "UPDATE cards SET daily_limit = ? WHERE card_id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBigDecimal(1, newLimit);
            ps.setInt(2, cardId);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean updateCardContactless(int cardId, boolean enabled) throws SQLException {
        final String sql = "UPDATE cards SET contactless_enabled = ? WHERE card_id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, enabled);
            ps.setInt(2, cardId);
            return ps.executeUpdate() > 0;
        }
    }
}
