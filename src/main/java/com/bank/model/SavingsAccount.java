// Author: Shaurya Kumar
// Concept: Inheritance, method overriding, runtime polymorphism, super keyword, final constants
package com.bank.model;

import com.bank.exception.AccountLimitException;
import com.bank.exception.InsufficientFundsException;
import com.bank.exception.InvalidAmountException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Savings account enforcing minimum balance, monthly withdrawal cap, and 3.5% p.a. interest.
 */
public class SavingsAccount extends Account {

    public static final BigDecimal MIN_BALANCE = new BigDecimal("1000.00");
    public static final BigDecimal ANNUAL_INTEREST_RATE = new BigDecimal("0.035");
    public static final int MONTHLY_WITHDRAWAL_LIMIT = 5;

    private int monthlyWithdrawals;

    /**
     * Constructor overload 1: new savings account with minimum opening balance.
     */
    public SavingsAccount(String accountNumber, long customerId) {
        super(accountNumber, customerId, MIN_BALANCE, LocalDate.now(), AccountStatus.ACTIVE);
        this.monthlyWithdrawals = 0;
    }

    /**
     * Constructor overload 2: full constructor when restoring from database.
     */
    public SavingsAccount(String accountNumber, long customerId, BigDecimal balance,
                          LocalDate openedOn, AccountStatus status) {
        super(accountNumber, customerId, balance, openedOn, status);
        this.monthlyWithdrawals = 0;
    }

    @Override
    public synchronized void withdraw(BigDecimal amount)
            throws InvalidAmountException, InsufficientFundsException, AccountLimitException {
        ensureActive();
        ensureValidPositiveAmount(amount);

        if (monthlyWithdrawals >= MONTHLY_WITHDRAWAL_LIMIT) {
            throw new AccountLimitException(
                    "Monthly withdrawal limit (" + MONTHLY_WITHDRAWAL_LIMIT + ") reached for savings account "
                            + accountNumber);
        }

        if (this.balance.compareTo(amount) < 0) {
            throw new InsufficientFundsException(
                    "Insufficient balance in " + accountNumber + ". Available: " + this.balance);
        }

        BigDecimal remaining = this.balance.subtract(amount);
        if (remaining.compareTo(MIN_BALANCE) < 0) {
            throw new AccountLimitException(
                    "Withdrawal denied: Savings account must maintain a minimum balance of " + MIN_BALANCE);
        }

        this.balance = normalizeAmount(remaining);
        this.monthlyWithdrawals++;
    }

    @Override
    public synchronized BigDecimal calculateMonthlyInterest() {
        if (this.status != AccountStatus.ACTIVE || this.balance.compareTo(BigDecimal.ZERO) <= 0) {
            return ZERO;
        }
        // Monthly interest = balance * (annualRate / 12)
        BigDecimal monthlyRate = ANNUAL_INTEREST_RATE.divide(new BigDecimal("12"), 10, RoundingMode.HALF_UP);
        return this.balance.multiply(monthlyRate).setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public AccountType getAccountType() {
        return AccountType.SAVINGS;
    }

    public synchronized int getMonthlyWithdrawals() {
        return monthlyWithdrawals;
    }

    public synchronized void setMonthlyWithdrawals(int monthlyWithdrawals) {
        this.monthlyWithdrawals = monthlyWithdrawals;
    }
}

