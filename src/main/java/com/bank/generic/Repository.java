// Author: Shaurya Kumar
// Concept: Generic interface Repository<T, ID> implemented by DAOs
package com.bank.generic;

import com.bank.exception.BankException;

import java.util.List;
import java.util.Optional;

/**
 * Generic CRUD contract shared across all data access objects.
 *
 * @param <T>  the entity type
 * @param <ID> the primary key type
 */
public interface Repository<T, ID> {

    T save(T entity) throws BankException;

    Optional<T> findById(ID id) throws BankException;

    List<T> findAll() throws BankException;

    boolean delete(ID id) throws BankException;
}

