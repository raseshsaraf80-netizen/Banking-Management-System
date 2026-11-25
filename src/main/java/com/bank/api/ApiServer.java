// Author: Rasesh Saraf
// Concept: Javalin 6 REST API server reusing core Java services, RBAC, exception-to-HTTP mapping
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
import com.bank.exception.AccountLimitException;
import com.bank.exception.AccountNotFoundException;
import com.bank.exception.BankException;
import com.bank.exception.DuplicateEntryException;
import com.bank.exception.InsufficientFundsException;
import com.bank.exception.InvalidAmountException;
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
import io.javalin.Javalin;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.json.JavalinJackson;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Lightweight Javalin 6 REST API exposing banking operations, admin controls, and AI chatbot.
 */
public class ApiServer {

    private static final String ATTR_CUSTOMER = "authenticatedCustomer";

    private final AuthService authService;
    private final BankService bankService;
    private final ChatbotService chatbotService;
    private final InterestService interestService;
    private final BankLogger bankLogger;
    private final TokenUtil tokenUtil;
    private final RateLimiter loginRateLimiter;
    private final RateLimiter chatRateLimiter;

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
        this.loginRateLimiter = new RateLimiter(30, 60);
        this.chatRateLimiter = new RateLimiter(40, 60);
    }

    public Javalin createApp(String frontendOrigin) {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        Javalin app = Javalin.create(config -> {
            config.jsonMapper(new JavalinJackson(mapper, false));
            config.bundledPlugins.enableCors(cors -> cors.addRule(it -> {
                if (frontendOrigin != null && !frontendOrigin.isBlank() && !"*".equals(frontendOrigin)) {
                    it.allowHost(frontendOrigin.trim());
                } else {
                    it.anyHost();
                }
            }));
        });

        // Global exception mapping from domain BankException hierarchy to HTTP status codes
        app.exception(InvalidCredentialsException.class, (e, ctx) ->
                ctx.status(401).json(Map.of("error", e.getMessage())));
        app.exception(UnauthorizedException.class, (e, ctx) ->
                ctx.status(403).json(Map.of("error", e.getMessage())));
        app.exception(AccountNotFoundException.class, (e, ctx) ->
                ctx.status(404).json(Map.of("error", e.getMessage())));
        app.exception(DuplicateEntryException.class, (e, ctx) ->
                ctx.status(409).json(Map.of("error", e.getMessage())));
        app.exception(ValidationException.class, (e, ctx) ->
                ctx.status(400).json(Map.of("error", e.getMessage())));
        app.exception(InvalidAmountException.class, (e, ctx) ->
                ctx.status(400).json(Map.of("error", e.getMessage())));
        app.exception(InsufficientFundsException.class, (e, ctx) ->
                ctx.status(400).json(Map.of("error", e.getMessage())));
        app.exception(AccountLimitException.class, (e, ctx) ->
                ctx.status(400).json(Map.of("error", e.getMessage())));
        app.exception(BankException.class, (e, ctx) ->
                ctx.status(400).json(Map.of("error", e.getMessage())));
        app.exception(BadRequestResponse.class, (e, ctx) ->
                ctx.status(400).json(Map.of("error", "Invalid request body: " + e.getMessage())));
        app.exception(JsonProcessingException.class, (e, ctx) ->
                ctx.status(400).json(Map.of("error", "Invalid JSON payload.")));
        app.exception(Exception.class, (e, ctx) ->
                ctx.status(500).json(Map.of("error", "Internal server error: " + e.getMessage())));

        // Public health check
        app.get("/health", ctx -> ctx.json(Map.of("status", "ok", "bank", "SRM Trust Bank")));

        // Public authentication endpoints
        app.post("/api/auth/register", this::handleRegister);
        app.post("/api/auth/login", this::handleLogin);

        // Enforce Bearer token authentication on all other /api/* routes
        app.before("/api/*", ctx -> {
            String path = ctx.path();
            if ("OPTIONS".equalsIgnoreCase(ctx.method().name())
                    || "/api/auth/register".equals(path)
                    || "/api/auth/login".equals(path)) {
                return;
            }
            String authHeader = ctx.header("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                throw new InvalidCredentialsException("Authorization: Bearer <token> header required.");
            }
            String token = authHeader.substring("Bearer ".length()).trim();
            TokenUtil.TokenClaims claims = tokenUtil.verifyToken(token);
            Customer actor = authService.findById(claims.customerId());
            ctx.attribute(ATTR_CUSTOMER, actor);
        });

        // Customer profile & account routes
        app.get("/api/auth/me", ctx -> ctx.json(CustomerDto.from(currentCustomer(ctx))));
        app.put("/api/auth/profile", this::handleUpdateProfile);
        app.post("/api/auth/change-password", this::handleChangePassword);
        app.get("/api/accounts", this::handleListAccounts);
        app.post("/api/accounts", this::handleOpenAccount);
        app.post("/api/accounts/{no}/deposit", this::handleDeposit);
        app.post("/api/accounts/{no}/withdraw", this::handleWithdraw);
        app.post("/api/accounts/{no}/close", this::handleCloseAccount);
        app.post("/api/transfer", this::handleTransfer);
        app.get("/api/accounts/{no}/transactions", this::handleTransactions);
        app.get("/api/accounts/{no}/statement/csv", this::handleExportCsv);

        // AI chatbot endpoint
        app.post("/api/chat", this::handleChat);

        // Admin-only routes
        app.get("/api/admin/overview", this::handleAdminOverview);
        app.get("/api/admin/customers", this::handleAdminCustomers);
        app.get("/api/admin/accounts", this::handleAdminAccounts);
        app.post("/api/admin/accounts/{no}/status", this::handleAdminAccountStatus);
        app.post("/api/admin/interest/run", this::handleAdminRunInterest);
        app.get("/api/admin/logs", this::handleAdminLogs);

        return app;
    }

    private void handleRegister(Context ctx) throws BankException {
        RegisterRequest req = ctx.bodyAsClass(RegisterRequest.class);
        Customer created = authService.register(
                req.name(), req.email(), req.phone(), req.address(), req.password());
        String token = tokenUtil.createToken(created);
        ctx.status(201).json(new AuthResponse(token, CustomerDto.from(created)));
    }

    private void handleLogin(Context ctx) throws BankException {
        if (!loginRateLimiter.allow(ctx.ip())) {
            ctx.status(429).json(Map.of("error", "Too many login attempts. Please wait a minute."));
            return;
        }
        LoginRequest req = ctx.bodyAsClass(LoginRequest.class);
        Customer customer = authService.login(req.email(), req.password());
        String token = tokenUtil.createToken(customer);
        ctx.json(new AuthResponse(token, CustomerDto.from(customer)));
    }

    private void handleChangePassword(Context ctx) throws BankException {
        Customer actor = currentCustomer(ctx);
        ChangePasswordRequest req = ctx.bodyAsClass(ChangePasswordRequest.class);
        authService.changePassword(actor.getCustomerId(), req.oldPassword(), req.newPassword());
        ctx.json(Map.of("message", "Password updated successfully."));
    }

    private void handleUpdateProfile(Context ctx) throws BankException {
        Customer actor = currentCustomer(ctx);
        UpdateProfileRequest req = ctx.bodyAsClass(UpdateProfileRequest.class);
        Customer updated = authService.updateProfile(
                actor.getCustomerId(), req.name(), req.phone(), req.address());
        ctx.json(CustomerDto.from(updated));
    }

    private void handleListAccounts(Context ctx) throws BankException {
        Customer actor = currentCustomer(ctx);
        List<AccountDto> accounts = bankService.getAccountsForCustomer(actor.getCustomerId())
                .stream()
                .map(AccountDto::from)
                .toList();
        ctx.json(accounts);
    }

    private void handleOpenAccount(Context ctx) throws BankException {
        Customer actor = currentCustomer(ctx);
        OpenAccountRequest req = ctx.bodyAsClass(OpenAccountRequest.class);
        AccountType type;
        try {
            type = AccountType.valueOf(req.accountType().trim().toUpperCase());
        } catch (Exception ex) {
            throw new ValidationException("accountType must be SAVINGS or CURRENT.");
        }
        Account created = bankService.openAccount(actor, type, req.initialDeposit());
        ctx.status(201).json(AccountDto.from(created));
    }

    private void handleDeposit(Context ctx) throws BankException {
        Customer actor = currentCustomer(ctx);
        String accNo = normalizeAccountNo(ctx.pathParam("no"));
        AmountRequest req = ctx.bodyAsClass(AmountRequest.class);
        Transaction txn = bankService.deposit(actor, accNo, req.amount(), req.description());
        ctx.json(txn);
    }

    private void handleWithdraw(Context ctx) throws BankException {
        Customer actor = currentCustomer(ctx);
        String accNo = normalizeAccountNo(ctx.pathParam("no"));
        AmountRequest req = ctx.bodyAsClass(AmountRequest.class);
        Transaction txn = bankService.withdraw(actor, accNo, req.amount(), req.description());
        ctx.json(txn);
    }

    private void handleCloseAccount(Context ctx) throws BankException {
        Customer actor = currentCustomer(ctx);
        String accNo = normalizeAccountNo(ctx.pathParam("no"));
        bankService.closeAccount(actor, accNo);
        ctx.json(Map.of("message", "Account " + accNo + " closed."));
    }

    private void handleTransfer(Context ctx) throws BankException {
        Customer actor = currentCustomer(ctx);
        TransferRequest req = ctx.bodyAsClass(TransferRequest.class);
        bankService.transfer(
                actor,
                normalizeAccountNo(req.fromAccount()),
                normalizeAccountNo(req.toAccount()),
                req.amount(),
                req.description()
        );
        ctx.json(Map.of("message", "Transfer completed successfully."));
    }

    private void handleTransactions(Context ctx) throws BankException {
        Customer actor = currentCustomer(ctx);
        String accNo = normalizeAccountNo(ctx.pathParam("no"));
        String fromParam = ctx.queryParam("from");
        String toParam = ctx.queryParam("to");
        String limitParam = ctx.queryParam("limit");

        if (limitParam != null && !limitParam.isBlank()) {
            int limit;
            try {
                limit = Integer.parseInt(limitParam.trim());
            } catch (NumberFormatException ex) {
                throw new ValidationException("limit must be an integer.");
            }
            ctx.json(bankService.getMiniStatement(actor, accNo, limit));
            return;
        }
        LocalDate from = (fromParam != null && !fromParam.isBlank()) ? parseDate(fromParam) : null;
        LocalDate to = (toParam != null && !toParam.isBlank()) ? parseDate(toParam) : null;
        ctx.json(bankService.getStatement(actor, accNo, from, to));
    }

    private void handleExportCsv(Context ctx) throws BankException {
        Customer actor = currentCustomer(ctx);
        String accNo = normalizeAccountNo(ctx.pathParam("no"));
        String fromParam = ctx.queryParam("from");
        String toParam = ctx.queryParam("to");
        LocalDate from = (fromParam != null && !fromParam.isBlank()) ? parseDate(fromParam) : null;
        LocalDate to = (toParam != null && !toParam.isBlank()) ? parseDate(toParam) : null;

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
        ctx.header("Content-Disposition", "attachment; filename=\"" + accNo + "_" + LocalDate.now() + ".csv\"");
        ctx.contentType("text/csv; charset=utf-8");
        ctx.result(csv.toString());
    }

    private void handleChat(Context ctx) {
        if (!chatRateLimiter.allow(ctx.ip())) {
            ctx.status(429).json(Map.of("error", "Too many chat requests. Please slow down."));
            return;
        }
        ChatRequest req = ctx.bodyAsClass(ChatRequest.class);
        String reply = chatbotService.ask(req.message(), req.history());
        ctx.json(Map.of("reply", reply));
    }

    private void handleAdminOverview(Context ctx) throws BankException {
        Customer admin = currentCustomer(ctx);
        authService.requireAdmin(admin);

        List<Customer> customers = authService.getAllCustomers(admin);
        List<Account> accounts = bankService.getAllAccounts(admin);

        int active = 0;
        int frozen = 0;
        int closed = 0;
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

        ctx.json(new AdminOverviewDto(
                customers.size(),
                accounts.size(),
                active,
                frozen,
                closed,
                totalDeposits,
                savingsDeposits,
                currentDeposits,
                bankService.getRecentActivity()
        ));
    }

    private void handleAdminCustomers(Context ctx) throws BankException {
        Customer admin = currentCustomer(ctx);
        List<CustomerDto> customers = authService.getAllCustomers(admin)
                .stream()
                .map(CustomerDto::from)
                .toList();
        ctx.json(customers);
    }

    private void handleAdminAccounts(Context ctx) throws BankException {
        Customer actor = currentCustomer(ctx);
        String keyword = ctx.queryParam("q");
        List<Account> source = (keyword != null && !keyword.isBlank())
                ? bankService.searchAccounts(actor, keyword)
                : bankService.getAllAccounts(actor);
        ctx.json(source.stream().map(AccountDto::from).toList());
    }

    private void handleAdminAccountStatus(Context ctx) throws BankException {
        Customer admin = currentCustomer(ctx);
        String accNo = normalizeAccountNo(ctx.pathParam("no"));
        StatusUpdateRequest req = ctx.bodyAsClass(StatusUpdateRequest.class);
        AccountStatus status;
        try {
            status = AccountStatus.valueOf(req.status().trim().toUpperCase());
        } catch (Exception ex) {
            throw new ValidationException("status must be ACTIVE, FROZEN, or CLOSED.");
        }
        bankService.updateAccountStatus(admin, accNo, status);
        ctx.json(Map.of("message", "Account " + accNo + " status updated to " + status.name()));
    }

    private void handleAdminRunInterest(Context ctx) throws BankException {
        Customer admin = currentCustomer(ctx);
        authService.requireAdmin(admin);
        int credited = interestService.runNow();
        ctx.json(Map.of(
                "creditedAccounts", credited,
                "message", "Credited monthly interest to " + credited + " active savings account(s)."
        ));
    }

    private void handleAdminLogs(Context ctx) throws BankException {
        Customer admin = currentCustomer(ctx);
        authService.requireAdmin(admin);
        ctx.json(Map.of("lines", bankLogger.readRecentLines(40)));
    }

    private Customer currentCustomer(Context ctx) throws InvalidCredentialsException {
        Customer customer = ctx.attribute(ATTR_CUSTOMER);
        if (customer == null) {
            throw new InvalidCredentialsException("Authentication required.");
        }
        return customer;
    }

    private String normalizeAccountNo(String raw) {
        return raw == null ? "" : raw.trim().toUpperCase();
    }

    private LocalDate parseDate(String raw) throws ValidationException {
        try {
            return LocalDate.parse(raw.trim());
        } catch (Exception ex) {
            throw new ValidationException("Invalid date format (expected YYYY-MM-DD): " + raw);
        }
    }

    public static void main(String[] args) throws BankException {
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
        int port = (portEnv != null && !portEnv.isBlank()) ? Integer.parseInt(portEnv.trim()) : 7070;
        String frontendOrigin = System.getenv("FRONTEND_ORIGIN");

        Javalin app = server.createApp(frontendOrigin);
        Runtime.getRuntime().addShutdownHook(new Thread(interestService::stopScheduler));
        app.start(port);
    }
}
