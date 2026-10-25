package com.securebank.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import org.mindrot.jbcrypt.BCrypt;

/**
 * SecureBank: Role-Based Core Banking & Transaction Management System
 * File: PasswordUtil.java
 *
 * Provides cryptographic hashing and verification for passwords and PINs.
 * Uses BCrypt (work factor 12) as primary algorithm.
 * Contains a salted SHA-512 fallback if BCrypt is not loaded.
 */
public final class PasswordUtil {

    private static final int BCRYPT_LOG_ROUNDS = 12;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private PasswordUtil() {
        // Utility class
    }

    /**
     * Hashes a user password using BCrypt with salt.
     *
     * @param plainPassword Plaintext password
     * @return BCrypt hash string
     */
    public static String hashPassword(String plainPassword) {
        if (plainPassword == null || plainPassword.trim().isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty.");
        }
        try {
            return BCrypt.hashpw(plainPassword, BCrypt.gensalt(BCRYPT_LOG_ROUNDS));
        } catch (NoClassDefFoundError | Exception e) {
            return fallbackSaltedHash(plainPassword);
        }
    }

    /**
     * Verifies a candidate password against the stored hash.
     *
     * @param candidatePassword Plaintext password entered by user
     * @param storedHash        Hashed password from database
     * @return true if password matches hash; false otherwise
     */
    public static boolean checkPassword(String candidatePassword, String storedHash) {
        if (candidatePassword == null || storedHash == null || storedHash.isEmpty()) {
            return false;
        }
        try {
            if (storedHash.startsWith("$2a$") || storedHash.startsWith("$2b$") || storedHash.startsWith("$2y$")) {
                return BCrypt.checkpw(candidatePassword, storedHash);
            }
            if (storedHash.startsWith("SHA512$")) {
                return verifyFallbackHash(candidatePassword, storedHash);
            }
            // Disallow legacy plaintext passwords - reject unrecognized hash formats
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Hashes a 4-digit PIN using BCrypt with a per-pin salt.
     *
     * @param pin 4-digit numeric string
     * @return Salted hash string
     */
    public static String hashPin(String pin) {
        if (pin == null || !pin.matches("^\\d{4}$")) {
            throw new IllegalArgumentException("PIN must be an exact 4-digit numeric string.");
        }
        try {
            return BCrypt.hashpw(pin, BCrypt.gensalt(10));
        } catch (NoClassDefFoundError | Exception e) {
            return fallbackSaltedHash(pin);
        }
    }

    /**
     * Verifies a 4-digit PIN against the stored hash.
     *
     * @param candidatePin Plaintext PIN
     * @param storedHash   Hashed PIN from database
     * @return true if matches
     */
    public static boolean checkPin(String candidatePin, String storedHash) {
        if (candidatePin == null || storedHash == null) {
            return false;
        }
        try {
            if (storedHash.startsWith("$2a$") || storedHash.startsWith("$2b$") || storedHash.startsWith("$2y$")) {
                return BCrypt.checkpw(candidatePin, storedHash);
            }
            if (storedHash.startsWith("SHA512$")) {
                return verifyFallbackHash(candidatePin, storedHash);
            }
            // Disallow legacy plaintext PINs - reject unrecognized hash formats
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private static String fallbackSaltedHash(String input) {
        byte[] salt = new byte[16];
        SECURE_RANDOM.nextBytes(salt);
        String saltStr = Base64.getEncoder().encodeToString(salt);
        String hash = sha512(saltStr + input);
        return "SHA512$" + saltStr + "$" + hash;
    }

    private static boolean verifyFallbackHash(String candidate, String stored) {
        String[] parts = stored.split("\\$");
        if (parts.length != 3) {
            return false;
        }
        String salt = parts[1];
        String expected = parts[2];
        String actual = sha512(salt + candidate);
        return MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8),
            actual.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String sha512(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            byte[] bytes = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-512 not available", e);
        }
    }

    /**
     * CLI utility to generate BCrypt hashes for testing, database seeding, and administrative setup.
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("SecureBank BCrypt Utility — Sample Hashes:");
            System.out.println("Password 'Password@123': " + hashPassword("Password@123"));
            System.out.println("Password 'Teller@123':   " + hashPassword("Teller@123"));
            System.out.println("Password 'Admin@123':    " + hashPassword("Admin@123"));
            System.out.println("PIN '1234':              " + hashPin("1234"));
            System.out.println("PIN '4321':              " + hashPin("4321"));
            System.out.println("PIN '9999':              " + hashPin("9999"));
            System.out.println("PIN '5678':              " + hashPin("5678"));
            System.out.println("PIN '2468':              " + hashPin("2468"));
        } else if (args.length == 1) {
            System.out.println("Hash: " + hashPassword(args[0]));
        } else if (args.length == 2 && "-pin".equalsIgnoreCase(args[0])) {
            System.out.println("PIN Hash: " + hashPin(args[1]));
        } else {
            System.out.println("Usage: java PasswordUtil [plainPassword | -pin <4-digit-pin>]");
        }
    }
}
