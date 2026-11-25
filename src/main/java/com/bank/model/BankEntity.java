// Author: Shaurya Kumar
// Concept: Multilevel inheritance root, abstract class, static & final constants, protected access
package com.bank.model;

import java.time.LocalDateTime;

/**
 * Abstract root entity for domain objects (multilevel hierarchy: BankEntity -> Account -> SavingsAccount/CurrentAccount).
 */
public abstract class BankEntity {

    // Constant shared across all banking entities
    public static final String BANK_NAME = "Meridian Trust Bank";

    protected LocalDateTime createdAt;

    protected BankEntity() {
        this.createdAt = LocalDateTime.now();
    }

    protected BankEntity(LocalDateTime createdAt) {
        this.createdAt = (createdAt != null) ? createdAt : LocalDateTime.now();
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Returns a short identifier label for logging and display.
     */
    public abstract String getEntitySummary();
}

