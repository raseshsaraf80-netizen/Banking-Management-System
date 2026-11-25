// Author: Shaurya Kumar
// Concept: Enum for transaction ledger entries
package com.bank.model;

/**
 * Types of monetary entries recorded in the transaction ledger.
 */
public enum TransactionType {
    DEPOSIT,
    WITHDRAWAL,
    TRANSFER_IN,
    TRANSFER_OUT,
    INTEREST
}

