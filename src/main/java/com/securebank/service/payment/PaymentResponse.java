package com.securebank.service.payment;

import java.time.LocalDateTime;

/**
 * Standard response object returned by payment gateways.
 * Transparently indicates whether the transaction was processed by a simulator
 * and attaches clear regulatory disclaimers when in simulation mode.
 */
public class PaymentResponse {

    public static final String SIMULATION_DISCLAIMER =
            "SIMULATED TRANSACTION: Development demonstration only. No real funds were moved and no external financial institution has confirmed this operation.";

    private final String clientReference;
    private final String providerReference;
    private final PaymentStatus status;
    private final String responseCode;
    private final String message;
    private final boolean simulated;
    private final String disclaimer;
    private final LocalDateTime timestamp;

    public PaymentResponse(String clientReference, String providerReference, PaymentStatus status,
                           String responseCode, String message, boolean simulated) {
        this.clientReference = clientReference;
        this.providerReference = providerReference;
        this.status = status;
        this.responseCode = responseCode;
        this.message = message;
        this.simulated = simulated;
        this.disclaimer = simulated ? SIMULATION_DISCLAIMER : null;
        this.timestamp = LocalDateTime.now();
    }

    public static PaymentResponse success(String clientReference, String providerReference, String message, boolean simulated) {
        return new PaymentResponse(clientReference, providerReference, PaymentStatus.SUCCESS, "200_OK", message, simulated);
    }

    public static PaymentResponse pending(String clientReference, String providerReference, String message, boolean simulated) {
        return new PaymentResponse(clientReference, providerReference, PaymentStatus.PENDING, "202_ACCEPTED", message, simulated);
    }

    public static PaymentResponse failure(String clientReference, String providerReference, String responseCode, String message, boolean simulated) {
        return new PaymentResponse(clientReference, providerReference, PaymentStatus.FAILED, responseCode, message, simulated);
    }

    public static PaymentResponse timeout(String clientReference, String providerReference, String message, boolean simulated) {
        return new PaymentResponse(clientReference, providerReference, PaymentStatus.TIMEOUT, "504_TIMEOUT", message, simulated);
    }

    public String getClientReference() {
        return clientReference;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getResponseCode() {
        return responseCode;
    }

    public String getMessage() {
        return message;
    }

    public boolean isSimulated() {
        return simulated;
    }

    public String getDisclaimer() {
        return disclaimer;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "PaymentResponse{" +
                "clientRef='" + clientReference + '\'' +
                ", providerRef='" + providerReference + '\'' +
                ", status=" + status +
                ", code='" + responseCode + '\'' +
                ", simulated=" + simulated +
                '}';
    }
}
