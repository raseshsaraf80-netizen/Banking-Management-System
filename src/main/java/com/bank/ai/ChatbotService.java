// Author: Rasesh Saraf
// Concept: Java 11+ HttpClient calling Anthropic Messages API (or Gemini fallback) with graceful offline mode
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
 * KoshAI — Server-side AI banking assistant for SRM Trust Bank.
 * Supports Anthropic Messages API (primary) and Google Gemini API (optional free-tier alternative).
 * Has zero database access and keeps API keys strictly in server environment variables.
 */
public class ChatbotService {

    private static final String DEFAULT_API_URL = "https://api.anthropic.com/v1/messages";
    private static final String DEFAULT_MODEL = "claude-sonnet-5-5";
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final String DEFAULT_GEMINI_MODEL = "gemini-2.5-flash";
    private static final int MAX_TOKENS = 500;
    private static final int MAX_HISTORY_TURNS = 6;
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static final String SYSTEM_PROMPT = """
            You are KoshAI, a friendly, concise AI banking-help assistant for SRM Trust Bank (a university demo bank).
            Help users understand app features and answer common FAQs:
            - Savings accounts require a minimum balance of ₹1,000, allow up to 5 withdrawals per month, and earn 3.5% p.a. interest (credited monthly).
            - Current accounts earn 0% interest and include a ₹10,000 overdraft limit.
            - Users can open accounts, deposit, withdraw, execute atomic ACID transfers, use EMI/FD/Savings calculators, and export statements as TXT, CSV, or Print/PDF.
            Strict safety rules:
            - Refuse to provide real financial, investment, tax, or legal advice.
            - Clearly state that you cannot view or change any customer's account data or balances.
            - Never ask for passwords, PINs, OTPs, or full card numbers.
            Keep answers clear, accurate, and concise.
            """;

    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final String apiKey;
    private final String model;
    private final String apiUrl;
    private final String geminiApiKey;
    private final String geminiModel;

    public ChatbotService() {
        this(
                System.getenv("ANTHROPIC_API_KEY"),
                resolveModel(),
                DEFAULT_API_URL,
                System.getenv("GEMINI_API_KEY"),
                resolveGeminiModel()
        );
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
        this.geminiModel = (geminiModel == null || geminiModel.isBlank())
                ? DEFAULT_GEMINI_MODEL
                : geminiModel.trim();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
        this.mapper = new ObjectMapper();
    }

    private static String resolveModel() {
        String env = System.getenv("CHATBOT_MODEL");
        return (env == null || env.isBlank()) ? DEFAULT_MODEL : env.trim();
    }

    private static String resolveGeminiModel() {
        String env = System.getenv("GEMINI_MODEL");
        return (env == null || env.isBlank()) ? DEFAULT_GEMINI_MODEL : env.trim();
    }

    /**
     * Sends the user message and recent chat history to Anthropic (or Gemini if configured)
     * and returns a safe reply. Never throws on network/API errors.
     */
    public String ask(String userMessage, List<ChatTurn> history) {
        if (userMessage == null || userMessage.isBlank()) {
            return "Hi, I'm KoshAI! Ask me anything about SRM Trust Bank accounts, transfers, calculators, or statements.";
        }
        if (apiKey != null && !apiKey.isBlank()) {
            return askAnthropic(userMessage, history);
        }
        if (geminiApiKey != null && !geminiApiKey.isBlank()) {
            return askGemini(userMessage, history);
        }
        return buildOfflineFallback(userMessage);
    }

    private String askAnthropic(String userMessage, List<ChatTurn> history) {
        try {
            ObjectNode payload = mapper.createObjectNode();
            payload.put("model", model);
            payload.put("max_tokens", MAX_TOKENS);
            payload.put("system", SYSTEM_PROMPT);

            ArrayNode messages = payload.putArray("messages");
            if (history != null && !history.isEmpty()) {
                int start = Math.max(0, history.size() - MAX_HISTORY_TURNS);
                for (int i = start; i < history.size(); i++) {
                    ChatTurn turn = history.get(i);
                    if (turn == null || turn.content() == null || turn.content().isBlank()) {
                        continue;
                    }
                    String role = "assistant".equalsIgnoreCase(turn.role()) ? "assistant" : "user";
                    ObjectNode msgNode = messages.addObject();
                    msgNode.put("role", role);
                    msgNode.put("content", turn.content());
                }
            }
            ObjectNode currentMsg = messages.addObject();
            currentMsg.put("role", "user");
            currentMsg.put("content", userMessage.trim());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .timeout(TIMEOUT)
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", ANTHROPIC_VERSION)
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                System.err.println("Chatbot API returned HTTP status " + response.statusCode());
                return "KoshAI is temporarily unavailable (HTTP "
                        + response.statusCode() + "). Please try again shortly.";
            }

            JsonNode root = mapper.readTree(response.body());
            JsonNode contentArray = root.path("content");
            if (contentArray.isArray() && !contentArray.isEmpty()) {
                String text = contentArray.get(0).path("text").asText("").trim();
                if (!text.isEmpty()) {
                    return text;
                }
            }
            return "KoshAI received an empty response. Please rephrase your banking question.";
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            System.err.println("Chatbot request interrupted.");
            return "The assistant request was interrupted. Please try again.";
        } catch (IOException ex) {
            System.err.println("Chatbot network/timeout error: " + ex.getMessage());
            return "Unable to reach KoshAI's AI backend right now. " + buildOfflineFallback(userMessage);
        }
    }

    private String askGemini(String userMessage, List<ChatTurn> history) {
        try {
            ObjectNode payload = mapper.createObjectNode();
            ObjectNode sysInst = payload.putObject("systemInstruction");
            sysInst.putArray("parts").addObject().put("text", SYSTEM_PROMPT);

            ArrayNode contents = payload.putArray("contents");
            if (history != null && !history.isEmpty()) {
                int start = Math.max(0, history.size() - MAX_HISTORY_TURNS);
                for (int i = start; i < history.size(); i++) {
                    ChatTurn turn = history.get(i);
                    if (turn == null || turn.content() == null || turn.content().isBlank()) {
                        continue;
                    }
                    String role = "assistant".equalsIgnoreCase(turn.role()) ? "model" : "user";
                    ObjectNode node = contents.addObject();
                    node.put("role", role);
                    node.putArray("parts").addObject().put("text", turn.content());
                }
            }
            ObjectNode userNode = contents.addObject();
            userNode.put("role", "user");
            userNode.putArray("parts").addObject().put("text", userMessage.trim());

            ObjectNode genCfg = payload.putObject("generationConfig");
            genCfg.put("maxOutputTokens", MAX_TOKENS);

            String[] candidatesToTry = {
                    geminiModel,
                    "gemini-2.0-flash",
                    "gemini-flash-latest"
            };
            String bodyJson = mapper.writeValueAsString(payload);
            String encodedKey = URLEncoder.encode(geminiApiKey, StandardCharsets.UTF_8);

            for (String candidateModel : candidatesToTry) {
                String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/"
                        + candidateModel + ":generateContent?key=" + encodedKey;

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .timeout(TIMEOUT)
                        .header("content-type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 404) {
                    System.err.println("Gemini model " + candidateModel + " returned 404, trying fallback...");
                    continue;
                }
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    System.err.println("Gemini API (" + candidateModel + ") returned HTTP " + response.statusCode());
                    return buildOfflineFallback(userMessage);
                }

                JsonNode root = mapper.readTree(response.body());
                JsonNode candidates = root.path("candidates");
                if (candidates.isArray() && !candidates.isEmpty()) {
                    JsonNode parts = candidates.get(0).path("content").path("parts");
                    if (parts.isArray() && !parts.isEmpty()) {
                        String text = parts.get(0).path("text").asText("").trim();
                        if (!text.isEmpty()) {
                            return text;
                        }
                    }
                }
            }
            return buildOfflineFallback(userMessage);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return "The assistant request was interrupted. Please try again.";
        } catch (IOException ex) {
            System.err.println("Gemini network/timeout error: " + ex.getMessage());
            return "Unable to reach KoshAI right now. " + buildOfflineFallback(userMessage);
        }
    }

    private String buildOfflineFallback(String userMessage) {
        String q = userMessage.toLowerCase();
        if (q.matches(".*\\b(hi|hello|hey|namaste)\\b.*")) {
            return "Hello! I'm KoshAI, your SRM Trust Bank assistant (currently in offline FAQ mode). "
                    + "Ask me about Savings vs Current accounts, transfers, calculators, or statement exports!";
        }
        if (q.contains("advice") || q.contains("invest") || q.contains("stock") || q.contains("legal")) {
            return "KoshAI: I cannot provide real financial, investment, or legal advice, "
                    + "and I cannot view or modify your account data.";
        }
        if (q.contains("compare") || (q.contains("savings") && q.contains("current"))) {
            return "KoshAI (Offline FAQ): Savings accounts require ₹1,000 minimum balance, allow 5 withdrawals/month, "
                    + "and earn 3.5% p.a. interest. Current accounts have no minimum balance, earn 0% interest, "
                    + "and include a ₹10,000 overdraft facility.";
        }
        if (q.contains("minimum") || q.contains("savings") || q.contains("interest")) {
            return "KoshAI (Offline FAQ): Savings accounts require a minimum balance of 1,000, "
                    + "allow 5 withdrawals per month, and earn 3.5% p.a. interest (credited monthly). "
                    + "I cannot view or modify your account data.";
        }
        if (q.contains("overdraft") || q.contains("current")) {
            return "KoshAI (Offline FAQ): Current accounts include an overdraft limit of 10,000 "
                    + "and earn 0% interest. I cannot view or modify your account data.";
        }
        if (q.contains("transfer") || q.contains("atomic") || q.contains("send money")) {
            return "KoshAI (Offline FAQ): Transfers are atomic (all-or-nothing in SQLite) with deadlock-free account locking. "
                    + "Open Payments & Transfers, pick your source account, enter the destination account (e.g. SB100003), and submit.";
        }
        if (q.contains("emi") || q.contains("calculator") || q.contains("fd")) {
            return "KoshAI (Offline FAQ): Open Financial Calculators to compute Loan EMI (with year-by-year amortization), "
                    + "Fixed Deposit maturity, and Savings 3.5% growth projections.";
        }
        if (q.contains("export") || q.contains("statement") || q.contains("csv") || q.contains("print")) {
            return "KoshAI (Offline FAQ): Open Statements & Export, filter by date or type, and click Download CSV or Print / PDF.";
        }
        if (q.contains("password") || q.contains("security")) {
            return "KoshAI (Offline FAQ): Passwords must be 8+ characters with uppercase, lowercase, and a digit. "
                    + "They are hashed with PBKDF2WithHmacSHA256. Never share your password with anyone.";
        }
        return "KoshAI is in offline FAQ mode (set ANTHROPIC_API_KEY or GEMINI_API_KEY on the server for live AI replies). "
                + "I can still answer FAQs on Savings minimum balance (1,000), Current overdraft (10,000), "
                + "3.5% interest, atomic transfers, EMI/FD calculators, or statement exports!";
    }
}
