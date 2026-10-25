package com.securebank.exception;

/**
 * SecureBank: Role-Based Core Banking & Transaction Management System
 * Exception thrown when a requested bank account cannot be located.
 */
public class AccountNotFoundException extends Exception {

    private static final long serialVersionUID = 1L;

    public AccountNotFoundException(String message) {
        super(message);
    }
}
