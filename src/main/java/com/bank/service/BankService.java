// Author: Shaurya Kumar
// Concept: Service layer, Collections (HashMap, ArrayList, LinkedList, TreeMap, Iterator),
//          Atomic JDBC transactions, nested try/catch/finally, deadlock-free lock ordering
package com.bank.service;

import com.bank.dao.AccountDao;
import com.bank.dao.TransactionDao;
import com.bank.exception.AccountLimitException;
import com.bank.exception.AccountNotFoundException;
import com.bank.exception.BankException;
import com.bank.exception.InvalidAmountException;
import com.bank.exception.UnauthorizedException;
import com.bank.exception.ValidationException;
import com.bank.model.Account;
import com.bank.model.AccountStatus;
import com.bank.model.AccountType;
import com.bank.model.CurrentAccount;
import com.bank.model.Customer;
import com.bank.model.Role;
import com.bank.model.SavingsAccount;
import com.bank.model.Transaction;
import com.bank.model.TransactionType;
import com.bank.util.BankLogger;
import com.bank.util.DatabaseManager;
import com.bank.util.IdGenerator;
import com.bank.util.Validator;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Core banking business logic shared by the JavaFX desktop client and the Javalin REST API.
 */
public class BankService {

    private static final int RECENT_ACTIVITY_LIMIT = 10;

    private final DatabaseManager dbManager;
    private final AccountDao accountDao;
    private final TransactionDao transactionDao;
    private final BankLogger logger;

    // Concept: HashMap cache for O(1) account lookups and consistent per-account lock objects
    private final Map<String, Account> accountCache = new HashMap<>();

    // Concept: LinkedList acting as a bounded recent-activity queue (last 10 transactions)
    private final LinkedList<Transaction> recentActivity = new LinkedList<>();

    public BankService(DatabaseManager dbManager, AccountDao accountDao,
                       TransactionDao transactionDao, BankLogger logger) throws BankException {
        this.dbManager = dbManager;
        this.accountDao = accountDao;
        this.transactionDao = transactionDao;
        this.logger = logger;
        warmCache();
    }

    private synchronized void warmCache() throws BankException {
        accountCache.clear();
        for (Account account : accountDao.findAll()) {
            accountCache.put(account.getAccountNumber(), account);
        }
    }

    public synchronized Account openAccount(Customer actor, AccountType type, BigDecimal initialDeposit)
            throws BankException {
        if (actor == null) {
            throw new UnauthorizedException("Authentication required.");
        }
        if (type == null) {
            throw new ValidationException("Account type is required.");
        }
        BigDecimal openingAmount = initialDeposit == null ? BigDecimal.ZERO : initialDeposit;
        if (openingAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidAmountException("Initial deposit cannot be negative.");
        }
        if (openingAmount.compareTo(BigDecimal.ZERO) > 0) {
            Validator.validateAmount(openingAmount);
        }
        if (type == AccountType.SAVINGS && openingAmount.compareTo(SavingsAccount.MIN_BALANCE) < 0) {
            throw new AccountLimitException(
                    "Savings account requires an opening deposit of at least " + SavingsAccount.MIN_BALANCE);
        }

        String accNo = IdGenerator.nextAccountNumber(type);
        Account account = (type == AccountType.SAVINGS)
                ? new SavingsAccount(accNo, actor.getCustomerId(), openingAmount, LocalDate.now(), AccountStatus.ACTIVE)
                : new CurrentAccount(accNo, actor.getCustomerId(), openingAmount, LocalDate.now(), AccountStatus.ACTIVE);

        accountDao.save(account);
        accountCache.put(accNo, account);

        if (openingAmount.compareTo(BigDecimal.ZERO) > 0) {
            Transaction openingTxn = new Transaction(
                    accNo, TransactionType.DEPOSIT, openingAmount, account.getBalance(), "Opening deposit");
            Transaction savedTxn = transactionDao.save(openingTxn);
            recordRecentActivity(savedTxn);
        }
        logger.log("OPEN_ACCOUNT " + accNo + " type=" + type + " customerId=" + actor.getCustomerId());
        return account;
    }

    public void closeAccount(Customer actor, String accountNumber) throws BankException {
        Account account = getAuthorizedAccount(actor, accountNumber);
        synchronized (account) {
            if (account.getBalance().compareTo(BigDecimal.ZERO) != 0) {
                throw new AccountLimitException(
                        "Cannot close account with non-zero balance (" + account.getBalance() + ").");
            }
            account.setStatus(AccountStatus.CLOSED);
            accountDao.updateStatus(accountNumber, AccountStatus.CLOSED);
            purgeClosedAccountsFromCache();
            logger.log("CLOSE_ACCOUNT " + accountNumber + " by customerId=" + actor.getCustomerId());
        }
    }

    public void updateAccountStatus(Customer admin, String accountNumber, AccountStatus status)
            throws BankException {
        if (admin == null || admin.getRole() != Role.ADMIN) {
            throw new UnauthorizedException("Only administrators can change account status.");
        }
        Account account = getAccountOrThrow(accountNumber);
        synchronized (account) {
            account.setStatus(status);
            accountDao.updateStatus(accountNumber, status);
            if (status == AccountStatus.CLOSED) {
                purgeClosedAccountsFromCache();
            }
            logger.log("STATUS_CHANGE " + accountNumber + " -> " + status + " by admin=" + admin.getCustomerId());
        }
    }

    /**
     * Concept: Iterator safely removing CLOSED accounts from HashMap during iteration.
     */
    public synchronized int purgeClosedAccountsFromCache() {
        int removed = 0;
        Iterator<Map.Entry<String, Account>> it = accountCache.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Account> entry = it.next();
            if (entry.getValue().getStatus() == AccountStatus.CLOSED) {
                it.remove();
                removed++;
            }
        }
        return removed;
    }

    public Transaction deposit(Customer actor, String accountNumber, BigDecimal amount, String description)
            throws BankException {
        Account account = getAuthorizedAccount(actor, accountNumber);
        Validator.validateAmount(amount);
        String desc = (description == null || description.isBlank()) ? "Deposit" : description.trim();

        synchronized (account) {
            BigDecimal previousBalance = account.getBalance();
            account.deposit(amount, desc);

            Connection conn = null;
            try {
                conn = dbManager.getConnection();
                conn.setAutoCommit(false);
                accountDao.updateBalance(conn, account.getAccountNumber(), account.getBalance());
                Transaction txn = transactionDao.save(conn, new Transaction(
                        account.getAccountNumber(), TransactionType.DEPOSIT, amount, account.getBalance(), desc));
                conn.commit();
                recordRecentActivity(txn);
                logger.log("DEPOSIT " + accountNumber + " +" + amount + " bal=" + account.getBalance());
                return txn;
            } catch (SQLException ex) {
                account.setBalance(previousBalance);
                rollbackQuietly(conn);
                throw new BankException("Deposit failed and was rolled back: " + ex.getMessage(), ex);
            } finally {
                restoreAndClose(conn);
            }
        }
    }

    public Transaction withdraw(Customer actor, String accountNumber, BigDecimal amount, String description)
            throws BankException {
        Account account = getAuthorizedAccount(actor, accountNumber);
        Validator.validateAmount(amount);
        String desc = (description == null || description.isBlank()) ? "Withdrawal" : description.trim();

        synchronized (account) {
            BigDecimal previousBalance = account.getBalance();
            int prevWithdrawals = (account instanceof SavingsAccount sa) ? sa.getMonthlyWithdrawals() : 0;

            account.withdraw(amount);

            Connection conn = null;
            try {
                conn = dbManager.getConnection();
                conn.setAutoCommit(false);
                accountDao.updateBalance(conn, account.getAccountNumber(), account.getBalance());
                Transaction txn = transactionDao.save(conn, new Transaction(
                        account.getAccountNumber(), TransactionType.WITHDRAWAL, amount, account.getBalance(), desc));
                conn.commit();
                recordRecentActivity(txn);
                logger.log("WITHDRAW " + accountNumber + " -" + amount + " bal=" + account.getBalance());
                return txn;
            } catch (SQLException ex) {
                account.setBalance(previousBalance);
                if (account instanceof SavingsAccount sa) {
                    sa.setMonthlyWithdrawals(prevWithdrawals);
                }
                rollbackQuietly(conn);
                throw new BankException("Withdrawal failed and was rolled back: " + ex.getMessage(), ex);
            } finally {
                restoreAndClose(conn);
            }
        }
    }

    /**
     * Atomic fund transfer between two accounts using consistent lock ordering,
     * nested try/catch/finally blocks, and JDBC transaction rollback on any failure.
     */
    public void transfer(Customer actor, String fromAccountNumber, String toAccountNumber,
                         BigDecimal amount, String description) throws BankException {
        Validator.validateAccountNumber(fromAccountNumber);
        Validator.validateAccountNumber(toAccountNumber);
        Validator.validateAmount(amount);

        if (fromAccountNumber.equals(toAccountNumber)) {
            throw new ValidationException("Source and destination accounts cannot be the same.");
        }

        Account from = getAuthorizedAccount(actor, fromAccountNumber);
        Account to = getAccountOrThrow(toAccountNumber);
        String desc = (description == null || description.isBlank()) ? "Fund transfer" : description.trim();

        // Consistent lock ordering by accountNumber prevents AB-BA deadlocks
        Account firstLock = from.getAccountNumber().compareTo(to.getAccountNumber()) < 0 ? from : to;
        Account secondLock = (firstLock == from) ? to : from;

        boolean succeeded = false;
        synchronized (firstLock) {
            synchronized (secondLock) {
                BigDecimal prevFromBal = from.getBalance();
                BigDecimal prevToBal = to.getBalance();
                int prevWithdrawals = (from instanceof SavingsAccount sa) ? sa.getMonthlyWithdrawals() : 0;

                Connection conn = null;
                try {
                    // Inner try performs domain validation and balance calculations
                    try {
                        from.withdraw(amount);
                        to.deposit(amount, "Transfer from " + fromAccountNumber);
                    } catch (BankException domainEx) {
                        // Restore in-memory state if credit failed after debit
                        from.setBalance(prevFromBal);
                        to.setBalance(prevToBal);
                        if (from instanceof SavingsAccount sa) {
                            sa.setMonthlyWithdrawals(prevWithdrawals);
                        }
                        throw domainEx;
                    }

                    conn = dbManager.getConnection();
                    conn.setAutoCommit(false);

                    accountDao.updateBalance(conn, from.getAccountNumber(), from.getBalance());
                    Transaction outTxn = transactionDao.save(conn, new Transaction(
                            from.getAccountNumber(), TransactionType.TRANSFER_OUT, amount,
                            from.getBalance(), desc + " -> " + toAccountNumber));

                    accountDao.updateBalance(conn, to.getAccountNumber(), to.getBalance());
                    Transaction inTxn = transactionDao.save(conn, new Transaction(
                            to.getAccountNumber(), TransactionType.TRANSFER_IN, amount,
                            to.getBalance(), desc + " <- " + fromAccountNumber));

                    conn.commit();
                    recordRecentActivity(outTxn);
                    recordRecentActivity(inTxn);
                    succeeded = true;
                } catch (SQLException | RuntimeException ex) {
                    from.setBalance(prevFromBal);
                    to.setBalance(prevToBal);
                    if (from instanceof SavingsAccount sa) {
                        sa.setMonthlyWithdrawals(prevWithdrawals);
                    }
                    rollbackQuietly(conn);
                    throw new BankException("Transfer failed and was rolled back: " + ex.getMessage(), ex);
                } finally {
                    restoreAndClose(conn);
                    logger.log("TRANSFER " + fromAccountNumber + " -> " + toAccountNumber
                            + " amount=" + amount + " status=" + (succeeded ? "SUCCESS" : "FAILED"));
                }
            }
        }
    }

    public BigDecimal getBalance(Customer actor, String accountNumber) throws BankException {
        return getAuthorizedAccount(actor, accountNumber).getBalance();
    }

    public List<Account> getAccountsForCustomer(long customerId) throws BankException {
        List<Account> accounts = accountDao.findByCustomerId(customerId);
        synchronized (this) {
            for (Account account : accounts) {
                accountCache.putIfAbsent(account.getAccountNumber(), account);
            }
        }
        return accounts;
    }

    public List<Account> getAllAccounts(Customer admin) throws BankException {
        if (admin == null || admin.getRole() != Role.ADMIN) {
            throw new UnauthorizedException("Admin privileges required.");
        }
        return accountDao.findAll();
    }

    public List<Account> searchAccounts(Customer admin, String keyword) throws BankException {
        if (admin == null || admin.getRole() != Role.ADMIN) {
            throw new UnauthorizedException("Admin privileges required.");
        }
        return accountDao.searchByKeyword(keyword);
    }

    public ArrayList<Transaction> getMiniStatement(Customer actor, String accountNumber, int limit)
            throws BankException {
        getAuthorizedAccount(actor, accountNumber);
        return new ArrayList<>(transactionDao.findRecentByAccountNumber(accountNumber, limit));
    }

    public ArrayList<Transaction> getStatement(Customer actor, String accountNumber,
                                               LocalDate from, LocalDate to) throws BankException {
        getAuthorizedAccount(actor, accountNumber);
        if (from == null && to == null) {
            return new ArrayList<>(transactionDao.findByAccountNumber(accountNumber));
        }
        return new ArrayList<>(transactionDao.findByAccountAndDateRange(accountNumber, from, to));
    }

    /**
     * Concept: TreeMap sorts statement transactions chronologically by LocalDate.
     */
    public TreeMap<LocalDate, List<Transaction>> getStatementGroupedByDate(
            Customer actor, String accountNumber) throws BankException {
        List<Transaction> txns = getStatement(actor, accountNumber, null, null);
        TreeMap<LocalDate, List<Transaction>> grouped = new TreeMap<>();
        for (Transaction t : txns) {
            LocalDate day = t.getTimestamp().toLocalDate();
            grouped.computeIfAbsent(day, k -> new ArrayList<>()).add(t);
        }
        return grouped;
    }

    public synchronized List<Transaction> getRecentActivity() {
        return new ArrayList<>(recentActivity);
    }

    /**
     * Applies monthly interest to all active savings accounts. Returns number of accounts credited.
     */
    public int applyMonthlyInterestToAllSavings() throws BankException {
        int credited = 0;
        List<Account> allAccounts = accountDao.findAll();
        for (Account dbAcc : allAccounts) {
            Account account = getAccountOrThrow(dbAcc.getAccountNumber());
            if (!(account instanceof SavingsAccount)) {
                continue;
            }
            synchronized (account) {
                BigDecimal interest = account.calculateMonthlyInterest();
                if (interest.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                BigDecimal prevBal = account.getBalance();
                account.deposit(interest, "Monthly savings interest");
                Connection conn = null;
                try {
                    conn = dbManager.getConnection();
                    conn.setAutoCommit(false);
                    accountDao.updateBalance(conn, account.getAccountNumber(), account.getBalance());
                    Transaction txn = transactionDao.save(conn, new Transaction(
                            account.getAccountNumber(), TransactionType.INTEREST,
                            interest, account.getBalance(), "Monthly interest credit (3.5% p.a.)"));
                    conn.commit();
                    recordRecentActivity(txn);
                    logger.log("INTEREST " + account.getAccountNumber() + " +" + interest);
                    credited++;
                } catch (SQLException ex) {
                    account.setBalance(prevBal);
                    rollbackQuietly(conn);
                    throw new BankException("Interest credit failed: " + ex.getMessage(), ex);
                } finally {
                    restoreAndClose(conn);
                }
            }
        }
        return credited;
    }

    public Account getAccountOrThrow(String accountNumber) throws BankException {
        synchronized (this) {
            Account cached = accountCache.get(accountNumber);
            if (cached != null) {
                return cached;
            }
        }
        Account fromDb = accountDao.findById(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountNumber));
        synchronized (this) {
            accountCache.putIfAbsent(accountNumber, fromDb);
            return accountCache.get(accountNumber);
        }
    }

    public Account getAuthorizedAccount(Customer actor, String accountNumber) throws BankException {
        if (actor == null) {
            throw new UnauthorizedException("Authentication required.");
        }
        Account account = getAccountOrThrow(accountNumber);
        if (actor.getRole() != Role.ADMIN && account.getCustomerId() != actor.getCustomerId()) {
            throw new UnauthorizedException("You do not have permission to access account " + accountNumber);
        }
        return account;
    }

    private synchronized void recordRecentActivity(Transaction txn) {
        recentActivity.addFirst(txn);
        while (recentActivity.size() > RECENT_ACTIVITY_LIMIT) {
            recentActivity.removeLast();
        }
    }

    private void rollbackQuietly(Connection conn) {
        if (conn != null) {
            try {
                conn.rollback();
            } catch (SQLException ignored) {
                // Keep original exception
            }
        }
    }

    private void restoreAndClose(Connection conn) {
        if (conn != null) {
            try {
                conn.setAutoCommit(true);
            } catch (SQLException ignored) {
                // Closing next
            }
            try {
                conn.close();
            } catch (SQLException ignored) {
                // Best effort close
            }
        }
    }
}

