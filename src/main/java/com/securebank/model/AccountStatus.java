package com.securebank.model;

/**
 * SecureBank: Role-Based Core Banking & Transaction Management System
 * Enum: AccountStatus
 *
 * Defines the operational lifecycle states of a bank account.
 */
public enum AccountStatus {
    ACTIVE("Active"),
    LOCKED("Locked due to Security Violations"),
    FROZEN("Administratively Frozen"),
    CLOSED("Safely Closed (Historical Record Retained)");

    private final String description;

    AccountStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean canTransact() {
        return this == ACTIVE;
    }

    public static AccountStatus fromString(String text) {
        if (text == null) {
            return ACTIVE;
        }
        for (AccountStatus status : AccountStatus.values()) {
            if (status.name().equalsIgnoreCase(text.trim())) {
                return status;
            }
        }
        return ACTIVE;
    }
}
