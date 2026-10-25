package com.securebank.security;

import com.securebank.util.PasswordUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Security Test Suite: BCrypt Password and PIN Hashing Verification.
 */
public class PasswordUtilTest {

    @Test
    @DisplayName("BCrypt: Passwords generate salted hashes that match plaintext on check")
    public void testPasswordHashingAndVerification() {
        String plain = "CapstoneBank2026!";
        String hash1 = PasswordUtil.hashPassword(plain);
        String hash2 = PasswordUtil.hashPassword(plain);

        // Hashes should differ due to random salting
        Assertions.assertNotEquals(hash1, hash2);

        // Both verify correctly against the original plaintext
        Assertions.assertTrue(PasswordUtil.checkPassword(plain, hash1));
        Assertions.assertTrue(PasswordUtil.checkPassword(plain, hash2));

        // Wrong password rejected
        Assertions.assertFalse(PasswordUtil.checkPassword("WrongPassword", hash1));
        Assertions.assertFalse(PasswordUtil.checkPassword("", hash1));
        Assertions.assertFalse(PasswordUtil.checkPassword(null, hash1));
    }

    @Test
    @DisplayName("BCrypt: PIN hashing generates salted verification tokens")
    public void testPinHashing() {
        String pin = "4321";
        String hash = PasswordUtil.hashPin(pin);

        Assertions.assertTrue(PasswordUtil.checkPin("4321", hash));
        Assertions.assertFalse(PasswordUtil.checkPin("1234", hash));
        Assertions.assertFalse(PasswordUtil.checkPin("0000", hash));
    }
}
