// Author: Shaurya Kumar
// Concept: Multithreading via Runnable + ScheduledExecutorService background thread
package com.bank.service;

import com.bank.exception.BankException;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Periodically credits monthly interest to active savings accounts on a background thread.
 * Also supports a synchronous {@link #runNow()} trigger for admin demos.
 */
public class InterestService implements Runnable {

    private final BankService bankService;
    private ScheduledExecutorService scheduler;

    public InterestService(BankService bankService) {
        this.bankService = bankService;
    }

    /**
     * Starts a daemon background scheduler running this Runnable periodically.
     */
    public synchronized void startScheduler(long intervalMinutes) {
        if (scheduler != null && !scheduler.isShutdown()) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "interest-scheduler-thread");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this, intervalMinutes, intervalMinutes, TimeUnit.MINUTES);
    }

    public synchronized void stopScheduler() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    @Override
    public void run() {
        try {
            runNow();
        } catch (BankException ex) {
            System.err.println("Scheduled interest calculation failed: " + ex.getMessage());
        }
    }

    /**
     * Manual trigger used by the Admin UI and tests to apply interest immediately.
     */
    public int runNow() throws BankException {
        return bankService.applyMonthlyInterestToAllSavings();
    }
}

