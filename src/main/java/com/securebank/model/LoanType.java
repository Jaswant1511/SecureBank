package com.securebank.model;

/**
 * Banking Loan Facility Types.
 */
public enum LoanType {
    PERSONAL("Personal Loan", 11.50),
    HOME("Home Mortgage Loan", 8.40),
    EDUCATION("Higher Education Loan", 9.25),
    AUTO("Vehicle / Auto Loan", 9.75);

    private final String displayName;
    private final double defaultRate;

    LoanType(String displayName, double defaultRate) {
        this.displayName = displayName;
        this.defaultRate = defaultRate;
    }

    public String getDisplayName() {
        return displayName;
    }

    public double getDefaultRate() {
        return defaultRate;
    }
}
