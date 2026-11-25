// Author: Sahil Raj
// Concept: Inter-thread communication using wait() and notify() (Producer-Consumer queue)
package com.bank.service;

import com.bank.util.BankLogger;

import java.util.LinkedList;
import java.util.Queue;

/**
 * Small producer-consumer notification queue demonstrating wait()/notify()
 * between transaction producer threads and a background audit logger thread.
 */
public class TransactionNotificationQueue {

    private final Queue<String> queue = new LinkedList<>();
    private final int capacity;
    private boolean stopped = false;

    public TransactionNotificationQueue(int capacity) {
        this.capacity = Math.max(1, capacity);
    }

    /**
     * Producer puts a notification message, waiting if the bounded queue is full.
     */
    public synchronized void publish(String message) throws InterruptedException {
        while (queue.size() >= capacity && !stopped) {
            wait();
        }
        if (stopped) {
            return;
        }
        queue.add(message);
        notifyAll();
    }

    /**
     * Consumer takes the next notification message, waiting if the queue is empty.
     * Returns null once stopped and drained.
     */
    public synchronized String take() throws InterruptedException {
        while (queue.isEmpty() && !stopped) {
            wait();
        }
        if (queue.isEmpty() && stopped) {
            return null;
        }
        String msg = queue.poll();
        notifyAll();
        return msg;
    }

    public synchronized void shutdown() {
        this.stopped = true;
        notifyAll();
    }

    /**
     * Starts a daemon worker thread that drains notifications into the BankLogger.
     */
    public Thread startConsumerThread(BankLogger logger) {
        Thread worker = new Thread(() -> {
            try {
                while (true) {
                    String msg = take();
                    if (msg == null) {
                        break;
                    }
                    logger.log("NOTIFY: " + msg);
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }, "txn-notification-logger");
        worker.setDaemon(true);
        worker.start();
        return worker;
    }
}

