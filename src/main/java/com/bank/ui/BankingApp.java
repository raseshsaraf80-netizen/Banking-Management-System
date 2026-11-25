// Author: Sahil Raj
// Concept: JavaFX Application entry point with Left Sidebar, MenuBar, ToolBar, ImageView on MenuItem, and RBAC routing
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
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

/**
 * Main JavaFX desktop application for SRM Trust Bank with distinct Customer & Admin portals.
 */
public class BankingApp extends Application {

    private AuthService authService;
    private BankService bankService;
    private StatementService statementService;
    private InterestService interestService;
    private ChatbotService chatbotService;
    private BankLogger bankLogger;

    private Stage primaryStage;
    private Customer currentCustomer;

    private DashboardPane dashboardPane;
    private TransactionPane transactionPane;
    private StatementPane statementPane;
    private CalculatorPane calculatorPane;
    private ProfilePane profilePane;
    private AdminPane adminPane;
    private ChatbotPane chatbotPane;

    @Override
    public void init() throws Exception {
        DatabaseManager dbManager = new DatabaseManager();
        dbManager.initializeDatabase();

        CustomerDao customerDao = new SqliteCustomerDao(dbManager);
        AccountDao accountDao = new SqliteAccountDao(dbManager);
        TransactionDao transactionDao = new SqliteTransactionDao(dbManager);
        this.bankLogger = new BankLogger();

        this.authService = new AuthService(customerDao);
        this.bankService = new BankService(dbManager, accountDao, transactionDao, bankLogger);
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
        Scene scene = new Scene(loginView, 1080, 720);
        primaryStage.setScene(scene);
    }

    private void onAuthenticated(Customer customer) {
        this.currentCustomer = customer;
        boolean isAdmin = customer.getRole() == Role.ADMIN;

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #f4f6f9;");

        Runnable refreshAll = () -> {
            if (dashboardPane != null) dashboardPane.refresh();
            if (transactionPane != null) transactionPane.refreshAccounts();
            if (statementPane != null) statementPane.refresh();
            if (adminPane != null) adminPane.refresh("");
        };

        statementPane = new StatementPane(bankService, statementService, customer);
        calculatorPane = new CalculatorPane();
        profilePane = new ProfilePane(authService, customer);
        chatbotPane = new ChatbotPane(chatbotService);

        if (isAdmin) {
            dashboardPane = null;
            transactionPane = null;
            adminPane = new AdminPane(authService, bankService, interestService, bankLogger, customer,
                    refreshAll, accNo -> {
                        statementPane.selectAccount(accNo);
                        root.setCenter(statementPane);
                    });
        } else {
            adminPane = null;
            dashboardPane = new DashboardPane(bankService, customer, refreshAll);
            transactionPane = new TransactionPane(bankService, customer, refreshAll);
        }

        MenuBar menuBar = buildMenuBar(root);
        ToolBar toolBar = buildToolBar(root, isAdmin);
        VBox sidebar = buildSidebar(root, customer, isAdmin);

        root.setTop(new VBox(menuBar, toolBar));
        root.setLeft(sidebar);
        root.setCenter(isAdmin ? adminPane : dashboardPane);

        Scene mainScene = new Scene(root, 1180, 760);
        primaryStage.setScene(mainScene);
    }

    private VBox buildSidebar(BorderPane root, Customer customer, boolean isAdmin) {
        VBox sidebar = new VBox(8);
        sidebar.setPrefWidth(235);
        sidebar.setPadding(new Insets(18, 14, 18, 14));
        String bg = isAdmin ? UiHelpers.ADMIN_DARK : UiHelpers.NAVY;
        String accent = isAdmin ? "#f59e0b" : "#14b8a6";
        sidebar.setStyle("-fx-background-color: " + bg + ";");

        Label brand = new Label("SRM Trust Bank");
        brand.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");
        Label badge = new Label(isAdmin ? "ADMIN COMMAND" : "CUSTOMER PORTAL");
        badge.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: " + accent
                + "; -fx-background-color: rgba(255,255,255,0.1); -fx-padding: 3 8; -fx-background-radius: 4;");

        VBox header = new VBox(4, brand, badge);
        header.setPadding(new Insets(0, 0, 14, 0));

        List<Button> navButtons = new ArrayList<>();
        Runnable clearActive = () -> navButtons.forEach(b ->
                b.setStyle("-fx-background-color: transparent; -fx-text-fill: #cbd5e1; "
                        + "-fx-font-weight: bold; -fx-alignment: CENTER_LEFT; -fx-padding: 10 12; -fx-cursor: hand;"));

        java.util.function.BiConsumer<String, Node> addNav = (label, target) -> {
            Button btn = new Button(label);
            btn.setMaxWidth(Double.MAX_VALUE);
            navButtons.add(btn);
            btn.setOnAction(e -> {
                clearActive.run();
                btn.setStyle("-fx-background-color: rgba(255,255,255,0.14); -fx-text-fill: #ffffff; "
                        + "-fx-font-weight: bold; -fx-alignment: CENTER_LEFT; -fx-padding: 10 12; "
                        + "-fx-border-color: transparent transparent transparent " + accent
                        + "; -fx-border-width: 0 0 0 3;");
                if (target == dashboardPane && dashboardPane != null) dashboardPane.refresh();
                if (target == transactionPane && transactionPane != null) transactionPane.refreshAccounts();
                if (target == statementPane) statementPane.refresh();
                if (target == adminPane && adminPane != null) adminPane.refresh("");
                root.setCenter(target);
            });
            sidebar.getChildren().add(btn);
        };

        sidebar.getChildren().add(header);
        if (isAdmin) {
            addNav.accept("Bank Command Center", adminPane);
            addNav.accept("Ledger Inspector", statementPane);
            addNav.accept("Financial Calculators", calculatorPane);
            addNav.accept("Admin Profile", profilePane);
            addNav.accept("KoshAI Assistant", chatbotPane);
        } else {
            addNav.accept("Overview & Accounts", dashboardPane);
            addNav.accept("Payments & Transfers", transactionPane);
            addNav.accept("Statements & Export", statementPane);
            addNav.accept("Financial Calculators", calculatorPane);
            addNav.accept("Profile & Security", profilePane);
            addNav.accept("KoshAI Assistant", chatbotPane);
        }

        clearActive.run();
        if (!navButtons.isEmpty()) {
            navButtons.get(0).fire();
        }

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Label userName = new Label(customer.getName());
        userName.setStyle("-fx-font-weight: bold; -fx-text-fill: #ffffff; -fx-font-size: 12.5px;");
        Label userEmail = new Label(customer.getEmail());
        userEmail.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");

        Button signOutBtn = new Button("Sign Out");
        signOutBtn.setMaxWidth(Double.MAX_VALUE);
        UiHelpers.styleButton(signOutBtn, "rgba(255,255,255,0.12)", "#ffffff");
        signOutBtn.setOnAction(e -> showLoginScreen());

        sidebar.getChildren().addAll(spacer, new VBox(2, userName, userEmail), signOutBtn);
        return sidebar;
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
                        + "JavaFX 21 + Plain JDBC SQLite + Javalin 6 + KoshAI"));
        helpMenu.getItems().add(aboutItem);

        return new MenuBar(fileMenu, helpMenu);
    }

    private ToolBar buildToolBar(BorderPane root, boolean isAdmin) {
        ToolBar toolBar = new ToolBar();
        if (isAdmin) {
            Button adminBtn = new Button("Admin Command");
            adminBtn.setOnAction(e -> {
                adminPane.refresh("");
                root.setCenter(adminPane);
            });
            toolBar.getItems().add(adminBtn);
        } else {
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
            toolBar.getItems().addAll(dashBtn, txnBtn);
        }

        Button stmtBtn = new Button("Statements");
        stmtBtn.setOnAction(e -> {
            statementPane.refresh();
            root.setCenter(statementPane);
        });

        Button calcBtn = new Button("Calculators");
        calcBtn.setOnAction(e -> root.setCenter(calculatorPane));

        Button chatBtn = new Button("KoshAI");
        chatBtn.setOnAction(e -> root.setCenter(chatbotPane));

        Button logoutBtn = new Button("Logout");
        logoutBtn.setOnAction(e -> showLoginScreen());

        toolBar.getItems().addAll(stmtBtn, calcBtn, chatBtn, new Separator(), logoutBtn);
        return toolBar;
    }

    public static void main(String[] args) throws BankException {
        launch(args);
    }
}
