// Author: Shaurya Kumar
// Concept: Inheritance, method overriding, runtime polymorphism, overdraft rule
package com.bank.model;

import com.bank.exception.AccountLimitException;
import com.bank.exception.InsufficientFundsException;
import com.bank.exception.InvalidAmountException;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Current account for everyday business transactions with an overdraft facility up to 10,000.
 */
public class CurrentAccount extends Account {

    public static final BigDecimal OVERDRAFT_LIMIT = new BigDecimal("10000.00");

    /**
     * Constructor overload 1: new current account with zero opening balance.
     */
    public CurrentAccount(String accountNumber, long customerId) {
        super(accountNumber, customerId, ZERO, LocalDate.now(), AccountStatus.ACTIVE);
    }

    /**
     * Constructor overload 2: full constructor when restoring from database.
     */
    public CurrentAccount(String accountNumber, long customerId, BigDecimal balance,
                          LocalDate openedOn, AccountStatus status) {
        super(accountNumber, customerId, balance, openedOn, status);
    }

    @Override
    public synchronized void withdraw(BigDecimal amount)
            throws InvalidAmountException, InsufficientFundsException, AccountLimitException {
        ensureActive();
        ensureValidPositiveAmount(amount);

        BigDecimal remaining = this.balance.subtract(amount);
        BigDecimal floor = OVERDRAFT_LIMIT.negate();
        if (remaining.compareTo(floor) < 0) {
            throw new AccountLimitException(
                    "Overdraft limit of " + OVERDRAFT_LIMIT + " exceeded on current account " + accountNumber);
        }

        this.balance = normalizeAmount(remaining);
    }

    @Override
    public BigDecimal calculateMonthlyInterest() {
        // Current accounts earn no interest
        return ZERO;
    }

    @Override
    public AccountType getAccountType() {
        return AccountType.CURRENT;
    }
}

