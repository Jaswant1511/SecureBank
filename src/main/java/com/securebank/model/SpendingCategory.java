package com.securebank.model;

/**
 * Spending categories for customer smart financial analytics and budgeting.
 */
public enum SpendingCategory {
    FOOD("Food & Dining"),
    TRANSPORT("Travel & Transport"),
    SHOPPING("Retail & Shopping"),
    UTILITIES("Bills & Utilities"),
    EDUCATION("Education & Fees"),
    ENTERTAINMENT("Entertainment & Leisure"),
    HEALTHCARE("Health & Wellness"),
    OTHER("General & Miscellaneous");

    private final String displayName;

    SpendingCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static SpendingCategory categorize(String description) {
        if (description == null || description.trim().isEmpty()) {
            return OTHER;
        }
        String lower = description.toLowerCase();
        if (lower.contains("food") || lower.contains("swiggy") || lower.contains("zomato") || lower.contains("restaurant")
                || lower.contains("cafe") || lower.contains("dining") || lower.contains("grocery") || lower.contains("supermarket")) {
            return FOOD;
        }
        if (lower.contains("uber") || lower.contains("ola") || lower.contains("metro") || lower.contains("fuel")
                || lower.contains("petrol") || lower.contains("flight") || lower.contains("railway") || lower.contains("irctc")) {
            return TRANSPORT;
        }
        if (lower.contains("amazon") || lower.contains("flipkart") || lower.contains("myntra") || lower.contains("mall")
                || lower.contains("store") || lower.contains("shopping") || lower.contains("retail")) {
            return SHOPPING;
        }
        if (lower.contains("bescom") || lower.contains("electricity") || lower.contains("water") || lower.contains("bwssb")
                || lower.contains("airtel") || lower.contains("jio") || lower.contains("gas") || lower.contains("bill")) {
            return UTILITIES;
        }
        if (lower.contains("school") || lower.contains("college") || lower.contains("university") || lower.contains("course")
                || lower.contains("udemy") || lower.contains("tuition") || lower.contains("education") || lower.contains("fee")) {
            return EDUCATION;
        }
        if (lower.contains("netflix") || lower.contains("cinema") || lower.contains("movie") || lower.contains("theatre")
                || lower.contains("game") || lower.contains("hotstar") || lower.contains("prime") || lower.contains("spotify")) {
            return ENTERTAINMENT;
        }
        if (lower.contains("pharmacy") || lower.contains("hospital") || lower.contains("clinic") || lower.contains("doctor")
                || lower.contains("apollo") || lower.contains("medplus") || lower.contains("health")) {
            return HEALTHCARE;
        }
        return OTHER;
    }
}
