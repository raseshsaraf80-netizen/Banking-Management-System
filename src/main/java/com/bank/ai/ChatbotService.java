// Author: Rasesh Saraf
// Concept: Java 11+ HttpClient calling Anthropic Messages API with graceful fallback
package com.bank.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Server-side AI chatbot client for the Anthropic Messages API.
 * Has zero database access and keeps the API key strictly in server environment variables.
 */
public class ChatbotService {

    private static final String DEFAULT_API_URL = "https://api.anthropic.com/v1/messages";
    private static final String DEFAULT_MODEL = "claude-sonnet-5-5";
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final int MAX_TOKENS = 500;
    private static final int MAX_HISTORY_TURNS = 6;
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static final String SYSTEM_PROMPT = """
            You are a friendly banking-help assistant for SRM Trust Bank (a university demo bank).
            Help users understand app features and answer common FAQs:
            - Savings accounts require a minimum balance of 1,000, allow up to 5 withdrawals per month, and earn 3.5% p.a. interest (credited monthly).
            - Current accounts earn no interest and include a 10,000 overdraft limit.
            - Users can open accounts, deposit, withdraw, transfer funds atomically, and export statements as TXT or CSV.
            Strict safety rules:
            - Refuse to provide real financial, investment, tax, or legal advice.
            - Clearly state that you cannot view or change any customer's account data or balances.
            - Never ask for passwords, PINs, OTPs, or full card numbers.
            Keep answers concise and helpful.
            """;

    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final String apiKey;
    private final String model;
    private final String apiUrl;

    public ChatbotService() {
        this(System.getenv("ANTHROPIC_API_KEY"), resolveModel(), DEFAULT_API_URL);
    }

    public ChatbotService(String apiKey, String model, String apiUrl) {
        this.apiKey = apiKey;
        this.model = (model == null || model.isBlank()) ? DEFAULT_MODEL : model.trim();
        this.apiUrl = (apiUrl == null || apiUrl.isBlank()) ? DEFAULT_API_URL : apiUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
        this.mapper = new ObjectMapper();
    }

    private static String resolveModel() {
        String env = System.getenv("CHATBOT_MODEL");
        return (env == null || env.isBlank()) ? DEFAULT_MODEL : env.trim();
    }

    /**
     * Sends the user message and recent chat history to Anthropic and returns a safe reply.
     * Never throws on network/API errors; returns a friendly fallback message instead.
     */
    public String ask(String userMessage, List<ChatTurn> history) {
        if (userMessage == null || userMessage.isBlank()) {
            return "Please enter a question about SRM Trust Bank features or FAQs.";
        }
        if (apiKey == null || apiKey.isBlank()) {
            return buildOfflineFallback(userMessage);
        }

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

            String bodyJson = mapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .timeout(TIMEOUT)
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", ANTHROPIC_VERSION)
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                System.err.println("Chatbot API returned HTTP status " + response.statusCode());
                return "The banking assistant is temporarily unavailable (HTTP "
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
            return "I received an empty response. Please rephrase your banking question.";
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            System.err.println("Chatbot request interrupted.");
            return "The assistant request was interrupted. Please try again.";
        } catch (IOException ex) {
            // Never log apiKey or headers
            System.err.println("Chatbot network/timeout error: " + ex.getMessage());
            return "Unable to reach the AI assistant right now (connection/timeout issue). "
                    + buildOfflineFallback(userMessage);
        }
    }

    private String buildOfflineFallback(String userMessage) {
        String q = userMessage.toLowerCase();
        if (q.contains("minimum") || q.contains("savings")) {
            return "Demo Assistant (offline mode): Savings accounts require a minimum balance of 1,000, "
                    + "allow 5 withdrawals per month, and earn 3.5% p.a. interest. "
                    + "I cannot view or modify your account data.";
        }
        if (q.contains("overdraft") || q.contains("current")) {
            return "Demo Assistant (offline mode): Current accounts include an overdraft limit of 10,000 "
                    + "and earn 0% interest. I cannot view or modify your account data.";
        }
        if (q.contains("export") || q.contains("statement")) {
            return "Demo Assistant (offline mode): Open the Statements screen and click Export TXT or Export CSV "
                    + "to save your statement to the statements/ folder.";
        }
        if (q.contains("advice") || q.contains("invest")) {
            return "I cannot provide real financial or legal advice, and I cannot view or change account data.";
        }
        return "AI assistant is in offline mode (ANTHROPIC_API_KEY not configured on the server). "
                + "I can still answer basic FAQs about Savings minimum balance (1,000), Current overdraft (10,000), "
                + "3.5% interest, or statement exports!";
    }
}

