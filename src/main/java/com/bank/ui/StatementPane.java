// Author: Sahil Raj
// Concept: JavaFX Statement viewer with DatePicker filters, TableView, and File I/O Export
package com.bank.ui;

import com.bank.exception.BankException;
import com.bank.model.Account;
import com.bank.model.Customer;
import com.bank.model.Transaction;
import com.bank.service.BankService;
import com.bank.service.StatementService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.io.File;
import java.time.LocalDate;
import java.util.List;

/**
 * Displays filtered account transactions and exports TXT / CSV statements via StatementService.
 */
public class StatementPane extends VBox {

    private final BankService bankService;
    private final StatementService statementService;
    private final Customer customer;

    private final ComboBox<String> accountBox = new ComboBox<>();
    private final DatePicker fromPicker = new DatePicker();
    private final DatePicker toPicker = new DatePicker();
    private final TableView<Transaction> table = new TableView<>();
    private List<Transaction> currentTransactions = List.of();

    public StatementPane(BankService bankService, StatementService statementService, Customer customer) {
        this.bankService = bankService;
        this.statementService = statementService;
        this.customer = customer;
        setSpacing(16);
        setPadding(new Insets(16));
        buildUi();
        refresh();
    }

    @SuppressWarnings("unchecked")
    private void buildUi() {
        VBox card = UiHelpers.createCard(16);
        Label header = new Label("Account Statements & File Export");
        header.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        Button filterBtn = new Button("Load / Filter");
        filterBtn.setOnAction(e -> loadTransactions());

        Button exportTxtBtn = new Button("Export TXT");
        exportTxtBtn.setStyle("-fx-background-color: #1d4ed8; -fx-text-fill: white;");
        exportTxtBtn.setOnAction(e -> exportStatement(true));

        Button exportCsvBtn = new Button("Export CSV");
        exportCsvBtn.setStyle("-fx-background-color: #0f766e; -fx-text-fill: white;");
        exportCsvBtn.setOnAction(e -> exportStatement(false));

        HBox controls = new HBox(10,
                new Label("Account:"), accountBox,
                new Label("From:"), fromPicker,
                new Label("To:"), toPicker,
                filterBtn, exportTxtBtn, exportCsvBtn);

        TableColumn<Transaction, String> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().getTxnId())));
        TableColumn<Transaction, String> tsCol = new TableColumn<>("Timestamp");
        tsCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getTimestamp().toString()));
        TableColumn<Transaction, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getType().name()));
        TableColumn<Transaction, String> amtCol = new TableColumn<>("Amount");
        amtCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getAmount().toPlainString()));
        TableColumn<Transaction, String> balCol = new TableColumn<>("Balance After");
        balCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getBalanceAfter().toPlainString()));
        TableColumn<Transaction, String> descCol = new TableColumn<>("Description");
        descCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getDescription()));

        table.getColumns().addAll(idCol, tsCol, typeCol, amtCol, balCol, descCol);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPrefHeight(320);

        card.getChildren().addAll(header, controls, table);
        getChildren().add(card);
    }

    public final void refresh() {
        try {
            List<Account> accounts = bankService.getAccountsForCustomer(customer.getCustomerId());
            List<String> numbers = accounts.stream().map(Account::getAccountNumber).toList();
            String selected = accountBox.getValue();
            accountBox.setItems(FXCollections.observableArrayList(numbers));
            if (selected != null && numbers.contains(selected)) {
                accountBox.setValue(selected);
            } else if (!numbers.isEmpty()) {
                accountBox.setValue(numbers.get(0));
            }
            loadTransactions();
        } catch (BankException ex) {
            UiHelpers.showError("Load Error", ex.getMessage());
        }
    }

    private void loadTransactions() {
        String accNo = accountBox.getValue();
        if (accNo == null || accNo.isBlank()) {
            table.setItems(FXCollections.observableArrayList());
            currentTransactions = List.of();
            return;
        }
        try {
            LocalDate from = fromPicker.getValue();
            LocalDate to = toPicker.getValue();
            currentTransactions = bankService.getStatement(customer, accNo, from, to);
            table.setItems(FXCollections.observableArrayList(currentTransactions));
        } catch (BankException ex) {
            UiHelpers.showError("Statement Error", ex.getMessage());
        }
    }

    public void exportCurrent(boolean asTxt) {
        exportStatement(asTxt);
    }

    private void exportStatement(boolean asTxt) {
        String accNo = accountBox.getValue();
        if (accNo == null || accNo.isBlank()) {
            UiHelpers.showError("Export Error", "Select an account first.");
            return;
        }
        try {
            loadTransactions();
            File exported = asTxt
                    ? statementService.exportToTxt(accNo, currentTransactions)
                    : statementService.exportToCsv(accNo, currentTransactions);
            UiHelpers.showInfo("Statement Exported",
                    "Saved statement to:\n" + exported.getAbsolutePath());
        } catch (BankException ex) {
            UiHelpers.showError("Export Error", ex.getMessage());
        }
    }
}

