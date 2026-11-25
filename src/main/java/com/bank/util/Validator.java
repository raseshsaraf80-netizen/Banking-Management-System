// Author: Shaurya Kumar
// Concept: Input validation using regex and custom ValidationException
package com.bank.util;

import com.bank.exception.InvalidAmountException;
import com.bank.exception.ValidationException;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * Centralizes validation rules for customer profiles, passwords, and monetary amounts.
 */
public final class Validator {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[0-9]{10}$");
    private static final Pattern PASSWORD_UPPER = Pattern.compile(".*[A-Z].*");
    private static final Pattern PASSWORD_LOWER = Pattern.compile(".*[a-z].*");
    private static final Pattern PASSWORD_DIGIT = Pattern.compile(".*\\d.*");

    private Validator() {
    }

    public static void validateName(String name) throws ValidationException {
        if (name == null || name.trim().length() < 2) {
            throw new ValidationException("Name must contain at least 2 non-space characters.");
        }
    }

    public static void validateEmail(String email) throws ValidationException {
        if (email == null || !EMAIL_PATTERN.matcher(email.trim()).matches()) {
            throw new ValidationException("Invalid email format.");
        }
    }

    public static void validatePhone(String phone) throws ValidationException {
        if (phone == null || !PHONE_PATTERN.matcher(phone.trim()).matches()) {
            throw new ValidationException("Phone number must be exactly 10 digits.");
        }
    }

    public static void validatePassword(String password) throws ValidationException {
        if (password == null || password.length() < 8
                || !PASSWORD_UPPER.matcher(password).matches()
                || !PASSWORD_LOWER.matcher(password).matches()
                || !PASSWORD_DIGIT.matcher(password).matches()) {
            throw new ValidationException(
                    "Password must be at least 8 characters and include uppercase, lowercase, and a digit.");
        }
    }

    /**
     * Validates that a monetary amount is positive and has at most 2 decimal places.
     */
    public static BigDecimal parseAndValidateAmount(String rawAmount)
            throws InvalidAmountException {
        if (rawAmount == null || rawAmount.trim().isEmpty()) {
            throw new InvalidAmountException("Amount is required.");
        }
        try {
            BigDecimal amount = new BigDecimal(rawAmount.trim());
            validateAmount(amount);
            return amount;
        } catch (NumberFormatException ex) {
            throw new InvalidAmountException("Amount must be a valid number.");
        }
    }

    public static void validateAmount(BigDecimal amount) throws InvalidAmountException {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidAmountException("Amount must be greater than zero.");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new InvalidAmountException("Amount cannot have more than 2 decimal places.");
        }
    }

    public static void validateAccountNumber(String accountNumber) throws ValidationException {
        if (accountNumber == null || accountNumber.trim().isEmpty()) {
            throw new ValidationException("Account number is required.");
        }
    }
}

