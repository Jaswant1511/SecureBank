package com.securebank.model;

/**
 * SecureBank: Role-Based Core Banking & Transaction Management System
 * Enum: Role
 *
 * Defines the role-based access control (RBAC) tiers.
 */
public enum Role {
    CUSTOMER("Customer", 1),
    TELLER("Bank Teller", 2),
    ADMIN("System Administrator", 3);

    private final String roleName;
    private final int level;

    Role(String roleName, int level) {
        this.roleName = roleName;
        this.level = level;
    }

    public String getRoleName() {
        return roleName;
    }

    public int getLevel() {
        return level;
    }

    public static Role fromString(String roleStr) {
        if (roleStr == null) {
            return CUSTOMER;
        }
        for (Role r : Role.values()) {
            if (r.name().equalsIgnoreCase(roleStr.trim())) {
                return r;
            }
        }
        return CUSTOMER;
    }
}
