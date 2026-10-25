package com.securebank.security;

import com.securebank.dao.AuditDAO;
import com.securebank.dao.UserDAO;
import com.securebank.exception.AuthenticationException;
import com.securebank.exception.AuthorizationException;
import com.securebank.model.Role;
import com.securebank.model.User;
import com.securebank.service.AuthService;
import com.securebank.util.DatabaseConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Authentication Test Suite: Login lifecycle, lockout after 3 strikes, and RBAC enforcement.
 */
public class AuthServiceTest {

    private AuthService authService;
    private UserDAO userDAO;

    @BeforeEach
    public void setUp() {
        DatabaseConfig.switchToEmbeddedH2();
        DatabaseConfig.seedTestData();
        userDAO = new UserDAO();
        authService = new AuthService(userDAO, new AuditDAO());
    }

    @Test
    @DisplayName("Authentication: Valid user signs in and receives authenticated User object")
    public void testValidLogin() throws AuthenticationException {
        User user = authService.login("aarav", "Password@123", "TEST-IP");
        Assertions.assertNotNull(user);
        Assertions.assertEquals("aarav", user.getUsername());
        Assertions.assertEquals(Role.CUSTOMER, user.getRole());
        Assertions.assertEquals(0, user.getFailedLogins());
    }

    @Test
    @DisplayName("Authentication: Multi-identifier login resolves user by email, full name, or account number")
    public void testMultiIdentifierLogin() throws AuthenticationException {
        // 1. Login with email
        User userByEmail = authService.login("aarav.sharma@example.com", "Password@123", "TEST-IP");
        Assertions.assertNotNull(userByEmail);
        Assertions.assertEquals("aarav", userByEmail.getUsername());

        // 2. Login with full name
        User userByName = authService.login("Aarav Sharma", "Password@123", "TEST-IP");
        Assertions.assertNotNull(userByName);
        Assertions.assertEquals("aarav", userByName.getUsername());

        // 3. Login with account number
        User userByAccount = authService.login("1001", "Password@123", "TEST-IP");
        Assertions.assertNotNull(userByAccount);
        Assertions.assertEquals("aarav", userByAccount.getUsername());
    }

    @Test
    @DisplayName("Authentication: Invalid password increments failed logins and locks after 3 attempts")
    public void testLoginLockoutMechanism() {
        // Attempt 1
        Assertions.assertThrows(AuthenticationException.class, () -> {
            authService.login("priya", "WrongPassword", "TEST-IP");
        });

        // Attempt 2
        Assertions.assertThrows(AuthenticationException.class, () -> {
            authService.login("priya", "WrongPassword", "TEST-IP");
        });

        // Attempt 3 -> Locks account
        AuthenticationException ex = Assertions.assertThrows(AuthenticationException.class, () -> {
            authService.login("priya", "WrongPassword", "TEST-IP");
        });
        Assertions.assertTrue(ex.getMessage().contains("LOCKED"));

        // Attempt 4: Even with correct password, locked account is rejected
        AuthenticationException lockedEx = Assertions.assertThrows(AuthenticationException.class, () -> {
            authService.login("priya", "Password@123", "TEST-IP");
        });
        Assertions.assertTrue(lockedEx.getMessage().contains("LOCKED"));
    }

    @Test
    @DisplayName("Authorization: Enforces minimum role requirements in service layer")
    public void testRoleAuthorizationEnforcement() {
        User customer = new User(1, "cust", "hash", Role.CUSTOMER, 0, false, true);
        User teller = new User(2, "teller", "hash", Role.TELLER, 0, false, true);
        User admin = new User(3, "admin", "hash", Role.ADMIN, 0, false, true);

        // Customer cannot access Teller or Admin operations
        Assertions.assertThrows(AuthorizationException.class, () -> {
            AuthService.enforceRole(customer, Role.TELLER);
        });
        Assertions.assertThrows(AuthorizationException.class, () -> {
            AuthService.enforceRole(customer, Role.ADMIN);
        });

        // Teller can access Teller operations, but not Admin
        Assertions.assertDoesNotThrow(() -> AuthService.enforceRole(teller, Role.TELLER));
        Assertions.assertThrows(AuthorizationException.class, () -> AuthService.enforceRole(teller, Role.ADMIN));

        // Admin can access all operations
        Assertions.assertDoesNotThrow(() -> AuthService.enforceRole(admin, Role.CUSTOMER));
        Assertions.assertDoesNotThrow(() -> AuthService.enforceRole(admin, Role.TELLER));
        Assertions.assertDoesNotThrow(() -> AuthService.enforceRole(admin, Role.ADMIN));
    }
}
