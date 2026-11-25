# Architecture & Design Diagrams — SRM Trust Bank

## 1. One-Page Architecture Overview

SRM Trust Bank follows a clean, layered Java architecture so that **both the JavaFX 21 desktop client and the Javalin 6 REST API share the exact same business logic and SQLite persistence layer**:

1. **Presentation Layer (`com.bank.ui`, `web/`, `com.bank.api`)**
   - **Desktop UI (`com.bank.ui`):** JavaFX 21 application (`BankingApp`) with `LoginView`, `DashboardPane`, `TransactionPane`, `StatementPane`, `AdminPane`, and `ChatbotPane`. Uses delegation listeners, `KeyPressedAdapter`, `MenuBar`/`ToolBar`, and `Platform.runLater` for async chatbot calls.
   - **Web Frontend (`web/`):** Responsive HTML/CSS/vanilla JS client calling the Javalin REST API via `fetch()`, storing HMAC-signed session tokens in `sessionStorage`, and providing a floating FAQ chatbot widget.
   - **REST Layer (`com.bank.api`):** `ApiServer` starts Javalin on `PORT` (default `7070`), enforces Bearer token authentication (`TokenUtil`) and rate limiting (`RateLimiter`), maps `BankException` subclasses to HTTP status codes (`400`, `401`, `403`, `404`, `409`), and never leaks password hashes.

2. **Service Layer (`com.bank.service`, `com.bank.ai`)**
   - `AuthService`: PBKDF2WithHmacSHA256 authentication, `HashSet` duplicate detection, RBAC (`CUSTOMER` / `ADMIN`).
   - `BankService`: Account lifecycle, deposits, withdrawals, deadlock-free atomic transfers (consistent lock ordering + SQLite `setAutoCommit(false)` commit/rollback), `HashMap` cache, `LinkedList` recent activity, `TreeMap` date statements, and `Iterator` cache cleanup.
   - `InterestService`: `Runnable` background scheduler + manual `runNow()` trigger for 3.5% p.a. savings interest.
   - `StatementService`: Stream-based TXT/CSV export and CSV import.
   - `ChatbotService`: Stateless `java.net.http.HttpClient` client for Anthropic Messages API with 10-second timeout, zero DB access, and offline FAQ fallback.

3. **Data Access & Domain Layer (`com.bank.dao`, `com.bank.model`, `com.bank.generic`, `com.bank.util`)**
   - `Repository<T, ID>` implemented by `SqliteCustomerDao`, `SqliteAccountDao`, and `SqliteTransactionDao` using parameterized `PreparedStatement` queries only.
   - `DatabaseManager` initializes SQLite (`PRAGMA foreign_keys = ON`) and seeds initial demo records on first run.

---

## 2. UML Class Diagram

```mermaid
classDiagram
    class BankEntity {
        <<abstract>>
        +BANK_NAME: String
        #createdAt: LocalDateTime
        +getEntitySummary()* String
    }

    class Transactable {
        <<interface>>
        +deposit(amount: BigDecimal)
        +withdraw(amount: BigDecimal)
        +transferTo(target: Account, amount: BigDecimal)
    }

    class Account {
        <<abstract>>
        #accountNumber: String
        #customerId: long
        #balance: BigDecimal
        #openedOn: LocalDate
        #status: AccountStatus
        +deposit(amount: BigDecimal)
        +deposit(amount: BigDecimal, description: String)
        +withdraw(amount: BigDecimal)*
        +calculateMonthlyInterest()* BigDecimal
        +getAccountType()* AccountType
    }

    class SavingsAccount {
        +MIN_BALANCE: BigDecimal
        +ANNUAL_INTEREST_RATE: BigDecimal
        +MONTHLY_WITHDRAWAL_LIMIT: int
        +withdraw(amount: BigDecimal)
        +calculateMonthlyInterest() BigDecimal
    }

    class CurrentAccount {
        +OVERDRAFT_LIMIT: BigDecimal
        +withdraw(amount: BigDecimal)
        +calculateMonthlyInterest() BigDecimal
    }

    class Customer {
        -customerId: long
        -name: String
        -email: String
        -phone: String
        -role: Role
    }

    class Transaction {
        -txnId: long
        -accountNumber: String
        -type: TransactionType
        -amount: BigDecimal
        -balanceAfter: BigDecimal
        -timestamp: LocalDateTime
    }

    class Repository~T, ID~ {
        <<interface>>
        +save(entity: T) T
        +findById(id: ID) Optional~T~
        +findAll() List~T~
        +delete(id: ID) boolean
    }

    BankEntity <|-- Account
    BankEntity <|-- Customer
    Transactable <|.. Account
    Account <|-- SavingsAccount
    Account <|-- CurrentAccount
    Customer "1" --> "0..*" Account : owns
    Account "1" --> "0..*" Transaction : records
```

---

## 3. Entity-Relationship (ER) Diagram

```mermaid
erDiagram
    CUSTOMERS {
        INTEGER customer_id PK
        TEXT name
        TEXT email UK
        TEXT phone
        TEXT address
        TEXT password_hash
        TEXT role
        TEXT created_at
    }

    ACCOUNTS {
        TEXT account_number PK
        INTEGER customer_id FK
        TEXT account_type
        TEXT balance
        TEXT status
        TEXT opened_on
    }

    TRANSACTIONS {
        INTEGER txn_id PK
        TEXT account_number FK
        TEXT type
        TEXT amount
        TEXT balance_after
        TEXT description
        TEXT created_at
    }

    CUSTOMERS ||--o{ ACCOUNTS : "holds"
    ACCOUNTS ||--o{ TRANSACTIONS : "logs"
```

