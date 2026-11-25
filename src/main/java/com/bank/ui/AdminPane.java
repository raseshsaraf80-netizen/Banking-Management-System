// Author: Sahil Raj
// Concept: JavaFX Executive Admin Command Center with KPIs, PieChart, BarChart, TreeView, TableView, and Audit Logs
package com.bank.ui;

import com.bank.exception.BankException;
import com.bank.model.Account;
import com.bank.model.AccountStatus;
import com.bank.model.AccountType;
import com.bank.model.Customer;
import com.bank.model.Transaction;
import com.bank.service.AuthService;
import com.bank.service.BankService;
import com.bank.service.InterestService;
import com.bank.util.BankLogger;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Administrator Command Center with bank-wide KPIs, Charts, TreeView account controls,
 * Customer Directory, and Audit Log console.
 */
public class AdminPane extends ScrollPane {

    private final AuthService authService;
    private final BankService bankService;
    private final InterestService interestService;
    private final BankLogger bankLogger;
    private final Customer admin;
    private final Runnable onDataChanged;
    private final Consumer<String> onInspectLedger;

    private final Label kpiLiquidityLbl = new Label("₹0.00");
    private final Label kpiCustomersLbl = new Label("0");
    private final Label kpiAccountsLbl = new Label("0");
    private final Label kpiStatusLbl = new Label("0 Active / 0 Frozen");

    private final PieChart liquidityChart = new PieChart();
    private final BarChart<String, Number> topAccountsChart;
    private final ListView<String> recentFeedList = new ListView<>();

    private final TextField searchField = new TextField();
    private final TreeView<String> treeView = new TreeView<>();

    private final TextField custSearchField = new TextField();
    private final TableView<Customer> customersTable = new TableView<>();
    private final TextArea auditLogArea = new TextArea();

    private List<Customer> cachedCustomers = List.of();

    public AdminPane(AuthService authService, BankService bankService,
                     InterestService interestService, BankLogger bankLogger,
                     Customer admin, Runnable onDataChanged,
                     Consumer<String> onInspectLedger) {
        this.authService = authService;
        this.bankService = bankService;
        this.interestService = interestService;
        this.bankLogger = bankLogger;
        this.admin = admin;
        this.onDataChanged = onDataChanged;
        this.onInspectLedger = onInspectLedger;

        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        this.topAccountsChart = new BarChart<>(xAxis, yAxis);
        this.topAccountsChart.setLegendVisible(false);

        setFitToWidth(true);
        setStyle("-fx-background-color: #f4f6f9; -fx-background: #f4f6f9;");
        buildUi();
        refresh("");
    }

    private void buildUi() {
        VBox root = new VBox(16);
        root.setPadding(new Insets(18));

        // Top 4 Admin KPIs
        VBox kpi1 = UiHelpers.createKpiCard("TOTAL BANK LIQUIDITY", kpiLiquidityLbl, "Savings + Current pool", UiHelpers.AMBER);
        VBox kpi2 = UiHelpers.createKpiCard("REGISTERED CUSTOMERS", kpiCustomersLbl, "Includes admin & customers", UiHelpers.PRIMARY);
        VBox kpi3 = UiHelpers.createKpiCard("TOTAL ACCOUNTS", kpiAccountsLbl, "Across the bank", UiHelpers.ACCENT);
        VBox kpi4 = UiHelpers.createKpiCard("ACCOUNT HEALTH", kpiStatusLbl, "Active / Frozen / Closed", UiHelpers.AMBER);
        HBox.setHgrow(kpi1, Priority.ALWAYS);
        HBox.setHgrow(kpi2, Priority.ALWAYS);
        HBox.setHgrow(kpi3, Priority.ALWAYS);
        HBox.setHgrow(kpi4, Priority.ALWAYS);
        HBox kpiRow = new HBox(12, kpi1, kpi2, kpi3, kpi4);

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().addAll(
                new Tab("Bank Overview & Charts", buildOverviewTab()),
                new Tab("Account Controls (TreeView)", buildAccountControlsTab()),
                new Tab("Customers Directory", buildCustomersTab()),
                new Tab("Audit Logs (bank.log)", buildAuditLogTab())
        );

        root.getChildren().addAll(kpiRow, tabs);
        setContent(root);
    }

    private VBox buildOverviewTab() {
        VBox box = new VBox(14);
        box.setPadding(new Insets(14, 0, 0, 0));

        VBox pieCard = UiHelpers.createCard(14);
        HBox.setHgrow(pieCard, Priority.ALWAYS);
        Label pieTitle = new Label("Liquidity Mix (Savings vs Current)");
        pieTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        liquidityChart.setPrefHeight(220);
        pieCard.getChildren().addAll(pieTitle, liquidityChart);

        VBox barCard = UiHelpers.createCard(14);
        HBox.setHgrow(barCard, Priority.ALWAYS);
        Label barTitle = new Label("Top Account Balances");
        barTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        topAccountsChart.setPrefHeight(220);
        barCard.getChildren().addAll(barTitle, topAccountsChart);

        HBox chartsRow = new HBox(14, pieCard, barCard);

        VBox opsCard = UiHelpers.createCard(14);
        Label opsTitle = new Label("Monthly Interest Engine & Live Bank Feed");
        opsTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        Button interestBtn = new Button("Run Monthly Interest Now (3.5% p.a.)");
        UiHelpers.styleButton(interestBtn, UiHelpers.AMBER, "#ffffff");
        interestBtn.setOnAction(e -> {
            try {
                int credited = interestService.runNow();
                UiHelpers.showInfo("Interest Run Complete",
                        "Credited monthly interest to " + credited + " active savings account(s).");
                refresh(searchField.getText());
                onDataChanged.run();
            } catch (BankException ex) {
                UiHelpers.showError("Interest Error", ex.getMessage());
            }
        });

        recentFeedList.setPrefHeight(160);
        opsCard.getChildren().addAll(opsTitle, interestBtn, new Label("Recent Session Transactions:"), recentFeedList);

        box.getChildren().addAll(chartsRow, opsCard);
        return box;
    }

    private VBox buildAccountControlsTab() {
        VBox box = new VBox(12);
        box.setPadding(new Insets(14, 0, 0, 0));

        VBox card = UiHelpers.createCard(16);
        searchField.setPromptText("Search by account #, customer name, or email");
        HBox.setHgrow(searchField, Priority.ALWAYS);

        Button searchBtn = new Button("Search");
        UiHelpers.styleButton(searchBtn, UiHelpers.PRIMARY, "#ffffff");
        searchBtn.setOnAction(e -> refresh(searchField.getText()));

        Button resetBtn = new Button("Show All");
        UiHelpers.styleButton(resetBtn, "#e2e8f0", "#0f172a");
        resetBtn.setOnAction(e -> {
            searchField.clear();
            refresh("");
        });

        HBox searchRow = new HBox(10, searchField, searchBtn, resetBtn);
        treeView.setPrefHeight(310);

        Button freezeBtn = new Button("Freeze Account");
        UiHelpers.styleButton(freezeBtn, "#fef3c7", "#92400e");
        freezeBtn.setOnAction(e -> changeSelectedStatus(AccountStatus.FROZEN));

        Button activateBtn = new Button("Activate Account");
        UiHelpers.styleButton(activateBtn, "#dcfce7", "#166534");
        activateBtn.setOnAction(e -> changeSelectedStatus(AccountStatus.ACTIVE));

        Button closeBtn = new Button("Close Account");
        UiHelpers.styleButton(closeBtn, "#fee2e2", "#991b1b");
        closeBtn.setOnAction(e -> changeSelectedStatus(AccountStatus.CLOSED));

        Button ledgerBtn = new Button("Inspect Ledger");
        UiHelpers.styleButton(ledgerBtn, UiHelpers.ADMIN_DARK, "#ffffff");
        ledgerBtn.setOnAction(e -> {
            String accNo = selectedAccountNumber();
            if (accNo != null) {
                onInspectLedger.accept(accNo);
            }
        });

        HBox actionRow = new HBox(10, freezeBtn, activateBtn, closeBtn, ledgerBtn);
        card.getChildren().addAll(searchRow, treeView, actionRow);
        box.getChildren().add(card);
        return box;
    }

    @SuppressWarnings("unchecked")
    private VBox buildCustomersTab() {
        VBox box = new VBox(12);
        box.setPadding(new Insets(14, 0, 0, 0));

        VBox card = UiHelpers.createCard(16);
        custSearchField.setPromptText("Filter customers by name, email, or phone...");
        custSearchField.textProperty().addListener((o, a, b) -> filterCustomersTable());

        TableColumn<Customer, String> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(d -> new SimpleStringProperty("#" + d.getValue().getCustomerId()));
        TableColumn<Customer, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getName()));
        TableColumn<Customer, String> emailCol = new TableColumn<>("Email");
        emailCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getEmail()));
        TableColumn<Customer, String> phoneCol = new TableColumn<>("Phone");
        phoneCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getPhone()));
        TableColumn<Customer, String> addrCol = new TableColumn<>("Address");
        addrCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getAddress()));
        TableColumn<Customer, String> roleCol = new TableColumn<>("Role");
        roleCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getRole().name()));

        customersTable.getColumns().addAll(idCol, nameCol, emailCol, phoneCol, addrCol, roleCol);
        customersTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        customersTable.setPrefHeight(340);

        card.getChildren().addAll(custSearchField, customersTable);
        box.getChildren().add(card);
        return box;
    }

    private VBox buildAuditLogTab() {
        VBox box = new VBox(12);
        box.setPadding(new Insets(14, 0, 0, 0));

        VBox card = UiHelpers.createCard(16);
        Button reloadBtn = new Button("Reload logs/bank.log");
        UiHelpers.styleButton(reloadBtn, UiHelpers.ADMIN_DARK, "#ffffff");
        reloadBtn.setOnAction(e -> loadAuditLogs());

        auditLogArea.setEditable(false);
        auditLogArea.setPrefHeight(340);
        auditLogArea.setStyle("-fx-control-inner-background: #0f172a; -fx-text-fill: #e2e8f0; "
                + "-fx-font-family: 'Consolas', monospace; -fx-font-size: 12px;");

        card.getChildren().addAll(reloadBtn, auditLogArea);
        box.getChildren().add(card);
        return box;
    }

    public final void refresh(String keyword) {
        try {
            cachedCustomers = authService.getAllCustomers(admin);
            Map<Long, Customer> customerMap = cachedCustomers.stream()
                    .collect(Collectors.toMap(Customer::getCustomerId, c -> c));

            List<Account> allAccounts = bankService.getAllAccounts(admin);
            List<Account> filteredAccounts = (keyword == null || keyword.isBlank())
                    ? allAccounts
                    : bankService.searchAccounts(admin, keyword);

            // KPIs
            BigDecimal totalLiquidity = BigDecimal.ZERO;
            BigDecimal savingsPool = BigDecimal.ZERO;
            BigDecimal currentPool = BigDecimal.ZERO;
            int active = 0;
            int frozen = 0;
            int closed = 0;

            for (Account acc : allAccounts) {
                totalLiquidity = totalLiquidity.add(acc.getBalance());
                BigDecimal pos = acc.getBalance().max(BigDecimal.ZERO);
                if (acc.getAccountType() == AccountType.SAVINGS) {
                    savingsPool = savingsPool.add(pos);
                } else {
                    currentPool = currentPool.add(pos);
                }
                if (acc.getStatus() == AccountStatus.ACTIVE) active++;
                else if (acc.getStatus() == AccountStatus.FROZEN) frozen++;
                else closed++;
            }

            kpiLiquidityLbl.setText(UiHelpers.formatMoney(totalLiquidity));
            kpiCustomersLbl.setText(String.valueOf(cachedCustomers.size()));
            kpiAccountsLbl.setText(String.valueOf(allAccounts.size()));
            kpiStatusLbl.setText(active + " Active / " + frozen + " Frozen / " + closed + " Closed");

            liquidityChart.setData(FXCollections.observableArrayList(
                    new PieChart.Data("Savings", savingsPool.doubleValue()),
                    new PieChart.Data("Current", currentPool.doubleValue())
            ));

            XYChart.Series<String, Number> series = new XYChart.Series<>();
            allAccounts.stream()
                    .sorted(Comparator.comparing(Account::getBalance).reversed())
                    .limit(5)
                    .forEach(a -> series.getData().add(
                            new XYChart.Data<>(a.getAccountNumber(), a.getBalance().doubleValue())));
            topAccountsChart.getData().setAll(series);

            List<String> feedItems = bankService.getRecentActivity().stream()
                    .map(Transaction::toString)
                    .toList();
            recentFeedList.setItems(FXCollections.observableArrayList(feedItems));

            // TreeView
            Map<Long, List<Account>> grouped = filteredAccounts.stream()
                    .collect(Collectors.groupingBy(Account::getCustomerId));
            TreeItem<String> rootNode = new TreeItem<>("SRM Trust Bank Customers & Accounts");
            rootNode.setExpanded(true);

            for (Map.Entry<Long, List<Account>> entry : grouped.entrySet()) {
                Customer owner = customerMap.get(entry.getKey());
                String label = (owner != null)
                        ? owner.getName() + " <" + owner.getEmail() + "> (ID " + owner.getCustomerId() + ")"
                        : "Customer #" + entry.getKey();
                TreeItem<String> custNode = new TreeItem<>(label);
                custNode.setExpanded(true);
                for (Account acc : entry.getValue()) {
                    custNode.getChildren().add(new TreeItem<>(acc.toString()));
                }
                rootNode.getChildren().add(custNode);
            }
            treeView.setRoot(rootNode);

            filterCustomersTable();
            loadAuditLogs();
        } catch (BankException ex) {
            UiHelpers.showError("Admin Load Error", ex.getMessage());
        }
    }

    private void filterCustomersTable() {
        String q = custSearchField.getText() == null ? "" : custSearchField.getText().trim().toLowerCase();
        List<Customer> filtered = cachedCustomers.stream()
                .filter(c -> q.isEmpty()
                        || c.getName().toLowerCase().contains(q)
                        || c.getEmail().toLowerCase().contains(q)
                        || c.getPhone().contains(q))
                .toList();
        customersTable.setItems(FXCollections.observableArrayList(filtered));
    }

    private void loadAuditLogs() {
        List<String> lines = bankLogger.readRecentLines(50);
        auditLogArea.setText(lines.isEmpty()
                ? "No audit log entries recorded yet."
                : String.join("\n", lines));
    }

    private String selectedAccountNumber() {
        TreeItem<String> selected = treeView.getSelectionModel().getSelectedItem();
        if (selected == null || selected.getValue() == null || !selected.isLeaf()) {
            UiHelpers.showError("Selection Required", "Please select an account leaf node in the tree.");
            return null;
        }
        return selected.getValue().split("\\s+")[0];
    }

    private void changeSelectedStatus(AccountStatus status) {
        String accNo = selectedAccountNumber();
        if (accNo == null) {
            return;
        }
        try {
            bankService.updateAccountStatus(admin, accNo, status);
            UiHelpers.showInfo("Status Updated", "Account " + accNo + " is now " + status + ".");
            refresh(searchField.getText());
            onDataChanged.run();
        } catch (BankException ex) {
            UiHelpers.showError("Update Failed", ex.getMessage());
        }
    }
}
