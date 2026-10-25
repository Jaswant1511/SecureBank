package com.securebank.exception;

/**
 * SecureBank: Role-Based Core Banking & Transaction Management System
 * Exception thrown for authentication failures, such as invalid credentials or locked user accounts.
 */
public class AuthenticationException extends Exception {

    private static final long serialVersionUID = 1L;

    public AuthenticationException(String message) {
        super(message);
    }

    public AuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
