// Author: Sahil Raj
// Concept: JavaFX AI Chatbot panel using background Thread and Platform.runLater()
package com.bank.ui;

import com.bank.ai.ChatTurn;
import com.bank.ai.ChatbotService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

/**
 * Banking help chatbot panel executing HTTP requests asynchronously off the JavaFX Application Thread.
 */
public class ChatbotPane extends VBox {

    private final ChatbotService chatbotService;
    private final List<ChatTurn> history = new ArrayList<>();
    private final TextArea chatArea = new TextArea();
    private final TextField inputField = new TextField();
    private final Button sendBtn = new Button("Send");

    public ChatbotPane(ChatbotService chatbotService) {
        this.chatbotService = chatbotService;
        setSpacing(12);
        setPadding(new Insets(16));
        buildUi();
    }

    private void buildUi() {
        VBox card = UiHelpers.createCard(16);
        Label header = new Label("SRM Trust Bank AI Assistant");
        header.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        Label note = new Label("Ask about account rules, minimum balance, interest rates, or statement exports.");
        note.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748b;");

        chatArea.setEditable(false);
        chatArea.setWrapText(true);
        chatArea.setPrefHeight(320);
        chatArea.setText("Assistant: Hello! Ask me anything about SRM Trust Bank features or FAQs.\n\n");

        inputField.setPromptText("Ask a banking FAQ...");
        HBox.setHgrow(inputField, Priority.ALWAYS);

        sendBtn.setStyle("-fx-background-color: #1d4ed8; -fx-text-fill: white; -fx-font-weight: bold;");
        sendBtn.setOnAction(e -> sendMessage());
        inputField.setOnAction(e -> sendMessage());

        Button clearBtn = new Button("Clear");
        clearBtn.setOnAction(e -> {
            history.clear();
            chatArea.setText("Assistant: Conversation cleared. How can I help?\n\n");
        });

        HBox inputRow = new HBox(10, inputField, sendBtn, clearBtn);
        card.getChildren().addAll(header, note, chatArea, inputRow);
        getChildren().add(card);
    }

    private void sendMessage() {
        String message = inputField.getText();
        if (message == null || message.isBlank()) {
            return;
        }
        String trimmed = message.trim();
        inputField.clear();
        sendBtn.setDisable(true);
        chatArea.appendText("You: " + trimmed + "\n");

        List<ChatTurn> snapshot = new ArrayList<>(history);
        // Run ChatbotService HTTP call on a background thread, then update UI with Platform.runLater
        Thread worker = new Thread(() -> {
            String reply = chatbotService.ask(trimmed, snapshot);
            Platform.runLater(() -> {
                history.add(new ChatTurn("user", trimmed));
                history.add(new ChatTurn("assistant", reply));
                chatArea.appendText("Assistant: " + reply + "\n\n");
                sendBtn.setDisable(false);
            });
        }, "javafx-chatbot-worker");
        worker.setDaemon(true);
        worker.start();
    }
}

