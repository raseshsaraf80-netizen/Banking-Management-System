// Author: Sahil Raj
// Concept: Plain JDBC DAO implementation with PreparedStatement and SQLException wrapping
package com.bank.dao;

import com.bank.exception.BankException;
import com.bank.model.Customer;
import com.bank.model.Role;
import com.bank.util.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * SQLite JDBC implementation of {@link CustomerDao}.
 */
public class SqliteCustomerDao implements CustomerDao {

    private final DatabaseManager dbManager;

    public SqliteCustomerDao(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    @Override
    public Customer save(Customer customer) throws BankException {
        String sql = """
                INSERT INTO customers(name, email, phone, address, password_hash, role, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, customer.getName());
            ps.setString(2, customer.getEmail().toLowerCase());
            ps.setString(3, customer.getPhone());
            ps.setString(4, customer.getAddress());
            ps.setString(5, customer.getPasswordHash());
            ps.setString(6, customer.getRole().name());
            ps.setString(7, customer.getCreatedAt().toString());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    customer.setCustomerId(keys.getLong(1));
                }
            }
            return customer;
        } catch (SQLException ex) {
            throw new BankException("Error saving customer: " + ex.getMessage(), ex);
        }
    }

    @Override
    public Optional<Customer> findById(Long id) throws BankException {
        String sql = "SELECT * FROM customers WHERE customer_id = ?";
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
            throw new BankException("Error finding customer by ID: " + ex.getMessage(), ex);
        }
    }

    @Override
    public Optional<Customer> findByEmail(String email) throws BankException {
        String sql = "SELECT * FROM customers WHERE LOWER(email) = LOWER(?)";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException ex) {
            throw new BankException("Error finding customer by email: " + ex.getMessage(), ex);
        }
    }

    @Override
    public boolean existsByEmailOrPhone(String email, String phone) throws BankException {
        String sql = "SELECT 1 FROM customers WHERE LOWER(email) = LOWER(?) OR phone = ? LIMIT 1";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            ps.setString(2, phone);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException ex) {
            throw new BankException("Error checking customer uniqueness: " + ex.getMessage(), ex);
        }
    }

    @Override
    public List<Customer> findAll() throws BankException {
        String sql = "SELECT * FROM customers ORDER BY customer_id";
        List<Customer> list = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
            return list;
        } catch (SQLException ex) {
            throw new BankException("Error listing customers: " + ex.getMessage(), ex);
        }
    }

    @Override
    public void updatePassword(long customerId, String newPasswordHash) throws BankException {
        String sql = "UPDATE customers SET password_hash = ? WHERE customer_id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newPasswordHash);
            ps.setLong(2, customerId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new BankException("Error updating customer password: " + ex.getMessage(), ex);
        }
    }

    @Override
    public void updateProfile(Customer customer) throws BankException {
        String sql = "UPDATE customers SET name = ?, phone = ?, address = ? WHERE customer_id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, customer.getName());
            ps.setString(2, customer.getPhone());
            ps.setString(3, customer.getAddress());
            ps.setLong(4, customer.getCustomerId());
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new BankException("Error updating customer profile: " + ex.getMessage(), ex);
        }
    }

    @Override
    public boolean delete(Long id) throws BankException {
        String sql = "DELETE FROM customers WHERE customer_id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException ex) {
            throw new BankException("Error deleting customer: " + ex.getMessage(), ex);
        }
    }

    private Customer mapRow(ResultSet rs) throws SQLException {
        return new Customer(
                rs.getLong("customer_id"),
                rs.getString("name"),
                rs.getString("email"),
                rs.getString("phone"),
                rs.getString("address"),
                rs.getString("password_hash"),
                Role.valueOf(rs.getString("role")),
                LocalDateTime.parse(rs.getString("created_at"))
        );
    }
}

