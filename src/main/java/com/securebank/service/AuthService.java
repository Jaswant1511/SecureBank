package com.securebank.service;

import com.securebank.dao.AuditDAO;
import com.securebank.dao.BankDAO;
import com.securebank.dao.CustomerDAO;
import com.securebank.dao.UserDAO;
import com.securebank.exception.AuthenticationException;
import com.securebank.exception.AuthorizationException;
import com.securebank.model.Account;
import com.securebank.model.AccountStatus;
import com.securebank.model.AuditLog;
import com.securebank.model.CurrentAccount;
import com.securebank.model.Customer;
import com.securebank.model.Role;
import com.securebank.model.SavingsAccount;
import com.securebank.model.User;
import com.securebank.util.DatabaseConfig;
import com.securebank.util.PasswordUtil;
import com.securebank.util.ValidationUtil;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;

/**
 * SecureBank: Role-Based Core Banking & Transaction Management System
 * File: AuthService.java
 *
 * Security Service managing Authentication, Password Hashing, Session State,
 * Brute-Force Throttling / Lockout (3 attempts), Role-Based Authorization,
 * Customer Registration, and Credential Management.
 */
public class AuthService {

    private final UserDAO userDAO;
    private final CustomerDAO customerDAO;
    private final BankDAO bankDAO;
    private final AuditDAO auditDAO;
    private final NotificationService notificationService;
    private final SuspiciousActivityService suspiciousActivityService;

    public static final int MAX_FAILED_LOGINS = 3;

    public AuthService() {
        this.userDAO = new UserDAO();
        this.customerDAO = new CustomerDAO();
        this.bankDAO = new BankDAO();
        this.auditDAO = new AuditDAO();
        this.notificationService = new NotificationService();
        this.suspiciousActivityService = new SuspiciousActivityService();
    }

    public AuthService(UserDAO userDAO, AuditDAO auditDAO) {
        this.userDAO = userDAO;
        this.customerDAO = new CustomerDAO();
        this.bankDAO = new BankDAO();
        this.auditDAO = auditDAO;
        this.notificationService = new NotificationService();
        this.suspiciousActivityService = new SuspiciousActivityService();
    }

    public AuthService(UserDAO userDAO, CustomerDAO customerDAO, BankDAO bankDAO, AuditDAO auditDAO,
                       NotificationService notificationService, SuspiciousActivityService suspiciousActivityService) {
        this.userDAO = userDAO;
        this.customerDAO = customerDAO;
        this.bankDAO = bankDAO;
        this.auditDAO = auditDAO;
        this.notificationService = notificationService != null ? notificationService : new NotificationService();
        this.suspiciousActivityService = suspiciousActivityService != null ? suspiciousActivityService : new SuspiciousActivityService();
    }

    /**
     * Authenticates a user against secure credentials with brute-force lockout protection.
     * Supports either username or email as the login identifier.
     *
     * @param identifier    Login username or email
     * @param plainPassword Raw password
     * @param ipAddress     Client IP or interface identifier
     * @return Authenticated User object
     * @throws AuthenticationException on invalid credentials, locked accounts, or inactive state
     */
    public User login(String identifier, String plainPassword, String ipAddress) throws AuthenticationException {
        if (identifier == null || identifier.trim().isEmpty()) {
            throw new AuthenticationException("Username or email is required.");
        }
        String trimmed = identifier.trim();
        if (trimmed.length() < 2) {
            throw new AuthenticationException("Username or identifier is too short.");
        }

        if (plainPassword == null || plainPassword.isEmpty()) {
            throw new AuthenticationException("Password is required.");
        }

        try {
            User user = userDAO.getUserByEmailOrUsername(trimmed);
            if (user == null) {
                recordAudit("ANONYMOUS", null, "LOGIN_FAILED", "USER", trimmed, "User not found", "FAILURE", ipAddress);
                throw new AuthenticationException("Invalid username or password.");
            }

            if (!user.isActive()) {
                recordAudit(user.getUsername(), user.getRole(), "LOGIN_REJECTED", "USER", String.valueOf(user.getUserId()), "User account inactive/deactivated", "REJECTED", ipAddress);
                throw new AuthenticationException("Account is deactivated. Please contact your bank administrator.");
            }

            if (user.isLocked()) {
                recordAudit(user.getUsername(), user.getRole(), "LOGIN_REJECTED", "USER", String.valueOf(user.getUserId()), "Locked user attempted login", "REJECTED", ipAddress);
                throw new AuthenticationException("Account is LOCKED due to 3 consecutive failed login attempts. Contact bank administrator.");
            }

            if (PasswordUtil.checkPassword(plainPassword, user.getPasswordHash())) {
                // Successful Login: Reset counters and record timestamp
                user.setFailedLogins(0);
                user.setLastLogin(LocalDateTime.now());
                userDAO.updateLoginSecurity(user.getUserId(), 0, false, user.getLastLogin());

                recordAudit(user.getUsername(), user.getRole(), "LOGIN_SUCCESS", "USER", String.valueOf(user.getUserId()), "Successful authentication", "SUCCESS", ipAddress);
                return user;
            } else {
                // Failed Attempt: Increment counter and lock if threshold reached
                int attempts = user.getFailedLogins() + 1;
                boolean lock = attempts >= MAX_FAILED_LOGINS;
                user.setFailedLogins(attempts);
                user.setLocked(lock);
                userDAO.updateLoginSecurity(user.getUserId(), attempts, lock, null);

                if (lock) {
                    recordAudit(user.getUsername(), user.getRole(), "ACCOUNT_LOCKED", "USER", String.valueOf(user.getUserId()), "Account locked after 3 failed password attempts", "FAILURE", ipAddress);
                    suspiciousActivityService.checkFailedLogin(user.getUserId(), user.getUsername(), attempts);
                    notificationService.notifyUser(user.getUserId(), "Security Alert: Account Locked", "Your account has been locked after 3 consecutive failed password attempts. Contact customer support.", "ALERT");
                    throw new AuthenticationException("Invalid credentials. 3 consecutive failed attempts! Account is now LOCKED.");
                } else {
                    recordAudit(user.getUsername(), user.getRole(), "LOGIN_FAILED", "USER", String.valueOf(user.getUserId()), "Wrong password attempt (" + attempts + "/" + MAX_FAILED_LOGINS + ")", "FAILURE", ipAddress);
                    throw new AuthenticationException("Invalid username or password. Attempt " + attempts + " of " + MAX_FAILED_LOGINS + " used.");
                }
            }
        } catch (SQLException e) {
            throw new AuthenticationException("Authentication service failure: " + e.getMessage(), e);
        }
    }

    /**
     * Changes a user's password with strict complexity verification and session audit.
     */
    public void changePassword(int userId, String oldPassword, String newPassword, String confirmPassword, String ipAddress)
            throws SQLException, AuthenticationException {
        if (oldPassword == null || oldPassword.isEmpty()) {
            throw new IllegalArgumentException("Current password is required.");
        }
        if (newPassword == null || newPassword.isEmpty()) {
            throw new IllegalArgumentException("New password is required.");
        }
        if (!newPassword.equals(confirmPassword)) {
            throw new IllegalArgumentException("New password and confirmation password do not match.");
        }
        ValidationUtil.validateStrongPassword(newPassword);

        User user = userDAO.getUserById(userId);
        if (user == null) {
            throw new AuthenticationException("User account not found.");
        }
        if (!user.isActive()) {
            throw new AuthenticationException("Account is deactivated.");
        }
        if (user.isLocked()) {
            throw new AuthenticationException("Account is locked. Cannot change password.");
        }

        if (!PasswordUtil.checkPassword(oldPassword, user.getPasswordHash())) {
            recordAudit(user.getUsername(), user.getRole(), "PASSWORD_CHANGE_FAILED", "USER", String.valueOf(userId),
                    "Incorrect current password supplied", "FAILURE", ipAddress);
            throw new AuthenticationException("Current password verification failed.");
        }

        if (PasswordUtil.checkPassword(newPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("New password must be different from your current password.");
        }

        String newHash = PasswordUtil.hashPassword(newPassword);
        userDAO.updatePasswordHash(userId, newHash);

        recordAudit(user.getUsername(), user.getRole(), "PASSWORD_CHANGE_SUCCESS", "USER", String.valueOf(userId),
                "User changed account password successfully", "SUCCESS", ipAddress);
        notificationService.notifyUser(userId, "Password Changed",
                "Your SecureBank password was successfully updated. If you did not make this change, please contact fraud support immediately.", "INFO");
    }

    /**
     * Registration Result encapsulating created User, Customer, and Primary Account records.
     */
    public static class RegistrationResult {
        private final User user;
        private final Customer customer;
        private final Account account;

        public RegistrationResult(User user, Customer customer, Account account) {
            this.user = user;
            this.customer = customer;
            this.account = account;
        }

        public User getUser() { return user; }
        public Customer getCustomer() { return customer; }
        public Account getAccount() { return account; }
    }

    /**
     * Executes atomic end-to-end customer registration:
     * 1. Validates inputs, formats, and strong password constraints.
     * 2. Enforces username and email uniqueness across the database.
     * 3. Creates User record with BCrypt hash (factor 12) and fixed Role.CUSTOMER.
     * 4. Creates Customer profile linked to User ID.
     * 5. Opens primary Account with initial deposit and ledger entry.
     * 6. Commits all records in a single database transaction or rolls back completely on any failure.
     */
    public RegistrationResult registerCustomer(
            String fullName,
            String email,
            String username,
            String phone,
            String plainPassword,
            String confirmPassword,
            String plainPin,
            String accountType,
            BigDecimal initialDeposit,
            String ipAddress
    ) throws SQLException {
        ValidationUtil.validateHolderName(fullName);
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Email address is required.");
        }
        if (username != null && !username.trim().isEmpty()) {
            username = username.trim().replaceAll("\\s+", "_");
        } else if (email != null && email.contains("@")) {
            username = email.substring(0, email.indexOf('@')).replaceAll("[^A-Za-z0-9_.-]", "_");
        } else if (fullName != null) {
            username = fullName.trim().toLowerCase().replaceAll("[^A-Za-z0-9_.-]", "_");
        }
        ValidationUtil.validateUsername(username);

        if (plainPassword == null || plainPassword.isEmpty()) {
            throw new IllegalArgumentException("Password is required.");
        }
        if (!plainPassword.equals(confirmPassword)) {
            throw new IllegalArgumentException("Password and password confirmation do not match.");
        }
        ValidationUtil.validateStrongPassword(plainPassword);

        if (phone != null && !phone.trim().isEmpty()) {
            ValidationUtil.validatePhone(phone);
        }

        String pin = (plainPin != null && !plainPin.trim().isEmpty()) ? plainPin.trim() : "1234";
        ValidationUtil.validatePin(pin);

        String type = (accountType != null && !accountType.trim().isEmpty()) ? accountType.trim().toUpperCase() : "SAVINGS";
        if (!"SAVINGS".equals(type) && !"CURRENT".equals(type)) {
            type = "SAVINGS";
        }

        BigDecimal deposit = (initialDeposit != null && initialDeposit.compareTo(BigDecimal.ZERO) >= 0)
                ? initialDeposit : new BigDecimal("1000.00");
        if ("SAVINGS".equals(type) && deposit.compareTo(SavingsAccount.MIN_BALANCE) < 0) {
            throw new IllegalArgumentException("Minimum initial deposit for a Savings Account is ₹" + SavingsAccount.MIN_BALANCE.setScale(2).toPlainString() + ".");
        }

        // Uniqueness verification
        User existingUser = userDAO.getUserByUsername(username.trim());
        if (existingUser != null) {
            throw new IllegalArgumentException("Username '" + username.trim() + "' is already registered. Please choose another username.");
        }
        Customer existingCustomer = customerDAO.getCustomerByEmail(email.trim());
        if (existingCustomer != null) {
            throw new IllegalArgumentException("Email address '" + email.trim() + "' is already registered.");
        }

        Connection conn = null;
        try {
            conn = DatabaseConfig.getConnection();
            conn.setAutoCommit(false);

            // 1. Create User (Strictly Role.CUSTOMER)
            User newUser = new User();
            newUser.setUsername(username.trim());
            newUser.setPasswordHash(PasswordUtil.hashPassword(plainPassword));
            newUser.setRole(Role.CUSTOMER);
            newUser.setActive(true);
            newUser.setLocked(false);
            newUser.setFailedLogins(0);
            newUser.setCreatedAt(LocalDateTime.now());
            int userId = userDAO.createUser(newUser, conn);
            newUser.setUserId(userId);

            // 2. Create Customer Profile
            Customer newCustomer = new Customer(
                    0,
                    userId,
                    fullName.trim(),
                    email.trim(),
                    phone != null ? phone.trim() : "",
                    "Customer Digital Registration"
            );
            int customerId = customerDAO.createCustomer(newCustomer, conn);
            newCustomer.setCustomerId(customerId);

            // 3. Create Primary Account
            Account account;
            if ("SAVINGS".equals(type)) {
                account = new SavingsAccount(
                        0,
                        customerId,
                        fullName.trim(),
                        deposit,
                        PasswordUtil.hashPin(pin),
                        0,
                        AccountStatus.ACTIVE,
                        LocalDateTime.now()
                );
            } else {
                account = new CurrentAccount(
                        0,
                        customerId,
                        fullName.trim(),
                        deposit,
                        PasswordUtil.hashPin(pin),
                        0,
                        AccountStatus.ACTIVE,
                        LocalDateTime.now()
                );
            }
            int accNo = bankDAO.createAccount(account, conn);
            account.setAccountNumber(accNo);

            conn.commit();

            // Post-commit notifications and audit
            recordAudit(username.trim(), Role.CUSTOMER, "CUSTOMER_REGISTERED", "USER",
                    String.valueOf(userId), "Customer profile and primary account #" + accNo + " created successfully", "SUCCESS", ipAddress);
            notificationService.notifyUser(userId, "Welcome to SecureBank",
                    "Your SecureBank digital account has been activated! Your primary account number is #" + accNo + ".", "SUCCESS");

            return new RegistrationResult(newUser, newCustomer, account);
        } catch (SQLException ex) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ignored) {}
            }
            throw ex;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException ignored) {}
            }
        }
    }

    /**
     * Registers a new User with salted BCrypt password hash.
     */
    public User registerUser(String username, String password, Role role, String actorUsername)
        throws SQLException, AuthorizationException {
        ValidationUtil.validateUsername(username);
        ValidationUtil.validatePassword(password);

        User existing = userDAO.getUserByUsername(username);
        if (existing != null) {
            throw new IllegalArgumentException("Username '" + username + "' is already registered.");
        }

        User newUser = new User();
        newUser.setUsername(username.trim());
        newUser.setPasswordHash(PasswordUtil.hashPassword(password));
        newUser.setRole(role != null ? role : Role.CUSTOMER);
        newUser.setActive(true);
        newUser.setLocked(false);
        newUser.setFailedLogins(0);

        int id = userDAO.createUser(newUser, null);
        newUser.setUserId(id);

        recordAudit(actorUsername != null ? actorUsername : "SYSTEM", Role.ADMIN, "USER_CREATED", "USER", String.valueOf(id), "Created user '" + username + "' with role " + newUser.getRole(), "SUCCESS", "LOCAL");
        return newUser;
    }

    /**
     * Unlocks a locked user account (Admin only).
     */
    public void unlockUser(int userId, User adminUser) throws SQLException, AuthorizationException {
        enforceRole(adminUser, Role.ADMIN);
        userDAO.unlockUser(userId);
        recordAudit(adminUser.getUsername(), adminUser.getRole(), "USER_UNLOCKED", "USER", String.valueOf(userId), "Admin unlocked user account", "SUCCESS", "LOCAL");
    }

    /**
     * Enforces required role authorization in the service layer.
     */
    public static void enforceRole(User user, Role minimumRole) throws AuthorizationException {
        if (user == null) {
            throw new AuthorizationException("Unauthenticated request. Please log in.");
        }
        if (user.getRole().getLevel() < minimumRole.getLevel()) {
            throw new AuthorizationException(
                String.format("Access Denied: Role '%s' insufficient. Minimum required: '%s'.",
                    user.getRole().getRoleName(), minimumRole.getRoleName())
            );
        }
    }

    private void recordAudit(String actor, Role role, String action, String entityType, String entityId, String details, String status, String ip) {
        try {
            AuditLog al = new AuditLog(0, actor, role, action, entityType, entityId, details, status, ip, LocalDateTime.now());
            auditDAO.recordAuditLog(al);
        } catch (Exception ignored) {
        }
    }
}
