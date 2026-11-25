// Author: Shaurya Kumar
// Concept: Custom checked exception hierarchy
package com.bank.exception;

/**
 * Thrown when login credentials (email/password) or session tokens are invalid.
 */
public class InvalidCredentialsException extends BankException {

    public InvalidCredentialsException(String message) {
        super(message);
    }
}

