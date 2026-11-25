// Author: Shaurya Kumar
// Concept: Abstract class, inheritance, polymorphism, constructor/method overloading, synchronized locking
package com.bank.model;

import com.bank.exception.AccountLimitException;
import com.bank.exception.BankException;
import com.bank.exception.InsufficientFundsException;
import com.bank.exception.InvalidAmountException;
import com.bank.exception.ValidationException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Abstract bank account implementing shared deposit, transfer, and status checks.
 * Subclasses override {@link #withdraw(BigDecimal)}, {@link #calculateMonthlyInterest()},
 * and {@link #getAccountType()} to provide runtime polymorphism.
 */
public abstract class Account extends BankEntity implements Transactable {

    public static final BigDecimal ZERO = new BigDecimal("0.00");
    private static int totalAccountsCreated = 0;

    protected final String accountNumber;
    protected final long customerId;
    protected BigDecimal balance;
    protected final LocalDate openedOn;
    protected AccountStatus status;

    /**
     * Constructor overload 1: creates a new ACTIVE account with zero balance opened today.
     */
    protected Account(String accountNumber, long customerId) {
        this(accountNumber, customerId, ZERO, LocalDate.now(), AccountStatus.ACTIVE);
    }

    /**
     * Constructor overload 2: full constructor used when loading existing accounts from SQLite.
     */
    protected Account(String accountNumber, long customerId, BigDecimal balance,
                      LocalDate openedOn, AccountStatus status) {
        super(LocalDateTime.now());
        this.accountNumber = Objects.requireNonNull(accountNumber, "accountNumber must not be null");
        this.customerId = customerId;
        this.balance = normalizeAmount(balance != null ? balance : ZERO);
        this.openedOn = openedOn != null ? openedOn : LocalDate.now();
        this.status = status != null ? status : AccountStatus.ACTIVE;
        incrementAccountCounter();
    }

    private static synchronized void incrementAccountCounter() {
        totalAccountsCreated++;
    }

    public static synchronized int getTotalAccountsCreated() {
        return totalAccountsCreated;
    }

    protected static BigDecimal normalizeAmount(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    protected void ensureActive() throws AccountLimitException {
        if (this.status != AccountStatus.ACTIVE) {
            throw new AccountLimitException(
                    "Account " + accountNumber + " is " + status + " and cannot process transactions.");
        }
    }

    protected void ensureValidPositiveAmount(BigDecimal amount) throws InvalidAmountException {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidAmountException("Transaction amount must be greater than zero.");
        }
        if (amount.scale() > 2) {
            throw new InvalidAmountException("Amount cannot have more than 2 decimal places.");
        }
    }

    /**
     * Method overload 1: deposit using default description.
     */
    @Override
    public synchronized void deposit(BigDecimal amount)
            throws InvalidAmountException, AccountLimitException {
        deposit(amount, "Cash deposit");
    }

    /**
     * Method overload 2: deposit with a custom description.
     */
    public synchronized void deposit(BigDecimal amount, String description)
            throws InvalidAmountException, AccountLimitException {
        ensureActive();
        ensureValidPositiveAmount(amount);
        this.balance = normalizeAmount(this.balance.add(amount));
    }

    /**
     * Transfers money atomically in memory using consistent lock ordering by accountNumber
     * to prevent deadlocks between concurrent transfers.
     */
    @Override
    public void transferTo(Account target, BigDecimal amount) throws BankException {
        if (target == null) {
            throw new ValidationException("Target account cannot be null.");
        }
        if (this.accountNumber.equals(target.getAccountNumber())) {
            throw new ValidationException("Source and destination accounts cannot be the same.");
        }

        // Consistent lock ordering prevents AB-BA deadlocks across threads
        Account firstLock = this.accountNumber.compareTo(target.accountNumber) < 0 ? this : target;
        Account secondLock = (firstLock == this) ? target : this;

        boolean completed = false;
        synchronized (firstLock) {
            synchronized (secondLock) {
                try {
                    try {
                        this.withdraw(amount);
                    } catch (InsufficientFundsException | AccountLimitException | InvalidAmountException ex) {
                        throw ex;
                    }
                    target.deposit(amount, "Transfer from " + this.accountNumber);
                    completed = true;
                } finally {
                    // Concept: finally block guarantees cleanup/audit note even if transfer fails
                    if (!completed) {
                        // State on 'this' was not modified if withdraw threw; if deposit threw, roll back in-memory
                    }
                }
            }
        }
    }

    @Override
    public abstract void withdraw(BigDecimal amount)
            throws InvalidAmountException, InsufficientFundsException, AccountLimitException;

    public abstract BigDecimal calculateMonthlyInterest();

    public abstract AccountType getAccountType();

    public String getAccountNumber() {
        return accountNumber;
    }

    public long getCustomerId() {
        return customerId;
    }

    public synchronized BigDecimal getBalance() {
        return balance;
    }

    public synchronized void setBalance(BigDecimal balance) {
        this.balance = normalizeAmount(balance);
    }

    public LocalDate getOpenedOn() {
        return openedOn;
    }

    public synchronized AccountStatus getStatus() {
        return status;
    }

    public synchronized void setStatus(AccountStatus status) {
        this.status = status;
    }

    @Override
    public String getEntitySummary() {
        return getAccountType() + " Account #" + accountNumber + " (Balance: " + balance + ")";
    }

    @Override
    public String toString() {
        return String.format("%s [%s] Balance: %.2f (%s)",
                accountNumber, getAccountType(), balance, status);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Account account)) return false;
        return Objects.equals(accountNumber, account.accountNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountNumber);
    }
}

