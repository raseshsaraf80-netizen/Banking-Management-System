// Author: Shaurya Kumar
// Concept: Static counter & synchronized ID generation
package com.bank.util;

import com.bank.model.AccountType;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Generates unique account numbers (e.g., SB100001, CA100002).
 */
public final class IdGenerator {

    private static final AtomicLong COUNTER = new AtomicLong(100100L);

    private IdGenerator() {
    }

    /**
     * Advances the counter above any existing numeric suffix loaded from SQLite.
     */
    public static void ensureMinimumCounter(long minSequence) {
        COUNTER.updateAndGet(current -> Math.max(current, minSequence));
    }

    public static String nextAccountNumber(AccountType type) {
        String prefix = (type == AccountType.SAVINGS) ? "SB" : "CA";
        return prefix + COUNTER.incrementAndGet();
    }
}

