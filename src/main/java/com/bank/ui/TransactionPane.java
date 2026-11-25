// Author: Sahil Raj
// Concept: JavaFX Payments & Transfers with ComboBox, RadioButton, live balance, quick chips, Esc clear
package com.bank.ui;

import com.bank.exception.BankException;
import com.bank.model.Account;
import com.bank.model.Customer;
import com.bank.model.Transaction;
import com.bank.service.BankService;
import com.bank.util.Validator;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles Deposit, Withdrawal, and Fund Transfer operations with live balance preview and receipts.
 */
public class TransactionPane extends VBox {

    private final BankService bankService;
    private final Customer customer;
    private final Runnable onDataChanged;

    private final ComboBox<String> accountBox = new ComboBox<>();
    private final Label liveBalanceLbl = new Label("Available Balance: ₹0.00");
    private final TextField targetField = new TextField();
    private final TextField amountField = new TextField();
    private final TextField descField = new TextField();
    private final Label feedbackLabel = new Label();
    private final Map<String, Account> accountByNo = new HashMap<>();

    public TransactionPane(BankService bankService, Customer customer, Runnable onDataChanged) {
        this.bankService = bankService;
        this.customer = customer;
        this.onDataChanged = onDataChanged;
        setSpacing(16);
        setPadding(new Insets(18));
        setStyle("-fx-background-color: #f4f6f9;");
        buildUi();
        refreshAccounts();
    }

    private void buildUi() {
        VBox card = UiHelpers.createCard(22);
        Label header = new Label("Payments & Atomic Fund Transfers (Press Esc to clear)");
        header.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #0f172a;");

        ToggleGroup opGroup = new ToggleGroup();
        RadioButton depositRb = new RadioButton("Deposit (+)");
        depositRb.setToggleGroup(opGroup);
        depositRb.setSelected(true);
        RadioButton withdrawRb = new RadioButton("Withdraw (-)");
        withdrawRb.setToggleGroup(opGroup);
        RadioButton transferRb = new RadioButton("Atomic Transfer (→)");
        transferRb.setToggleGroup(opGroup);

        HBox opRow = new HBox(18, depositRb, withdrawRb, transferRb);

        accountBox.setMaxWidth(Double.MAX_VALUE);
        accountBox.setOnAction(e -> updateLiveBalance());
        liveBalanceLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #0d9488; -fx-font-size: 13px;");

        targetField.setPromptText("Destination Account # (e.g., SB100003)");
        targetField.setDisable(true);
        amountField.setPromptText("Amount (max 2 decimals)");
        descField.setPromptText("Optional description");

        // Quick recipient buttons
        Button recMeera = makeChip("Meera (SB100003)", () -> targetField.setText("SB100003"));
        Button recKabir = makeChip("Kabir (CA100004)", () -> targetField.setText("CA100004"));
        Button recAarav = makeChip("Aarav (SB100001)", () -> targetField.setText("SB100001"));
        HBox recipientChips = new HBox(8, new Label("Quick Payees:"), recMeera, recKabir, recAarav);
        recipientChips.setDisable(true);

        // Quick amount chips
        Button amt500 = makeChip("+500", () -> addQuickAmount(500));
        Button amt1000 = makeChip("+1,000", () -> addQuickAmount(1000));
        Button amt5000 = makeChip("+5,000", () -> addQuickAmount(5000));
        HBox amtChips = new HBox(8, new Label("Quick Amounts:"), amt500, amt1000, amt5000);

        opGroup.selectedToggleProperty().addListener((obs, oldT, newT) -> {
            boolean isTransfer = transferRb.isSelected();
            targetField.setDisable(!isTransfer);
            recipientChips.setDisable(!isTransfer);
            if (!isTransfer) {
                targetField.clear();
            }
        });

        feedbackLabel.setWrapText(true);

        Button submitBtn = new Button("Execute Transaction");
        UiHelpers.styleButton(submitBtn, UiHelpers.ACCENT, "#ffffff");

        Button clearBtn = new Button("Clear Form");
        UiHelpers.styleButton(clearBtn, "#e2e8f0", "#0f172a");

        Runnable clearForm = () -> {
            targetField.clear();
            amountField.clear();
            descField.clear();
            feedbackLabel.setText("");
        };

        clearBtn.setOnAction(e -> clearForm.run());

        // Esc key clears the transaction form using KeyPressedAdapter
        KeyPressedAdapter escClearHandler = new KeyPressedAdapter() {
            @Override
            protected void onEscapePressed(KeyEvent event) {
                clearForm.run();
            }
        };
        targetField.setOnKeyPressed(escClearHandler);
        amountField.setOnKeyPressed(escClearHandler);
        descField.setOnKeyPressed(escClearHandler);

        submitBtn.setOnAction(e -> {
            feedbackLabel.setText("");
            String selectedAcc = accountBox.getValue();
            if (selectedAcc == null || selectedAcc.isBlank()) {
                UiHelpers.showError("Validation Error", "Please select a source account.");
                return;
            }
            try {
                BigDecimal amount = Validator.parseAndValidateAmount(amountField.getText());
                String desc = descField.getText();
                if (depositRb.isSelected()) {
                    Transaction txn = bankService.deposit(customer, selectedAcc, amount, desc);
                    feedbackLabel.setStyle("-fx-text-fill: #0f766e;");
                    feedbackLabel.setText("Deposited " + UiHelpers.formatMoney(amount) + " into " + selectedAcc);
                    UiHelpers.showInfo("Transaction Receipt",
                            "Reference #" + txn.getTxnId() + "\nAccount: " + selectedAcc
                                    + "\nType: DEPOSIT\nAmount: " + UiHelpers.formatMoney(amount)
                                    + "\nNew Balance: " + UiHelpers.formatMoney(txn.getBalanceAfter()));
                } else if (withdrawRb.isSelected()) {
                    Transaction txn = bankService.withdraw(customer, selectedAcc, amount, desc);
                    feedbackLabel.setStyle("-fx-text-fill: #0f766e;");
                    feedbackLabel.setText("Withdrew " + UiHelpers.formatMoney(amount) + " from " + selectedAcc);
                    UiHelpers.showInfo("Transaction Receipt",
                            "Reference #" + txn.getTxnId() + "\nAccount: " + selectedAcc
                                    + "\nType: WITHDRAWAL\nAmount: " + UiHelpers.formatMoney(amount)
                                    + "\nNew Balance: " + UiHelpers.formatMoney(txn.getBalanceAfter()));
                } else {
                    String toAcc = targetField.getText().trim().toUpperCase();
                    bankService.transfer(customer, selectedAcc, toAcc, amount, desc);
                    feedbackLabel.setStyle("-fx-text-fill: #0f766e;");
                    feedbackLabel.setText("Transferred " + UiHelpers.formatMoney(amount) + " from "
                            + selectedAcc + " to " + toAcc);
                    UiHelpers.showInfo("Atomic Transfer Receipt",
                            "From: " + selectedAcc + "\nTo: " + toAcc
                                    + "\nAmount: " + UiHelpers.formatMoney(amount)
                                    + "\nStatus: COMMITTED (ACID)");
                }
                amountField.clear();
                descField.clear();
                refreshAccounts();
                onDataChanged.run();
            } catch (BankException ex) {
                feedbackLabel.setStyle("-fx-text-fill: #dc2626;");
                feedbackLabel.setText(ex.getMessage());
                UiHelpers.showError("Transaction Failed", ex.getMessage());
            }
        });

        HBox btnRow = new HBox(10, submitBtn, clearBtn);

        card.getChildren().addAll(
                header, opRow,
                new Label("Source Account"), accountBox, liveBalanceLbl,
                new Label("Destination Account (for Transfer)"), targetField, recipientChips,
                new Label("Amount (₹)"), amountField, amtChips,
                new Label("Description"), descField,
                feedbackLabel, btnRow
        );
        getChildren().add(card);
    }

    private Button makeChip(String text, Runnable action) {
        Button b = new Button(text);
        b.setStyle("-fx-background-color: #f1f5f9; -fx-border-color: #cbd5e1; "
                + "-fx-background-radius: 999; -fx-border-radius: 999; -fx-font-size: 11px; -fx-padding: 3 10;");
        b.setOnAction(e -> action.run());
        return b;
    }

    private void addQuickAmount(int delta) {
        BigDecimal curr = BigDecimal.ZERO;
        try {
            if (!amountField.getText().isBlank()) {
                curr = new BigDecimal(amountField.getText().trim());
            }
        } catch (NumberFormatException ignored) {
        }
        amountField.setText(curr.add(BigDecimal.valueOf(delta)).toPlainString());
    }

    private void updateLiveBalance() {
        Account acc = accountByNo.get(accountBox.getValue());
        liveBalanceLbl.setText(acc == null
                ? "Available Balance: ₹0.00"
                : "Available Balance: " + UiHelpers.formatMoney(acc.getBalance()) + " (" + acc.getAccountType() + ")");
    }

    public final void refreshAccounts() {
        try {
            List<Account> accounts = bankService.getAccountsForCustomer(customer.getCustomerId());
            accountByNo.clear();
            for (Account a : accounts) {
                accountByNo.put(a.getAccountNumber(), a);
            }
            List<String> numbers = accounts.stream().map(Account::getAccountNumber).toList();
            String current = accountBox.getValue();
            accountBox.setItems(FXCollections.observableArrayList(numbers));
            if (current != null && numbers.contains(current)) {
                accountBox.setValue(current);
            } else if (!numbers.isEmpty()) {
                accountBox.setValue(numbers.get(0));
            }
            updateLiveBalance();
        } catch (BankException ex) {
            UiHelpers.showError("Load Error", ex.getMessage());
        }
    }
}
