// Author: Shaurya Kumar
// Concept: Interface defining core banking operations
package com.bank.model;

import com.bank.exception.AccountLimitException;
import com.bank.exception.BankException;
import com.bank.exception.InsufficientFundsException;
import com.bank.exception.InvalidAmountException;

import java.math.BigDecimal;

/**
 * Contract for any account capable of deposits, withdrawals, and transfers.
 */
public interface Transactable {

    void deposit(BigDecimal amount) throws InvalidAmountException, AccountLimitException;

    void withdraw(BigDecimal amount)
            throws InvalidAmountException, InsufficientFundsException, AccountLimitException;

    void transferTo(Account target, BigDecimal amount) throws BankException;
}

