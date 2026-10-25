package com.securebank.exception;

/**
 * SecureBank: Role-Based Core Banking & Transaction Management System
 * Exception thrown when business validation fails (e.g., negative amounts, malformed inputs).
 */
public class ValidationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ValidationException(String message) {
        super(message);
    }
}
