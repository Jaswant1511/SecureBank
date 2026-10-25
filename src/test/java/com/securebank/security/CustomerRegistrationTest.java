package com.securebank.security;

import com.securebank.exception.AuthenticationException;
import com.securebank.model.Role;
import com.securebank.model.User;
import com.securebank.service.AuthService;
import com.securebank.util.DatabaseConfig;
import java.math.BigDecimal;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Customer Registration & Credential Lifecycle Test Suite:
 * Validates self-service customer registration, role isolation (strictly CUSTOMER),
 * uniqueness enforcement (username and email), strong password compliance,
 * atomic database persistence across User/Customer/Account, and password change workflows.
 */
public class CustomerRegistrationTest {

    private AuthService authService;

    @BeforeEach
    public void setUp() throws Exception {
        DatabaseConfig.switchToEmbeddedH2();
        authService = new AuthService();
    }

    @Test
    @DisplayName("Registration: Successfully registers customer with User, Customer, and Primary Account")
    public void testSuccessfulCustomerRegistration() throws Exception {
        String username = "priya_" + System.currentTimeMillis();
        String email = "priya." + System.currentTimeMillis() + "@securebank.com";

        AuthService.RegistrationResult result = authService.registerCustomer(
                "Priya Sharma",
                email,
                username,
                "+919876543210",
                "Priya#Secure2026",
                "Priya#Secure2026",
                "4321",
                "SAVINGS",
                new BigDecimal("2500.00"),
                "127.0.0.1"
        );

        Assertions.assertNotNull(result);
        Assertions.assertNotNull(result.getUser());
        Assertions.assertTrue(result.getUser().getUserId() > 0);
        Assertions.assertEquals(username, result.getUser().getUsername());
        Assertions.assertEquals(Role.CUSTOMER, result.getUser().getRole());

        Assertions.assertNotNull(result.getCustomer());
        Assertions.assertTrue(result.getCustomer().getCustomerId() > 0);
        Assertions.assertEquals("Priya Sharma", result.getCustomer().getFullName());
        Assertions.assertEquals(email, result.getCustomer().getEmail());

        Assertions.assertNotNull(result.getAccount());
        Assertions.assertTrue(result.getAccount().getAccountNumber() > 0);
        Assertions.assertEquals(0, new BigDecimal("2500.00").compareTo(result.getAccount().getBalance()));

        // Verify login succeeds using username
        User authByUsername = authService.login(username, "Priya#Secure2026", "127.0.0.1");
        Assertions.assertNotNull(authByUsername);
        Assertions.assertEquals(result.getUser().getUserId(), authByUsername.getUserId());

        // Verify login succeeds using email
        User authByEmail = authService.login(email, "Priya#Secure2026", "127.0.0.1");
        Assertions.assertNotNull(authByEmail);
        Assertions.assertEquals(result.getUser().getUserId(), authByEmail.getUserId());
    }

    @Test
    @DisplayName("Registration: Rejects duplicate username registration")
    public void testDuplicateUsernameRejection() {
        String username = "dupuser_" + System.currentTimeMillis();
        String email1 = "user1." + System.currentTimeMillis() + "@securebank.com";
        String email2 = "user2." + System.currentTimeMillis() + "@securebank.com";

        Assertions.assertDoesNotThrow(() -> {
            authService.registerCustomer(
                    "User One", email1, username, "9876543210",
                    "StrongPass#1", "StrongPass#1", "1234", "SAVINGS",
                    new BigDecimal("1500.00"), "127.0.0.1"
            );
        });

        IllegalArgumentException ex = Assertions.assertThrows(IllegalArgumentException.class, () -> {
            authService.registerCustomer(
                    "User Two", email2, username, "9876543211",
                    "StrongPass#2", "StrongPass#2", "1234", "SAVINGS",
                    new BigDecimal("1500.00"), "127.0.0.1"
            );
        });
        Assertions.assertTrue(ex.getMessage().contains("already registered"));
    }

    @Test
    @DisplayName("Registration: Rejects duplicate email registration")
    public void testDuplicateEmailRejection() {
        String email = "shared." + System.currentTimeMillis() + "@securebank.com";
        String user1 = "u1_" + System.currentTimeMillis();
        String user2 = "u2_" + System.currentTimeMillis();

        Assertions.assertDoesNotThrow(() -> {
            authService.registerCustomer(
                    "First Account", email, user1, "9876543210",
                    "StrongPass#1", "StrongPass#1", "1234", "SAVINGS",
                    new BigDecimal("1500.00"), "127.0.0.1"
            );
        });

        IllegalArgumentException ex = Assertions.assertThrows(IllegalArgumentException.class, () -> {
            authService.registerCustomer(
                    "Second Account", email, user2, "9876543211",
                    "StrongPass#2", "StrongPass#2", "1234", "SAVINGS",
                    new BigDecimal("1500.00"), "127.0.0.1"
            );
        });
        Assertions.assertTrue(ex.getMessage().contains("already registered"));
    }

    @Test
    @DisplayName("Registration: Enforces strong password complexity")
    public void testWeakPasswordRejection() {
        String email = "weak." + System.currentTimeMillis() + "@securebank.com";
        String user = "weak_" + System.currentTimeMillis();

        // Missing digit and special char
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            authService.registerCustomer(
                    "Weak Password", email, user, "9876543210",
                    "weakpassword", "weakpassword", "1234", "SAVINGS",
                    new BigDecimal("1000.00"), "127.0.0.1"
            );
        });

        // Missing uppercase
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            authService.registerCustomer(
                    "Weak Password", email, user, "9876543210",
                    "weakpass#1", "weakpass#1", "1234", "SAVINGS",
                    new BigDecimal("1000.00"), "127.0.0.1"
            );
        });
    }

    @Test
    @DisplayName("Registration: Rejects non-matching password confirmation")
    public void testPasswordMismatchRejection() {
        String email = "mismatch." + System.currentTimeMillis() + "@securebank.com";
        String user = "mismatch_" + System.currentTimeMillis();

        IllegalArgumentException ex = Assertions.assertThrows(IllegalArgumentException.class, () -> {
            authService.registerCustomer(
                    "Mismatch User", email, user, "9876543210",
                    "StrongPass#1", "DifferentPass#2", "1234", "SAVINGS",
                    new BigDecimal("1000.00"), "127.0.0.1"
            );
        });
        Assertions.assertTrue(ex.getMessage().toLowerCase().contains("match"));
    }

    @Test
    @DisplayName("Registration: Enforces savings minimum initial deposit policy (₹1,000)")
    public void testSavingsInitialDepositMinimumPolicy() {
        String email = "deposit." + System.currentTimeMillis() + "@securebank.com";
        String user = "dep_" + System.currentTimeMillis();

        IllegalArgumentException ex = Assertions.assertThrows(IllegalArgumentException.class, () -> {
            authService.registerCustomer(
                    "Low Deposit", email, user, "9876543210",
                    "StrongPass#1", "StrongPass#1", "1234", "SAVINGS",
                    new BigDecimal("400.00"), "127.0.0.1"
            );
        });
        Assertions.assertTrue(ex.getMessage().contains("1000.00") || ex.getMessage().contains("1,000"));
    }

    @Test
    @DisplayName("Password Change: Successful password update and credential rotation")
    public void testChangePasswordWorkflow() throws Exception {
        String username = "pwdchange_" + System.currentTimeMillis();
        String email = "pwdchange." + System.currentTimeMillis() + "@securebank.com";

        AuthService.RegistrationResult reg = authService.registerCustomer(
                "Change Pwd User", email, username, "9876543210",
                "OldPass#2026", "OldPass#2026", "1234", "SAVINGS",
                new BigDecimal("1500.00"), "127.0.0.1"
        );

        int userId = reg.getUser().getUserId();

        // Change password
        authService.changePassword(userId, "OldPass#2026", "NewSuper#Pass99", "NewSuper#Pass99", "127.0.0.1");

        // Old password must fail
        Assertions.assertThrows(AuthenticationException.class, () -> {
            authService.login(username, "OldPass#2026", "127.0.0.1");
        });

        // New password must succeed
        User updatedUser = authService.login(username, "NewSuper#Pass99", "127.0.0.1");
        Assertions.assertNotNull(updatedUser);
        Assertions.assertEquals(userId, updatedUser.getUserId());
    }

    @Test
    @DisplayName("Password Change: Rejects incorrect current password")
    public void testChangePasswordWrongCurrentPassword() throws Exception {
        String username = "wrongpwd_" + System.currentTimeMillis();
        String email = "wrongpwd." + System.currentTimeMillis() + "@securebank.com";

        AuthService.RegistrationResult reg = authService.registerCustomer(
                "Wrong Pwd User", email, username, "9876543210",
                "CorrectPass#1", "CorrectPass#1", "1234", "SAVINGS",
                new BigDecimal("1500.00"), "127.0.0.1"
        );

        Assertions.assertThrows(AuthenticationException.class, () -> {
            authService.changePassword(reg.getUser().getUserId(), "WrongOldPass#9", "NewPass#2026", "NewPass#2026", "127.0.0.1");
        });
    }

    @Test
    @DisplayName("Password Change: Rejects reuse of identical password")
    public void testChangePasswordSameAsOldPassword() throws Exception {
        String username = "samepwd_" + System.currentTimeMillis();
        String email = "samepwd." + System.currentTimeMillis() + "@securebank.com";

        AuthService.RegistrationResult reg = authService.registerCustomer(
                "Same Pwd User", email, username, "9876543210",
                "Identical#Pass1", "Identical#Pass1", "1234", "SAVINGS",
                new BigDecimal("1500.00"), "127.0.0.1"
        );

        IllegalArgumentException ex = Assertions.assertThrows(IllegalArgumentException.class, () -> {
            authService.changePassword(reg.getUser().getUserId(), "Identical#Pass1", "Identical#Pass1", "Identical#Pass1", "127.0.0.1");
        });
        Assertions.assertTrue(ex.getMessage().toLowerCase().contains("different"));
    }
}
