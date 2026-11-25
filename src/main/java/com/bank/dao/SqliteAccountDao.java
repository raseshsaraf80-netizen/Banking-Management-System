// Author: Sahil Raj
// Concept: Plain JDBC DAO with polymorphic Account instantiation (SavingsAccount / CurrentAccount)
package com.bank.dao;

import com.bank.exception.BankException;
import com.bank.model.Account;
import com.bank.model.AccountStatus;
import com.bank.model.AccountType;
import com.bank.model.CurrentAccount;
import com.bank.model.SavingsAccount;
import com.bank.util.DatabaseManager;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * SQLite JDBC implementation of {@link AccountDao}.
 */
public class SqliteAccountDao implements AccountDao {

    private final DatabaseManager dbManager;

    public SqliteAccountDao(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    @Override
    public Account save(Account account) throws BankException {
        String sql = """
                INSERT INTO accounts(account_number, customer_id, account_type, balance, status, opened_on)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, account.getAccountNumber());
            ps.setLong(2, account.getCustomerId());
            ps.setString(3, account.getAccountType().name());
            ps.setString(4, account.getBalance().toPlainString());
            ps.setString(5, account.getStatus().name());
            ps.setString(6, account.getOpenedOn().toString());
            ps.executeUpdate();
            return account;
        } catch (SQLException ex) {
            throw new BankException("Error saving account: " + ex.getMessage(), ex);
        }
    }

    @Override
    public Optional<Account> findById(String accountNumber) throws BankException {
        String sql = "SELECT * FROM accounts WHERE account_number = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, accountNumber);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException ex) {
            throw new BankException("Error finding account: " + ex.getMessage(), ex);
        }
    }

    @Override
    public List<Account> findByCustomerId(long customerId) throws BankException {
        String sql = "SELECT * FROM accounts WHERE customer_id = ? ORDER BY account_number";
        List<Account> accounts = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    accounts.add(mapRow(rs));
                }
            }
            return accounts;
        } catch (SQLException ex) {
            throw new BankException("Error finding accounts for customer: " + ex.getMessage(), ex);
        }
    }

    @Override
    public List<Account> searchByKeyword(String keyword) throws BankException {
        String pattern = "%" + (keyword == null ? "" : keyword.trim()) + "%";
        String sql = """
                SELECT a.* FROM accounts a
                JOIN customers c ON a.customer_id = c.customer_id
                WHERE a.account_number LIKE ? OR c.name LIKE ? OR c.email LIKE ?
                ORDER BY a.account_number
                """;
        List<Account> accounts = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, pattern);
            ps.setString(2, pattern);
            ps.setString(3, pattern);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    accounts.add(mapRow(rs));
                }
            }
            return accounts;
        } catch (SQLException ex) {
            throw new BankException("Error searching accounts: " + ex.getMessage(), ex);
        }
    }

    @Override
    public List<Account> findAll() throws BankException {
        String sql = "SELECT * FROM accounts ORDER BY account_number";
        List<Account> accounts = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                accounts.add(mapRow(rs));
            }
            return accounts;
        } catch (SQLException ex) {
            throw new BankException("Error listing accounts: " + ex.getMessage(), ex);
        }
    }

    @Override
    public void updateBalance(String accountNumber, BigDecimal newBalance) throws BankException {
        try (Connection conn = dbManager.getConnection()) {
            updateBalance(conn, accountNumber, newBalance);
        } catch (SQLException ex) {
            throw new BankException("Error updating balance: " + ex.getMessage(), ex);
        }
    }

    @Override
    public void updateBalance(Connection conn, String accountNumber, BigDecimal newBalance)
            throws SQLException {
        String sql = "UPDATE accounts SET balance = ? WHERE account_number = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newBalance.toPlainString());
            ps.setString(2, accountNumber);
            int updated = ps.executeUpdate();
            if (updated == 0) {
                throw new SQLException("Account not found for balance update: " + accountNumber);
            }
        }
    }

    @Override
    public void updateStatus(String accountNumber, AccountStatus status) throws BankException {
        String sql = "UPDATE accounts SET status = ? WHERE account_number = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setString(2, accountNumber);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new BankException("Error updating account status: " + ex.getMessage(), ex);
        }
    }

    @Override
    public boolean delete(String accountNumber) throws BankException {
        String sql = "DELETE FROM accounts WHERE account_number = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, accountNumber);
            return ps.executeUpdate() > 0;
        } catch (SQLException ex) {
            throw new BankException("Error deleting account: " + ex.getMessage(), ex);
        }
    }

    private Account mapRow(ResultSet rs) throws SQLException {
        String accNo = rs.getString("account_number");
        long customerId = rs.getLong("customer_id");
        AccountType type = AccountType.valueOf(rs.getString("account_type"));
        BigDecimal balance = new BigDecimal(rs.getString("balance"));
        AccountStatus status = AccountStatus.valueOf(rs.getString("status"));
        LocalDate openedOn = LocalDate.parse(rs.getString("opened_on"));

        // Runtime polymorphism: instantiate concrete subclass based on account_type
        if (type == AccountType.SAVINGS) {
            return new SavingsAccount(accNo, customerId, balance, openedOn, status);
        }
        return new CurrentAccount(accNo, customerId, balance, openedOn, status);
    }
}

