# Meridian Trust Bank — Banking Management System (CSE 213)

A complete **Advanced Java Programming (CSE 213)** banking management system built with **Java 17**, **plain JDBC + SQLite**, **JavaFX 21**, **Javalin 6 REST API**, **JDK `HttpClient` AI Chatbot**, and a **static web frontend**.

- **Course:** Advanced Java Programming (CSE 213), SRM University-AP, 2026–27
- **Team:** Rasesh Saraf (Coordinator), Shaurya Kumar, Sahil Raj

---

## Key Features

- **OOP Domain Hierarchy:** `BankEntity` -> `Account` (`SavingsAccount`, `CurrentAccount`) & `Customer`, implementing `Transactable`, constructor/method overloading, runtime polymorphism, and `BigDecimal` money precision.
- **Custom Exception Hierarchy:** Checked `BankException` with specialized exceptions (`InsufficientFundsException`, `InvalidAmountException`, `AccountNotFoundException`, `InvalidCredentialsException`, `AccountLimitException`, `ValidationException`, `UnauthorizedException`, `DuplicateEntryException`).
- **Plain JDBC & SQLite:** Parameterized `PreparedStatement` DAOs, foreign-key enforcement, and atomic multi-step fund transfers (`setAutoCommit(false)`, `commit`, `rollback`).
- **Collections & Generics:** `HashMap` account cache, `LinkedList` recent activity queue, `TreeMap` date-grouped statements, `HashSet` duplicate contact check, `Iterator` safe removal, plus `Repository<T, ID>`, `Pair<K, V>`, and wildcard generic helpers.
- **Multithreading & Concurrency:** Background `InterestService` (`Runnable` + `ScheduledExecutorService`), `synchronized` operations with deadlock-free account lock ordering, `ConcurrencyDemo` with 50 concurrent threads, and `wait()`/`notify()` notification queue.
- **File I/O:** TXT and CSV statement exports (`FileOutputStream` / `BufferedWriter`), CSV import (`FileInputStream`), and stream-based audit logging to `logs/bank.log`.
- **JavaFX 21 Desktop App:** Login/Register, Dashboard, Deposit/Withdraw/Transfer, Statements & Export, Admin `TreeView` Panel, `MenuBar`/`ToolBar` with `ImageView` icons, `KeyEvent` + adapter listeners, `DropShadow`, and async AI Chatbot pane.
- **REST API & Web Frontend:** Javalin 6 API with signed HMAC-SHA256 bearer tokens, per-IP rate limiting, CORS, responsive vanilla HTML/CSS/JS frontend (`web/`), and floating AI FAQ assistant widget.

---

## Setup & Running Locally

### Prerequisites
- **JDK 17+** (`java -version`)
- **Apache Maven 3.9+** (`mvn -version`)

### 1. Run Build & All Tests
```bash
mvn clean verify
```

### 2. Run the JavaFX Desktop Application
```bash
mvn javafx:run
```

### 3. Run the REST API Server (Port 7070)
```bash
mvn package
java -jar target/banking-system-1.0.0-api.jar
```
(Or run `com.bank.api.ApiServer` directly from your IDE.)

### 4. Run the Static Web Frontend
Serve `web/` with any static file server (e.g., VS Code Live Server or Python):
```bash
cd web
python -m http.server 5500
```
Then open `http://localhost:5500`. Make sure `API_BASE_URL` in `web/app.js` matches your running API URL (`http://localhost:7070` locally).

---

## Environment Variables

| Variable | Default | Purpose |
|---|---|---|
| `BANK_DB_PATH` | `./bank.db` | Path to SQLite database file |
| `PORT` | `7070` | Port for Javalin REST API server |
| `FRONTEND_ORIGIN` | `*` (any) | Allowed CORS origin for the web frontend |
| `TOKEN_SECRET` | `dev-banking-secret-change-me` | HMAC-SHA256 signing secret for API session tokens |
| `ANTHROPIC_API_KEY` | *(empty)* | Server-side Anthropic API key (never expose in UI/JS/git) |
| `CHATBOT_MODEL` | `claude-sonnet-5-5` | Anthropic model used by `ChatbotService` |

Copy `.env.example` to `.env` for local configuration. Without `ANTHROPIC_API_KEY`, the chatbot runs in safe offline FAQ fallback mode.

---

## Sample Seeded Logins

On first run, `DatabaseManager` seeds an admin and three sample customers:

| Role | Email | Password | Seeded Accounts |
|---|---|---|---|
| `ADMIN` | `admin@bank.local` | `Admin@123` | — |
| `CUSTOMER` | `aarav@bank.local` | `Aarav@123` | `SB100001` (Savings: 15,000), `CA100002` (Current: 5,000) |
| `CUSTOMER` | `meera@bank.local` | `Meera@123` | `SB100003` (Savings: 25,000) |
| `CUSTOMER` | `kabir@bank.local` | `Kabir@123` | `CA100004` (Current: 12,000) |

---

## REST API Endpoints

| Method | Path | Auth | Purpose |
|---|---|---|---|
| `GET` | `/health` | Public | Host health check |
| `POST` | `/api/auth/register` | Public | Register a new customer and receive token |
| `POST` | `/api/auth/login` | Public | Authenticate and receive signed Bearer token |
| `GET` | `/api/auth/me` | Bearer | Current authenticated customer profile |
| `POST` | `/api/auth/change-password` | Bearer | Update account password |
| `GET` | `/api/accounts` | Bearer | List logged-in customer's accounts |
| `POST` | `/api/accounts` | Bearer | Open a new `SAVINGS` or `CURRENT` account |
| `POST` | `/api/accounts/{no}/deposit` | Bearer | Deposit funds into owned account |
| `POST` | `/api/accounts/{no}/withdraw` | Bearer | Withdraw funds from owned account |
| `POST` | `/api/accounts/{no}/close` | Bearer | Close a zero-balance owned account |
| `POST` | `/api/transfer` | Bearer | Atomic transfer between accounts |
| `GET` | `/api/accounts/{no}/transactions` | Bearer | Statement (`?from=`, `?to=`, or `?limit=`) |
| `GET` | `/api/accounts/{no}/statement/csv` | Bearer | Download statement as CSV |
| `POST` | `/api/chat` | Bearer | Ask the banking FAQ chatbot |
| `GET` | `/api/admin/overview` | Admin | Bank-wide liquidity, counts & recent activity |
| `GET` | `/api/admin/customers` | Admin | List all registered customers |
| `GET` | `/api/admin/accounts` | Admin | List/search (`?q=`) all accounts in the bank |
| `POST` | `/api/admin/accounts/{no}/status` | Admin | Freeze, activate, or close an account |
| `POST` | `/api/admin/interest/run` | Admin | Credit 3.5% monthly interest to active savings |

---

## Cloud Deployment (Render / Railway + Vercel)

1. **Deploy Java Backend (Render / Railway / Fly.io):**
   - Connect your Git repository and choose **Docker** deployment using the root `Dockerfile`.
   - Set env vars: `TOKEN_SECRET`, `FRONTEND_ORIGIN` (your Vercel URL), `ANTHROPIC_API_KEY` (optional), and `BANK_DB_PATH=/app/data/bank.db`.
   - **Important Persistence Note:** SQLite on free container hosting can lose data on container restarts or redeploys unless a **persistent disk/volume** is mounted at `/app/data`.
2. **Deploy Web Frontend (Vercel):**
   - Update `API_BASE_URL` at the top of `web/app.js` to your deployed backend URL.
   - Deploy the `web/` directory as a static site on Vercel (`web/vercel.json` is preconfigured).

---

## Syllabus Concept Mapping (CSE 213)

| Syllabus Concept | Where Demonstrated |
|---|---|
| Abstract Class & Multilevel Inheritance | `BankEntity` -> `Account` -> `SavingsAccount`, `CurrentAccount`; `BankEntity` -> `Customer` (`src/main/java/com/bank/model/`) |
| Interfaces | `Transactable` (`model/Transactable.java`), `Repository<T, ID>` (`generic/Repository.java`), `Filterable<T>` (`generic/Filterable.java`) |
| Constructor & Method Overloading | `Account`, `SavingsAccount`, `CurrentAccount`, `Customer`, `Transaction`; `Account.deposit(amount)` & `deposit(amount, desc)` |
| Method Overriding & Runtime Polymorphism | `withdraw()` and `calculateMonthlyInterest()` in `SavingsAccount` & `CurrentAccount`; `SqliteAccountDao.mapRow()` |
| `this`, `super`, `static`, `final`, Access Specifiers, Packages | `Account`, `BankEntity`, `SavingsAccount`, `CurrentAccount`, `IdGenerator`, package hierarchy `com.bank.*` |
| Custom Checked Exceptions & `try/catch/nested try/finally` | `BankException` & subclasses (`exception/*.java`); `BankService.transfer()` nested try & `finally` cleanup |
| Plain JDBC (`Connection`, `PreparedStatement`, `ResultSet`, Atomicity) | `DatabaseManager`, `SqliteCustomerDao`, `SqliteAccountDao`, `SqliteTransactionDao`, `BankService.transfer()` |
| Collections (`HashMap`, `ArrayList`, `LinkedList`, `TreeMap`, `HashSet`, `Iterator`) | `BankService` (`accountCache`, `recentActivity`, `getStatementGroupedByDate`, `purgeClosedAccountsFromCache`), `AuthService` (`HashSet`) |
| Generics & Wildcards (`? extends`, `? super`) | `Repository<T, ID>`, `Pair<K, V>`, `BaseFilter<T>`, `AccountBalanceFilter`, `GenericUtils.totalBalance(List<? extends Account>)` |
| Multithreading (`Thread`, `Runnable`, `synchronized`, `join`, `wait/notify`, deadlock prevention) | `InterestService`, `Account.transferTo`, `BankService.transfer`, `TransactionNotificationQueue`, `src/test/java/com/bank/ConcurrencyDemo.java` |
| File I/O (`FileOutputStream`, `FileInputStream`, `BufferedWriter`, try-with-resources) | `StatementService.java`, `BankLogger.java` |
| JavaFX UI, Delegation Events, `KeyEvent`, Adapter, `MenuBar`/`ToolBar`/`ImageView`, `DropShadow` | `BankingApp`, `LoginView`, `DashboardPane`, `TransactionPane`, `StatementPane`, `AdminPane`, `ChatbotPane`, `KeyPressedAdapter`, `UiHelpers` |
| HTTP Client & Lightweight REST API | `ChatbotService.java` (`java.net.http.HttpClient`), `ApiServer.java` (Javalin 6) |

