// Author: Rasesh Saraf
// Concept: Immutable data transfer record for chatbot conversation history
package com.bank.ai;

/**
 * Represents a single user or assistant turn sent to the Anthropic Messages API.
 */
public record ChatTurn(String role, String content) {
}

