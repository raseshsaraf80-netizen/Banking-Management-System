// Author: Rasesh Saraf
// Concept: Simple in-memory session token store using UUID and ConcurrentHashMap
package com.bank.api;

import com.bank.exception.InvalidCredentialsException;
import com.bank.model.Customer;
import com.bank.model.Role;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generates and verifies random UUID bearer tokens for logged-in API users.
 */
public class TokenUtil {

    private static final long TOKEN_TTL_SECONDS = 8 * 3600L;

    public record TokenClaims(long customerId, Role role, long expiresAtEpoch) {
    }

    private final Map<String, TokenClaims> activeSessions = new ConcurrentHashMap<>();

    public TokenUtil(String ignoredSecret) {
        // Simple prototype session store; constructor keeps compatibility with ApiServer config.
    }

    public String createToken(Customer customer) {
        String token = UUID.randomUUID().toString();
        long expiresAt = Instant.now().getEpochSecond() + TOKEN_TTL_SECONDS;
        activeSessions.put(token, new TokenClaims(customer.getCustomerId(), customer.getRole(), expiresAt));
        return token;
    }

    public TokenClaims verifyToken(String token) throws InvalidCredentialsException {
        if (token == null || token.isBlank()) {
            throw new InvalidCredentialsException("Missing authorization token.");
        }
        TokenClaims claims = activeSessions.get(token.trim());
        if (claims == null) {
            throw new InvalidCredentialsException("Invalid or expired session token. Please log in again.");
        }
        if (Instant.now().getEpochSecond() > claims.expiresAtEpoch()) {
            activeSessions.remove(token.trim());
            throw new InvalidCredentialsException("Session token has expired. Please log in again.");
        }
        return claims;
    }
}
