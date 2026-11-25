// Author: Sahil Raj
// Concept: JavaFX Admin Panel with TreeView, search, Freeze/Close actions, and Interest trigger
package com.bank.ui;

import com.bank.exception.BankException;
import com.bank.model.Account;
import com.bank.model.AccountStatus;
import com.bank.model.Customer;
import com.bank.service.AuthService;
import com.bank.service.BankService;
import com.bank.service.InterestService;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Administrator panel displaying all customers and accounts in a TreeView,
 * with account search, freeze/activate/close controls, and manual interest execution.
 */
public class AdminPane extends VBox {

    private final AuthService authService;
    private final BankService bankService;
    private final InterestService interestService;
    private final Customer admin;
    private final Runnable onDataChanged;

    private final TextField searchField = new TextField();
    private final TreeView<String> treeView = new TreeView<>();

    public AdminPane(AuthService authService, BankService bankService,
                     InterestService interestService, Customer admin, Runnable onDataChanged) {
        this.authService = authService;
        this.bankService = bankService;
        this.interestService = interestService;
        this.admin = admin;
        this.onDataChanged = onDataChanged;
        setSpacing(16);
        setPadding(new Insets(16));
        buildUi();
        refresh("");
    }

    private void buildUi() {
        VBox card = UiHelpers.createCard(16);
        Label title = new Label("Admin Control Center");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        searchField.setPromptText("Search by account #, customer name, or email");
        HBox.setHgrow(searchField, Priority.ALWAYS);

        Button searchBtn = new Button("Search");
        searchBtn.setOnAction(e -> refresh(searchField.getText()));
        Button resetBtn = new Button("Show All");
        resetBtn.setOnAction(e -> {
            searchField.clear();
            refresh("");
        });

        HBox searchRow = new HBox(10, searchField, searchBtn, resetBtn);

        treeView.setPrefHeight(290);

        Button freezeBtn = new Button("Freeze Selected");
        freezeBtn.setOnAction(e -> changeSelectedStatus(AccountStatus.FROZEN));

        Button activateBtn = new Button("Activate Selected");
        activateBtn.setOnAction(e -> changeSelectedStatus(AccountStatus.ACTIVE));

        Button closeBtn = new Button("Close Selected");
        closeBtn.setOnAction(e -> changeSelectedStatus(AccountStatus.CLOSED));

        Button interestBtn = new Button("Run Interest Now");
        interestBtn.setStyle("-fx-background-color: #0f766e; -fx-text-fill: white; -fx-font-weight: bold;");
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

        HBox actionRow = new HBox(10, freezeBtn, activateBtn, closeBtn, interestBtn);
        card.getChildren().addAll(title, searchRow, treeView, actionRow);
        getChildren().add(card);
    }

    public final void refresh(String keyword) {
        try {
            List<Customer> customers = authService.getAllCustomers(admin);
            Map<Long, Customer> customerMap = customers.stream()
                    .collect(Collectors.toMap(Customer::getCustomerId, c -> c));

            List<Account> accounts = (keyword == null || keyword.isBlank())
                    ? bankService.getAllAccounts(admin)
                    : bankService.searchAccounts(admin, keyword);

            Map<Long, List<Account>> grouped = accounts.stream()
                    .collect(Collectors.groupingBy(Account::getCustomerId));

            TreeItem<String> root = new TreeItem<>("SRM Trust Bank Customers & Accounts");
            root.setExpanded(true);

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
                root.getChildren().add(custNode);
            }
            treeView.setRoot(root);
        } catch (BankException ex) {
            UiHelpers.showError("Admin Load Error", ex.getMessage());
        }
    }

    private void changeSelectedStatus(AccountStatus status) {
        TreeItem<String> selected = treeView.getSelectionModel().getSelectedItem();
        if (selected == null || selected.getValue() == null || !selected.isLeaf()) {
            UiHelpers.showError("Selection Required", "Please select an account leaf node in the tree.");
            return;
        }
        String text = selected.getValue();
        String accNo = text.split("\\s+")[0];
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

