// Author: Shaurya Kumar
// Concept: Custom checked exception base class (extends Exception)
package com.bank.exception;

/**
 * Base checked exception for all domain, validation, and persistence errors in the banking system.
 */
public class BankException extends Exception {

    public BankException(String message) {
        super(message);
    }

    public BankException(String message, Throwable cause) {
        super(message, cause);
    }
}

