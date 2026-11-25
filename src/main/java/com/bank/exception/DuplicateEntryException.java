// Author: Shaurya Kumar
// Concept: Custom checked exception for duplicate records (409 Conflict)
package com.bank.exception;

/**
 * Thrown when creating a customer or account that violates uniqueness constraints.
 */
public class DuplicateEntryException extends BankException {

    public DuplicateEntryException(String message) {
        super(message);
    }
}

