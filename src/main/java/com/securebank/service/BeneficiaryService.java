package com.securebank.service;

import com.securebank.dao.AuditDAO;
import com.securebank.dao.BankDAO;
import com.securebank.dao.BeneficiaryDAO;
import com.securebank.dao.CustomerDAO;
import com.securebank.exception.AccountNotFoundException;
import com.securebank.exception.AuthorizationException;
import com.securebank.model.Account;
import com.securebank.model.AuditLog;
import com.securebank.model.Beneficiary;
import com.securebank.model.Customer;
import com.securebank.model.Role;
import com.securebank.model.User;
import com.securebank.util.ValidationUtil;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * SecureBank: Role-Based Core Banking & Transaction Management System
 * File: BeneficiaryService.java
 *
 * Manages verified customer payment counterparties and transfer limits with customer ownership enforcement.
 */
public class BeneficiaryService {

    private final BeneficiaryDAO beneficiaryDAO;
    private final BankDAO bankDAO;
    private final CustomerDAO customerDAO;
    private final AuditDAO auditDAO;

    public BeneficiaryService() {
        this.beneficiaryDAO = new BeneficiaryDAO();
        this.bankDAO = new BankDAO();
        this.customerDAO = new CustomerDAO();
        this.auditDAO = new AuditDAO();
    }

    public BeneficiaryService(BeneficiaryDAO beneficiaryDAO, BankDAO bankDAO, CustomerDAO customerDAO, AuditDAO auditDAO) {
        this.beneficiaryDAO = beneficiaryDAO != null ? beneficiaryDAO : new BeneficiaryDAO();
        this.bankDAO = bankDAO != null ? bankDAO : new BankDAO();
        this.customerDAO = customerDAO != null ? customerDAO : new CustomerDAO();
        this.auditDAO = auditDAO != null ? auditDAO : new AuditDAO();
    }

    /**
     * Registers a new verified beneficiary for an authenticated customer.
     */
    public Beneficiary addBeneficiary(
            int customerId,
            int beneficiaryAccount,
            String name,
            String bankName,
            String ifsc,
            BigDecimal limit,
            User actor
    ) throws SQLException, AuthorizationException, AccountNotFoundException {
        if (actor == null) {
            throw new AuthorizationException("Unauthenticated request. Please log in.");
        }
        verifyCustomerOwnership(customerId, actor);
        ValidationUtil.validateAccountNumber(beneficiaryAccount);

        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Beneficiary name is required.");
        }
        if (limit == null || limit.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transfer limit must be strictly positive.");
        }

        // Prevent self-beneficiary registration
        Account destAccount = bankDAO.getAccountByNumber(beneficiaryAccount);
        if (destAccount != null && destAccount.getCustomerId() == customerId) {
            throw new IllegalArgumentException("Cannot add your own account as a beneficiary.");
        }

        String effectiveBank = (bankName != null && !bankName.trim().isEmpty()) ? bankName.trim() : "SecureBank Retail";
        String effectiveIfsc = (ifsc != null && !ifsc.trim().isEmpty()) ? ifsc.trim().toUpperCase() : "SECU0001001";

        Beneficiary b = new Beneficiary(
                0,
                customerId,
                beneficiaryAccount,
                name.trim(),
                effectiveBank,
                effectiveIfsc,
                limit,
                "ACTIVE",
                LocalDateTime.now()
        );

        int id = beneficiaryDAO.addBeneficiary(b);
        b.setBeneficiaryId(id);

        recordAudit(
                actor.getUsername(),
                actor.getRole(),
                "ADD_BENEFICIARY",
                "CUSTOMER",
                String.valueOf(customerId),
                String.format("Added beneficiary %s for Account #%d (Limit: ₹%s)", name.trim(), beneficiaryAccount, limit.toPlainString()),
                "SUCCESS"
        );

        return b;
    }

    /**
     * Lists active beneficiaries for the authenticated customer.
     */
    public List<Beneficiary> getBeneficiaries(int customerId, User actor) throws SQLException, AuthorizationException {
        if (actor == null) {
            throw new AuthorizationException("Unauthenticated request.");
        }
        verifyCustomerOwnership(customerId, actor);
        return beneficiaryDAO.getBeneficiariesByCustomerId(customerId);
    }

    /**
     * Deactivates a beneficiary with customer ownership enforcement.
     */
    public boolean deleteBeneficiary(int beneficiaryId, int customerId, User actor) throws SQLException, AuthorizationException {
        if (actor == null) {
            throw new AuthorizationException("Unauthenticated request.");
        }
        verifyCustomerOwnership(customerId, actor);
        boolean success = beneficiaryDAO.deleteBeneficiary(beneficiaryId, customerId);
        if (success) {
            recordAudit(
                    actor.getUsername(),
                    actor.getRole(),
                    "DELETE_BENEFICIARY",
                    "BENEFICIARY",
                    String.valueOf(beneficiaryId),
                    "Deactivated beneficiary #" + beneficiaryId + " for customer #" + customerId,
                    "SUCCESS"
            );
        }
        return success;
    }

    private void verifyCustomerOwnership(int customerId, User actor) throws AuthorizationException, SQLException {
        if (actor.getRole() == Role.CUSTOMER) {
            Customer cust = customerDAO.getCustomerByUserId(actor.getUserId());
            if (cust == null || cust.getCustomerId() != customerId) {
                throw new AuthorizationException("Access Denied: You cannot access or modify another customer's beneficiaries.");
            }
        }
    }

    private void recordAudit(String actor, Role role, String action, String type, String id, String details, String status) {
        try {
            AuditLog al = new AuditLog(0, actor, role, action, type, id, details, status, "LOCAL", LocalDateTime.now());
            auditDAO.recordAuditLog(al);
        } catch (Exception ignored) {
        }
    }
}
