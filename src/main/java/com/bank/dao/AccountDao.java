// Author: Sahil Raj
// Concept: DAO interface extending generic Repository<Account, String>
package com.bank.dao;

import com.bank.exception.BankException;
import com.bank.generic.Repository;
import com.bank.model.Account;
import com.bank.model.AccountStatus;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Data access contract for {@link Account} persistence.
 */
public interface AccountDao extends Repository<Account, String> {

    List<Account> findByCustomerId(long customerId) throws BankException;

    List<Account> searchByKeyword(String keyword) throws BankException;

    void updateBalance(String accountNumber, BigDecimal newBalance) throws BankException;

    void updateBalance(Connection conn, String accountNumber, BigDecimal newBalance) throws SQLException;

    void updateStatus(String accountNumber, AccountStatus status) throws BankException;
}

