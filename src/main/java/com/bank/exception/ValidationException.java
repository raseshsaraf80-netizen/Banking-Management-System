// Author: Shaurya Kumar
// Concept: Custom checked exception hierarchy
package com.bank.exception;

/**
 * Thrown when user input fails format or business validation rules.
 */
public class ValidationException extends BankException {

    public ValidationException(String message) {
        super(message);
    }
}

