// Author: Rasesh Saraf
// Concept: Built-in JDK HttpServer REST API + static web server reusing core Java services
package com.bank.api;

import com.bank.ai.ChatbotService;
import com.bank.api.ApiDtos.AccountDto;
import com.bank.api.ApiDtos.AdminOverviewDto;
import com.bank.api.ApiDtos.AmountRequest;
import com.bank.api.ApiDtos.AuthResponse;
import com.bank.api.ApiDtos.ChangePasswordRequest;
import com.bank.api.ApiDtos.ChatRequest;
import com.bank.api.ApiDtos.CustomerDto;
import com.bank.api.ApiDtos.LoginRequest;
import com.bank.api.ApiDtos.OpenAccountRequest;
import com.bank.api.ApiDtos.RegisterRequest;
import com.bank.api.ApiDtos.StatusUpdateRequest;
import com.bank.api.ApiDtos.TransferRequest;
import com.bank.api.ApiDtos.UpdateProfileRequest;
import com.bank.dao.AccountDao;
import com.bank.dao.CustomerDao;
import com.bank.dao.SqliteAccountDao;
import com.bank.dao.SqliteCustomerDao;
import com.bank.dao.SqliteTransactionDao;
import com.bank.dao.TransactionDao;
import com.bank.exception.AccountNotFoundException;
import com.bank.exception.BankException;
import com.bank.exception.DuplicateEntryException;
import com.bank.exception.InvalidCredentialsException;
import com.bank.exception.UnauthorizedException;
import com.bank.exception.ValidationException;
import com.bank.model.Account;
import com.bank.model.AccountStatus;
import com.bank.model.AccountType;
import com.bank.model.Customer;
import com.bank.model.Transaction;
import com.bank.service.AuthService;
import com.bank.service.BankService;
import com.bank.service.InterestService;
import com.bank.util.BankLogger;
import com.bank.util.DatabaseManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Lightweight built-in JDK HttpServer serving both the REST API (/api/*, /health)
 * and the static web portal (web/) on a single local port (default 8080).
 */
public class ApiServer {

    private final AuthService authService;
    private final BankService bankService;
    private final ChatbotService chatbotService;
    private final InterestService interestService;
    private final BankLogger bankLogger;
    private final TokenUtil tokenUtil;
    private final RateLimiter loginRateLimiter = new RateLimiter(30, 60);
    private final RateLimiter chatRateLimiter = new RateLimiter(40, 60);
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private HttpServer server;

    public ApiServer(AuthService authService, BankService bankService,
                     ChatbotService chatbotService, InterestService interestService,
                     TokenUtil tokenUtil) {
        this(authService, bankService, chatbotService, interestService, new BankLogger(), tokenUtil);
    }

    public ApiServer(AuthService authService, BankService bankService,
                     ChatbotService chatbotService, InterestService interestService,
                     BankLogger bankLogger, TokenUtil tokenUtil) {
        this.authService = authService;
        this.bankService = bankService;
        this.chatbotService = chatbotService;
        this.interestService = interestService;
        this.bankLogger = bankLogger;
        this.tokenUtil = tokenUtil;
    }

    /** Starts the HTTP server on the given port (0 for random free port) and returns the bound port. */
    public int start(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/health", this::handleHealth);
        server.createContext("/api/", this::handleApi);
        server.createContext("/", this::handleStatic);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        return server.getAddress().getPort();
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void handleHealth(HttpExchange ex) throws IOException {
        addCorsHeaders(ex);
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            sendEmpty(ex, 204);
            return;
        }
        sendJson(ex, 200, Map.of(
                "status", "ok",
                "bank", "Meridian Trust Bank",
                "aiMode", chatbotService.getMode()
        ));
    }

    private void handleStatic(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 405, Map.of("error", "Method not allowed"));
            return;
        }
        String rawPath = ex.getRequestURI().getPath();
        String relative = ("/".equals(rawPath) || rawPath.isBlank()) ? "index.html" : rawPath.substring(1);
        Path webRoot = Path.of("web").toAbsolutePath().normalize();
        Path target = webRoot.resolve(relative).normalize();
        if (!target.startsWith(webRoot) || !Files.isRegularFile(target)) {
            sendText(ex, 404, "text/plain; charset=utf-8", "Not found");
            return;
        }
        String contentType = switch (relative.substring(relative.lastIndexOf('.') + 1)) {
            case "html" -> "text/html; charset=utf-8";
            case "css" -> "text/css; charset=utf-8";
            case "js" -> "application/javascript; charset=utf-8";
            default -> "application/octet-stream";
        };
        byte[] bytes = Files.readAllBytes(target);
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    private void handleApi(HttpExchange ex) throws IOException {
        addCorsHeaders(ex);
        String method = ex.getRequestMethod().toUpperCase();
        if ("OPTIONS".equals(method)) {
            sendEmpty(ex, 204);
            return;
        }
        String path = ex.getRequestURI().getPath();
        try {
            if ("POST".equals(method) && "/api/auth/register".equals(path)) {
                handleRegister(ex);
            } else if ("POST".equals(method) && "/api/auth/login".equals(path)) {
                handleLogin(ex);
            } else {
                Customer actor = authenticate(ex);
                routeAuthenticated(ex, method, path, actor);
            }
        } catch (InvalidCredentialsException e) {
            sendJson(ex, 401, Map.of("error", e.getMessage()));
        } catch (UnauthorizedException e) {
            sendJson(ex, 403, Map.of("error", e.getMessage()));
        } catch (AccountNotFoundException e) {
            sendJson(ex, 404, Map.of("error", e.getMessage()));
        } catch (DuplicateEntryException e) {
            sendJson(ex, 409, Map.of("error", e.getMessage()));
        } catch (BankException e) {
            sendJson(ex, 400, Map.of("error", e.getMessage()));
        } catch (JsonProcessingException e) {
            sendJson(ex, 400, Map.of("error", "Invalid JSON payload."));
        } catch (Exception e) {
            sendJson(ex, 500, Map.of("error", "Internal server error: " + e.getMessage()));
        }
    }

    private void routeAuthenticated(HttpExchange ex, String method, String path, Customer actor)
            throws IOException, BankException {
        if ("GET".equals(method) && "/api/auth/me".equals(path)) {
            sendJson(ex, 200, CustomerDto.from(actor));
        } else if ("PUT".equals(method) && "/api/auth/profile".equals(path)) {
            UpdateProfileRequest req = readJson(ex, UpdateProfileRequest.class);
            Customer updated = authService.updateProfile(actor.getCustomerId(), req.name(), req.phone(), req.address());
            sendJson(ex, 200, CustomerDto.from(updated));
        } else if ("POST".equals(method) && "/api/auth/change-password".equals(path)) {
            ChangePasswordRequest req = readJson(ex, ChangePasswordRequest.class);
            authService.changePassword(actor.getCustomerId(), req.oldPassword(), req.newPassword());
            sendJson(ex, 200, Map.of("message", "Password updated successfully."));
        } else if ("GET".equals(method) && "/api/accounts".equals(path)) {
            List<AccountDto> accounts = bankService.getAccountsForCustomer(actor.getCustomerId())
                    .stream().map(AccountDto::from).toList();
            sendJson(ex, 200, accounts);
        } else if ("POST".equals(method) && "/api/accounts".equals(path)) {
            handleOpenAccount(ex, actor);
        } else if ("POST".equals(method) && "/api/transfer".equals(path)) {
            TransferRequest req = readJson(ex, TransferRequest.class);
            bankService.transfer(actor, normalizeAccountNo(req.fromAccount()),
                    normalizeAccountNo(req.toAccount()), req.amount(), req.description());
            sendJson(ex, 200, Map.of("message", "Transfer completed successfully."));
        } else if ("POST".equals(method) && "/api/chat".equals(path)) {
            handleChat(ex);
        } else if ("GET".equals(method) && "/api/admin/overview".equals(path)) {
            handleAdminOverview(ex, actor);
        } else if ("GET".equals(method) && "/api/admin/customers".equals(path)) {
            List<CustomerDto> customers = authService.getAllCustomers(actor).stream().map(CustomerDto::from).toList();
            sendJson(ex, 200, customers);
        } else if ("GET".equals(method) && "/api/admin/accounts".equals(path)) {
            String q = queryParam(ex, "q");
            List<Account> source = (q != null && !q.isBlank())
                    ? bankService.searchAccounts(actor, q)
                    : bankService.getAllAccounts(actor);
            sendJson(ex, 200, source.stream().map(AccountDto::from).toList());
        } else if ("POST".equals(method) && "/api/admin/interest/run".equals(path)) {
            authService.requireAdmin(actor);
            int credited = interestService.runNow();
            sendJson(ex, 200, Map.of("creditedAccounts", credited,
                    "message", "Credited monthly interest to " + credited + " active savings account(s)."));
        } else if ("GET".equals(method) && "/api/admin/logs".equals(path)) {
            authService.requireAdmin(actor);
            sendJson(ex, 200, Map.of("lines", bankLogger.readRecentLines(40)));
        } else if (path.startsWith("/api/accounts/")) {
            routeAccountAction(ex, method, path, actor);
        } else if ("POST".equals(method) && path.startsWith("/api/admin/accounts/") && path.endsWith("/status")) {
            String accNo = normalizeAccountNo(extractSegment(path, "/api/admin/accounts/", "/status"));
            StatusUpdateRequest req = readJson(ex, StatusUpdateRequest.class);
            AccountStatus status;
            try {
                status = AccountStatus.valueOf(req.status().trim().toUpperCase());
            } catch (Exception e) {
                throw new ValidationException("status must be ACTIVE, FROZEN, or CLOSED.");
            }
            bankService.updateAccountStatus(actor, accNo, status);
            sendJson(ex, 200, Map.of("message", "Account " + accNo + " status updated to " + status.name()));
        } else {
            sendJson(ex, 404, Map.of("error", "Endpoint not found: " + method + " " + path));
        }
    }

    private void routeAccountAction(HttpExchange ex, String method, String path, Customer actor)
            throws IOException, BankException {
        if ("POST".equals(method) && path.endsWith("/deposit")) {
            String accNo = normalizeAccountNo(extractSegment(path, "/api/accounts/", "/deposit"));
            AmountRequest req = readJson(ex, AmountRequest.class);
            sendJson(ex, 200, bankService.deposit(actor, accNo, req.amount(), req.description()));
        } else if ("POST".equals(method) && path.endsWith("/withdraw")) {
            String accNo = normalizeAccountNo(extractSegment(path, "/api/accounts/", "/withdraw"));
            AmountRequest req = readJson(ex, AmountRequest.class);
            sendJson(ex, 200, bankService.withdraw(actor, accNo, req.amount(), req.description()));
        } else if ("POST".equals(method) && path.endsWith("/close")) {
            String accNo = normalizeAccountNo(extractSegment(path, "/api/accounts/", "/close"));
            bankService.closeAccount(actor, accNo);
            sendJson(ex, 200, Map.of("message", "Account " + accNo + " closed."));
        } else if ("GET".equals(method) && path.endsWith("/transactions")) {
            String accNo = normalizeAccountNo(extractSegment(path, "/api/accounts/", "/transactions"));
            handleTransactions(ex, actor, accNo);
        } else if ("GET".equals(method) && path.endsWith("/statement/csv")) {
            String accNo = normalizeAccountNo(extractSegment(path, "/api/accounts/", "/statement/csv"));
            handleExportCsv(ex, actor, accNo);
        } else {
            sendJson(ex, 404, Map.of("error", "Endpoint not found: " + method + " " + path));
        }
    }

    private void handleRegister(HttpExchange ex) throws IOException, BankException {
        RegisterRequest req = readJson(ex, RegisterRequest.class);
        Customer created = authService.register(req.name(), req.email(), req.phone(), req.address(), req.password());
        sendJson(ex, 201, new AuthResponse(tokenUtil.createToken(created), CustomerDto.from(created)));
    }

    private void handleLogin(HttpExchange ex) throws IOException, BankException {
        if (!loginRateLimiter.allow(clientIp(ex))) {
            sendJson(ex, 429, Map.of("error", "Too many login attempts. Please wait a minute."));
            return;
        }
        LoginRequest req = readJson(ex, LoginRequest.class);
        Customer customer = authService.login(req.email(), req.password());
        sendJson(ex, 200, new AuthResponse(tokenUtil.createToken(customer), CustomerDto.from(customer)));
    }

    private void handleOpenAccount(HttpExchange ex, Customer actor) throws IOException, BankException {
        OpenAccountRequest req = readJson(ex, OpenAccountRequest.class);
        AccountType type;
        try {
            type = AccountType.valueOf(req.accountType().trim().toUpperCase());
        } catch (Exception e) {
            throw new ValidationException("accountType must be SAVINGS or CURRENT.");
        }
        Account created = bankService.openAccount(actor, type, req.initialDeposit());
        sendJson(ex, 201, AccountDto.from(created));
    }

    private void handleTransactions(HttpExchange ex, Customer actor, String accNo) throws IOException, BankException {
        String limitParam = queryParam(ex, "limit");
        if (limitParam != null && !limitParam.isBlank()) {
            try {
                int limit = Integer.parseInt(limitParam.trim());
                sendJson(ex, 200, bankService.getMiniStatement(actor, accNo, limit));
                return;
            } catch (NumberFormatException e) {
                throw new ValidationException("limit must be an integer.");
            }
        }
        LocalDate from = parseOptionalDate(queryParam(ex, "from"));
        LocalDate to = parseOptionalDate(queryParam(ex, "to"));
        sendJson(ex, 200, bankService.getStatement(actor, accNo, from, to));
    }

    private void handleExportCsv(HttpExchange ex, Customer actor, String accNo) throws IOException, BankException {
        LocalDate from = parseOptionalDate(queryParam(ex, "from"));
        LocalDate to = parseOptionalDate(queryParam(ex, "to"));
        List<Transaction> txns = bankService.getStatement(actor, accNo, from, to);

        StringBuilder csv = new StringBuilder("txn_id,account_number,type,amount,balance_after,timestamp,description\n");
        for (Transaction t : txns) {
            String desc = (t.getDescription() == null ? "" : t.getDescription()).replace("\"", "\"\"");
            csv.append(t.getTxnId()).append(',')
                    .append(t.getAccountNumber()).append(',')
                    .append(t.getType().name()).append(',')
                    .append(t.getAmount().toPlainString()).append(',')
                    .append(t.getBalanceAfter().toPlainString()).append(',')
                    .append(t.getTimestamp()).append(',')
                    .append('"').append(desc).append('"').append('\n');
        }
        ex.getResponseHeaders().set("Content-Disposition",
                "attachment; filename=\"" + accNo + "_" + LocalDate.now() + ".csv\"");
        sendText(ex, 200, "text/csv; charset=utf-8", csv.toString());
    }

    private void handleChat(HttpExchange ex) throws IOException {
        if (!chatRateLimiter.allow(clientIp(ex))) {
            sendJson(ex, 429, Map.of("error", "Too many chat requests. Please slow down."));
            return;
        }
        ChatRequest req = readJson(ex, ChatRequest.class);
        String reply = chatbotService.ask(req.message(), req.history());
        sendJson(ex, 200, Map.of("reply", reply));
    }

    private void handleAdminOverview(HttpExchange ex, Customer admin) throws IOException, BankException {
        authService.requireAdmin(admin);
        List<Customer> customers = authService.getAllCustomers(admin);
        List<Account> accounts = bankService.getAllAccounts(admin);

        int active = 0, frozen = 0, closed = 0;
        BigDecimal totalDeposits = BigDecimal.ZERO;
        BigDecimal savingsDeposits = BigDecimal.ZERO;
        BigDecimal currentDeposits = BigDecimal.ZERO;

        for (Account acc : accounts) {
            if (acc.getStatus() == AccountStatus.ACTIVE) active++;
            else if (acc.getStatus() == AccountStatus.FROZEN) frozen++;
            else if (acc.getStatus() == AccountStatus.CLOSED) closed++;

            totalDeposits = totalDeposits.add(acc.getBalance());
            if (acc.getAccountType() == AccountType.SAVINGS) {
                savingsDeposits = savingsDeposits.add(acc.getBalance());
            } else {
                currentDeposits = currentDeposits.add(acc.getBalance());
            }
        }
        sendJson(ex, 200, new AdminOverviewDto(
                customers.size(), accounts.size(), active, frozen, closed,
                totalDeposits, savingsDeposits, currentDeposits, bankService.getRecentActivity()
        ));
    }

    private Customer authenticate(HttpExchange ex) throws BankException {
        String authHeader = ex.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new InvalidCredentialsException("Authorization: Bearer <token> header required.");
        }
        String token = authHeader.substring("Bearer ".length()).trim();
        TokenUtil.TokenClaims claims = tokenUtil.verifyToken(token);
        return authService.findById(claims.customerId());
    }

    private <T> T readJson(HttpExchange ex, Class<T> type) throws IOException {
        try (InputStream in = ex.getRequestBody()) {
            return mapper.readValue(in, type);
        }
    }

    private void sendJson(HttpExchange ex, int status, Object body) throws IOException {
        sendText(ex, status, "application/json; charset=utf-8", mapper.writeValueAsString(body));
    }

    private void sendText(HttpExchange ex, int status, String contentType, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    private void sendEmpty(HttpExchange ex, int status) throws IOException {
        ex.sendResponseHeaders(status, -1);
        ex.close();
    }

    private void addCorsHeaders(HttpExchange ex) {
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    private String queryParam(HttpExchange ex, String name) {
        String query = ex.getRequestURI().getRawQuery();
        if (query == null || query.isBlank()) {
            return null;
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            String key = URLDecoder.decode(eq >= 0 ? pair.substring(0, eq) : pair, StandardCharsets.UTF_8);
            if (name.equals(key)) {
                return eq >= 0 ? URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8) : "";
            }
        }
        return null;
    }

    private String extractSegment(String path, String prefix, String suffix) {
        return path.substring(prefix.length(), path.length() - suffix.length());
    }

    private String clientIp(HttpExchange ex) {
        return ex.getRemoteAddress() != null ? ex.getRemoteAddress().getAddress().getHostAddress() : "local";
    }

    private String normalizeAccountNo(String raw) {
        return raw == null ? "" : raw.trim().toUpperCase();
    }

    private LocalDate parseOptionalDate(String raw) throws ValidationException {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (Exception e) {
            throw new ValidationException("Invalid date format (expected YYYY-MM-DD): " + raw);
        }
    }

    public static void main(String[] args) throws BankException, IOException {
        DatabaseManager dbManager = new DatabaseManager();
        dbManager.initializeDatabase();

        CustomerDao customerDao = new SqliteCustomerDao(dbManager);
        AccountDao accountDao = new SqliteAccountDao(dbManager);
        TransactionDao transactionDao = new SqliteTransactionDao(dbManager);
        BankLogger logger = new BankLogger();

        AuthService authService = new AuthService(customerDao);
        BankService bankService = new BankService(dbManager, accountDao, transactionDao, logger);
        ChatbotService chatbotService = new ChatbotService();
        InterestService interestService = new InterestService(bankService);
        interestService.startScheduler(60);

        TokenUtil tokenUtil = new TokenUtil(System.getenv("TOKEN_SECRET"));
        ApiServer server = new ApiServer(authService, bankService, chatbotService, interestService, logger, tokenUtil);

        String portEnv = System.getenv("PORT");
        int port = (portEnv != null && !portEnv.isBlank()) ? Integer.parseInt(portEnv.trim()) : 8080;
        int boundPort = server.start(port);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop();
            interestService.stopScheduler();
        }));
        System.out.println("Meridian Trust Bank Web Portal & API running at http://localhost:" + boundPort);
    }
}
