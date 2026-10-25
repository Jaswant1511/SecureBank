package com.securebank.service.payment;

/**
 * Lifecycle states for external financial payments.
 */
public enum PaymentStatus {
    /**
     * Payment has been initiated with the external provider, awaiting final settlement or webhook confirmation.
     */
    PENDING,

    /**
     * Authoritatively confirmed as successful by the external provider or authentic webhook callback.
     */
    SUCCESS,

    /**
     * Explicitly rejected or declined by the provider (e.g. invalid recipient, insufficient funds).
     */
    FAILED,

    /**
     * Network timeout or connection drop occurred during provider communication; requires status inquiry.
     */
    TIMEOUT,

    /**
     * The payment was reversed or refunded by the provider or bank operations.
     */
    REVERSED;

    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == REVERSED;
    }
}
