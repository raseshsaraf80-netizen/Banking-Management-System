// Author: Sahil Raj
// Concept: JavaFX UI utilities (Alerts, DropShadow effect, KPI cards, programmatic ImageView icon)
package com.bank.ui;

import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Shared fintech styling, icon, formatting, and alert helpers for JavaFX screens.
 */
public final class UiHelpers {

    public static final String NAVY = "#0b2545";
    public static final String ADMIN_DARK = "#111827";
    public static final String PRIMARY = "#1d4ed8";
    public static final String ACCENT = "#0d9488";
    public static final String AMBER = "#d97706";
    public static final String BG_CANVAS = "#f4f6f9";

    private UiHelpers() {
    }

    public static VBox createCard(double padding) {
        VBox card = new VBox(12);
        card.setPadding(new Insets(padding));
        card.setStyle("-fx-background-color: #ffffff; -fx-background-radius: 12; "
                + "-fx-border-color: #e2e8f0; -fx-border-radius: 12;");
        // Concept: JavaFX DropShadow effect on main cards
        DropShadow shadow = new DropShadow();
        shadow.setRadius(14);
        shadow.setOffsetY(4);
        shadow.setColor(Color.rgb(15, 23, 42, 0.08));
        card.setEffect(shadow);
        return card;
    }

    public static VBox createKpiCard(String title, Label valueLabel, String footerText, String accentHex) {
        VBox card = new VBox(4);
        card.setPadding(new Insets(14, 16, 14, 16));
        card.setStyle("-fx-background-color: #ffffff; -fx-background-radius: 10; "
                + "-fx-border-color: " + accentHex + " #e2e8f0 #e2e8f0 #e2e8f0; "
                + "-fx-border-width: 3 1 1 1; -fx-border-radius: 10;");

        Label titleLbl = new Label(title);
        titleLbl.setStyle("-fx-font-size: 11.5px; -fx-font-weight: bold; -fx-text-fill: #64748b;");

        valueLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #0f172a;");

        Label footLbl = new Label(footerText);
        footLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");

        card.getChildren().addAll(titleLbl, valueLabel, footLbl);
        return card;
    }

    public static void styleButton(Button btn, String bgHex, String textHex) {
        btn.setStyle("-fx-background-color: " + bgHex + "; -fx-text-fill: " + textHex
                + "; -fx-font-weight: bold; -fx-background-radius: 7; -fx-padding: 8 14; -fx-cursor: hand;");
    }

    public static String formatMoney(BigDecimal amount) {
        BigDecimal safe = amount == null ? BigDecimal.ZERO : amount;
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN"));
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);
        return "₹" + nf.format(safe);
    }

    public static void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Builds a crisp 16x16 JavaFX Image icon for MenuItem graphics without external binary assets.
     */
    public static ImageView createBadgeIcon(Color primaryColor) {
        WritableImage img = new WritableImage(16, 16);
        PixelWriter pw = img.getPixelWriter();
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                boolean border = (x == 1 || x == 14 || y == 2 || y == 13);
                boolean inner = (x >= 3 && x <= 12 && y >= 4 && y <= 11);
                if (border) {
                    pw.setColor(x, y, primaryColor.darker());
                } else if (inner) {
                    pw.setColor(x, y, (y == 6 || y == 9) ? Color.WHITE : primaryColor);
                } else {
                    pw.setColor(x, y, Color.TRANSPARENT);
                }
            }
        }
        Image image = img;
        return new ImageView(image);
    }
}
