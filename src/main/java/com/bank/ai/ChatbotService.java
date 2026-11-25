// Author: Rasesh Saraf
// Concept: Java 11+ HttpClient calling Anthropic Claude or Google Gemini API with offline FAQ fallback
package com.bank.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * KoshAI — Server-side AI banking assistant for Meridian Trust Bank.
 * Calls Anthropic (primary) or Google Gemini (optional) via HttpClient,
 * with a built-in offline FAQ fallback when no API key is configured.
 */
public class ChatbotService {

    private static final String DEFAULT_API_URL = "https://api.anthropic.com/v1/messages";
    private static final String DEFAULT_MODEL = "claude-sonnet-5-5";
    private static final String DEFAULT_GEMINI_MODEL = "gemini-2.0-flash";
    private static final int MAX_TOKENS = 500;
    private static final int MAX_HISTORY = 6;
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static final String SYSTEM_PROMPT = """
            You are KoshAI, a friendly AI banking assistant for Meridian Trust Bank (a university demo bank).
            - Savings accounts: minimum balance ₹1,000, max 5 withdrawals/month, 3.5% p.a. interest.
            - Current accounts: ₹10,000 overdraft limit, 0% interest.
            - Features: open accounts, deposit, withdraw, atomic transfers, EMI/FD/Savings calculators, CSV/TXT/Print statements.
            Rules: Refuse real financial or legal advice, state you cannot view/change account data, and never ask for passwords or PINs.
            """;

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String apiKey;
    private final String model;
    private final String apiUrl;
    private final String geminiApiKey;
    private final String geminiModel;

    public ChatbotService() {
        this(System.getenv("ANTHROPIC_API_KEY"), envOr("CHATBOT_MODEL", DEFAULT_MODEL),
                DEFAULT_API_URL, System.getenv("GEMINI_API_KEY"), envOr("GEMINI_MODEL", DEFAULT_GEMINI_MODEL));
    }

    public ChatbotService(String apiKey, String model, String apiUrl) {
        this(apiKey, model, apiUrl, null, DEFAULT_GEMINI_MODEL);
    }

    public ChatbotService(String apiKey, String model, String apiUrl,
                          String geminiApiKey, String geminiModel) {
        this.apiKey = apiKey == null ? null : apiKey.trim();
        this.model = (model == null || model.isBlank()) ? DEFAULT_MODEL : model.trim();
        this.apiUrl = (apiUrl == null || apiUrl.isBlank()) ? DEFAULT_API_URL : apiUrl;
        this.geminiApiKey = geminiApiKey == null ? null : geminiApiKey.trim();
        this.geminiModel = (geminiModel == null || geminiModel.isBlank()) ? DEFAULT_GEMINI_MODEL : geminiModel.trim();
    }

    private static String envOr(String name, String fallback) {
        String v = System.getenv(name);
        return (v == null || v.isBlank()) ? fallback : v.trim();
    }

    public String getMode() {
        if (apiKey != null && !apiKey.isBlank()) return "anthropic";
        if (geminiApiKey != null && !geminiApiKey.isBlank()) return "gemini";
        return "offline";
    }

    public String ask(String userMessage, List<ChatTurn> history) {
        if (userMessage == null || userMessage.isBlank()) {
            return "Hi, I'm KoshAI! Ask me about Meridian Trust Bank accounts, transfers, calculators, or statements.";
        }
        if (apiKey != null && !apiKey.isBlank()) {
            return callAnthropic(userMessage, history);
        }
        if (geminiApiKey != null && !geminiApiKey.isBlank()) {
            return callGemini(userMessage, history);
        }
        return buildOfflineFallback(userMessage);
    }

    private String callAnthropic(String userMessage, List<ChatTurn> history) {
        try {
            ObjectNode payload = mapper.createObjectNode();
            payload.put("model", model);
            payload.put("max_tokens", MAX_TOKENS);
            payload.put("system", SYSTEM_PROMPT);

            ArrayNode messages = payload.putArray("messages");
            addRecentHistory(messages, history, "assistant");
            messages.addObject().put("role", "user").put("content", userMessage.trim());

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .timeout(TIMEOUT)
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() < 200 || res.statusCode() >= 300) {
                return "KoshAI is temporarily unavailable (HTTP " + res.statusCode() + "). Please try again shortly.";
            }
            String text = mapper.readTree(res.body()).path("content").path(0).path("text").asText("").trim();
            return text.isEmpty() ? "KoshAI received an empty response." : text;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return "The assistant request was interrupted. Please try again.";
        } catch (IOException ex) {
            return "Unable to reach KoshAI right now. " + buildOfflineFallback(userMessage);
        }
    }

    private String callGemini(String userMessage, List<ChatTurn> history) {
        try {
            ObjectNode payload = mapper.createObjectNode();
            payload.putObject("systemInstruction").putArray("parts").addObject().put("text", SYSTEM_PROMPT);
            ArrayNode contents = payload.putArray("contents");
            if (history != null) {
                int start = Math.max(0, history.size() - MAX_HISTORY);
                for (int i = start; i < history.size(); i++) {
                    ChatTurn t = history.get(i);
                    if (t == null || t.content() == null || t.content().isBlank()) continue;
                    String role = "assistant".equalsIgnoreCase(t.role()) ? "model" : "user";
                    contents.addObject().put("role", role).putArray("parts").addObject().put("text", t.content());
                }
            }
            contents.addObject().put("role", "user").putArray("parts").addObject().put("text", userMessage.trim());
            payload.putObject("generationConfig").put("maxOutputTokens", MAX_TOKENS);

            String bodyJson = mapper.writeValueAsString(payload);
            String keyParam = URLEncoder.encode(geminiApiKey, StandardCharsets.UTF_8);
            String[] models = {geminiModel, "gemini-2.5-flash", "gemini-flash-latest"};

            for (String m : models) {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/"
                                + m + ":generateContent?key=" + keyParam))
                        .timeout(TIMEOUT)
                        .header("content-type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                        .build();

                HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() == 404) continue;
                if (res.statusCode() < 200 || res.statusCode() >= 300) {
                    return buildOfflineFallback(userMessage) + " [Gemini: HTTP " + res.statusCode() + "]";
                }
                String text = mapper.readTree(res.body()).path("candidates").path(0)
                        .path("content").path("parts").path(0).path("text").asText("").trim();
                if (!text.isEmpty()) return text;
            }
            return buildOfflineFallback(userMessage);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return "The assistant request was interrupted. Please try again.";
        } catch (IOException ex) {
            return "Unable to reach KoshAI right now. " + buildOfflineFallback(userMessage);
        }
    }

    private void addRecentHistory(ArrayNode target, List<ChatTurn> history, String assistantRole) {
        if (history == null) return;
        int start = Math.max(0, history.size() - MAX_HISTORY);
        for (int i = start; i < history.size(); i++) {
            ChatTurn t = history.get(i);
            if (t == null || t.content() == null || t.content().isBlank()) continue;
            String role = "assistant".equalsIgnoreCase(t.role()) ? assistantRole : "user";
            target.addObject().put("role", role).put("content", t.content());
        }
    }

    private String buildOfflineFallback(String userMessage) {
        String q = userMessage.toLowerCase();
        if (q.matches(".*\\b(hi|hello|hey|namaste)\\b.*")) {
            return "Hello! I'm KoshAI, your Meridian Trust Bank assistant (offline FAQ mode). "
                    + "Ask me about Savings vs Current accounts, transfers, calculators, or statement exports!";
        }
        if (q.contains("advice") || q.contains("invest") || q.contains("stock") || q.contains("legal")) {
            return "KoshAI: I cannot provide real financial, investment, or legal advice, and cannot view/change account data.";
        }
        if (q.contains("compare") || (q.contains("savings") && q.contains("current"))) {
            return "KoshAI (Offline FAQ): Savings accounts require ₹1,000 minimum balance, allow 5 withdrawals/month, "
                    + "and earn 3.5% p.a. Current accounts have ₹10,000 overdraft and 0% interest.";
        }
        if (q.contains("minimum") || q.contains("savings") || q.contains("interest")) {
            return "KoshAI (Offline FAQ): Savings accounts require a ₹1,000 minimum balance, allow 5 withdrawals/month, "
                    + "and earn 3.5% p.a. interest (credited monthly).";
        }
        if (q.contains("overdraft") || q.contains("current")) {
            return "KoshAI (Offline FAQ): Current accounts include a ₹10,000 overdraft limit and earn 0% interest.";
        }
        if (q.contains("transfer") || q.contains("atomic")) {
            return "KoshAI (Offline FAQ): Transfers are atomic (all-or-nothing in SQLite) with deadlock-free account locking.";
        }
        if (q.contains("emi") || q.contains("calculator") || q.contains("fd")) {
            return "KoshAI (Offline FAQ): Open Financial Calculators to compute Loan EMI, Fixed Deposit maturity, and Savings 3.5% growth.";
        }
        if (q.contains("export") || q.contains("statement") || q.contains("csv") || q.contains("print")) {
            return "KoshAI (Offline FAQ): Open Statements & Export, apply filters, and click Download CSV or Print / PDF.";
        }
        return "KoshAI is in offline FAQ mode (no AI key active). Ask about Savings minimum balance (₹1,000), "
                + "Current overdraft (₹10,000), 3.5% interest, atomic transfers, EMI/FD calculators, or statement exports!";
    }
}
