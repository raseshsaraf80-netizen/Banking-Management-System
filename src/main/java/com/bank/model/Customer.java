// Author: Shaurya Kumar
// Concept: Inheritance (extends BankEntity), encapsulation, constructor overloading
package com.bank.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;

/**
 * Represents a bank customer or administrator profile.
 */
public class Customer extends BankEntity {

    private long customerId;
    private String name;
    private String email;
    private String phone;
    private String address;
    @JsonIgnore
    private String passwordHash;
    private Role role;

    /**
     * Constructor overload 1: new customer registration before database ID assignment.
     */
    public Customer(String name, String email, String phone, String address, String passwordHash) {
        this(0L, name, email, phone, address, passwordHash, Role.CUSTOMER, LocalDateTime.now());
    }

    /**
     * Constructor overload 2: full constructor used when reading from database.
     */
    public Customer(long customerId, String name, String email, String phone, String address,
                    String passwordHash, Role role, LocalDateTime createdAt) {
        super(createdAt);
        this.customerId = customerId;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.address = address;
        this.passwordHash = passwordHash;
        this.role = role != null ? role : Role.CUSTOMER;
    }

    public long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(long customerId) {
        this.customerId = customerId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    @JsonIgnore
    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    @Override
    public String getEntitySummary() {
        return "Customer #" + customerId + ": " + name + " (" + role + ")";
    }

    @Override
    public String toString() {
        return String.format("Customer{id=%d, name='%s', email='%s', role=%s}",
                customerId, name, email, role);
    }
}

