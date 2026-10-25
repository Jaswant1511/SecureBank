package com.securebank.exception;

/**
 * SecureBank: Role-Based Core Banking & Transaction Management System
 * General checked exception thrown when a banking service operation cannot be completed.
 */
public class BankingServiceException extends Exception {

    private static final long serialVersionUID = 1L;

    public BankingServiceException(String message) {
        super(message);
    }

    public BankingServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
