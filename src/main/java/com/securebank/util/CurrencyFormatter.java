package com.securebank.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * SecureBank: Role-Based Core Banking & Transaction Management System
 * File: CurrencyFormatter.java
 *
 * Provides standardized financial formatting and arithmetic helpers for Indian Rupees (Rs. / INR).
 * All monetary amounts are held in BigDecimal with 2 decimal places (RoundingMode.HALF_UP).
 */
public final class CurrencyFormatter {

    public static final Locale INDIA_LOCALE = Locale.forLanguageTag("en-IN");
    private static final DecimalFormat CURRENCY_FMT;

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(INDIA_LOCALE);
        CURRENCY_FMT = new DecimalFormat("#,##,##0.00", symbols);
    }

    private CurrencyFormatter() {
        // Utility class
    }

    /**
     * Formats a BigDecimal to standard Indian Rupee string (e.g., "Rs. 14,500.00").
     */
    public static String format(BigDecimal amount) {
        if (amount == null) {
            return "Rs. 0.00";
        }
        return "Rs. " + CURRENCY_FMT.format(amount.setScale(2, RoundingMode.HALF_UP));
    }

    /**
     * Formats a double amount (for backward compatibility).
     */
    public static String format(double amount) {
        return format(BigDecimal.valueOf(amount));
    }

    /**
     * Validates and returns an amount strictly scaled to 2 decimal places.
     */
    public static BigDecimal toMoney(double amount) {
        return BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Validates and returns a BigDecimal strictly scaled to 2 decimal places.
     */
    public static BigDecimal toMoney(BigDecimal amount) {
        if (amount == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Parses a string input into a valid monetary BigDecimal.
     * Rejects NaN, negative, non-numeric, or excessively precise values.
     */
    public static BigDecimal parsePositiveMoney(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Amount cannot be empty.");
        }
        String clean = text.trim().replace("Rs.", "").replace("Rs", "").replace(",", "").trim();
        try {
            BigDecimal val = new BigDecimal(clean).setScale(2, RoundingMode.HALF_UP);
            if (val.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Monetary amount must be strictly greater than zero.");
            }
            return val;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid monetary amount format: " + text);
        }
    }
}
