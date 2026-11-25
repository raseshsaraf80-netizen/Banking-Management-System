// Author: Sahil Raj
// Concept: JavaFX Financial Calculators pane with PieChart & Year-by-Year Amortization TableView
package com.bank.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Interactive Loan EMI (with PieChart & Amortization Table), Fixed Deposit, and Savings 3.5% calculators.
 */
public class CalculatorPane extends VBox {

    private record AmortizationRow(String year, String principal, String interest, String balance) {
    }

    public CalculatorPane() {
        setSpacing(12);
        setPadding(new Insets(18));
        setStyle("-fx-background-color: #f4f6f9;");
        buildUi();
    }

    private void buildUi() {
        VBox card = UiHelpers.createCard(16);
        Label header = new Label("Financial Planning Calculators");
        header.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #0f172a;");

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().addAll(
                new Tab("Loan EMI & Amortization", buildEmiTab()),
                new Tab("Fixed Deposit (FD)", buildFdTab()),
                new Tab("Savings Growth (3.5%)", buildSavingsTab())
        );

        card.getChildren().addAll(header, tabs);
        getChildren().add(card);
    }

    @SuppressWarnings("unchecked")
    private VBox buildEmiTab() {
        VBox box = new VBox(12);
        box.setPadding(new Insets(14));

        TextField principalField = new TextField("500000");
        TextField rateField = new TextField("9.0");
        TextField monthsField = new TextField("60");
        Label resultLabel = new Label();
        resultLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #0d9488;");

        PieChart emiPie = new PieChart();
        emiPie.setPrefHeight(190);
        emiPie.setPrefWidth(260);

        TableView<AmortizationRow> schedTable = new TableView<>();
        TableColumn<AmortizationRow, String> yrCol = new TableColumn<>("Year");
        yrCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().year()));
        TableColumn<AmortizationRow, String> prCol = new TableColumn<>("Principal Paid");
        prCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().principal()));
        TableColumn<AmortizationRow, String> inCol = new TableColumn<>("Interest Paid");
        inCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().interest()));
        TableColumn<AmortizationRow, String> blCol = new TableColumn<>("Remaining Balance");
        blCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().balance()));
        schedTable.getColumns().addAll(yrCol, prCol, inCol, blCol);
        schedTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        schedTable.setPrefHeight(190);

        Runnable calcAction = () -> {
            try {
                double p = Double.parseDouble(principalField.getText().trim());
                double annualRate = Double.parseDouble(rateField.getText().trim());
                int n = Integer.parseInt(monthsField.getText().trim());
                if (p <= 0 || annualRate < 0 || n <= 0 || n > 360) {
                    throw new IllegalArgumentException();
                }
                double r = (annualRate / 12.0) / 100.0;
                double emi = (r == 0) ? (p / n) : (p * r * Math.pow(1 + r, n)) / (Math.pow(1 + r, n) - 1);
                double totalPay = emi * n;
                double totalInt = totalPay - p;
                resultLabel.setText(String.format(
                        "Monthly EMI: %s   |   Total Interest: %s   |   Total Payable: %s",
                        UiHelpers.formatMoney(BigDecimal.valueOf(emi)),
                        UiHelpers.formatMoney(BigDecimal.valueOf(totalInt)),
                        UiHelpers.formatMoney(BigDecimal.valueOf(totalPay))));

                emiPie.setData(FXCollections.observableArrayList(
                        new PieChart.Data("Principal", p),
                        new PieChart.Data("Interest", totalInt)
                ));

                List<AmortizationRow> rows = new ArrayList<>();
                double bal = p;
                double yrPrin = 0;
                double yrInt = 0;
                for (int m = 1; m <= n; m++) {
                    double mInt = bal * r;
                    double mPrin = Math.min(bal, emi - mInt);
                    bal = Math.max(0, bal - mPrin);
                    yrPrin += mPrin;
                    yrInt += mInt;
                    if (m % 12 == 0 || m == n) {
                        rows.add(new AmortizationRow(
                                "Year " + ((m + 11) / 12),
                                UiHelpers.formatMoney(BigDecimal.valueOf(yrPrin)),
                                UiHelpers.formatMoney(BigDecimal.valueOf(yrInt)),
                                UiHelpers.formatMoney(BigDecimal.valueOf(bal))
                        ));
                        yrPrin = 0;
                        yrInt = 0;
                    }
                }
                schedTable.setItems(FXCollections.observableArrayList(rows));
            } catch (Exception ex) {
                UiHelpers.showError("Invalid Input", "Enter valid positive numbers (1–360 months).");
            }
        };

        Button calcBtn = new Button("Calculate EMI");
        UiHelpers.styleButton(calcBtn, UiHelpers.PRIMARY, "#ffffff");
        calcBtn.setOnAction(e -> calcAction.run());

        HBox inputs = new HBox(10,
                new VBox(4, new Label("Principal (₹)"), principalField),
                new VBox(4, new Label("Annual Rate (%)"), rateField),
                new VBox(4, new Label("Months"), monthsField),
                calcBtn);
        HBox.setHgrow(inputs.getChildren().get(0), Priority.ALWAYS);

        HBox outputRow = new HBox(14, emiPie, schedTable);
        HBox.setHgrow(schedTable, Priority.ALWAYS);

        box.getChildren().addAll(inputs, resultLabel, outputRow);
        calcAction.run();
        return box;
    }

    private VBox buildFdTab() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(14));

        TextField depositField = new TextField("100000");
        TextField rateField = new TextField("6.8");
        TextField yearsField = new TextField("3");
        Label resultLabel = new Label();
        resultLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #0d9488;");

        Button calcBtn = new Button("Calculate Maturity");
        UiHelpers.styleButton(calcBtn, UiHelpers.ACCENT, "#ffffff");
        calcBtn.setOnAction(e -> {
            try {
                double p = Double.parseDouble(depositField.getText().trim());
                double r = Double.parseDouble(rateField.getText().trim()) / 100.0;
                double t = Double.parseDouble(yearsField.getText().trim());
                if (p <= 0 || r < 0 || t <= 0) {
                    throw new IllegalArgumentException();
                }
                double maturity = p * Math.pow(1 + r / 4.0, 4 * t);
                double earned = maturity - p;
                resultLabel.setText(String.format("Maturity Value: %s   |   Interest Earned: %s",
                        UiHelpers.formatMoney(BigDecimal.valueOf(maturity)),
                        UiHelpers.formatMoney(BigDecimal.valueOf(earned))));
            } catch (Exception ex) {
                UiHelpers.showError("Invalid Input", "Enter valid positive numbers for FD calculation.");
            }
        });

        box.getChildren().addAll(
                new Label("FD Deposit Amount (₹)"), depositField,
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
        resultLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #0d9488;");

        Button calcBtn = new Button("Project Savings Growth");
        UiHelpers.styleButton(calcBtn, UiHelpers.PRIMARY, "#ffffff");
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
                        "Projected Balance: %s   |   Contributed: %s   |   Interest: %s",
                        UiHelpers.formatMoney(BigDecimal.valueOf(projected)),
                        UiHelpers.formatMoney(BigDecimal.valueOf(contributed)),
                        UiHelpers.formatMoney(BigDecimal.valueOf(projected - contributed))));
            } catch (Exception ex) {
                UiHelpers.showError("Invalid Input", "Enter valid numbers for Savings projection.");
            }
        });

        box.getChildren().addAll(
                new Label("Starting Balance (₹)"), balanceField,
                new Label("Monthly Deposit (₹)"), monthlyAddField,
                new Label("Years"), yearsField,
                calcBtn, resultLabel
        );
        return box;
    }
}
