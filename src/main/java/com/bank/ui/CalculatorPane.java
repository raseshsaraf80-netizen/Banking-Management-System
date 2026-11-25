// Author: Sahil Raj
// Concept: JavaFX Financial Calculators pane (Loan EMI, Fixed Deposit, Savings 3.5% Planner)
package com.bank.ui;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

/**
 * Provides interactive Loan EMI, Fixed Deposit, and Savings Interest calculators.
 */
public class CalculatorPane extends VBox {

    public CalculatorPane() {
        setSpacing(12);
        setPadding(new Insets(16));
        buildUi();
    }

    private void buildUi() {
        VBox card = UiHelpers.createCard(16);
        Label header = new Label("Financial Planning Calculators");
        header.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #0f172a;");

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().addAll(
                new Tab("Loan EMI", buildEmiTab()),
                new Tab("Fixed Deposit", buildFdTab()),
                new Tab("Savings Growth (3.5%)", buildSavingsTab())
        );

        card.getChildren().addAll(header, tabs);
        getChildren().add(card);
    }

    private VBox buildEmiTab() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(14));

        TextField principalField = new TextField("500000");
        TextField rateField = new TextField("9.0");
        TextField monthsField = new TextField("60");
        Label resultLabel = new Label();
        resultLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #0f766e;");

        Button calcBtn = new Button("Calculate EMI");
        calcBtn.setStyle("-fx-background-color: #1d4ed8; -fx-text-fill: white; -fx-font-weight: bold;");
        calcBtn.setOnAction(e -> {
            try {
                double p = Double.parseDouble(principalField.getText().trim());
                double annualRate = Double.parseDouble(rateField.getText().trim());
                int n = Integer.parseInt(monthsField.getText().trim());
                if (p <= 0 || annualRate < 0 || n <= 0) {
                    throw new IllegalArgumentException("Values must be positive.");
                }
                double r = (annualRate / 12.0) / 100.0;
                double emi = (r == 0) ? (p / n) : (p * r * Math.pow(1 + r, n)) / (Math.pow(1 + r, n) - 1);
                double totalPay = emi * n;
                double totalInt = totalPay - p;
                resultLabel.setText(String.format(
                        "Monthly EMI: %.2f  |  Total Interest: %.2f  |  Total Payable: %.2f",
                        emi, totalInt, totalPay));
            } catch (Exception ex) {
                UiHelpers.showError("Invalid Input", "Enter valid positive numbers for EMI calculation.");
            }
        });

        box.getChildren().addAll(
                new Label("Loan Amount"), principalField,
                new Label("Annual Interest Rate (%)"), rateField,
                new Label("Tenure (Months)"), monthsField,
                calcBtn, resultLabel
        );
        return box;
    }

    private VBox buildFdTab() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(14));

        TextField depositField = new TextField("100000");
        TextField rateField = new TextField("6.8");
        TextField yearsField = new TextField("3");
        Label resultLabel = new Label();
        resultLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #0f766e;");

        Button calcBtn = new Button("Calculate Maturity");
        calcBtn.setStyle("-fx-background-color: #0f766e; -fx-text-fill: white; -fx-font-weight: bold;");
        calcBtn.setOnAction(e -> {
            try {
                double p = Double.parseDouble(depositField.getText().trim());
                double r = Double.parseDouble(rateField.getText().trim()) / 100.0;
                double t = Double.parseDouble(yearsField.getText().trim());
                if (p <= 0 || r < 0 || t <= 0) {
                    throw new IllegalArgumentException();
                }
                // Quarterly compounding
                double maturity = p * Math.pow(1 + r / 4.0, 4 * t);
                double earned = maturity - p;
                resultLabel.setText(String.format(
                        "Maturity Value: %.2f  |  Interest Earned: %.2f", maturity, earned));
            } catch (Exception ex) {
                UiHelpers.showError("Invalid Input", "Enter valid positive numbers for FD calculation.");
            }
        });

        box.getChildren().addAll(
                new Label("FD Deposit Amount"), depositField,
                new Label("Annual FD Rate (%)"), rateField,
                new Label("Tenure (Years)"), yearsField,
                calcBtn, resultLabel
        );
        return box;
    }

    private VBox buildSavingsTab() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(14));

        TextField balanceField = new TextField("25000");
        TextField monthlyAddField = new TextField("2000");
        TextField yearsField = new TextField("5");
        Label resultLabel = new Label();
        resultLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #0f766e;");

        Button calcBtn = new Button("Project Savings Growth");
        calcBtn.setStyle("-fx-background-color: #1d4ed8; -fx-text-fill: white; -fx-font-weight: bold;");
        calcBtn.setOnAction(e -> {
            try {
                double bal = Double.parseDouble(balanceField.getText().trim());
                double monthly = Double.parseDouble(monthlyAddField.getText().trim());
                int years = Integer.parseInt(yearsField.getText().trim());
                if (bal < 0 || monthly < 0 || years <= 0) {
                    throw new IllegalArgumentException();
                }
                double monthlyRate = 0.035 / 12.0;
                double projected = bal;
                for (int m = 0; m < years * 12; m++) {
                    projected = (projected + monthly) * (1 + monthlyRate);
                }
                double contributed = bal + monthly * years * 12;
                resultLabel.setText(String.format(
                        "Projected Balance: %.2f  |  Total Contributed: %.2f  |  Interest Earned: %.2f",
                        projected, contributed, projected - contributed));
            } catch (Exception ex) {
                UiHelpers.showError("Invalid Input", "Enter valid numbers for Savings projection.");
            }
        });

        box.getChildren().addAll(
                new Label("Starting Balance"), balanceField,
                new Label("Monthly Deposit"), monthlyAddField,
                new Label("Years"), yearsField,
                calcBtn, resultLabel
        );
        return box;
    }
}

