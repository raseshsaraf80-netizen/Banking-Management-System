// Author: Shaurya Kumar
// Concept: JUnit 5 tests for BankService, OOP polymorphism, exceptions, atomic rollback, and generics
package com.bank;

import com.bank.dao.AccountDao;
import com.bank.dao.SqliteAccountDao;
import com.bank.dao.SqliteCustomerDao;
import com.bank.dao.SqliteTransactionDao;
import com.bank.dao.TransactionDao;
import com.bank.exception.AccountLimitException;
import com.bank.exception.AccountNotFoundException;
import com.bank.exception.InsufficientFundsException;
import com.bank.exception.InvalidAmountException;
import com.bank.exception.UnauthorizedException;
import com.bank.generic.AccountBalanceFilter;
import com.bank.generic.GenericUtils;
import com.bank.generic.Pair;
import com.bank.model.Account;
import com.bank.model.AccountStatus;
import com.bank.model.AccountType;
import com.bank.model.Customer;
import com.bank.model.Transaction;
import com.bank.service.AuthService;
import com.bank.service.BankService;
import com.bank.service.InterestService;
import com.bank.util.BankLogger;
import com.bank.util.DatabaseManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BankServiceTest {

    @TempDir
    Path tempDir;

    private DatabaseManager dbManager;
    private AccountDao accountDao;
    private TransactionDao transactionDao;
    private AuthService authService;
    private BankService bankService;
    private BankLogger logger;
    private Customer aarav;
    private Customer meera;
    private Customer admin;

    @BeforeEach
    void setUp() throws Exception {
        dbManager = new DatabaseManager(tempDir.resolve("bank-test.db").toString());
        dbManager.initializeDatabase();
        SqliteCustomerDao customerDao = new SqliteCustomerDao(dbManager);
        accountDao = new SqliteAccountDao(dbManager);
        transactionDao = new SqliteTransactionDao(dbManager);
        logger = new BankLogger(tempDir.resolve("test-bank.log").toString());
        authService = new AuthService(customerDao);
        bankService = new BankService(dbManager, accountDao, transactionDao, logger);

        aarav = authService.login("aarav@bank.local", "Aarav@123");
        meera = authService.login("meera@bank.local", "Meera@123");
        admin = authService.login("admin@bank.local", "Admin@123");
    }

    @Test
    void depositAndWithdrawHappyPath() throws Exception {
        bankService.deposit(aarav, "SB100001", new BigDecimal("2500.00"), "Salary credit");
        assertEquals(new BigDecimal("17500.00"), bankService.getBalance(aarav, "SB100001"));

        bankService.withdraw(aarav, "SB100001", new BigDecimal("1500.00"), "ATM");
        assertEquals(new BigDecimal("16000.00"), bankService.getBalance(aarav, "SB100001"));
    }

    @Test
    void savingsMinimumBalanceAndWithdrawalLimitEnforced() throws Exception {
        // SB100001 starts at 15000.00; trying to drop below 1000.00 throws AccountLimitException
        assertThrows(AccountLimitException.class, () ->
                bankService.withdraw(aarav, "SB100001", new BigDecimal("14500.00"), "Too much"));

        // Exceeding balance throws InsufficientFundsException
        assertThrows(InsufficientFundsException.class, () ->
                bankService.withdraw(aarav, "SB100001", new BigDecimal("50000.00"), "Over balance"));

        // Monthly withdrawal limit = 5
        for (int i = 0; i < 5; i++) {
            bankService.withdraw(aarav, "SB100001", new BigDecimal("100.00"), "Draw " + i);
        }
        assertThrows(AccountLimitException.class, () ->
                bankService.withdraw(aarav, "SB100001", new BigDecimal("100.00"), "6th draw"));
    }

    @Test
    void currentAccountAllowsOverdraftUpToLimit() throws Exception {
        // CA100002 starts at 5000.00; overdraft limit is 10000.00 -> max withdrawal 15000.00
        bankService.withdraw(aarav, "CA100002", new BigDecimal("12000.00"), "Vendor payment");
        assertEquals(new BigDecimal("-7000.00"), bankService.getBalance(aarav, "CA100002"));

        assertThrows(AccountLimitException.class, () ->
                bankService.withdraw(aarav, "CA100002", new BigDecimal("4000.00"), "Exceed overdraft"));
    }

    @Test
    void exceptionPathsForInvalidAmountMissingAccountAndAuthorization() {
        assertThrows(InvalidAmountException.class, () ->
                bankService.deposit(aarav, "SB100001", new BigDecimal("-50"), "Negative"));
        assertThrows(AccountNotFoundException.class, () ->
                bankService.getBalance(aarav, "SB999999"));
        assertThrows(UnauthorizedException.class, () ->
                bankService.deposit(aarav, "SB100003", new BigDecimal("100"), "Meera's account"));
    }

    @Test
    void transferAtomicityRollsBackWhenCreditSideFails() throws Exception {
        // Wrap TransactionDao so the 2nd insert (credit side TRANSFER_IN) fails with SQLException
        TransactionDao failingCreditDao = new SqliteTransactionDao(dbManager) {
            private int insertCount = 0;

            @Override
            public Transaction save(Connection conn, Transaction txn) throws SQLException {
                insertCount++;
                if (insertCount == 2) {
                    throw new SQLException("Simulated DB failure on credit side");
                }
                return super.save(conn, txn);
            }
        };

        BankService atomicService = new BankService(dbManager, accountDao, failingCreditDao, logger);
        BigDecimal beforeFrom = atomicService.getBalance(aarav, "SB100001");
        BigDecimal beforeTo = atomicService.getBalance(meera, "SB100003");

        assertThrows(Exception.class, () ->
                atomicService.transfer(aarav, "SB100001", "SB100003", new BigDecimal("2000.00"), "Rent"));

        // Verify both in-memory cache and SQLite database rolled back completely
        assertEquals(beforeFrom, atomicService.getBalance(aarav, "SB100001"));
        assertEquals(beforeTo, atomicService.getBalance(meera, "SB100003"));
        assertEquals(beforeFrom, accountDao.findById("SB100001").orElseThrow().getBalance());
        assertEquals(beforeTo, accountDao.findById("SB100003").orElseThrow().getBalance());
    }

    @Test
    void interestServiceCreditsSavingsAccountsOnly() throws Exception {
        InterestService interestService = new InterestService(bankService);
        int credited = interestService.runNow();
        assertEquals(2, credited); // SB100001 (Aarav) and SB100003 (Meera)

        // 15000 * 0.035 / 12 = 43.75
        assertEquals(new BigDecimal("15043.75"), bankService.getBalance(aarav, "SB100001"));
        // Current account earns zero interest
        assertEquals(new BigDecimal("5000.00"), bankService.getBalance(aarav, "CA100002"));
    }

    @Test
    void collectionsGenericsAndIteratorCloseAccountWork() throws Exception {
        Account newAcc = bankService.openAccount(aarav, AccountType.CURRENT, new BigDecimal("500.00"));
        assertTrue(bankService.getRecentActivity().size() >= 1);

        TreeMap<LocalDate, List<Transaction>> grouped =
                bankService.getStatementGroupedByDate(aarav, newAcc.getAccountNumber());
        assertFalse(grouped.isEmpty());

        List<Account> aaravAccounts = bankService.getAccountsForCustomer(aarav.getCustomerId());
        double total = GenericUtils.totalBalance(aaravAccounts);
        assertEquals(20500.00, total, 0.01);

        List<Account> highBal = GenericUtils.filterList(
                aaravAccounts, new AccountBalanceFilter(new BigDecimal("10000.00")));
        assertEquals(1, highBal.size());

        Pair<Customer, List<Account>> pair = new Pair<>(aarav, aaravAccounts);
        assertEquals("Aarav Sharma", pair.getKey().getName());

        // Withdraw all and close account -> Iterator removes it from cache
        bankService.withdraw(aarav, newAcc.getAccountNumber(), new BigDecimal("500.00"), "Empty");
        bankService.closeAccount(aarav, newAcc.getAccountNumber());
        assertEquals(AccountStatus.CLOSED,
                accountDao.findById(newAcc.getAccountNumber()).orElseThrow().getStatus());
        assertThrows(AccountLimitException.class, () ->
                bankService.deposit(aarav, newAcc.getAccountNumber(), new BigDecimal("100.00"), "Closed"));
    }
}

