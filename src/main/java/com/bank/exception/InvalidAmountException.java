// Author: Shaurya Kumar
// Concept: Custom checked exception hierarchy
package com.bank.exception;

/**
 * Thrown when a monetary amount is null, zero, negative, or has more than two decimal places.
 */
public class InvalidAmountException extends BankException {

    public InvalidAmountException(String message) {
        super(message);
    }
}

