// Author: Shaurya Kumar
// Concept: Custom checked exception hierarchy
package com.bank.exception;

/**
 * Thrown when a requested account number or customer record cannot be found.
 */
public class AccountNotFoundException extends BankException {

    public AccountNotFoundException(String message) {
        super(message);
    }
}

