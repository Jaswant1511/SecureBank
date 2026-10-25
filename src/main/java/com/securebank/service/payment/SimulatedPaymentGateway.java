package com.securebank.service.payment;

import com.securebank.util.DatabaseConfig;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Deterministic Payment Gateway Simulator for Development and Demonstration.
 *
 * SAFETY CONTROLS:
 * - Strictly prohibited from running in PRODUCTION environments.
 * - Always sets isSimulated() = true and attaches simulation disclaimers.
 * - Never claims real monetary transfer or genuine external bank clearance.
 * - Supports deterministic testing of SUCCESS, FAILED, TIMEOUT, and PENDING outcomes.
 * - Provides HMAC-SHA256 signature verification and replay protection.
 */
public class SimulatedPaymentGateway implements PaymentGateway {

    private static final Logger LOGGER = Logger.getLogger(SimulatedPaymentGateway.class.getName());

    public static final String PROVIDER_NAME = "DevelopmentPaymentSimulator";
    public static final String DEFAULT_HMAC_SECRET = "securebank_simulated_hmac_secret_key_2026_demo";

    private final String hmacSecret;
    private final Set<String> processedCallbackSignatures = ConcurrentHashMap.newKeySet();
    private final Map<String, PaymentStatus> simulatedLedger = new ConcurrentHashMap<>();

    public SimulatedPaymentGateway() {
        this(resolveSecret());
    }

    public SimulatedPaymentGateway(String secret) {
        this.hmacSecret = secret != null && !secret.isEmpty() ? secret : DEFAULT_HMAC_SECRET;
    }

    private static String resolveSecret() {
        String env = System.getenv("SECUREBANK_PAYMENT_SECRET");
        return (env != null && !env.trim().isEmpty()) ? env.trim() : DEFAULT_HMAC_SECRET;
    }

    private void assertNotProduction() {
        if (DatabaseConfig.isProduction()) {
            throw new IllegalStateException(
                "SECURITY VIOLATION: Development payment simulator cannot be invoked in PRODUCTION mode. Configure a certified, licensed payment provider.");
        }
    }

    @Override
    public PaymentResponse initiatePayment(PaymentRequest request) {
        assertNotProduction();
        if (request == null) {
            throw new IllegalArgumentException("PaymentRequest cannot be null.");
        }

        String clientRef = request.getClientReference();
        BigDecimal amount = request.getAmount();

        // Traceable simulated provider reference
        String providerRef = "SIM-TXN-" + System.currentTimeMillis() + "-" +
                Math.abs(clientRef.hashCode() % 100000);

        // 1. Explicit test control via request flags
        String purpose = request.getPurpose() != null ? request.getPurpose().toUpperCase() : "";
        if (clientRef.contains("TEST_FAIL") || purpose.contains("FAIL")) {
            simulatedLedger.put(providerRef, PaymentStatus.FAILED);
            return PaymentResponse.failure(clientRef, providerRef, "SIM_400_EXPLICIT_FAIL",
                    "Simulated deterministic failure triggered by test condition.", true);
        }
        if (clientRef.contains("TEST_TIMEOUT") || purpose.contains("TIMEOUT")) {
            simulatedLedger.put(providerRef, PaymentStatus.TIMEOUT);
            return PaymentResponse.timeout(clientRef, providerRef,
                    "Simulated provider communication timeout.", true);
        }
        if (clientRef.contains("TEST_PENDING") || purpose.contains("PENDING")) {
            simulatedLedger.put(providerRef, PaymentStatus.PENDING);
            return PaymentResponse.pending(clientRef, providerRef,
                    "Simulated payment pending asynchronous settlement.", true);
        }

        // 2. Deterministic rules based on monetary amounts for automated test verification
        if (amount != null) {
            // Amount ending in .99 triggers upstream network timeout
            if (amount.remainder(BigDecimal.ONE).compareTo(new BigDecimal("0.99")) == 0) {
                simulatedLedger.put(providerRef, PaymentStatus.TIMEOUT);
                return PaymentResponse.timeout(clientRef, providerRef,
                        "Simulated network timeout triggered by test amount (.99).", true);
            }
            // Amount ending in .50 triggers provider rejection
            if (amount.remainder(BigDecimal.ONE).compareTo(new BigDecimal("0.50")) == 0) {
                simulatedLedger.put(providerRef, PaymentStatus.FAILED);
                return PaymentResponse.failure(clientRef, providerRef, "SIM_402_INSUFFICIENT_FUNDS",
                        "Simulated provider rejection triggered by test amount (.50): Insufficient destination balance.", true);
            }
            // Amounts exceeding single transfer limit
            if (amount.compareTo(new BigDecimal("100000.00")) > 0) {
                simulatedLedger.put(providerRef, PaymentStatus.FAILED);
                return PaymentResponse.failure(clientRef, providerRef, "SIM_LIMIT_EXCEEDED",
                        "Simulated provider maximum transaction ceiling of ₹100,000.00 exceeded.", true);
            }
        }

        // 3. Normal deterministic success
        simulatedLedger.put(providerRef, PaymentStatus.SUCCESS);
        return PaymentResponse.success(clientRef, providerRef,
                "Simulated payment authorized successfully. [DEMONSTRATION ONLY: NO REAL MONEY MOVED]", true);
    }

    @Override
    public PaymentResponse inquirePaymentStatus(String providerReference) {
        assertNotProduction();
        if (providerReference == null || providerReference.trim().isEmpty()) {
            throw new IllegalArgumentException("Provider reference is required for status inquiry.");
        }

        PaymentStatus status = simulatedLedger.get(providerReference);
        if (status == PaymentStatus.PENDING) {
            // Unless designated as permanently stuck for reconciliation testing, an inquiry simulates downstream clearing settlement
            if (providerReference.contains("STUCK")) {
                status = PaymentStatus.PENDING;
            } else {
                status = PaymentStatus.SUCCESS;
                simulatedLedger.put(providerReference, PaymentStatus.SUCCESS);
            }
        } else if (status == null) {
            // Check deterministic substrings for unit testing
            if (providerReference.contains("FAIL")) {
                status = PaymentStatus.FAILED;
            } else if (providerReference.contains("TIMEOUT")) {
                status = PaymentStatus.TIMEOUT;
            } else if (providerReference.contains("STUCK")) {
                status = PaymentStatus.PENDING;
            } else {
                status = PaymentStatus.SUCCESS;
            }
        }

        return new PaymentResponse("INQUIRY-" + providerReference, providerReference, status,
                "200_INQUIRY", "Inquired status from simulator: " + status, true);
    }

    @Override
    public boolean verifyCallbackSignature(String callbackPayload, String signature) {
        if (callbackPayload == null || signature == null || signature.trim().isEmpty()) {
            return false;
        }

        // Replay protection: Each signature must be unique within current operational cache
        if (processedCallbackSignatures.contains(signature)) {
            LOGGER.warning("[SECURITY] Replay detected! Webhook signature was previously processed: " + signature);
            return false;
        }

        String expectedSignature = generateSignature(callbackPayload);
        boolean authentic = constantTimeEquals(expectedSignature, signature);
        if (authentic) {
            processedCallbackSignatures.add(signature);
        }
        return authentic;
    }

    /**
     * Generates HMAC-SHA256 signature for a payload using the configured secret.
     */
    public String generateSignature(String payload) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(hmacSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            hmac.init(secretKey);
            byte[] hash = hmac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate HMAC-SHA256 signature", e);
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public boolean isSimulated() {
        return true;
    }

    public void clearCallbackCache() {
        processedCallbackSignatures.clear();
        simulatedLedger.clear();
    }
}
