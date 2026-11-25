// Author: Sahil Raj
// Concept: JavaFX Profile & Security pane (update contact profile & PBKDF2 password)
package com.bank.ui;

import com.bank.exception.BankException;
import com.bank.model.Customer;
import com.bank.service.AuthService;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Allows customers and admins to update profile details and change their PBKDF2 password.
 */
public class ProfilePane extends VBox {

    private final AuthService authService;
    private final Customer customer;

    public ProfilePane(AuthService authService, Customer customer) {
        this.authService = authService;
        this.customer = customer;
        setSpacing(16);
        setPadding(new Insets(18));
        setStyle("-fx-background-color: #f4f6f9;");
        buildUi();
    }

    private void buildUi() {
        VBox profileCard = UiHelpers.createCard(18);
        HBox.setHgrow(profileCard, Priority.ALWAYS);
        Label pTitle = new Label("Profile & Contact Details");
        pTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        TextField nameField = new TextField(customer.getName());
        TextField emailField = new TextField(customer.getEmail());
        emailField.setDisable(true);
        TextField phoneField = new TextField(customer.getPhone());
        TextField addrField = new TextField(customer.getAddress());

        Button saveBtn = new Button("Save Profile Changes");
        UiHelpers.styleButton(saveBtn, UiHelpers.ACCENT, "#ffffff");
        saveBtn.setOnAction(e -> {
            try {
                authService.updateProfile(
                        customer.getCustomerId(),
                        nameField.getText(),
                        phoneField.getText(),
                        addrField.getText()
                );
                UiHelpers.showInfo("Profile Updated", "Your contact details have been saved.");
            } catch (BankException ex) {
                UiHelpers.showError("Update Failed", ex.getMessage());
            }
        });

        profileCard.getChildren().addAll(
                pTitle,
                new Label("Full Name"), nameField,
                new Label("Email (Read-only)"), emailField,
                new Label("10-Digit Phone"), phoneField,
                new Label("Address"), addrField,
                saveBtn
        );

        VBox securityCard = UiHelpers.createCard(18);
        HBox.setHgrow(securityCard, Priority.ALWAYS);
        Label sTitle = new Label("Security & Password");
        sTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        Label meta = new Label("Customer ID #" + customer.getCustomerId()
                + "  •  Role: " + customer.getRole() + "  •  PBKDF2WithHmacSHA256");
        meta.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748b;");

        PasswordField oldPw = new PasswordField();
        oldPw.setPromptText("Current Password");
        PasswordField newPw = new PasswordField();
        newPw.setPromptText("New Password (min 8 chars, upper, lower, digit)");

        Button pwBtn = new Button("Update Password");
        UiHelpers.styleButton(pwBtn, UiHelpers.PRIMARY, "#ffffff");
        pwBtn.setOnAction(e -> {
            try {
                authService.changePassword(customer.getCustomerId(), oldPw.getText(), newPw.getText());
                oldPw.clear();
                newPw.clear();
                UiHelpers.showInfo("Password Changed", "Your password has been updated.");
            } catch (BankException ex) {
                UiHelpers.showError("Password Error", ex.getMessage());
            }
        });

        securityCard.getChildren().addAll(
                sTitle, meta,
                new Label("Current Password"), oldPw,
                new Label("New Password"), newPw,
                pwBtn
        );

        HBox row = new HBox(16, profileCard, securityCard);
        getChildren().add(row);
    }
}

