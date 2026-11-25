// Author: Sahil Raj
// Concept: JavaFX Login & Registration screen with delegation listeners and KeyEvent handling
package com.bank.ui;

import com.bank.exception.BankException;
import com.bank.model.Customer;
import com.bank.service.AuthService;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

/**
 * Login and Registration view supporting Enter-to-submit and Esc-to-clear keyboard events.
 */
public class LoginView extends StackPane {

    private final AuthService authService;
    private final Consumer<Customer> onLoginSuccess;

    public LoginView(AuthService authService, Consumer<Customer> onLoginSuccess) {
        this.authService = authService;
        this.onLoginSuccess = onLoginSuccess;
        setPadding(new Insets(32));
        setStyle("-fx-background-color: #f1f5f9;");
        buildUi();
    }

    private void buildUi() {
        VBox card = UiHelpers.createCard(24);
        card.setMaxWidth(440);
        card.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("SRM Trust Bank");
        title.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #0f172a;");
        Label subtitle = new Label("CSE 213 Advanced Java Banking System");
        subtitle.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748b;");

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Tab loginTab = new Tab("Sign In", buildLoginForm());
        Tab registerTab = new Tab("Register", buildRegisterForm());
        tabs.getTabs().addAll(loginTab, registerTab);

        Label demoHint = new Label("Demo logins: aarav@bank.local / Aarav@123  |  admin@bank.local / Admin@123");
        demoHint.setWrapText(true);
        demoHint.setStyle("-fx-font-size: 11px; -fx-text-fill: #475569;");

        card.getChildren().addAll(title, subtitle, tabs, demoHint);
        getChildren().add(card);
        StackPane.setAlignment(card, Pos.CENTER);
    }

    private VBox buildLoginForm() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(16, 4, 8, 4));

        TextField emailField = new TextField();
        emailField.setPromptText("Email (e.g., aarav@bank.local)");

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Password");

        Label statusLabel = new Label();
        statusLabel.setStyle("-fx-text-fill: #dc2626; -fx-font-size: 12px;");

        Button loginBtn = new Button("Login");
        loginBtn.setMaxWidth(Double.MAX_VALUE);
        loginBtn.setStyle("-fx-background-color: #1d4ed8; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10;");

        Runnable doLogin = () -> {
            statusLabel.setText("");
            try {
                Customer customer = authService.login(emailField.getText(), passwordField.getText());
                onLoginSuccess.accept(customer);
            } catch (BankException ex) {
                statusLabel.setText(ex.getMessage());
                UiHelpers.showError("Login Failed", ex.getMessage());
            }
        };

        // Explicit ActionEvent delegation listener
        loginBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                doLogin.run();
            }
        });

        // Adapter-style KeyEvent handler: Enter submits login, Esc clears fields
        KeyPressedAdapter keyHandler = new KeyPressedAdapter() {
            @Override
            protected void onEnterPressed(KeyEvent event) {
                doLogin.run();
            }

            @Override
            protected void onEscapePressed(KeyEvent event) {
                emailField.clear();
                passwordField.clear();
                statusLabel.setText("");
            }
        };
        emailField.setOnKeyPressed(keyHandler);
        passwordField.setOnKeyPressed(keyHandler);

        box.getChildren().addAll(
                new Label("Email"), emailField,
                new Label("Password"), passwordField,
                statusLabel, loginBtn
        );
        return box;
    }

    private VBox buildRegisterForm() {
        VBox box = new VBox(8);
        box.setPadding(new Insets(14, 4, 8, 4));

        TextField nameField = new TextField();
        nameField.setPromptText("Full Name");
        TextField emailField = new TextField();
        emailField.setPromptText("Email");
        TextField phoneField = new TextField();
        phoneField.setPromptText("10-digit Phone");
        TextField addressField = new TextField();
        addressField.setPromptText("Address");
        PasswordField passField = new PasswordField();
        passField.setPromptText("Min 8 chars, upper, lower, digit");

        Label errorLabel = new Label();
        errorLabel.setWrapText(true);
        errorLabel.setStyle("-fx-text-fill: #dc2626; -fx-font-size: 12px;");

        Button registerBtn = new Button("Create Customer Account");
        registerBtn.setMaxWidth(Double.MAX_VALUE);
        registerBtn.setStyle("-fx-background-color: #0f766e; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10;");

        registerBtn.setOnAction(e -> {
            errorLabel.setText("");
            try {
                Customer created = authService.register(
                        nameField.getText(),
                        emailField.getText(),
                        phoneField.getText(),
                        addressField.getText(),
                        passField.getText()
                );
                UiHelpers.showInfo("Registration Successful",
                        "Welcome, " + created.getName() + "! Signing you in now.");
                onLoginSuccess.accept(created);
            } catch (BankException ex) {
                errorLabel.setText(ex.getMessage());
                UiHelpers.showError("Registration Error", ex.getMessage());
            }
        });

        box.getChildren().addAll(
                new Label("Full Name"), nameField,
                new Label("Email"), emailField,
                new Label("Phone"), phoneField,
                new Label("Address"), addressField,
                new Label("Password"), passField,
                errorLabel, registerBtn
        );
        return box;
    }
}

