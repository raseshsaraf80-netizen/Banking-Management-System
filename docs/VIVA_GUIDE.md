# Faculty Viva & Explanation Guide — Meridian Trust Bank (CSE 213)

## 1. How to Explain Our Tech Stack in 30 Seconds
> *"We built a pure Core Java 17 Banking Management System without heavy frameworks like Spring Boot, Hibernate, or even Javalin. Every CSE 213 concept—OOP, custom exceptions, plain JDBC, collections, generics, multithreading, File I/O, and JavaFX—is written directly by us. Both the JavaFX 21 desktop app and the web portal (served on `http://localhost:8080` by Java's built-in `HttpServer`) share the exact same Java service layer and SQLite database."*

- **Java 17 + Maven:** Single-module Java project with minimal dependencies (`sqlite-jdbc`, `javafx`, `jackson`, `junit-jupiter`).
- **SQLite + Plain JDBC:** Zero database server setup; `DatabaseManager` creates `bank.db` automatically using `PreparedStatement`.
- **JavaFX 21:** Native desktop banking app (`mvn javafx:run`).
- **Built-in JDK `HttpServer` + HTML/CSS/JS:** `com.sun.net.httpserver.HttpServer` (part of standard Java, zero extra web framework) serves both `web/` and `/api/*` on `http://localhost:8080` (`mvn exec:java`).
- **JDK `HttpClient` Chatbot:** Calls Gemini/Anthropic if an API key is set, or falls back to an offline banking FAQ engine.

---

## 2. Team Roles (Per CSE 213 Project Role Keywords)

| Member | Assigned Keywords | What to Explain in Viva |
|---|---|---|
| **Rasesh Saraf** (Coordinator) | System Design, Module Integration, Documentation | `ApiServer.java` (REST routes & exception-to-HTTP mapping), `TokenUtil.java` (UUID session map), `ChatbotService.java` (`HttpClient` AI + FAQ fallback), `web/` frontend, `Dockerfile` + Render/Vercel deployment. |
| **Shaurya Kumar** | Requirements Analysis, Java Implementation, Software Testing | `BankEntity -> Account -> SavingsAccount/CurrentAccount`, `Transactable`, `BankException` hierarchy, `Repository<T,ID>`, `Pair<K,V>`, `GenericUtils`, `BankService` (collections + atomic transfers), and JUnit 5 tests. |
| **Sahil Raj** | Database Design, User Interface Design, Project Demonstration | `DatabaseManager.java` (3 tables + foreign keys + seeding), `Sqlite*Dao` (`PreparedStatement`), `StatementService` & `BankLogger` (File I/O), `ConcurrencyDemo` (50 threads + `wait()/notify()`), and JavaFX 21 desktop UI (`com.bank.ui.*`). |

---

## 3. Top 10 Viva Questions & Simple 2-Line Answers

1. **Why did you use `BigDecimal` instead of `double` for money?**
   `double` has binary floating-point rounding errors (like `0.1 + 0.2 != 0.3`). `BigDecimal` guarantees exact decimal arithmetic to 2 decimal places.
2. **How do you demonstrate Runtime Polymorphism?**
   `Account` declares abstract `withdraw()` and `calculateMonthlyInterest()`. At runtime, `SavingsAccount` enforces ₹1,000 minimum balance and 3.5% interest, while `CurrentAccount` allows ₹10,000 overdraft and 0% interest.
3. **How do you prevent SQL Injection?**
   Every SQL query in `SqliteCustomerDao`, `SqliteAccountDao`, and `SqliteTransactionDao` uses `PreparedStatement` with `?` placeholders—never string concatenation.
4. **How are fund transfers atomic?**
   `BankService.transfer()` sets `conn.setAutoCommit(false)`, debits the sender, credits the receiver, inserts both ledger rows, and calls `conn.commit()`. If any step fails, `conn.rollback()` undoes everything.
5. **How do you prevent deadlocks during concurrent transfers?**
   In `BankService.transfer()` and `Account.transferTo()`, we always lock the two `Account` objects in alphabetical order of `accountNumber` (`firstLock`, `secondLock`), preventing AB-BA deadlocks.
6. **Where are Collections used meaningfully?**
   `HashMap` for account cache, `LinkedList` for last-10 recent activity, `TreeMap` for date-sorted statements, `HashSet` for duplicate email/phone checks, and `Iterator` to safely remove `CLOSED` accounts from the cache.
7. **Where are Generics & Wildcards used?**
   `Repository<T, ID>` is implemented by all 3 DAOs, `Pair<K, V>` pairs values, `Filterable<T> -> BaseFilter<T> -> AccountBalanceFilter` shows a generic hierarchy, and `GenericUtils.totalBalance(List<? extends Account>)` uses a bounded wildcard.
8. **How is Multithreading demonstrated?**
   `InterestService` implements `Runnable` on a background `ScheduledExecutorService`, `ConcurrencyDemo` runs 50 concurrent deposit/withdrawal `Thread`s with `join()` and `Thread.State`, and `TransactionNotificationQueue` uses `wait()`/`notifyAll()`.
9. **How is File I/O used?**
   `StatementService` exports TXT/CSV statements using `FileOutputStream` + `BufferedWriter` and reads CSV back with `FileInputStream` + `BufferedReader` inside try-with-resources; `BankLogger` appends to `logs/bank.log`.
10. **How are passwords and API keys kept secure?**
    Passwords are hashed with salted `PBKDF2WithHmacSHA256` (`PasswordUtil`) and never returned in JSON (`@JsonIgnore` + DTOs). AI API keys live only in server environment variables, never in frontend JS or Git.

