// Author: Sahil Raj
// Concept: JavaFX Dashboard with TableView, RadioButton, and GenericUtils total balance
package com.bank.ui;

import com.bank.exception.BankException;
import com.bank.generic.GenericUtils;
import com.bank.model.Account;
import com.bank.model.AccountType;
import com.bank.model.Customer;
import com.bank.service.BankService;
import com.bank.util.Validator;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.util.List;

/**
 * Customer dashboard displaying all owned accounts, total balance, and an account-opening card.
 */
public class DashboardPane extends VBox {

    private final BankService bankService;
    private final Customer customer;
    private final Runnable onDataChanged;

    private final TableView<Account> accountTable = new TableView<>();
    private final Label totalBalanceLabel = new Label("Total Balance: 0.00");

    public DashboardPane(BankService bankService, Customer customer, Runnable onDataChanged) {
        this.bankService = bankService;
        this.customer = customer;
        this.onDataChanged = onDataChanged;
        setSpacing(16);
        setPadding(new Insets(16));
        buildUi();
        refresh();
    }

    @SuppressWarnings("unchecked")
    private void buildUi() {
        VBox summaryCard = UiHelpers.createCard(16);
        Label welcome = new Label("Welcome, " + customer.getName() + " (" + customer.getRole() + ")");
        welcome.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #0f172a;");
        totalBalanceLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #0f766e;");
        summaryCard.getChildren().addAll(welcome, totalBalanceLabel);

        VBox tableCard = UiHelpers.createCard(16);
        Label accountsHeader = new Label("Your Accounts");
        accountsHeader.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");

        TableColumn<Account, String> noCol = new TableColumn<>("Account #");
        noCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getAccountNumber()));
        TableColumn<Account, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getAccountType().name()));
        TableColumn<Account, String> balCol = new TableColumn<>("Balance");
        balCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getBalance().toPlainString()));
        TableColumn<Account, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getStatus().name()));
        TableColumn<Account, String> dateCol = new TableColumn<>("Opened On");
        dateCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getOpenedOn().toString()));

        accountTable.getColumns().addAll(noCol, typeCol, balCol, statusCol, dateCol);
        accountTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        accountTable.setPrefHeight(220);

        Button refreshBtn = new Button("Refresh Accounts");
        refreshBtn.setOnAction(e -> refresh());

        tableCard.getChildren().addAll(accountsHeader, accountTable, refreshBtn);

        VBox openCard = UiHelpers.createCard(16);
        Label openTitle = new Label("Open New Account");
        openTitle.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");

        ToggleGroup typeGroup = new ToggleGroup();
        RadioButton savingsRb = new RadioButton("Savings (Min 1,000 | 3.5% p.a.)");
        savingsRb.setToggleGroup(typeGroup);
        savingsRb.setSelected(true);
        RadioButton currentRb = new RadioButton("Current (10,000 Overdraft | 0% interest)");
        currentRb.setToggleGroup(typeGroup);

        TextField initialDepositField = new TextField("1000.00");
        initialDepositField.setPromptText("Opening deposit amount");
        HBox.setHgrow(initialDepositField, Priority.ALWAYS);

        Button openBtn = new Button("Open Account");
        openBtn.setStyle("-fx-background-color: #1d4ed8; -fx-text-fill: white; -fx-font-weight: bold;");
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

        HBox radioRow = new HBox(16, savingsRb, currentRb);
        HBox inputRow = new HBox(10, new Label("Opening Deposit:"), initialDepositField, openBtn);
        openCard.getChildren().addAll(openTitle, radioRow, inputRow);

        getChildren().addAll(summaryCard, tableCard, openCard);
    }

    public final void refresh() {
        try {
            List<Account> accounts = bankService.getAccountsForCustomer(customer.getCustomerId());
            accountTable.setItems(FXCollections.observableArrayList(accounts));
            BigDecimal total = GenericUtils.totalBalanceBigDecimal(accounts);
            totalBalanceLabel.setText("Total Balance across " + accounts.size()
                    + " account(s): " + total.toPlainString());
        } catch (BankException ex) {
            UiHelpers.showError("Load Error", ex.getMessage());
        }
    }
}

