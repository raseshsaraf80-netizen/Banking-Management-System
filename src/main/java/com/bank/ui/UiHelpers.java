// Author: Sahil Raj
// Concept: JavaFX UI utilities (Alerts, DropShadow effect, programmatic ImageView icon for MenuItem)
package com.bank.ui;

import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

/**
 * Shared styling, icon, and alert helpers for JavaFX screens.
 */
public final class UiHelpers {

    private UiHelpers() {
    }

    public static VBox createCard(double padding) {
        VBox card = new VBox(12);
        card.setPadding(new Insets(padding));
        card.setStyle("-fx-background-color: #ffffff; -fx-background-radius: 10; "
                + "-fx-border-color: #e2e8f0; -fx-border-radius: 10;");
        // Concept: JavaFX DropShadow effect on main card
        DropShadow shadow = new DropShadow();
        shadow.setRadius(14);
        shadow.setOffsetY(4);
        shadow.setColor(Color.rgb(15, 23, 42, 0.12));
        card.setEffect(shadow);
        return card;
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

