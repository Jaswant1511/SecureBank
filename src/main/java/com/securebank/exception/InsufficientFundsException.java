package com.securebank.exception;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * SecureBank: Role-Based Core Banking & Transaction Management System
 * Custom Checked Exception thrown when a withdrawal or transfer violates
 * account-specific balance rules:
 *  - SavingsAccount: Minimum balance of Rs. 1,000.00 must be maintained.
 *  - CurrentAccount: Overdraft limit of Rs. 5,000.00 cannot be exceeded.
 */
public class InsufficientFundsException extends Exception {

    private static final long serialVersionUID = 1L;

    // Exact monetary shortfall in Rupees (Rs.)
    private final BigDecimal shortfall;

    public InsufficientFundsException(String message, BigDecimal shortfall) {
        super(message);
        this.shortfall = shortfall != null ? shortfall.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
    }

    public InsufficientFundsException(String message, double shortfall) {
        this(message, BigDecimal.valueOf(shortfall));
    }

    public BigDecimal getShortfall() {
        return shortfall;
    }

    public double getShortfallAsDouble() {
        return shortfall.doubleValue();
    }

    public String getFormattedAlert() {
        return String.format(
            "InsufficientFundsException: %s Shortfall: Rs. %,.2f",
            getMessage(),
            shortfall.doubleValue()
        );
    }
}
