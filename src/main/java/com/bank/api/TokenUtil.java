// Author: Rasesh Saraf
// Concept: Stateless HMAC-SHA256 session token signing using JDK javax.crypto.Mac
package com.bank.api;

import com.bank.exception.InvalidCredentialsException;
import com.bank.model.Customer;
import com.bank.model.Role;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

/**
 * Signs and verifies lightweight HMAC-SHA256 bearer tokens for the REST API.
 */
public class TokenUtil {

    private static final String HMAC_ALG = "HmacSHA256";
    private static final long TOKEN_TTL_SECONDS = 8 * 3600L;

    private final byte[] secretBytes;

    public TokenUtil(String secret) {
        String effective = (secret == null || secret.isBlank())
                ? "dev-banking-secret-change-me"
                : secret;
        this.secretBytes = effective.getBytes(StandardCharsets.UTF_8);
    }

    public record TokenClaims(long customerId, Role role, long expiresAtEpoch) {
    }

    public String createToken(Customer customer) {
        long expiresAt = Instant.now().getEpochSecond() + TOKEN_TTL_SECONDS;
        String payload = customer.getCustomerId() + "|" + customer.getRole().name() + "|" + expiresAt;
        String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        String sig = sign(encodedPayload);
        return encodedPayload + "." + sig;
    }

    public TokenClaims verifyToken(String token) throws InvalidCredentialsException {
        if (token == null || !token.contains(".")) {
            throw new InvalidCredentialsException("Missing or malformed authorization token.");
        }
        String[] parts = token.split("\\.", 2);
        String expectedSig = sign(parts[0]);
        if (!MessageDigest.isEqual(
                expectedSig.getBytes(StandardCharsets.UTF_8),
                parts[1].getBytes(StandardCharsets.UTF_8))) {
            throw new InvalidCredentialsException("Invalid authorization token signature.");
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            String[] fields = decoded.split("\\|");
            if (fields.length != 3) {
                throw new InvalidCredentialsException("Invalid authorization token payload.");
            }
            long customerId = Long.parseLong(fields[0]);
            Role role = Role.valueOf(fields[1]);
            long expiresAt = Long.parseLong(fields[2]);
            if (Instant.now().getEpochSecond() > expiresAt) {
                throw new InvalidCredentialsException("Session token has expired. Please log in again.");
            }
            return new TokenClaims(customerId, role, expiresAt);
        } catch (IllegalArgumentException ex) {
            throw new InvalidCredentialsException("Corrupted authorization token.");
        }
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALG);
            mac.init(new SecretKeySpec(secretBytes, HMAC_ALG));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to sign session token", ex);
        }
    }
}

