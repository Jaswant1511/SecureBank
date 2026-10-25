package com.securebank.exception;

/**
 * SecureBank: Role-Based Core Banking & Transaction Management System
 * Exception thrown when an operation is attempted on a locked, frozen, or closed account.
 */
public class InactiveAccountException extends Exception {

    private static final long serialVersionUID = 1L;

    public InactiveAccountException(String message) {
        super(message);
    }
}
