package com.securebank.model;

/**
 * SecureBank: Role-Based Core Banking & Transaction Management System
 * Enum: AccountType
 *
 * Defines the core banking account categories supported by the platform.
 */
public enum AccountType {
    SAVINGS("Savings Account"),
    CURRENT("Current / Business Account");

    private final String displayName;

    AccountType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static AccountType fromString(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Account type cannot be null.");
        }
        for (AccountType type : AccountType.values()) {
            if (type.name().equalsIgnoreCase(text.trim())) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid account type: " + text);
    }
}
