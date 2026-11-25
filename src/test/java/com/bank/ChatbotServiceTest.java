// Author: Rasesh Saraf
// Concept: JUnit 5 tests for ChatbotService HTTP client and fallback behavior
package com.bank;

import com.bank.ai.ChatTurn;
import com.bank.ai.ChatbotService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatbotServiceTest {

    @Test
    void missingApiKeyReturnsFriendlyFallback() {
        ChatbotService service = new ChatbotService(null, "claude-sonnet-5-5", null);
        String reply = service.ask("What is the minimum balance?", List.of());
        assertTrue(reply.contains("1,000"));
    }

    @Test
    void sendsRequiredAnthropicHeadersAndParsesReply() throws Exception {
        AtomicReference<String> seenKey = new AtomicReference<>();
        AtomicReference<String> seenVersion = new AtomicReference<>();

        HttpServer mockApi = HttpServer.create(new InetSocketAddress(0), 0);
        mockApi.createContext("/v1/messages", exchange -> {
            seenKey.set(exchange.getRequestHeaders().getFirst("x-api-key"));
            seenVersion.set(exchange.getRequestHeaders().getFirst("anthropic-version"));
            byte[] resp = "{\"content\":[{\"type\":\"text\",\"text\":\"Savings accounts earn 3.5% p.a.\"}]}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });
        mockApi.start();
        try {
            String url = "http://localhost:" + mockApi.getAddress().getPort() + "/v1/messages";
            ChatbotService service = new ChatbotService("dummy-test-key", "claude-sonnet-5-5", url);
            String reply = service.ask("Interest rate?",
                    List.of(new ChatTurn("user", "Hi"), new ChatTurn("assistant", "Hello!")));

            assertEquals("dummy-test-key", seenKey.get());
            assertEquals("2023-06-01", seenVersion.get());
            assertEquals("Savings accounts earn 3.5% p.a.", reply);
        } finally {
            mockApi.stop(0);
        }
    }

    @Test
    void apiErrorStatusReturnsGracefulFallback() throws Exception {
        HttpServer mockApi = HttpServer.create(new InetSocketAddress(0), 0);
        mockApi.createContext("/v1/messages", exchange -> {
            byte[] resp = "{\"error\":\"overloaded\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(503, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });
        mockApi.start();
        try {
            String url = "http://localhost:" + mockApi.getAddress().getPort() + "/v1/messages";
            ChatbotService service = new ChatbotService("dummy-test-key", "claude-sonnet-5-5", url);
            String reply = service.ask("Help me", List.of());
            assertTrue(reply.contains("503"));
        } finally {
            mockApi.stop(0);
        }
    }
}

