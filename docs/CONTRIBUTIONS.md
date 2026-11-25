# Team Contributions — CSE 213 Banking Management System

**Course:** Advanced Java Programming (CSE 213), SRM University-AP (2026–27)

## Work Split

### 1. Rasesh Saraf (Coordinator)
- **Packages & Files:**
  - `com.bank.api.*` (`ApiServer`, `ApiDtos`, `TokenUtil`, `RateLimiter`)
  - `com.bank.ai.*` (`ChatbotService`, `ChatTurn`)
  - `web/` (`index.html`, `style.css`, `app.js`, `vercel.json`)
  - Deployment & Docs (`Dockerfile`, `.env.example`, `README.md`, `docs/architecture.md`, `docs/CONTRIBUTIONS.md`)
  - Integration Tests (`ApiServerTest`, `ChatbotServiceTest`)
- **Viva Focus:** Javalin REST routing, HTTP status mapping for custom exceptions, HMAC-SHA256 bearer tokens, per-IP rate limiting, CORS, JDK `HttpClient` integration with Anthropic Messages API, static web `fetch()` client, and Docker multi-stage packaging.

### 2. Shaurya Kumar
- **Packages & Files:**
  - `com.bank.model.*` (`BankEntity`, `Transactable`, `Account`, `SavingsAccount`, `CurrentAccount`, `Customer`, `Transaction`, enums)
  - `com.bank.exception.*` (`BankException` and all checked domain exceptions)
  - `com.bank.generic.*` (`Repository`, `Pair`, `Filterable`, `BaseFilter`, `AccountBalanceFilter`, `GenericUtils`)
  - `com.bank.service.*` (`BankService`, `AuthService`, `InterestService`)
  - `com.bank.util.*` (`Validator`, `IdGenerator`)
  - Unit Tests (`ValidatorTest`, `AuthServiceTest`, `BankServiceTest`)
- **Viva Focus:** OOP hierarchy and runtime polymorphism, checked exception hierarchy and nested `try/catch/finally`, Collections (`HashMap`, `LinkedList`, `TreeMap`, `HashSet`, `Iterator`), Generics with bounded wildcards (`? extends Account`, `? super T`), atomic transfer rollback, and deadlock-free lock ordering.

### 3. Sahil Raj
- **Packages & Files:**
  - `com.bank.util.DatabaseManager`, `com.bank.util.PasswordUtil`, `com.bank.util.BankLogger`
  - `com.bank.dao.*` (`CustomerDao`, `AccountDao`, `TransactionDao`, and SQLite JDBC implementations)
  - `com.bank.service.StatementService`, `com.bank.service.TransactionNotificationQueue`
  - `com.bank.ui.*` (`BankingApp`, `LoginView`, `DashboardPane`, `TransactionPane`, `StatementPane`, `AdminPane`, `ChatbotPane`, `KeyPressedAdapter`, `UiHelpers`)
  - File I/O & Concurrency Tests (`StatementServiceTest`, `ConcurrencyDemo`)
- **Viva Focus:** Plain JDBC with `PreparedStatement` and `PRAGMA foreign_keys = ON`, PBKDF2 password hashing, File I/O (`FileOutputStream`, `FileInputStream`, `BufferedWriter`, try-with-resources), multithreading (`Thread.State`, `join()`, `wait()/notify()`), and JavaFX desktop UI with delegation listeners, `KeyEvent`, adapter classes, `MenuBar`/`ToolBar`, and `Platform.runLater`.

