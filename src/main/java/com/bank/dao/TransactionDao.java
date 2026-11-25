// Author: Sahil Raj
// Concept: DAO interface extending generic Repository<Transaction, Long>
package com.bank.dao;

import com.bank.exception.BankException;
import com.bank.generic.Repository;
import com.bank.model.Transaction;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/**
 * Data access contract for {@link Transaction} ledger entries.
 */
public interface TransactionDao extends Repository<Transaction, Long> {

    Transaction save(Connection conn, Transaction txn) throws SQLException;

    List<Transaction> findByAccountNumber(String accountNumber) throws BankException;

    List<Transaction> findRecentByAccountNumber(String accountNumber, int limit) throws BankException;

    List<Transaction> findByAccountAndDateRange(String accountNumber, LocalDate from, LocalDate to)
            throws BankException;
}

