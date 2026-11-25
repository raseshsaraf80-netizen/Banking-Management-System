// Author: Sahil Raj
// Concept: JavaFX split Login & Registration screen with delegation listeners and KeyEvent handling
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
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

/**
 * Split-pane Login and Registration view matching the web portal aesthetic,
 * supporting Enter-to-submit, Esc-to-clear, and one-click demo logins.
 */
public class LoginView extends HBox {

    private final AuthService authService;
    private final Consumer<Customer> onLoginSuccess;

    public LoginView(AuthService authService, Consumer<Customer> onLoginSuccess) {
        this.authService = authService;
        this.onLoginSuccess = onLoginSuccess;
        buildUi();
    }

    private void buildUi() {
        VBox hero = buildHeroPane();
        hero.setPrefWidth(460);
        hero.setMinWidth(380);
        HBox.setHgrow(hero, Priority.SOMETIMES);

        StackPane rightPane = new StackPane();
        rightPane.setPadding(new Insets(32));
        rightPane.setStyle("-fx-background-color: #f4f6f9;");
        HBox.setHgrow(rightPane, Priority.ALWAYS);

        VBox card = UiHelpers.createCard(26);
        card.setMaxWidth(430);
        card.setMaxHeight(Region.USE_PREF_SIZE);

        Label heading = new Label("Welcome back");
        heading.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #0f172a;");
        Label sub = new Label("Sign in to access your accounts or the Admin Command Center.");
        sub.setWrapText(true);
        sub.setStyle("-fx-font-size: 12.5px; -fx-text-fill: #64748b;");

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        TextField loginEmail = new TextField();
        PasswordField loginPass = new PasswordField();

        Tab loginTab = new Tab("Sign In", buildLoginForm(loginEmail, loginPass));
        Tab registerTab = new Tab("Open Profile", buildRegisterForm());
        tabs.getTabs().addAll(loginTab, registerTab);

        Label demoLbl = new Label("QUICK DEMO ACCESS");
        demoLbl.setStyle("-fx-font-size: 10.5px; -fx-font-weight: bold; -fx-text-fill: #64748b;");

        Button aaravBtn = new Button("Customer: Aarav");
        UiHelpers.styleButton(aaravBtn, "#e2e8f0", "#0f172a");
        aaravBtn.setOnAction(e -> {
            tabs.getSelectionModel().select(loginTab);
            loginEmail.setText("aarav@bank.local");
            loginPass.setText("Aarav@123");
        });

        Button meeraBtn = new Button("Customer: Meera");
        UiHelpers.styleButton(meeraBtn, "#e2e8f0", "#0f172a");
        meeraBtn.setOnAction(e -> {
            tabs.getSelectionModel().select(loginTab);
            loginEmail.setText("meera@bank.local");
            loginPass.setText("Meera@123");
        });

        Button adminBtn = new Button("Admin Portal");
        UiHelpers.styleButton(adminBtn, "#fef3c7", "#92400e");
        adminBtn.setOnAction(e -> {
            tabs.getSelectionModel().select(loginTab);
            loginEmail.setText("admin@bank.local");
            loginPass.setText("Admin@123");
        });

        HBox demoRow = new HBox(8, aaravBtn, meeraBtn, adminBtn);
        card.getChildren().addAll(heading, sub, tabs, demoLbl, demoRow);

        rightPane.getChildren().add(card);
        StackPane.setAlignment(card, Pos.CENTER);

        getChildren().addAll(hero, rightPane);
    }

    private VBox buildHeroPane() {
        VBox hero = new VBox(20);
        hero.setPadding(new Insets(44));
        hero.setStyle("-fx-background-color: linear-gradient(to bottom right, #08182e, #0b2545, #102f57);");

        Label brand = new Label("SRM Trust Bank");
        brand.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");
        Label brandSub = new Label("Personal & Corporate Banking • CSE 213");
        brandSub.setStyle("-fx-font-size: 12px; -fx-text-fill: #94a3b8;");

        Region spacer1 = new Region();
        VBox.setVgrow(spacer1, Priority.ALWAYS);

        Label eyebrow = new Label("DESKTOP BANKING PLATFORM");
        eyebrow.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #5eead4;");
        Label headline = new Label("Banking built for\nclarity and control.");
        headline.setStyle("-fx-font-size: 30px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");
        Label copy = new Label("Manage savings & current accounts, execute atomic transfers, export statements, and run financial projections.");
        copy.setWrapText(true);
        copy.setStyle("-fx-font-size: 13.5px; -fx-text-fill: #cbd5e1;");

        VBox bullets = new VBox(8,
                makeBullet("✓  Real-time balances & atomic SQLite transfers"),
                makeBullet("✓  3.5% p.a. Savings yield & ₹10,000 Current overdraft"),
                makeBullet("✓  TXT/CSV statement export & KoshAI assistant")
        );

        Region spacer2 = new Region();
        VBox.setVgrow(spacer2, Priority.ALWAYS);

        Label stats = new Label("3.5% p.a. Savings   •   ₹10,000 Overdraft   •   PBKDF2 256-bit Auth");
        stats.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #94a3b8; "
                + "-fx-border-color: rgba(255,255,255,0.15) transparent transparent transparent; -fx-padding: 14 0 0 0;");

        hero.getChildren().addAll(new VBox(2, brand, brandSub), spacer1, eyebrow, headline, copy, bullets, spacer2, stats);
        return hero;
    }

    private Label makeBullet(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-size: 13px; -fx-text-fill: #e2e8f0;");
        return l;
    }

    private VBox buildLoginForm(TextField emailField, PasswordField passwordField) {
        VBox box = new VBox(10);
        box.setPadding(new Insets(16, 4, 8, 4));

        emailField.setPromptText("Email (e.g., aarav@bank.local)");
        passwordField.setPromptText("Password");

        Label statusLabel = new Label();
        statusLabel.setWrapText(true);
        statusLabel.setStyle("-fx-text-fill: #dc2626; -fx-font-size: 12px;");

        Button loginBtn = new Button("Sign In to Portal");
        loginBtn.setMaxWidth(Double.MAX_VALUE);
        UiHelpers.styleButton(loginBtn, UiHelpers.PRIMARY, "#ffffff");

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
                new Label("Email Address"), emailField,
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

        Button registerBtn = new Button("Create Banking Profile");
        registerBtn.setMaxWidth(Double.MAX_VALUE);
        UiHelpers.styleButton(registerBtn, UiHelpers.ACCENT, "#ffffff");

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
                new Label("10-Digit Phone"), phoneField,
                new Label("Address"), addressField,
                new Label("Password"), passField,
                errorLabel, registerBtn
        );
        return box;
    }
}
