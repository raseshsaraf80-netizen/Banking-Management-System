// Author: Shaurya Kumar
// Concept: Generic abstract class implementing a generic interface (generic class hierarchy)
package com.bank.generic;

/**
 * Base class for named filters in the generic class hierarchy.
 *
 * @param <T> type of item filtered
 */
public abstract class BaseFilter<T> implements Filterable<T> {

    private final String filterName;

    protected BaseFilter(String filterName) {
        this.filterName = filterName;
    }

    public String getFilterName() {
        return filterName;
    }
}

