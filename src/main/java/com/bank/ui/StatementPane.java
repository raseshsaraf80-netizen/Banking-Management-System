// Author: Sahil Raj
// Concept: JavaFX Statement viewer with DatePicker, Type/Keyword filters, summary strip, and File I/O Export
package com.bank.ui;

import com.bank.exception.BankException;
import com.bank.model.Account;
import com.bank.model.Customer;
import com.bank.model.Role;
import com.bank.model.Transaction;
import com.bank.model.TransactionType;
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
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.io.File;
import java.math.BigDecimal;
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
    private final ComboBox<String> typeFilterBox = new ComboBox<>();
    private final TextField keywordField = new TextField();
    private final Label summaryLbl = new Label("Entries: 0");
    private final TableView<Transaction> table = new TableView<>();
    private List<Transaction> currentTransactions = List.of();

    public StatementPane(BankService bankService, StatementService statementService, Customer customer) {
        this.bankService = bankService;
        this.statementService = statementService;
        this.customer = customer;
        setSpacing(16);
        setPadding(new Insets(18));
        setStyle("-fx-background-color: #f4f6f9;");
        buildUi();
        refresh();
    }

    @SuppressWarnings("unchecked")
    private void buildUi() {
        VBox card = UiHelpers.createCard(16);
        Label header = new Label(customer.getRole() == Role.ADMIN
                ? "Bank-Wide Ledger Inspector & File Export"
                : "Account Statements & File Export");
        header.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #0f172a;");

        typeFilterBox.setItems(FXCollections.observableArrayList(
                "ALL", "DEPOSIT", "WITHDRAWAL", "TRANSFER_IN", "TRANSFER_OUT", "INTEREST"));
        typeFilterBox.setValue("ALL");
        typeFilterBox.setOnAction(e -> loadTransactions());

        keywordField.setPromptText("Search description...");
        keywordField.textProperty().addListener((o, a, b) -> loadTransactions());
        HBox.setHgrow(keywordField, Priority.ALWAYS);

        Button filterBtn = new Button("Apply");
        UiHelpers.styleButton(filterBtn, UiHelpers.PRIMARY, "#ffffff");
        filterBtn.setOnAction(e -> loadTransactions());

        Button resetBtn = new Button("Reset");
        UiHelpers.styleButton(resetBtn, "#e2e8f0", "#0f172a");
        resetBtn.setOnAction(e -> {
            fromPicker.setValue(null);
            toPicker.setValue(null);
            typeFilterBox.setValue("ALL");
            keywordField.clear();
            loadTransactions();
        });

        Button exportTxtBtn = new Button("Export TXT");
        UiHelpers.styleButton(exportTxtBtn, UiHelpers.NAVY, "#ffffff");
        exportTxtBtn.setOnAction(e -> exportStatement(true));

        Button exportCsvBtn = new Button("Export CSV");
        UiHelpers.styleButton(exportCsvBtn, UiHelpers.ACCENT, "#ffffff");
        exportCsvBtn.setOnAction(e -> exportStatement(false));

        accountBox.setOnAction(e -> loadTransactions());

        HBox row1 = new HBox(10,
                new Label("Account:"), accountBox,
                new Label("From:"), fromPicker,
                new Label("To:"), toPicker,
                new Label("Type:"), typeFilterBox);

        HBox row2 = new HBox(10, keywordField, filterBtn, resetBtn, exportTxtBtn, exportCsvBtn);

        summaryLbl.setStyle("-fx-font-size: 12.5px; -fx-font-weight: bold; -fx-text-fill: #334155; "
                + "-fx-background-color: #f8fafc; -fx-padding: 8 12; -fx-background-radius: 6;");

        TableColumn<Transaction, String> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(d -> new SimpleStringProperty("#" + d.getValue().getTxnId()));
        TableColumn<Transaction, String> tsCol = new TableColumn<>("Timestamp");
        tsCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getTimestamp().toString().replace("T", " ")));
        TableColumn<Transaction, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getType().name()));
        TableColumn<Transaction, String> amtCol = new TableColumn<>("Amount");
        amtCol.setCellValueFactory(d -> new SimpleStringProperty(UiHelpers.formatMoney(d.getValue().getAmount())));
        TableColumn<Transaction, String> balCol = new TableColumn<>("Balance After");
        balCol.setCellValueFactory(d -> new SimpleStringProperty(UiHelpers.formatMoney(d.getValue().getBalanceAfter())));
        TableColumn<Transaction, String> descCol = new TableColumn<>("Description");
        descCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getDescription()));

        table.getColumns().addAll(idCol, tsCol, typeCol, amtCol, balCol, descCol);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPrefHeight(330);

        card.getChildren().addAll(header, row1, row2, summaryLbl, table);
        getChildren().add(card);
    }

    public void selectAccount(String accountNumber) {
        refresh();
        if (accountNumber != null && accountBox.getItems().contains(accountNumber)) {
            accountBox.setValue(accountNumber);
            loadTransactions();
        }
    }

    public final void refresh() {
        try {
            List<Account> accounts = (customer.getRole() == Role.ADMIN)
                    ? bankService.getAllAccounts(customer)
                    : bankService.getAccountsForCustomer(customer.getCustomerId());
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
            summaryLbl.setText("No account selected.");
            return;
        }
        try {
            LocalDate from = fromPicker.getValue();
            LocalDate to = toPicker.getValue();
            List<Transaction> raw = bankService.getStatement(customer, accNo, from, to);

            String typeFilter = typeFilterBox.getValue();
            String kw = keywordField.getText() == null ? "" : keywordField.getText().trim().toLowerCase();

            currentTransactions = raw.stream()
                    .filter(t -> typeFilter == null || "ALL".equals(typeFilter) || t.getType().name().equals(typeFilter))
                    .filter(t -> kw.isEmpty() || (t.getDescription() != null && t.getDescription().toLowerCase().contains(kw)))
                    .toList();

            BigDecimal credits = BigDecimal.ZERO;
            BigDecimal debits = BigDecimal.ZERO;
            for (Transaction t : currentTransactions) {
                if (t.getType() == TransactionType.DEPOSIT
                        || t.getType() == TransactionType.TRANSFER_IN
                        || t.getType() == TransactionType.INTEREST) {
                    credits = credits.add(t.getAmount());
                } else {
                    debits = debits.add(t.getAmount());
                }
            }
            summaryLbl.setText("Account: " + accNo
                    + "   |   Entries: " + currentTransactions.size()
                    + "   |   Credits: " + UiHelpers.formatMoney(credits)
                    + "   |   Debits: " + UiHelpers.formatMoney(debits));

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
