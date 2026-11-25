// Author: Sahil Raj
// Concept: JDBC Connection management, DDL schema setup, PreparedStatement seeding
package com.bank.util;

import com.bank.exception.BankException;
import com.bank.model.AccountStatus;
import com.bank.model.AccountType;
import com.bank.model.Role;
import com.bank.model.TransactionType;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Manages SQLite JDBC connections, creates database tables on startup,
 * and seeds initial demo customers and accounts on first run.
 */
public class DatabaseManager {

    private final String dbPath;
    private final String jdbcUrl;

    /**
     * Uses BANK_DB_PATH from system property or environment variable, defaulting to ./bank.db.
     */
    public DatabaseManager() {
        this(resolveDefaultPath());
    }

    public DatabaseManager(String dbPath) {
        this.dbPath = (dbPath == null || dbPath.isBlank()) ? "./bank.db" : dbPath;
        this.jdbcUrl = "jdbc:sqlite:" + this.dbPath;
    }

    private static String resolveDefaultPath() {
        String prop = System.getProperty("BANK_DB_PATH");
        if (prop != null && !prop.isBlank()) {
            return prop;
        }
        String env = System.getenv("BANK_DB_PATH");
        if (env != null && !env.isBlank()) {
            return env;
        }
        return "./bank.db";
    }

    public String getDbPath() {
        return dbPath;
    }

    /**
     * Opens a new SQLite connection with foreign keys and a 5-second busy timeout enabled.
     */
    public Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(jdbcUrl);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON;");
            stmt.execute("PRAGMA busy_timeout = 5000;");
        }
        return conn;
    }

    /**
     * Creates schema tables if missing and seeds sample data if the database is empty.
     */
    public synchronized void initializeDatabase() throws BankException {
        File dbFile = new File(dbPath);
        File parent = dbFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        String createCustomers = """
                CREATE TABLE IF NOT EXISTS customers (
                  customer_id INTEGER PRIMARY KEY AUTOINCREMENT,
                  name TEXT NOT NULL,
                  email TEXT NOT NULL UNIQUE,
                  phone TEXT NOT NULL,
                  address TEXT,
                  password_hash TEXT NOT NULL,
                  role TEXT NOT NULL DEFAULT 'CUSTOMER',
                  created_at TEXT NOT NULL
                );
                """;

        String createAccounts = """
                CREATE TABLE IF NOT EXISTS accounts (
                  account_number TEXT PRIMARY KEY,
                  customer_id INTEGER NOT NULL REFERENCES customers(customer_id),
                  account_type TEXT NOT NULL,
                  balance TEXT NOT NULL,
                  status TEXT NOT NULL DEFAULT 'ACTIVE',
                  opened_on TEXT NOT NULL
                );
                """;

        String createTransactions = """
                CREATE TABLE IF NOT EXISTS transactions (
                  txn_id INTEGER PRIMARY KEY AUTOINCREMENT,
                  account_number TEXT NOT NULL REFERENCES accounts(account_number),
                  type TEXT NOT NULL,
                  amount TEXT NOT NULL,
                  balance_after TEXT NOT NULL,
                  description TEXT,
                  created_at TEXT NOT NULL
                );
                """;

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(createCustomers);
            stmt.execute(createAccounts);
            stmt.execute(createTransactions);
            seedIfEmpty(conn);
            syncAccountCounter(conn);
        } catch (SQLException ex) {
            throw new BankException("Failed to initialize database: " + ex.getMessage(), ex);
        }
    }

    private void syncAccountCounter(Connection conn) throws SQLException {
        String sql = "SELECT account_number FROM accounts";
        long maxSeq = 100100L;
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String accNo = rs.getString("account_number");
                if (accNo != null && accNo.length() > 2) {
                    try {
                        long seq = Long.parseLong(accNo.substring(2));
                        if (seq > maxSeq) {
                            maxSeq = seq;
                        }
                    } catch (NumberFormatException ignored) {
                        // Ignore custom account numbers without numeric suffix
                    }
                }
            }
        }
        IdGenerator.ensureMinimumCounter(maxSeq);
    }

    private void seedIfEmpty(Connection conn) throws SQLException {
        try (PreparedStatement countPs = conn.prepareStatement("SELECT COUNT(*) FROM customers");
             ResultSet rs = countPs.executeQuery()) {
            if (rs.next() && rs.getInt(1) > 0) {
                return;
            }
        }

        boolean originalAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            String now = LocalDateTime.now().toString();
            String today = LocalDate.now().toString();

            // Seed Admin + 3 sample customers (Aarav, Meera, Kabir)
            insertSeedCustomer(conn, "Admin User", "admin@bank.local", "9999900000",
                    "SRM Trust Bank HQ", PasswordUtil.hashPassword("Admin@123"), Role.ADMIN, now);
            long aaravId = insertSeedCustomer(conn, "Aarav Sharma", "aarav@bank.local", "9876543210",
                    "Vijayawada, AP", PasswordUtil.hashPassword("Aarav@123"), Role.CUSTOMER, now);
            long meeraId = insertSeedCustomer(conn, "Meera Nair", "meera@bank.local", "9876543211",
                    "Amaravati, AP", PasswordUtil.hashPassword("Meera@123"), Role.CUSTOMER, now);
            long kabirId = insertSeedCustomer(conn, "Kabir Verma", "kabir@bank.local", "9876543212",
                    "Guntur, AP", PasswordUtil.hashPassword("Kabir@123"), Role.CUSTOMER, now);

            // Seed demo accounts
            insertSeedAccount(conn, "SB100001", aaravId, AccountType.SAVINGS, "15000.00", today);
            insertSeedAccount(conn, "CA100002", aaravId, AccountType.CURRENT, "5000.00", today);
            insertSeedAccount(conn, "SB100003", meeraId, AccountType.SAVINGS, "25000.00", today);
            insertSeedAccount(conn, "CA100004", kabirId, AccountType.CURRENT, "12000.00", today);

            // Seed opening deposit ledger records
            insertSeedTransaction(conn, "SB100001", TransactionType.DEPOSIT, "15000.00", "15000.00",
                    "Opening deposit", now);
            insertSeedTransaction(conn, "CA100002", TransactionType.DEPOSIT, "5000.00", "5000.00",
                    "Opening deposit", now);
            insertSeedTransaction(conn, "SB100003", TransactionType.DEPOSIT, "25000.00", "25000.00",
                    "Opening deposit", now);
            insertSeedTransaction(conn, "CA100004", TransactionType.DEPOSIT, "12000.00", "12000.00",
                    "Opening deposit", now);

            conn.commit();
        } catch (SQLException ex) {
            conn.rollback();
            throw ex;
        } finally {
            conn.setAutoCommit(originalAutoCommit);
        }
    }

    private long insertSeedCustomer(Connection conn, String name, String email, String phone,
                                    String address, String passwordHash, Role role, String createdAt)
            throws SQLException {
        String sql = """
                INSERT INTO customers(name, email, phone, address, password_hash, role, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, email);
            ps.setString(3, phone);
            ps.setString(4, address);
            ps.setString(5, passwordHash);
            ps.setString(6, role.name());
            ps.setString(7, createdAt);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        throw new SQLException("Failed to obtain generated customer ID during seed.");
    }

    private void insertSeedAccount(Connection conn, String accountNumber, long customerId,
                                   AccountType type, String balance, String openedOn)
            throws SQLException {
        String sql = """
                INSERT INTO accounts(account_number, customer_id, account_type, balance, status, opened_on)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, accountNumber);
            ps.setLong(2, customerId);
            ps.setString(3, type.name());
            ps.setString(4, balance);
            ps.setString(5, AccountStatus.ACTIVE.name());
            ps.setString(6, openedOn);
            ps.executeUpdate();
        }
    }

    private void insertSeedTransaction(Connection conn, String accountNumber, TransactionType type,
                                       String amount, String balanceAfter, String description,
                                       String createdAt) throws SQLException {
        String sql = """
                INSERT INTO transactions(account_number, type, amount, balance_after, description, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, accountNumber);
            ps.setString(2, type.name());
            ps.setString(3, amount);
            ps.setString(4, balanceAfter);
            ps.setString(5, description);
            ps.setString(6, createdAt);
            ps.executeUpdate();
        }
    }
}

