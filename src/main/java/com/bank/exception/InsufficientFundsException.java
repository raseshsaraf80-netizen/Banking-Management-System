// Author: Shaurya Kumar
// Concept: Custom checked exception hierarchy
package com.bank.exception;

/**
 * Thrown when an account does not have sufficient balance for a withdrawal or transfer.
 */
public class InsufficientFundsException extends BankException {

    public InsufficientFundsException(String message) {
        super(message);
    }
}

