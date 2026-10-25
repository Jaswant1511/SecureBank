package com.securebank.service.payment;

/**
 * SecureBank: Financial Integration Architecture
 *
 * Clean abstraction for external payment provider operations.
 * Implementations may connect to a real payment provider (e.g., RazorPay, PayU)
 * or a development simulator for demonstration and testing purposes.
 *
 * Design principles:
 * - External API calls MUST NOT be performed inside a database transaction holding financial locks.
 * - Provider communication is strictly separated from core banking business logic.
 * - Callers must handle all PaymentResponse states (SUCCESS, FAILED, PENDING, TIMEOUT).
 * - Never trust a callback status field alone; verify authenticity and correlate with the original request.
 */
public interface PaymentGateway {

    /**
     * Initiates a payment request to an external provider.
     *
     * @param request The payment initiation request containing amount, reference, and destination details.
     * @return A PaymentResponse with the provider's acknowledgment, reference, and initial status.
     */
    PaymentResponse initiatePayment(PaymentRequest request);

    /**
     * Queries the current status of a previously initiated payment.
     *
     * @param providerReference The external provider's unique reference for the payment.
     * @return A PaymentResponse reflecting the provider's current status.
     */
    PaymentResponse inquirePaymentStatus(String providerReference);

    /**
     * Verifies the authenticity of a provider callback payload.
     * Implementations should validate signatures, timestamps, and replay protection.
     *
     * @param callbackPayload The raw callback body from the provider.
     * @param signature       The provider-supplied signature or HMAC.
     * @return true if the callback is authentic and not a replay.
     */
    boolean verifyCallbackSignature(String callbackPayload, String signature);

    /**
     * Returns the name of the payment provider (e.g., "SimulatedProvider", "RazorPay").
     */
    String getProviderName();

    /**
     * Returns true if this gateway is operating in simulation mode.
     * Simulated gateways never move real money and must clearly indicate this in all responses.
     */
    boolean isSimulated();
}
