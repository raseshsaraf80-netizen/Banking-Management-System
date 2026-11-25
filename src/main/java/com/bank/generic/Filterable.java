// Author: Shaurya Kumar
// Concept: Generic interface for type-safe filtering
package com.bank.generic;

/**
 * Generic predicate-like interface for filtering domain objects.
 *
 * @param <T> type of item to inspect
 */
public interface Filterable<T> {

    boolean matches(T item);
}

