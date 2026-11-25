// Author: Sahil Raj
// Concept: JavaFX Application entry point with MenuBar, ToolBar, ImageView on MenuItem, and screen routing
package com.bank.ui;

import com.bank.ai.ChatbotService;
import com.bank.dao.AccountDao;
import com.bank.dao.CustomerDao;
import com.bank.dao.SqliteAccountDao;
import com.bank.dao.SqliteCustomerDao;
import com.bank.dao.SqliteTransactionDao;
import com.bank.dao.TransactionDao;
import com.bank.exception.BankException;
import com.bank.model.Customer;
import com.bank.model.Role;
import com.bank.service.AuthService;
import com.bank.service.BankService;
import com.bank.service.InterestService;
import com.bank.service.StatementService;
import com.bank.util.BankLogger;
import com.bank.util.DatabaseManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

/**
 * Main JavaFX desktop application for SRM Trust Bank.
 */
public class BankingApp extends Application {

    private AuthService authService;
    private BankService bankService;
    private StatementService statementService;
    private InterestService interestService;
    private ChatbotService chatbotService;

    private Stage primaryStage;
    private Customer currentCustomer;

    private DashboardPane dashboardPane;
    private TransactionPane transactionPane;
    private StatementPane statementPane;
    private CalculatorPane calculatorPane;
    private AdminPane adminPane;
    private ChatbotPane chatbotPane;

    @Override
    public void init() throws Exception {
        DatabaseManager dbManager = new DatabaseManager();
        dbManager.initializeDatabase();

        CustomerDao customerDao = new SqliteCustomerDao(dbManager);
        AccountDao accountDao = new SqliteAccountDao(dbManager);
        TransactionDao transactionDao = new SqliteTransactionDao(dbManager);
        BankLogger logger = new BankLogger();

        this.authService = new AuthService(customerDao);
        this.bankService = new BankService(dbManager, accountDao, transactionDao, logger);
        this.statementService = new StatementService();
        this.interestService = new InterestService(bankService);
        this.chatbotService = new ChatbotService();
        this.interestService.startScheduler(60);
    }

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        stage.setTitle("SRM Trust Bank - Advanced Java (CSE 213)");
        showLoginScreen();
        stage.show();
    }

    @Override
    public void stop() {
        if (interestService != null) {
            interestService.stopScheduler();
        }
    }

    private void showLoginScreen() {
        this.currentCustomer = null;
        LoginView loginView = new LoginView(authService, this::onAuthenticated);
        Scene scene = new Scene(loginView, 980, 680);
        primaryStage.setScene(scene);
    }

    private void onAuthenticated(Customer customer) {
        this.currentCustomer = customer;
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #f1f5f9;");

        Runnable refreshAll = () -> {
            if (dashboardPane != null) dashboardPane.refresh();
            if (transactionPane != null) transactionPane.refreshAccounts();
            if (statementPane != null) statementPane.refresh();
            if (adminPane != null) adminPane.refresh("");
        };

        dashboardPane = new DashboardPane(bankService, customer, refreshAll);
        transactionPane = new TransactionPane(bankService, customer, refreshAll);
        statementPane = new StatementPane(bankService, statementService, customer);
        calculatorPane = new CalculatorPane();
        chatbotPane = new ChatbotPane(chatbotService);
        if (customer.getRole() == Role.ADMIN) {
            adminPane = new AdminPane(authService, bankService, interestService, customer, refreshAll);
        } else {
            adminPane = null;
        }

        MenuBar menuBar = buildMenuBar(root);
        ToolBar toolBar = buildToolBar(root);
        root.setTop(new VBox(menuBar, toolBar));
        root.setCenter(dashboardPane);

        Scene mainScene = new Scene(root, 1020, 720);
        primaryStage.setScene(mainScene);
    }

    private MenuBar buildMenuBar(BorderPane root) {
        Menu fileMenu = new Menu("File");
        MenuItem exportTxtItem = new MenuItem("Export Statement (TXT)");
        // ImageView graphic on MenuItem as required by section 10
        exportTxtItem.setGraphic(UiHelpers.createBadgeIcon(Color.web("#1d4ed8")));
        exportTxtItem.setOnAction(e -> {
            root.setCenter(statementPane);
            statementPane.exportCurrent(true);
        });

        MenuItem exportCsvItem = new MenuItem("Export Statement (CSV)");
        exportCsvItem.setGraphic(UiHelpers.createBadgeIcon(Color.web("#0f766e")));
        exportCsvItem.setOnAction(e -> {
            root.setCenter(statementPane);
            statementPane.exportCurrent(false);
        });

        MenuItem logoutItem = new MenuItem("Logout");
        logoutItem.setOnAction(e -> showLoginScreen());

        MenuItem exitItem = new MenuItem("Exit");
        exitItem.setOnAction(e -> Platform.exit());

        fileMenu.getItems().addAll(
                exportTxtItem, exportCsvItem, new SeparatorMenuItem(), logoutItem, exitItem);

        Menu helpMenu = new Menu("Help");
        MenuItem aboutItem = new MenuItem("About SRM Trust Bank");
        aboutItem.setGraphic(UiHelpers.createBadgeIcon(Color.web("#475569")));
        aboutItem.setOnAction(e -> UiHelpers.showInfo("About",
                "SRM Trust Bank - CSE 213 Advanced Java Project\n"
                        + "Team: Rasesh Saraf, Shaurya Kumar, Sahil Raj\n"
                        + "JavaFX 21 + Plain JDBC SQLite + Javalin 6"));
        helpMenu.getItems().add(aboutItem);

        return new MenuBar(fileMenu, helpMenu);
    }

    private ToolBar buildToolBar(BorderPane root) {
        Button dashBtn = new Button("Dashboard");
        dashBtn.setOnAction(e -> {
            dashboardPane.refresh();
            root.setCenter(dashboardPane);
        });

        Button txnBtn = new Button("Deposit / Withdraw / Transfer");
        txnBtn.setOnAction(e -> {
            transactionPane.refreshAccounts();
            root.setCenter(transactionPane);
        });

        Button stmtBtn = new Button("Statements");
        stmtBtn.setOnAction(e -> {
            statementPane.refresh();
            root.setCenter(statementPane);
        });

        Button calcBtn = new Button("Calculators");
        calcBtn.setOnAction(e -> root.setCenter(calculatorPane));

        Button chatBtn = new Button("AI Assistant");
        chatBtn.setOnAction(e -> root.setCenter(chatbotPane));

        Button logoutBtn = new Button("Logout");
        logoutBtn.setOnAction(e -> showLoginScreen());

        ToolBar toolBar = new ToolBar(dashBtn, txnBtn, stmtBtn, calcBtn, chatBtn);
        if (currentCustomer.getRole() == Role.ADMIN && adminPane != null) {
            Button adminBtn = new Button("Admin Panel");
            adminBtn.setStyle("-fx-background-color: #0f172a; -fx-text-fill: white;");
            adminBtn.setOnAction(e -> {
                adminPane.refresh("");
                root.setCenter(adminPane);
            });
            toolBar.getItems().add(adminBtn);
        }
        toolBar.getItems().addAll(new Separator(), logoutBtn);
        return toolBar;
    }

    public static void main(String[] args) throws BankException {
        launch(args);
    }
}

