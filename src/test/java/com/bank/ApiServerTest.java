// Author: Rasesh Saraf
// Concept: Integration tests for Javalin REST API using JDK HttpClient
package com.bank;

import com.bank.ai.ChatbotService;
import com.bank.api.ApiServer;
import com.bank.api.TokenUtil;
import com.bank.dao.SqliteAccountDao;
import com.bank.dao.SqliteCustomerDao;
import com.bank.dao.SqliteTransactionDao;
import com.bank.service.AuthService;
import com.bank.service.BankService;
import com.bank.service.InterestService;
import com.bank.util.BankLogger;
import com.bank.util.DatabaseManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiServerTest {

    @TempDir
    Path tempDir;

    private Javalin app;
    private String baseUrl;
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        DatabaseManager dbManager = new DatabaseManager(tempDir.resolve("api-test.db").toString());
        dbManager.initializeDatabase();

        AuthService authService = new AuthService(new SqliteCustomerDao(dbManager));
        BankService bankService = new BankService(
                dbManager,
                new SqliteAccountDao(dbManager),
                new SqliteTransactionDao(dbManager),
                new BankLogger(tempDir.resolve("api-bank.log").toString())
        );
        ChatbotService chatbotService = new ChatbotService("", "claude-sonnet-5-5", null);
        InterestService interestService = new InterestService(bankService);
        TokenUtil tokenUtil = new TokenUtil("test-secret");

        ApiServer server = new ApiServer(authService, bankService, chatbotService, interestService, tokenUtil);
        app = server.createApp("*").start(0);
        baseUrl = "http://localhost:" + app.port();
    }

    @AfterEach
    void tearDown() {
        if (app != null) {
            app.stop();
        }
    }

    @Test
    void healthAndLoginDepositAndUnauthorizedAccessWork() throws Exception {
        HttpResponse<String> health = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/health")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, health.statusCode());

        // Unauthenticated call returns 401
        HttpResponse<String> unauth = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/api/accounts")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(401, unauth.statusCode());

        // Login as Aarav
        HttpResponse<String> loginRes = postJson("/api/auth/login",
                "{\"email\":\"aarav@bank.local\",\"password\":\"Aarav@123\"}", null);
        assertEquals(200, loginRes.statusCode());
        assertFalse(loginRes.body().contains("passwordHash"));
        String aaravToken = mapper.readTree(loginRes.body()).path("token").asText();

        // Wrong password returns 401
        HttpResponse<String> badLogin = postJson("/api/auth/login",
                "{\"email\":\"aarav@bank.local\",\"password\":\"Wrong@123\"}", null);
        assertEquals(401, badLogin.statusCode());

        // Deposit into Aarav's account SB100001 succeeds
        HttpResponse<String> depRes = postJson("/api/accounts/SB100001/deposit",
                "{\"amount\":500.00,\"description\":\"API deposit\"}", aaravToken);
        assertEquals(200, depRes.statusCode());
        JsonNode depJson = mapper.readTree(depRes.body());
        assertEquals(0, new BigDecimal("15500.00").compareTo(depJson.path("balanceAfter").decimalValue()));

        // Customer trying to touch Meera's account SB100003 gets 403 Forbidden
        HttpResponse<String> otherDep = postJson("/api/accounts/SB100003/deposit",
                "{\"amount\":100.00,\"description\":\"Unauthorized\"}", aaravToken);
        assertEquals(403, otherDep.statusCode());

        // Customer accessing admin endpoint gets 403 Forbidden
        HttpResponse<String> adminDenied = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/api/admin/accounts"))
                        .header("Authorization", "Bearer " + aaravToken)
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(403, adminDenied.statusCode());

        // Admin login + admin endpoint succeeds
        HttpResponse<String> adminLogin = postJson("/api/auth/login",
                "{\"email\":\"admin@bank.local\",\"password\":\"Admin@123\"}", null);
        String adminToken = mapper.readTree(adminLogin.body()).path("token").asText();

        HttpResponse<String> adminAccounts = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/api/admin/accounts"))
                        .header("Authorization", "Bearer " + adminToken)
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, adminAccounts.statusCode());
        assertTrue(mapper.readTree(adminAccounts.body()).size() >= 4);

        // Admin overview, customers, freeze/activate, and interest run
        HttpResponse<String> overviewRes = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/api/admin/overview"))
                        .header("Authorization", "Bearer " + adminToken)
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, overviewRes.statusCode());
        assertTrue(mapper.readTree(overviewRes.body()).path("totalCustomers").asInt() >= 4);

        HttpResponse<String> customersRes = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/api/admin/customers"))
                        .header("Authorization", "Bearer " + adminToken)
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, customersRes.statusCode());
        assertFalse(customersRes.body().contains("passwordHash"));

        HttpResponse<String> freezeRes = postJson("/api/admin/accounts/CA100004/status",
                "{\"status\":\"FROZEN\"}", adminToken);
        assertEquals(200, freezeRes.statusCode());

        HttpResponse<String> interestRes = postJson("/api/admin/interest/run", "{}", adminToken);
        assertEquals(200, interestRes.statusCode());
        assertEquals(2, mapper.readTree(interestRes.body()).path("creditedAccounts").asInt());

        // Customer cannot call admin interest run (403)
        HttpResponse<String> custInterest = postJson("/api/admin/interest/run", "{}", aaravToken);
        assertEquals(403, custInterest.statusCode());

        // Customer CSV statement export
        HttpResponse<String> csvRes = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/api/accounts/SB100001/statement/csv"))
                        .header("Authorization", "Bearer " + aaravToken)
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, csvRes.statusCode());
        assertTrue(csvRes.body().contains("txn_id,account_number,type"));

        // Profile update (PUT /api/auth/profile)
        HttpResponse<String> profileRes = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/api/auth/profile"))
                        .header("Authorization", "Bearer " + aaravToken)
                        .header("Content-Type", "application/json")
                        .PUT(HttpRequest.BodyPublishers.ofString(
                                "{\"name\":\"Aarav Sharma\",\"phone\":\"9876543210\",\"address\":\"Amaravati, AP\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, profileRes.statusCode());
        assertEquals("Amaravati, AP", mapper.readTree(profileRes.body()).path("address").asText());

        // Admin audit logs
        HttpResponse<String> logsRes = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/api/admin/logs"))
                        .header("Authorization", "Bearer " + adminToken)
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, logsRes.statusCode());
        assertTrue(mapper.readTree(logsRes.body()).path("lines").size() >= 1);

        // Chat endpoint works gracefully in offline mode
        HttpResponse<String> chatRes = postJson("/api/chat",
                "{\"message\":\"What is the minimum balance for savings?\",\"history\":[]}", aaravToken);
        assertEquals(200, chatRes.statusCode());
        assertTrue(chatRes.body().contains("1,000"));
    }

    private HttpResponse<String> postJson(String path, String json, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
