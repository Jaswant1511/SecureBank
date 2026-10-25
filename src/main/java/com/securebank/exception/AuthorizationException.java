package com.securebank.exception;

/**
 * SecureBank: Role-Based Core Banking & Transaction Management System
 * Exception thrown when an authenticated user attempts an operation outside their authorized role.
 */
public class AuthorizationException extends Exception {

    private static final long serialVersionUID = 1L;

    public AuthorizationException(String message) {
        super(message);
    }
}
