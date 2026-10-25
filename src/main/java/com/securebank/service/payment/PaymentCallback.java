package com.securebank.service.payment;

import java.time.LocalDateTime;

/**
 * Model representing an asynchronous webhook notification or callback from an external payment gateway.
 */
public class PaymentCallback {

    private final String providerReference;
    private final String clientReference;
    private final PaymentStatus status;
    private final String signature;
    private final String rawPayload;
    private final LocalDateTime timestamp;

    public PaymentCallback(String providerReference, String clientReference, PaymentStatus status,
                           String signature, String rawPayload) {
        this.providerReference = providerReference;
        this.clientReference = clientReference;
        this.status = status;
        this.signature = signature;
        this.rawPayload = rawPayload;
        this.timestamp = LocalDateTime.now();
    }

    public String getProviderReference() {
        return providerReference;
    }

    public String getClientReference() {
        return clientReference;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getSignature() {
        return signature;
    }

    public String getRawPayload() {
        return rawPayload;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }
}
