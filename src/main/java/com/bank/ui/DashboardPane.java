// Author: Sahil Raj
// Concept: JavaFX Dashboard with KPI cards, PieChart portfolio mix, TableView, and account actions
package com.bank.ui;

import com.bank.exception.BankException;
import com.bank.generic.GenericUtils;
import com.bank.model.Account;
import com.bank.model.AccountStatus;
import com.bank.model.AccountType;
import com.bank.model.Customer;
import com.bank.service.BankService;
import com.bank.util.Validator;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Customer overview dashboard with KPI cards, PieChart allocation, TableView, and account management.
 */
public class DashboardPane extends ScrollPane {

    private final BankService bankService;
    private final Customer customer;
    private final Runnable onDataChanged;

    private final Label kpiTotalLbl = new Label("₹0.00");
    private final Label kpiCountLbl = new Label("0");
    private final Label kpiInterestLbl = new Label("₹0.00");
    private final TableView<Account> accountTable = new TableView<>();
    private final PieChart allocationChart = new PieChart();

    public DashboardPane(BankService bankService, Customer customer, Runnable onDataChanged) {
        this.bankService = bankService;
        this.customer = customer;
        this.onDataChanged = onDataChanged;
        setFitToWidth(true);
        setStyle("-fx-background-color: #f4f6f9; -fx-background: #f4f6f9;");
        buildUi();
        refresh();
    }

    @SuppressWarnings("unchecked")
    private void buildUi() {
        VBox root = new VBox(16);
        root.setPadding(new Insets(18));

        // Top KPI Row
        VBox kpi1 = UiHelpers.createKpiCard("TOTAL PORTFOLIO BALANCE", kpiTotalLbl, "Combined active accounts", UiHelpers.ACCENT);
        VBox kpi2 = UiHelpers.createKpiCard("ACTIVE ACCOUNTS", kpiCountLbl, "Savings & Current", UiHelpers.PRIMARY);
        VBox kpi3 = UiHelpers.createKpiCard("EST. MONTHLY INTEREST", kpiInterestLbl, "3.5% p.a. on Savings", UiHelpers.ACCENT);
        HBox.setHgrow(kpi1, Priority.ALWAYS);
        HBox.setHgrow(kpi2, Priority.ALWAYS);
        HBox.setHgrow(kpi3, Priority.ALWAYS);
        HBox kpiRow = new HBox(14, kpi1, kpi2, kpi3);

        // Middle Row: Accounts Table + PieChart
        VBox tableCard = UiHelpers.createCard(16);
        HBox.setHgrow(tableCard, Priority.ALWAYS);
        Label accountsHeader = new Label("Your Accounts");
        accountsHeader.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");

        TableColumn<Account, String> noCol = new TableColumn<>("Account #");
        noCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getAccountNumber()));
        TableColumn<Account, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getAccountType().name()));
        TableColumn<Account, String> balCol = new TableColumn<>("Balance");
        balCol.setCellValueFactory(data -> new SimpleStringProperty(UiHelpers.formatMoney(data.getValue().getBalance())));
        TableColumn<Account, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getStatus().name()));
        TableColumn<Account, String> dateCol = new TableColumn<>("Opened On");
        dateCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getOpenedOn().toString()));

        accountTable.getColumns().addAll(noCol, typeCol, balCol, statusCol, dateCol);
        accountTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        accountTable.setPrefHeight(220);

        Button refreshBtn = new Button("Refresh");
        UiHelpers.styleButton(refreshBtn, "#e2e8f0", "#0f172a");
        refreshBtn.setOnAction(e -> refresh());

        Button closeBtn = new Button("Close Selected (Zero Balance)");
        UiHelpers.styleButton(closeBtn, "#fee2e2", "#991b1b");
        closeBtn.setOnAction(e -> {
            Account selected = accountTable.getSelectionModel().getSelectedItem();
            if (selected == null) {
                UiHelpers.showError("Select Account", "Click an account row first.");
                return;
            }
            try {
                bankService.closeAccount(customer, selected.getAccountNumber());
                UiHelpers.showInfo("Account Closed", "Closed account " + selected.getAccountNumber());
                refresh();
                onDataChanged.run();
            } catch (BankException ex) {
                UiHelpers.showError("Cannot Close Account", ex.getMessage());
            }
        });

        HBox tableActions = new HBox(10, refreshBtn, closeBtn);
        tableCard.getChildren().addAll(accountsHeader, accountTable, tableActions);

        VBox chartCard = UiHelpers.createCard(16);
        chartCard.setPrefWidth(320);
        Label chartTitle = new Label("Portfolio Allocation");
        chartTitle.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");
        allocationChart.setLabelsVisible(true);
        allocationChart.setLegendVisible(true);
        allocationChart.setPrefHeight(240);
        chartCard.getChildren().addAll(chartTitle, allocationChart);

        HBox midRow = new HBox(16, tableCard, chartCard);

        // Open New Account Card
        VBox openCard = UiHelpers.createCard(16);
        Label openTitle = new Label("Open New Account");
        openTitle.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");

        ToggleGroup typeGroup = new ToggleGroup();
        RadioButton savingsRb = new RadioButton("Savings (Min ₹1,000 | 3.5% p.a.)");
        savingsRb.setToggleGroup(typeGroup);
        savingsRb.setSelected(true);
        RadioButton currentRb = new RadioButton("Current (₹10,000 Overdraft | 0% interest)");
        currentRb.setToggleGroup(typeGroup);

        TextField initialDepositField = new TextField("1000.00");
        initialDepositField.setPromptText("Opening deposit amount");
        HBox.setHgrow(initialDepositField, Priority.ALWAYS);

        Button openBtn = new Button("Open Account");
        UiHelpers.styleButton(openBtn, UiHelpers.PRIMARY, "#ffffff");
        openBtn.setOnAction(e -> {
            try {
                AccountType type = savingsRb.isSelected() ? AccountType.SAVINGS : AccountType.CURRENT;
                String raw = initialDepositField.getText();
                BigDecimal opening = (raw == null || raw.isBlank())
                        ? BigDecimal.ZERO
                        : Validator.parseAndValidateAmount(raw);
                Account created = bankService.openAccount(customer, type, opening);
                UiHelpers.showInfo("Account Opened",
                        "Created " + created.getAccountType() + " account " + created.getAccountNumber());
                refresh();
                onDataChanged.run();
            } catch (BankException ex) {
                UiHelpers.showError("Cannot Open Account", ex.getMessage());
            }
        });

        HBox radioRow = new HBox(18, savingsRb, currentRb);
        HBox inputRow = new HBox(10, new Label("Opening Deposit (₹):"), initialDepositField, openBtn);
        openCard.getChildren().addAll(openTitle, radioRow, inputRow);

        root.getChildren().addAll(kpiRow, midRow, openCard);
        setContent(root);
    }

    public final void refresh() {
        try {
            List<Account> accounts = bankService.getAccountsForCustomer(customer.getCustomerId());
            accountTable.setItems(FXCollections.observableArrayList(accounts));

            BigDecimal total = GenericUtils.totalBalanceBigDecimal(accounts);
            long activeCount = accounts.stream().filter(a -> a.getStatus() == AccountStatus.ACTIVE).count();

            BigDecimal savingsSum = BigDecimal.ZERO;
            BigDecimal currentSum = BigDecimal.ZERO;
            for (Account acc : accounts) {
                BigDecimal bal = acc.getBalance().max(BigDecimal.ZERO);
                if (acc.getAccountType() == AccountType.SAVINGS) {
                    savingsSum = savingsSum.add(bal);
                } else {
                    currentSum = currentSum.add(bal);
                }
            }
            BigDecimal estMonthlyInt = savingsSum
                    .multiply(new BigDecimal("0.035"))
                    .divide(new BigDecimal("12"), 2, RoundingMode.HALF_UP);

            kpiTotalLbl.setText(UiHelpers.formatMoney(total));
            kpiCountLbl.setText(String.valueOf(activeCount));
            kpiInterestLbl.setText(UiHelpers.formatMoney(estMonthlyInt));

            allocationChart.setData(FXCollections.observableArrayList(
                    new PieChart.Data("Savings", savingsSum.doubleValue()),
                    new PieChart.Data("Current", currentSum.doubleValue())
            ));
        } catch (BankException ex) {
            UiHelpers.showError("Load Error", ex.getMessage());
        }
    }
}
