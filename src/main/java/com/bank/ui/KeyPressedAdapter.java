// Author: Sahil Raj
// Concept: Adapter-style event handler for JavaFX KeyEvent delegation model
package com.bank.ui;

import javafx.event.EventHandler;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

/**
 * Adapter-style abstract listener simplifying keyboard event handling.
 * Subclasses override only the key callbacks they need.
 */
public abstract class KeyPressedAdapter implements EventHandler<KeyEvent> {

    @Override
    public final void handle(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER) {
            onEnterPressed(event);
        } else if (event.getCode() == KeyCode.ESCAPE) {
            onEscapePressed(event);
        }
    }

    protected void onEnterPressed(KeyEvent event) {
        // Default no-op
    }

    protected void onEscapePressed(KeyEvent event) {
        // Default no-op
    }
}

