// Author: Shaurya Kumar
// Concept: Custom checked exception for authorization (403 Forbidden)
package com.bank.exception;

/**
 * Thrown when an authenticated user attempts an operation outside their permissions.
 */
public class UnauthorizedException extends BankException {

    public UnauthorizedException(String message) {
        super(message);
    }
}

