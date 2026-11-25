// Author: Shaurya Kumar
// Concept: JUnit 5 tests for input validation edge cases
package com.bank;

import com.bank.exception.InvalidAmountException;
import com.bank.exception.ValidationException;
import com.bank.util.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ValidatorTest {

    @Test
    void validInputsPass() throws Exception {
        Validator.validateName("Diya Menon");
        Validator.validateEmail("diya.menon@example.com");
        Validator.validatePhone("9876543210");
        Validator.validatePassword("Strong@123");
        BigDecimal amt = Validator.parseAndValidateAmount("1250.50");
        assertEquals(new BigDecimal("1250.50"), amt);
    }

    @Test
    void invalidNamesThrowValidationException() {
        assertThrows(ValidationException.class, () -> Validator.validateName(null));
        assertThrows(ValidationException.class, () -> Validator.validateName(" "));
        assertThrows(ValidationException.class, () -> Validator.validateName("A"));
    }

    @Test
    void invalidEmailsAndPhonesThrowValidationException() {
        assertThrows(ValidationException.class, () -> Validator.validateEmail("not-an-email"));
        assertThrows(ValidationException.class, () -> Validator.validateEmail("user@domain"));
        assertThrows(ValidationException.class, () -> Validator.validatePhone("12345"));
        assertThrows(ValidationException.class, () -> Validator.validatePhone("98765abc10"));
    }

    @Test
    void weakPasswordsThrowValidationException() {
        assertThrows(ValidationException.class, () -> Validator.validatePassword("short1A"));
        assertThrows(ValidationException.class, () -> Validator.validatePassword("alllowercase1"));
        assertThrows(ValidationException.class, () -> Validator.validatePassword("ALLUPPERCASE1"));
        assertThrows(ValidationException.class, () -> Validator.validatePassword("NoDigitsHere"));
    }

    @Test
    void invalidAmountsThrowInvalidAmountException() {
        assertThrows(InvalidAmountException.class, () -> Validator.parseAndValidateAmount(""));
        assertThrows(InvalidAmountException.class, () -> Validator.parseAndValidateAmount("abc"));
        assertThrows(InvalidAmountException.class, () -> Validator.parseAndValidateAmount("0"));
        assertThrows(InvalidAmountException.class, () -> Validator.parseAndValidateAmount("-100"));
        assertThrows(InvalidAmountException.class, () -> Validator.parseAndValidateAmount("10.123"));
    }
}

