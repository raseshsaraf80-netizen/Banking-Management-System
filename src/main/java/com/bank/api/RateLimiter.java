// Author: Rasesh Saraf
// Concept: Simple per-IP request counter for rate limiting login and chat routes
package com.bank.api;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory per-IP rate limiter protecting login and chatbot endpoints.
 */
public class RateLimiter {

    private static class Counter {
        long windowStart;
        int count;

        Counter(long windowStart) {
            this.windowStart = windowStart;
        }
    }

    private final int maxRequests;
    private final long windowSeconds;
    private final Map<String, Counter> counters = new ConcurrentHashMap<>();

    public RateLimiter(int maxRequests, long windowSeconds) {
        this.maxRequests = maxRequests;
        this.windowSeconds = windowSeconds;
    }

    public boolean allow(String ip) {
        long now = Instant.now().getEpochSecond();
        Counter c = counters.computeIfAbsent(ip, k -> new Counter(now));
        synchronized (c) {
            if (now - c.windowStart >= windowSeconds) {
                c.windowStart = now;
                c.count = 0;
            }
            if (c.count >= maxRequests) {
                return false;
            }
            c.count++;
            return true;
        }
    }
}
