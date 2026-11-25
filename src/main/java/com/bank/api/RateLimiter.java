// Author: Rasesh Saraf
// Concept: Thread-safe sliding-window in-memory rate limiter for login and chat routes
package com.bank.api;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory per-IP rate limiter protecting login and chatbot endpoints.
 */
public class RateLimiter {

    private final int maxRequests;
    private final long windowSeconds;
    private final Map<String, Deque<Long>> requestLog = new ConcurrentHashMap<>();

    public RateLimiter(int maxRequests, long windowSeconds) {
        this.maxRequests = maxRequests;
        this.windowSeconds = windowSeconds;
    }

    public boolean allow(String key) {
        long now = Instant.now().getEpochSecond();
        Deque<Long> timestamps = requestLog.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && (now - timestamps.peekFirst()) >= windowSeconds) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= maxRequests) {
                return false;
            }
            timestamps.addLast(now);
            return true;
        }
    }
}

