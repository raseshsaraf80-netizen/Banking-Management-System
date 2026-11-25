// Author: Shaurya Kumar
// Concept: Authentication service, HashSet duplicate detection, PBKDF2 verification, RBAC
package com.bank.service;

import com.bank.dao.CustomerDao;
import com.bank.exception.AccountNotFoundException;
import com.bank.exception.BankException;
import com.bank.exception.DuplicateEntryException;
import com.bank.exception.InvalidCredentialsException;
import com.bank.exception.UnauthorizedException;
import com.bank.model.Customer;
import com.bank.model.Role;
import com.bank.util.PasswordUtil;
import com.bank.util.Validator;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Handles customer registration, login, password updates, and role-based access checks.
 */
public class AuthService {

    private final CustomerDao customerDao;

    // Concept: HashSet used for fast O(1) duplicate email and phone detection
    private final Set<String> registeredEmails = new HashSet<>();
    private final Set<String> registeredPhones = new HashSet<>();

    public AuthService(CustomerDao customerDao) throws BankException {
        this.customerDao = customerDao;
        refreshContactCache();
    }

    public synchronized void refreshContactCache() throws BankException {
        registeredEmails.clear();
        registeredPhones.clear();
        for (Customer c : customerDao.findAll()) {
            registeredEmails.add(c.getEmail().toLowerCase());
            registeredPhones.add(c.getPhone());
        }
    }

    public synchronized Customer register(String name, String email, String phone,
                                          String address, String rawPassword) throws BankException {
        Validator.validateName(name);
        Validator.validateEmail(email);
        Validator.validatePhone(phone);
        Validator.validatePassword(rawPassword);

        String normalizedEmail = email.trim().toLowerCase();
        String normalizedPhone = phone.trim();

        if (registeredEmails.contains(normalizedEmail)
                || registeredPhones.contains(normalizedPhone)
                || customerDao.existsByEmailOrPhone(normalizedEmail, normalizedPhone)) {
            throw new DuplicateEntryException("An account with this email or phone already exists.");
        }

        String hash = PasswordUtil.hashPassword(rawPassword);
        Customer customer = new Customer(
                name.trim(),
                normalizedEmail,
                normalizedPhone,
                address == null ? "" : address.trim(),
                hash
        );
        Customer saved = customerDao.save(customer);
        registeredEmails.add(normalizedEmail);
        registeredPhones.add(normalizedPhone);
        return saved;
    }

    public Customer login(String email, String rawPassword) throws BankException {
        if (email == null || email.isBlank() || rawPassword == null || rawPassword.isBlank()) {
            throw new InvalidCredentialsException("Email and password are required.");
        }
        Customer customer = customerDao.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password."));

        if (!PasswordUtil.verifyPassword(rawPassword, customer.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid email or password.");
        }
        return customer;
    }

    public void changePassword(long customerId, String oldPassword, String newPassword)
            throws BankException {
        Customer customer = customerDao.findById(customerId)
                .orElseThrow(() -> new AccountNotFoundException("Customer not found."));
        if (!PasswordUtil.verifyPassword(oldPassword, customer.getPasswordHash())) {
            throw new InvalidCredentialsException("Current password is incorrect.");
        }
        Validator.validatePassword(newPassword);
        String newHash = PasswordUtil.hashPassword(newPassword);
        customerDao.updatePassword(customerId, newHash);
        customer.setPasswordHash(newHash);
    }

    public void requireAdmin(Customer actor) throws UnauthorizedException {
        if (actor == null || actor.getRole() != Role.ADMIN) {
            throw new UnauthorizedException("Administrator privileges are required for this action.");
        }
    }

    public Customer findById(long customerId) throws BankException {
        return customerDao.findById(customerId)
                .orElseThrow(() -> new AccountNotFoundException("Customer not found: " + customerId));
    }

    public List<Customer> getAllCustomers(Customer admin) throws BankException {
        requireAdmin(admin);
        return customerDao.findAll();
    }

    public Customer updateProfile(long customerId, String name, String phone, String address)
            throws BankException {
        Validator.validateName(name);
        Validator.validatePhone(phone);
        Customer customer = findById(customerId);
        customer.setName(name.trim());
        customer.setPhone(phone.trim());
        customer.setAddress(address == null ? "" : address.trim());
        customerDao.updateProfile(customer);
        refreshContactCache();
        return customer;
    }
}

