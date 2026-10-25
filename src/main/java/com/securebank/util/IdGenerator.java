package com.securebank.util;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * SecureBank: Role-Based Core Banking & Transaction Management System
 * File: IdGenerator.java
 *
 * Generates collision-resistant, cryptographically secure transaction reference numbers.
 */
public final class IdGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String ALPHANUM = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private IdGenerator() {
        // Utility class
    }

    /**
     * Generates a unique transaction reference code.
     * Example: TXN-20261017-8FK2A9
     */
    public static String generateTransactionRef() {
        String datePart = LocalDateTime.now().format(DATE_FMT);
        StringBuilder suffix = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            suffix.append(ALPHANUM.charAt(RANDOM.nextInt(ALPHANUM.length())));
        }
        return "TXN-" + datePart + "-" + suffix;
    }

    /**
     * Generates an idempotency request token.
     */
    public static String generateIdempotencyKey() {
        byte[] bytes = new byte[12];
        RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder("IDEM-");
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }
}
