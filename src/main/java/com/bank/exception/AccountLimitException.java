// Author: Shaurya Kumar
// Concept: Custom checked exception hierarchy
package com.bank.exception;

/**
 * Thrown when an operation violates account rules such as minimum balance,
 * monthly withdrawal count, overdraft limit, or frozen/closed status.
 */
public class AccountLimitException extends BankException {

    public AccountLimitException(String message) {
        super(message);
    }
}

