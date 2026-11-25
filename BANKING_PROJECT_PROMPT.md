# MASTER PROMPT: Banking Management System (Advanced Java, CSE 213)

You are a senior Java engineer pair-programming with a 3-student team. Build the project below end to end, phase by phase. After each phase, make sure the project compiles and tests pass before moving on. Keep code simple, well commented, and easy for students to explain in a viva.

---

## 0. Context and hard rules

- **Course:** Advanced Java Programming (CSE 213), SRM University-AP, 2026-27.
- **Team (3):** Rasesh Saraf (coordinator), Shaurya Kumar, Sahil Raj.
- **Grading cares about:** meaningful use of core Java (OOP, exceptions, collections, JDBC, multithreading, I/O, events, generics, JavaFX), clean code organisation, input validation, and every member being able to explain their part.
- **Java must be the primary language.** The Java core is the graded product. The website and chatbot are add-ons built on top of it.
- **No black boxes:** avoid heavy frameworks that hide the Java concepts. Do NOT use Spring Boot, Hibernate or JPA. Use plain JDBC.
- **Readable over clever:** no advanced tricks the students cannot explain. Add short comments saying which syllabus concept each class demonstrates (e.g. `// Concept: Abstract class + inheritance`).
- Do not use the names "Ananya" or "Rohan" anywhere (sample data, comments, tests). Use sample names such as Aarav, Meera, Kabir, Diya.

## 1. Tech stack (fixed)

| Area | Choice |
|---|---|
| Language / build | Java 17, Maven (multi-module is NOT needed; single module) |
| Database | SQLite via `org.xerial:sqlite-jdbc`, plain JDBC with `PreparedStatement` |
| Desktop UI | JavaFX 21 (`org.openjfx`), FXML optional, code-built UI is fine |
| REST layer | Javalin 6 (lightweight) + Jackson for JSON |
| Tests | JUnit 5 |
| Password hashing | PBKDF2 from the JDK (`javax.crypto`), no extra library |
| Chatbot | Java 11+ `java.net.http.HttpClient` calling the Anthropic Messages API |
| Web frontend | Plain HTML, CSS, vanilla JS (single folder `web/`) |
| Deployment | Backend: Docker image for Render/Railway/Fly.io. Frontend: static site on Vercel. (Vercel does not run Java.) |

## 2. Repository layout

```
banking-system/
├── pom.xml
├── Dockerfile
├── README.md
├── docs/                      # class diagram, ER diagram, report notes
├── web/                       # static website (index.html, style.css, app.js)
├── src/main/java/com/bank/
│   ├── model/                 # Account (abstract), SavingsAccount, CurrentAccount, Customer, Transaction, enums
│   ├── exception/             # custom exceptions
│   ├── dao/                   # JDBC data access (interfaces + SQLite impls)
│   ├── service/               # BankService, AuthService, InterestService, StatementService
│   ├── util/                  # DatabaseManager, Validator, PasswordUtil, IdGenerator
│   ├── generic/               # Repository<T,ID>, Pair<K,V>, generic helpers
│   ├── ui/                    # JavaFX app, controllers, event handlers
│   ├── api/                   # Javalin REST server + routes
│   └── ai/                    # ChatbotService (Anthropic API client)
└── src/test/java/com/bank/    # JUnit tests
```

## 3. Domain model (OOP)

Implement and comment these:

- `abstract class Account` with: `accountNumber`, `customerId`, `balance` (use `BigDecimal`), `openedOn`, `status`. Abstract methods: `withdraw(BigDecimal)`, `calculateMonthlyInterest()`, `getAccountType()`. Concrete: `deposit`, `getBalance`, `toString`.
- `SavingsAccount extends Account`: minimum balance (e.g. 1000), interest rate (e.g. 3.5% p.a.), monthly withdrawal limit.
- `CurrentAccount extends Account`: overdraft limit (e.g. 10000), no interest.
- `interface Transactable { void deposit(BigDecimal); void withdraw(BigDecimal) throws ...; void transferTo(Account, BigDecimal) throws ...; }` implemented by `Account`.
- `Customer`: id, name, email, phone, address, hashed password, role (`CUSTOMER` / `ADMIN`).
- `Transaction`: id, accountNumber, type (`DEPOSIT`, `WITHDRAWAL`, `TRANSFER_IN`, `TRANSFER_OUT`, `INTEREST`), amount, balanceAfter, timestamp, description.
- Demonstrate: constructors and **constructor overloading**, **method overloading** (e.g. `deposit(amount)` and `deposit(amount, description)`), **method overriding / runtime polymorphism** (`withdraw`, `calculateMonthlyInterest`), `this`, `static` (e.g. account number counter / bank name), `final` (constants), `super`, access specifiers, multilevel hierarchy (add `abstract class BankEntity` -> `Account`/`Customer` if it stays natural), and **packages**.

## 4. Exception handling

Create these checked exceptions under `com.bank.exception`, all extending a base `BankException extends Exception`:

- `InsufficientFundsException`
- `InvalidAmountException`
- `AccountNotFoundException`
- `InvalidCredentialsException`
- `AccountLimitException` (min balance / withdrawal limit / overdraft exceeded)
- `ValidationException`

Rules: use try / multiple catch / nested try / `throw` / `throws` / `finally` meaningfully. Example: transfer uses nested try, and `finally` closes resources and logs the attempt. DAO methods wrap `SQLException` into a `BankException`. Never swallow exceptions silently.

## 5. Database (JDBC + SQLite)

`DatabaseManager` creates `bank.db` and tables on first run (path configurable via env var `BANK_DB_PATH`, default `./bank.db`). Enable `PRAGMA foreign_keys = ON`.

```sql
CREATE TABLE IF NOT EXISTS customers (
  customer_id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL,
  email TEXT NOT NULL UNIQUE,
  phone TEXT NOT NULL,
  address TEXT,
  password_hash TEXT NOT NULL,
  role TEXT NOT NULL DEFAULT 'CUSTOMER',
  created_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS accounts (
  account_number TEXT PRIMARY KEY,
  customer_id INTEGER NOT NULL REFERENCES customers(customer_id),
  account_type TEXT NOT NULL,          -- SAVINGS / CURRENT
  balance TEXT NOT NULL,               -- BigDecimal stored as text
  status TEXT NOT NULL DEFAULT 'ACTIVE',
  opened_on TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS transactions (
  txn_id INTEGER PRIMARY KEY AUTOINCREMENT,
  account_number TEXT NOT NULL REFERENCES accounts(account_number),
  type TEXT NOT NULL,
  amount TEXT NOT NULL,
  balance_after TEXT NOT NULL,
  description TEXT,
  created_at TEXT NOT NULL
);
```

- Full **CRUD** for customers, accounts, transactions through DAO classes using `PreparedStatement` only (no string concatenation in SQL).
- **Transfers must be atomic:** `setAutoCommit(false)`, commit on success, rollback on any failure.
- Seed an admin user and 3 sample customers on first run (passwords hashed, documented in README).

## 6. Collections and generics

- Keep an in-memory cache `Map<String, Account>` (`HashMap`) in `BankService`; use `ArrayList<Transaction>` for statements, `LinkedList` for a "recent activity" queue (last 10), `TreeMap` for sorted statements by date, `HashSet` for detecting duplicate emails/phones, and an `Iterator` to loop and remove closed accounts.
- Generic code: `interface Repository<T, ID>` with `save/findById/findAll/delete`, implemented by the DAOs; a `Pair<K,V>` generic class; one generic method with a wildcard parameter (e.g. `static double totalBalance(List<? extends Account> accounts)`); a generic interface and a small generic class hierarchy.

## 7. Services (business logic)

- `AuthService`: register, login (PBKDF2 with salt), change password, role check.
- `BankService`: open account, close account, deposit, withdraw, transfer, balance enquiry, mini statement (last N), full statement with date range, search accounts (admin).
- `Validator`: email regex, 10-digit phone, positive amounts with max 2 decimals, non-empty names, strong password rule. Throw `ValidationException` with clear messages.
- `InterestService`: monthly interest applied to all active savings accounts.

## 8. Multithreading

- `InterestService` runs on a background thread (`Runnable` implementation) via a scheduled thread, with a manual "Run now" trigger for demo.
- `synchronized` account operations. For transfers, **lock the two accounts in a consistent order (by account number)** to avoid deadlock.
- A demo class `ConcurrencyDemo` (in `src/test`) that fires 50 concurrent deposits/withdrawals on one account with `Thread` objects and proves the final balance is correct. Also show thread life-cycle states and `join()`.
- Show inter-thread communication with `wait()/notify()` in one small, clearly labelled example (e.g. a transaction-notification queue between a producer thread and a logger thread).

## 9. File I/O

- `StatementService` exports an account statement to `statements/<accountNumber>_<date>.txt` and `.csv` using `FileOutputStream` / `BufferedWriter`, and can read a CSV back with `FileInputStream`. Use try-with-resources.
- Append every transaction to `logs/bank.log` (plain stream-based logger, not a framework).

## 10. JavaFX desktop app (events, AWT-style delegation model)

Screens:
1. **Login / Register**
2. **Customer dashboard:** accounts list (`ListView` or `TableView`), balance, quick actions
3. **Deposit / Withdraw / Transfer** forms (`TextField`, `ComboBox`, `RadioButton` for account type, validation messages)
4. **Statement** view with filters, **Export** button (calls StatementService)
5. **Admin panel:** `TreeView` or table of all customers/accounts, search, freeze/close account, "Run interest now"
6. **MenuBar** (File: Export, Logout, Exit; Help: About) with a `Toolbar`, and an image on at least one `MenuItem`
7. **Chatbot panel** (see section 12)

Events: use the delegation event model with explicit listeners/handlers, include at least one `KeyEvent` handler (Enter submits login; Esc clears a form) and one adapter-style example. Show a clear error alert for every custom exception. Basic effects (e.g. `DropShadow`) on the main card. Keep UI code separated from service code.

## 11. REST API (for the website)

Javalin server in `com.bank.api`, started by `ApiServer.main` on `PORT` env var (default 7070), reusing the same services as the desktop app. CORS enabled for the configured frontend origin (`FRONTEND_ORIGIN` env var).

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/auth/register` | create customer |
| POST | `/api/auth/login` | returns a signed session token |
| GET | `/api/accounts` | the logged-in customer's accounts |
| POST | `/api/accounts` | open a new account |
| POST | `/api/accounts/{no}/deposit` | deposit |
| POST | `/api/accounts/{no}/withdraw` | withdraw |
| POST | `/api/transfer` | transfer between accounts |
| GET | `/api/accounts/{no}/transactions` | statement (optional `from`, `to`, `limit`) |
| POST | `/api/chat` | chatbot message |
| GET | `/api/admin/accounts` | admin-only list |
| GET | `/health` | health check for the host |

Rules: authenticate every route except register/login/health; a customer can only touch their own accounts (authorisation check in service layer, not just UI); map each `BankException` to a proper HTTP status (400, 401, 403, 404, 409) with a JSON `{ "error": "..." }`; never return password hashes; simple in-memory rate limit on login and chat.

## 12. AI chatbot

- `ChatbotService` in `com.bank.ai` calls `POST https://api.anthropic.com/v1/messages` using `HttpClient`. Headers: `x-api-key` from env var `ANTHROPIC_API_KEY`, `anthropic-version: 2023-06-01`, `content-type: application/json`. Model name comes from env var `CHATBOT_MODEL` (default `claude-sonnet-5-5`), `max_tokens` 500.
- **The API key lives only on the server. Never put it in JavaFX code, web JS, git, or the README.** Provide `.env.example` and add `.env` to `.gitignore`.
- System prompt: a friendly banking-help assistant for "this demo bank"; answers FAQs (how to open an account, transfer limits, minimum balance, how interest works, how to export statements) and explains app features; refuses to give real financial/legal advice; says it cannot see or change account data; never asks for passwords, PINs or full card numbers.
- Chatbot gets **no database access**. It receives only the user's message plus the last few chat turns. (Optional stretch: the backend may add the logged-in user's own current balance to the prompt only if the user explicitly asks for it.)
- Handle API failure, timeout (10 s) and empty replies gracefully with a friendly fallback message. Log errors without logging the key.
- Use it from both the JavaFX chat panel (run the HTTP call on a background thread, update the UI with `Platform.runLater`) and the website widget.

## 13. Website (`web/`)

Simple, clean, responsive: login/register page, dashboard (accounts, balance cards), forms for deposit/withdraw/transfer, transaction table, floating chatbot widget. Plain `fetch()` calls to the API; the base URL is set in one config constant at the top of `app.js`. Store the token in memory or `sessionStorage`. Show error messages from the API. Add a `vercel.json` for static hosting.

## 14. Testing

JUnit 5 tests (use a temp SQLite file per test):
- deposit/withdraw happy paths and every exception path
- savings minimum balance, current account overdraft
- transfer atomicity (rollback when the credit side fails)
- validator edge cases
- concurrency test (section 8)
- auth: wrong password, duplicate email, role access
- API route tests for at least login, deposit and unauthorised access

## 15. Deployment files

- `Dockerfile` (multi-stage: Maven build, then slim JRE 17 runtime; runs `ApiServer`; exposes `PORT`).
- `README.md` with: overview, feature list, setup (JDK 17, Maven, how to run the JavaFX app with `mvn javafx:run`, how to run the API), env vars table, sample logins, API table, deployment steps for Render + Vercel, and a "Syllabus concept mapping" table (concept -> class/file).
- Note in the README that SQLite on free hosting can lose data on restart unless a persistent disk is attached.

## 16. Documentation to generate

Under `docs/`: UML class diagram (Mermaid), ER diagram (Mermaid), a one-page architecture description, and a `CONTRIBUTIONS.md` using the split below.

## 17. Work split (tag classes and commits accordingly)

- **Rasesh:** `api/`, `ai/`, `web/`, Dockerfile, deployment, README
- **Shaurya:** `model/`, `exception/`, `generic/`, `service/BankService`, collections, unit tests for these
- **Sahil:** `dao/`, `util/DatabaseManager`, file I/O, `ui/` (JavaFX), multithreading demo

Put a header comment `// Author: <name>` on each class. Everything shares the same services, so keep interfaces stable.

## 18. Execution order (do all of it, with a compile + test check after each step)

1. Maven project skeleton, dependencies, package structure
2. Model, exceptions, validator
3. Database manager, DAOs, schema, seed data
4. Services (auth, bank, interest, statements) + unit tests
5. Multithreading + file I/O + concurrency demo
6. JavaFX app (all screens)
7. REST API + API tests
8. Chatbot service + JavaFX panel + `/api/chat`
9. Web frontend
10. Dockerfile, README, docs, contribution file
11. Final pass: remove dead code, make sure `mvn clean verify` passes, list how to run everything

## 19. Definition of done

- `mvn clean verify` passes
- `mvn javafx:run` opens the desktop app; login, deposit, withdraw, transfer, statement export all work
- API runs locally and the website works against it
- Chatbot answers FAQs and fails gracefully with no API key
- No secrets in the repo
- README maps every syllabus concept to a file
- At the end, print a short list of anything you could not complete or that needs manual action by the team

## 20. Style rules for the agent

- Prefer clarity to cleverness; short methods; meaningful names; Javadoc on public classes
- No unexplained magic: if you use a library feature, add a one-line comment saying what it does
- Do not copy large blocks from external sources
- If a requirement conflicts with another, ask once, otherwise pick the simpler option and note it in the README
