// Author: Sahil Raj
// Concept: JavaFX Deposit/Withdraw/Transfer screen with ComboBox, RadioButton, KeyEvent (Esc clear)
package com.bank.ui;

import com.bank.exception.BankException;
import com.bank.model.Account;
import com.bank.model.Customer;
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
import java.util.List;

/**
 * Handles Deposit, Withdrawal, and Fund Transfer operations with clear validation feedback.
 */
public class TransactionPane extends VBox {

    private final BankService bankService;
    private final Customer customer;
    private final Runnable onDataChanged;

    private final ComboBox<String> accountBox = new ComboBox<>();
    private final TextField targetField = new TextField();
    private final TextField amountField = new TextField();
    private final TextField descField = new TextField();
    private final Label feedbackLabel = new Label();

    public TransactionPane(BankService bankService, Customer customer, Runnable onDataChanged) {
        this.bankService = bankService;
        this.customer = customer;
        this.onDataChanged = onDataChanged;
        setSpacing(16);
        setPadding(new Insets(16));
        buildUi();
        refreshAccounts();
    }

    private void buildUi() {
        VBox card = UiHelpers.createCard(20);
        Label header = new Label("Deposit / Withdraw / Transfer (Press Esc to clear form)");
        header.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        ToggleGroup opGroup = new ToggleGroup();
        RadioButton depositRb = new RadioButton("Deposit");
        depositRb.setToggleGroup(opGroup);
        depositRb.setSelected(true);
        RadioButton withdrawRb = new RadioButton("Withdraw");
        withdrawRb.setToggleGroup(opGroup);
        RadioButton transferRb = new RadioButton("Transfer");
        transferRb.setToggleGroup(opGroup);

        HBox opRow = new HBox(16, depositRb, withdrawRb, transferRb);

        accountBox.setMaxWidth(Double.MAX_VALUE);
        targetField.setPromptText("Destination Account # (e.g. SB100003)");
        targetField.setDisable(true);
        amountField.setPromptText("Amount (max 2 decimals)");
        descField.setPromptText("Optional description");

        opGroup.selectedToggleProperty().addListener((obs, oldT, newT) -> {
            boolean isTransfer = transferRb.isSelected();
            targetField.setDisable(!isTransfer);
            if (!isTransfer) {
                targetField.clear();
            }
        });

        feedbackLabel.setWrapText(true);

        Button submitBtn = new Button("Execute Transaction");
        submitBtn.setStyle("-fx-background-color: #0f766e; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10;");

        Button clearBtn = new Button("Clear");

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
                    bankService.deposit(customer, selectedAcc, amount, desc);
                    feedbackLabel.setStyle("-fx-text-fill: #0f766e;");
                    feedbackLabel.setText("Deposited " + amount + " into " + selectedAcc);
                } else if (withdrawRb.isSelected()) {
                    bankService.withdraw(customer, selectedAcc, amount, desc);
                    feedbackLabel.setStyle("-fx-text-fill: #0f766e;");
                    feedbackLabel.setText("Withdrew " + amount + " from " + selectedAcc);
                } else {
                    bankService.transfer(customer, selectedAcc, targetField.getText().trim(), amount, desc);
                    feedbackLabel.setStyle("-fx-text-fill: #0f766e;");
                    feedbackLabel.setText("Transferred " + amount + " from " + selectedAcc
                            + " to " + targetField.getText().trim());
                }
                amountField.clear();
                descField.clear();
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
                new Label("Account"), accountBox,
                new Label("Destination Account (for Transfer)"), targetField,
                new Label("Amount"), amountField,
                new Label("Description"), descField,
                feedbackLabel, btnRow
        );
        getChildren().add(card);
    }

    public final void refreshAccounts() {
        try {
            List<Account> accounts = bankService.getAccountsForCustomer(customer.getCustomerId());
            List<String> numbers = accounts.stream().map(Account::getAccountNumber).toList();
            String current = accountBox.getValue();
            accountBox.setItems(FXCollections.observableArrayList(numbers));
            if (current != null && numbers.contains(current)) {
                accountBox.setValue(current);
            } else if (!numbers.isEmpty()) {
                accountBox.setValue(numbers.get(0));
            }
        } catch (BankException ex) {
            UiHelpers.showError("Load Error", ex.getMessage());
        }
    }
}

