// Author: Shaurya Kumar
// Concept: JUnit 5 tests for AuthService (PBKDF2 login, duplicate email/phone, RBAC)
package com.bank;

import com.bank.dao.CustomerDao;
import com.bank.dao.SqliteCustomerDao;
import com.bank.exception.DuplicateEntryException;
import com.bank.exception.InvalidCredentialsException;
import com.bank.exception.UnauthorizedException;
import com.bank.model.Customer;
import com.bank.service.AuthService;
import com.bank.util.DatabaseManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthServiceTest {

    @TempDir
    Path tempDir;

    private AuthService authService;

    @BeforeEach
    void setUp() throws Exception {
        DatabaseManager dbManager = new DatabaseManager(tempDir.resolve("auth-test.db").toString());
        dbManager.initializeDatabase();
        CustomerDao customerDao = new SqliteCustomerDao(dbManager);
        authService = new AuthService(customerDao);
    }

    @Test
    void registerAndLoginSucceed() throws Exception {
        Customer diya = authService.register("Diya Patel", "diya@bank.local",
                "9123456789", "Tirupati, AP", "Diya@1234");
        assertEquals("diya@bank.local", diya.getEmail());

        Customer loggedIn = authService.login("diya@bank.local", "Diya@1234");
        assertEquals(diya.getCustomerId(), loggedIn.getCustomerId());
    }

    @Test
    void seededLoginAndWrongPassword() throws Exception {
        Customer aarav = authService.login("aarav@bank.local", "Aarav@123");
        assertEquals("Aarav Sharma", aarav.getName());

        assertThrows(InvalidCredentialsException.class,
                () -> authService.login("aarav@bank.local", "WrongPass@1"));
    }

    @Test
    void duplicateEmailOrPhoneThrowsDuplicateEntryException() {
        assertThrows(DuplicateEntryException.class, () ->
                authService.register("Fake Aarav", "aarav@bank.local", "9000011111", "City", "Pass@1234"));
        assertThrows(DuplicateEntryException.class, () ->
                authService.register("Fake Phone", "unique@bank.local", "9876543210", "City", "Pass@1234"));
    }

    @Test
    void roleAccessEnforcedForAdminOperations() throws Exception {
        Customer aarav = authService.login("aarav@bank.local", "Aarav@123");
        Customer admin = authService.login("admin@bank.local", "Admin@123");

        assertThrows(UnauthorizedException.class, () -> authService.requireAdmin(aarav));
        authService.requireAdmin(admin);
        assertEquals(4, authService.getAllCustomers(admin).size());
    }

    @Test
    void changePasswordUpdatesHash() throws Exception {
        Customer meera = authService.login("meera@bank.local", "Meera@123");
        String oldHash = meera.getPasswordHash();

        authService.changePassword(meera.getCustomerId(), "Meera@123", "MeeraNew@9");
        Customer updated = authService.login("meera@bank.local", "MeeraNew@9");
        assertNotEquals(oldHash, updated.getPasswordHash());
        assertThrows(InvalidCredentialsException.class,
                () -> authService.login("meera@bank.local", "Meera@123"));
    }
}

