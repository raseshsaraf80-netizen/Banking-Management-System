// Author: Sahil Raj
// Concept: Plain JDBC Transaction DAO supporting transactional connections and date-range queries
package com.bank.dao;

import com.bank.exception.BankException;
import com.bank.model.Transaction;
import com.bank.model.TransactionType;
import com.bank.util.DatabaseManager;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * SQLite JDBC implementation of {@link TransactionDao}.
 */
public class SqliteTransactionDao implements TransactionDao {

    private final DatabaseManager dbManager;

    public SqliteTransactionDao(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    @Override
    public Transaction save(Transaction txn) throws BankException {
        try (Connection conn = dbManager.getConnection()) {
            return save(conn, txn);
        } catch (SQLException ex) {
            throw new BankException("Error saving transaction: " + ex.getMessage(), ex);
        }
    }

    @Override
    public Transaction save(Connection conn, Transaction txn) throws SQLException {
        String sql = """
                INSERT INTO transactions(account_number, type, amount, balance_after, description, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, txn.getAccountNumber());
            ps.setString(2, txn.getType().name());
            ps.setString(3, txn.getAmount().toPlainString());
            ps.setString(4, txn.getBalanceAfter().toPlainString());
            ps.setString(5, txn.getDescription());
            ps.setString(6, txn.getTimestamp().toString());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    txn.setTxnId(keys.getLong(1));
                }
            }
            return txn;
        }
    }

    @Override
    public Optional<Transaction> findById(Long id) throws BankException {
        String sql = "SELECT * FROM transactions WHERE txn_id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException ex) {
            throw new BankException("Error finding transaction: " + ex.getMessage(), ex);
        }
    }

    @Override
    public List<Transaction> findAll() throws BankException {
        String sql = "SELECT * FROM transactions ORDER BY txn_id DESC";
        List<Transaction> list = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
            return list;
        } catch (SQLException ex) {
            throw new BankException("Error listing transactions: " + ex.getMessage(), ex);
        }
    }

    @Override
    public List<Transaction> findByAccountNumber(String accountNumber) throws BankException {
        String sql = "SELECT * FROM transactions WHERE account_number = ? ORDER BY txn_id DESC";
        List<Transaction> list = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, accountNumber);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
            return list;
        } catch (SQLException ex) {
            throw new BankException("Error fetching account transactions: " + ex.getMessage(), ex);
        }
    }

    @Override
    public List<Transaction> findRecentByAccountNumber(String accountNumber, int limit)
            throws BankException {
        String sql = """
                SELECT * FROM transactions
                WHERE account_number = ?
                ORDER BY txn_id DESC
                LIMIT ?
                """;
        List<Transaction> list = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, accountNumber);
            ps.setInt(2, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
            return list;
        } catch (SQLException ex) {
            throw new BankException("Error fetching recent transactions: " + ex.getMessage(), ex);
        }
    }

    @Override
    public List<Transaction> findByAccountAndDateRange(String accountNumber, LocalDate from, LocalDate to)
            throws BankException {
        String sql = """
                SELECT * FROM transactions
                WHERE account_number = ?
                  AND substr(created_at, 1, 10) >= ?
                  AND substr(created_at, 1, 10) <= ?
                ORDER BY txn_id DESC
                """;
        String fromStr = (from != null) ? from.toString() : "0000-01-01";
        String toStr = (to != null) ? to.toString() : "9999-12-31";
        List<Transaction> list = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, accountNumber);
            ps.setString(2, fromStr);
            ps.setString(3, toStr);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
            return list;
        } catch (SQLException ex) {
            throw new BankException("Error filtering transactions by date: " + ex.getMessage(), ex);
        }
    }

    @Override
    public boolean delete(Long id) throws BankException {
        String sql = "DELETE FROM transactions WHERE txn_id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException ex) {
            throw new BankException("Error deleting transaction: " + ex.getMessage(), ex);
        }
    }

    private Transaction mapRow(ResultSet rs) throws SQLException {
        return new Transaction(
                rs.getLong("txn_id"),
                rs.getString("account_number"),
                TransactionType.valueOf(rs.getString("type")),
                new BigDecimal(rs.getString("amount")),
                new BigDecimal(rs.getString("balance_after")),
                LocalDateTime.parse(rs.getString("created_at")),
                rs.getString("description")
        );
    }
}

