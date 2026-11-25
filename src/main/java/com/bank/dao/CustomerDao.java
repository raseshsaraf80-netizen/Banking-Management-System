// Author: Sahil Raj
// Concept: DAO interface extending generic Repository<Customer, Long>
package com.bank.dao;

import com.bank.exception.BankException;
import com.bank.generic.Repository;
import com.bank.model.Customer;

import java.util.Optional;

/**
 * Data access contract for {@link Customer} persistence.
 */
public interface CustomerDao extends Repository<Customer, Long> {

    Optional<Customer> findByEmail(String email) throws BankException;

    boolean existsByEmailOrPhone(String email, String phone) throws BankException;

    void updatePassword(long customerId, String newPasswordHash) throws BankException;

    void updateProfile(Customer customer) throws BankException;
}

