// Author: Shaurya Kumar
// Concept: Generic methods and bounded wildcard parameters (? extends Account, ? super T)
package com.bank.generic;

import com.bank.model.Account;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class showcasing bounded wildcards and generic methods.
 */
public final class GenericUtils {

    private GenericUtils() {
    }

    /**
     * Upper-bounded wildcard method: accepts List<SavingsAccount>, List<CurrentAccount>, or List<Account>.
     */
    public static double totalBalance(List<? extends Account> accounts) {
        return totalBalanceBigDecimal(accounts).doubleValue();
    }

    /**
     * Exact BigDecimal sum over any list of Account subtypes.
     */
    public static BigDecimal totalBalanceBigDecimal(List<? extends Account> accounts) {
        BigDecimal total = BigDecimal.ZERO;
        if (accounts == null) {
            return total;
        }
        for (Account account : accounts) {
            if (account != null) {
                total = total.add(account.getBalance());
            }
        }
        return total;
    }

    /**
     * Generic method using a lower-bounded wildcard filter.
     */
    public static <T> List<T> filterList(List<T> items, Filterable<? super T> filter) {
        List<T> result = new ArrayList<>();
        if (items == null || filter == null) {
            return result;
        }
        for (T item : items) {
            if (filter.matches(item)) {
                result.add(item);
            }
        }
        return result;
    }
}

