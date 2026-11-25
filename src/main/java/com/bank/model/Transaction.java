// Author: Shaurya Kumar
// Concept: Encapsulation, BigDecimal precision for money, constructor overloading
package com.bank.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Immutable-style ledger entry representing a single transaction on an account.
 */
public class Transaction {

    private long txnId;
    private String accountNumber;
    private TransactionType type;
    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private LocalDateTime timestamp;
    private String description;

    /**
     * Constructor overload 1: creates a new transaction before DB auto-increment ID assignment.
     */
    public Transaction(String accountNumber, TransactionType type, BigDecimal amount,
                       BigDecimal balanceAfter, String description) {
        this(0L, accountNumber, type, amount, balanceAfter, LocalDateTime.now(), description);
    }

    /**
     * Constructor overload 2: full constructor used when loading from database.
     */
    public Transaction(long txnId, String accountNumber, TransactionType type, BigDecimal amount,
                       BigDecimal balanceAfter, LocalDateTime timestamp, String description) {
        this.txnId = txnId;
        this.accountNumber = accountNumber;
        this.type = type;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.timestamp = timestamp != null ? timestamp : LocalDateTime.now();
        this.description = description;
    }

    public long getTxnId() {
        return txnId;
    }

    public void setTxnId(long txnId) {
        this.txnId = txnId;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public TransactionType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return String.format("[%s] %s | %s | Amount: %.2f | Balance: %.2f | %s",
                timestamp, accountNumber, type, amount, balanceAfter, description);
    }
}

