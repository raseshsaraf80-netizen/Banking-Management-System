// Author: Sahil Raj
// Concept: Multithreading demo — 50 concurrent threads, Thread states, join(), deadlock-free transfers, wait()/notify()
package com.bank;

import com.bank.dao.AccountDao;
import com.bank.dao.SqliteAccountDao;
import com.bank.dao.SqliteCustomerDao;
import com.bank.dao.SqliteTransactionDao;
import com.bank.dao.TransactionDao;
import com.bank.model.Customer;
import com.bank.service.AuthService;
import com.bank.service.BankService;
import com.bank.service.TransactionNotificationQueue;
import com.bank.util.BankLogger;
import com.bank.util.DatabaseManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Demonstrates synchronized concurrency, thread lifecycle states, join(),
 * deadlock-free opposing transfers, and wait()/notify() inter-thread communication.
 */
class ConcurrencyDemo {

    @TempDir
    Path tempDir;

    @Test
    void fiftyConcurrentDepositsAndWithdrawalsPreserveExactBalance() throws Exception {
        DatabaseManager dbManager = new DatabaseManager(tempDir.resolve("concurrency.db").toString());
        dbManager.initializeDatabase();
        AccountDao accountDao = new SqliteAccountDao(dbManager);
        TransactionDao txnDao = new SqliteTransactionDao(dbManager);
        BankLogger logger = new BankLogger(tempDir.resolve("concurrency.log").toString());
        AuthService authService = new AuthService(new SqliteCustomerDao(dbManager));
        BankService bankService = new BankService(dbManager, accountDao, txnDao, logger);

        Customer aarav = authService.login("aarav@bank.local", "Aarav@123");
        // CA100002 starts at 5000.00. Run 25 deposits of +100 and 25 withdrawals of -50 => net +1250.00 = 6250.00
        List<Thread> threads = new ArrayList<>();
        List<Throwable> errors = new ArrayList<>();

        for (int i = 0; i < 25; i++) {
            Thread dep = new Thread(() -> {
                try {
                    bankService.deposit(aarav, "CA100002", new BigDecimal("100.00"), "Concurrent deposit");
                } catch (Throwable t) {
                    synchronized (errors) {
                        errors.add(t);
                    }
                }
            }, "deposit-worker-" + i);

            Thread wdr = new Thread(() -> {
                try {
                    bankService.withdraw(aarav, "CA100002", new BigDecimal("50.00"), "Concurrent withdraw");
                } catch (Throwable t) {
                    synchronized (errors) {
                        errors.add(t);
                    }
                }
            }, "withdraw-worker-" + i);

            // Thread lifecycle state NEW before start()
            assertEquals(Thread.State.NEW, dep.getState());
            threads.add(dep);
            threads.add(wdr);
        }

        for (Thread t : threads) {
            t.start();
        }
        for (Thread t : threads) {
            t.join();
            assertEquals(Thread.State.TERMINATED, t.getState());
        }

        assertTrue(errors.isEmpty(), "Unexpected worker errors: " + errors);
        assertEquals(new BigDecimal("6250.00"), bankService.getBalance(aarav, "CA100002"));
        assertEquals(new BigDecimal("6250.00"), accountDao.findById("CA100002").orElseThrow().getBalance());
    }

    @Test
    void opposingConcurrentTransfersNeverDeadlock() throws Exception {
        DatabaseManager dbManager = new DatabaseManager(tempDir.resolve("deadlock-free.db").toString());
        dbManager.initializeDatabase();
        BankLogger logger = new BankLogger(tempDir.resolve("transfer-conc.log").toString());
        AuthService authService = new AuthService(new SqliteCustomerDao(dbManager));
        BankService bankService = new BankService(
                dbManager, new SqliteAccountDao(dbManager), new SqliteTransactionDao(dbManager), logger);

        Customer admin = authService.login("admin@bank.local", "Admin@123");
        List<Thread> workers = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            workers.add(new Thread(() -> {
                try {
                    bankService.transfer(admin, "SB100001", "SB100003", new BigDecimal("100.00"), "A->B");
                } catch (Exception ignored) {
                }
            }));
            workers.add(new Thread(() -> {
                try {
                    bankService.transfer(admin, "SB100003", "SB100001", new BigDecimal("100.00"), "B->A");
                } catch (Exception ignored) {
                }
            }));
        }
        for (Thread w : workers) {
            w.start();
        }
        for (Thread w : workers) {
            w.join();
        }
        // Equal opposing transfers leave combined balance conserved (15000 + 25000 = 40000)
        BigDecimal total = bankService.getBalance(admin, "SB100001")
                .add(bankService.getBalance(admin, "SB100003"));
        assertEquals(new BigDecimal("40000.00"), total);
    }

    @Test
    void waitNotifyQueueDeliversAllNotificationsToLogger() throws Exception {
        Path logPath = tempDir.resolve("notify.log");
        BankLogger logger = new BankLogger(logPath.toString());
        TransactionNotificationQueue queue = new TransactionNotificationQueue(4);
        Thread consumer = queue.startConsumerThread(logger);

        Thread producer = new Thread(() -> {
            try {
                for (int i = 1; i <= 5; i++) {
                    queue.publish("TXN #" + i + " completed");
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        });
        producer.start();
        producer.join();

        queue.shutdown();
        consumer.join(2000);

        List<String> lines = Files.readAllLines(logPath);
        assertEquals(5, lines.size());
    }
}

