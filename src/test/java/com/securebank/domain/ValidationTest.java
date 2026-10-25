package com.securebank.domain;

import com.securebank.util.CurrencyFormatter;
import com.securebank.util.ValidationUtil;
import java.math.BigDecimal;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Validation Test Suite: Input validation boundaries and currency parsing.
 */
public class ValidationTest {

    @Test
    @DisplayName("Currency: Validates positive amounts and rejects negative, zero, or malformed inputs")
    public void testMonetaryValidation() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> CurrencyFormatter.parsePositiveMoney("0.00"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> CurrencyFormatter.parsePositiveMoney("-50.00"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> CurrencyFormatter.parsePositiveMoney("abc"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> CurrencyFormatter.parsePositiveMoney(null));

        BigDecimal parsed = CurrencyFormatter.parsePositiveMoney("Rs. 1,450.50");
        Assertions.assertEquals(new BigDecimal("1450.50"), parsed);
    }

    @Test
    @DisplayName("PIN Validation: Accepts only exact 4-digit numeric string")
    public void testPinValidation() {
        Assertions.assertDoesNotThrow(() -> ValidationUtil.validatePin("1234"));
        Assertions.assertDoesNotThrow(() -> ValidationUtil.validatePin("0000"));

        Assertions.assertThrows(IllegalArgumentException.class, () -> ValidationUtil.validatePin("123")); // too short
        Assertions.assertThrows(IllegalArgumentException.class, () -> ValidationUtil.validatePin("12345")); // too long
        Assertions.assertThrows(IllegalArgumentException.class, () -> ValidationUtil.validatePin("12a4")); // non-numeric
        Assertions.assertThrows(IllegalArgumentException.class, () -> ValidationUtil.validatePin(null));
    }

    @Test
    @DisplayName("User Validation: Username and Password security rules")
    public void testUserValidation() {
        Assertions.assertDoesNotThrow(() -> ValidationUtil.validateUsername("aarav_sharma"));
        Assertions.assertDoesNotThrow(() -> ValidationUtil.validatePassword("Password@123"));

        Assertions.assertThrows(IllegalArgumentException.class, () -> ValidationUtil.validateUsername("ab")); // too short
        Assertions.assertThrows(IllegalArgumentException.class, () -> ValidationUtil.validatePassword("12345")); // too short
    }
}
