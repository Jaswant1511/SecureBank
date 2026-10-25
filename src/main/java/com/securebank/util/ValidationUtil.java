package com.securebank.util;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * SecureBank: Role-Based Core Banking & Transaction Management System
 * File: ValidationUtil.java
 *
 * Enforces business input validation rules across presentation, service, and DAO layers.
 */
public final class ValidationUtil {

    private static final Pattern NAME_PATTERN = Pattern.compile("^[A-Za-z\\s.'-]{2,100}$");
    private static final Pattern PIN_PATTERN = Pattern.compile("^\\d{4}$");
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_.@-]{3,50}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^(\\+91[\\-\\s]?)?[6-9]\\d{9}$");

    private ValidationUtil() {
        // Utility class
    }

    public static void validateHolderName(String name) {
        if (name == null || !NAME_PATTERN.matcher(name.trim()).matches()) {
            throw new IllegalArgumentException(
                "Customer name must be between 2 and 100 alphabetical characters."
            );
        }
    }

    public static void validatePin(String pin) {
        if (pin == null || !PIN_PATTERN.matcher(pin.trim()).matches()) {
            throw new IllegalArgumentException("Security PIN must be an exact 4-digit numeric string (0000-9999).");
        }
    }

    public static void validateUsername(String username) {
        if (username == null || !USERNAME_PATTERN.matcher(username.trim()).matches()) {
            throw new IllegalArgumentException(
                "Username must be 3 to 50 alphanumeric characters (symbols allowed: _ . - @)."
            );
        }
    }

    public static void validatePassword(String password) {
        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters in length.");
        }
    }

    public static void validateStrongPassword(String password) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters in length.");
        }
        boolean hasUpper = false;
        boolean hasLower = false;
        boolean hasDigit = false;
        boolean hasSpecial = false;
        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c)) hasUpper = true;
            else if (Character.isLowerCase(c)) hasLower = true;
            else if (Character.isDigit(c)) hasDigit = true;
            else hasSpecial = true;
        }
        if (!hasUpper || !hasLower || !hasDigit || !hasSpecial) {
            throw new IllegalArgumentException(
                "Password must contain at least one uppercase letter, one lowercase letter, one digit, and one special character."
            );
        }
    }

    public static void validateEmail(String email) {
        if (email != null && !email.trim().isEmpty() && !EMAIL_PATTERN.matcher(email.trim()).matches()) {
            throw new IllegalArgumentException("Invalid email format: " + email);
        }
    }

    public static void validatePhone(String phone) {
        if (phone != null && !phone.trim().isEmpty() && !PHONE_PATTERN.matcher(phone.trim().replaceAll("\\s+", "")).matches()) {
            throw new IllegalArgumentException("Invalid phone number format: " + phone);
        }
    }

    public static void validatePositiveAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transaction amount must be strictly positive (> 0.00).");
        }
    }

    public static void validateMonetaryAmount(BigDecimal amount) {
        validatePositiveAmount(amount);
        if (amount.scale() > 2) {
            throw new IllegalArgumentException("Transaction amount cannot have more than 2 decimal places.");
        }
        if (amount.compareTo(new BigDecimal("100000000.00")) > 0) {
            throw new IllegalArgumentException("Transaction amount exceeds single transaction safety ceiling (₹10,00,00,000.00).");
        }
    }

    public static void validatePositiveAmount(double amount) {
        if (Double.isNaN(amount) || Double.isInfinite(amount) || amount <= 0.0) {
            throw new IllegalArgumentException("Transaction amount must be strictly positive (> 0.00).");
        }
    }

    public static void validateAccountNumber(int accNo) {
        if (accNo <= 0) {
            throw new IllegalArgumentException("Account number must be a positive integer.");
        }
    }
}
