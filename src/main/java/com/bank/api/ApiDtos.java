// Author: Rasesh Saraf
// Concept: Clean DTO records ensuring passwordHash is never serialized in REST JSON responses
package com.bank.api;

import com.bank.ai.ChatTurn;
import com.bank.model.Account;
import com.bank.model.AccountStatus;
import com.bank.model.AccountType;
import com.bank.model.Customer;
import com.bank.model.Role;
import com.bank.model.Transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Request and response DTOs for the Javalin REST API.
 */
public final class ApiDtos {

    private ApiDtos() {
    }

    public record RegisterRequest(String name, String email, String phone, String address, String password) {
    }

    public record LoginRequest(String email, String password) {
    }

    public record ChangePasswordRequest(String oldPassword, String newPassword) {
    }

    public record UpdateProfileRequest(String name, String phone, String address) {
    }

    public record OpenAccountRequest(String accountType, BigDecimal initialDeposit) {
    }

    public record AmountRequest(BigDecimal amount, String description) {
    }

    public record TransferRequest(String fromAccount, String toAccount, BigDecimal amount, String description) {
    }

    public record StatusUpdateRequest(String status) {
    }

    public record ChatRequest(String message, List<ChatTurn> history) {
    }

    public record CustomerDto(long customerId, String name, String email, String phone,
                              String address, Role role, LocalDateTime createdAt) {
        public static CustomerDto from(Customer c) {
            return new CustomerDto(c.getCustomerId(), c.getName(), c.getEmail(),
                    c.getPhone(), c.getAddress(), c.getRole(), c.getCreatedAt());
        }
    }

    public record AuthResponse(String token, CustomerDto customer) {
    }

    public record AccountDto(String accountNumber, long customerId, AccountType accountType,
                             BigDecimal balance, AccountStatus status, LocalDate openedOn) {
        public static AccountDto from(Account a) {
            return new AccountDto(a.getAccountNumber(), a.getCustomerId(), a.getAccountType(),
                    a.getBalance(), a.getStatus(), a.getOpenedOn());
        }
    }

    public record AdminOverviewDto(int totalCustomers, int totalAccounts, int activeAccounts,
                                   int frozenAccounts, int closedAccounts,
                                   BigDecimal totalDeposits, BigDecimal savingsDeposits,
                                   BigDecimal currentDeposits, List<Transaction> recentActivity) {
    }
}
