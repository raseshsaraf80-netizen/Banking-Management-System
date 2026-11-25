// Author: Shaurya Kumar
// Concept: Concrete subclass of a generic base class (BaseFilter<Account>)
package com.bank.generic;

import com.bank.model.Account;

import java.math.BigDecimal;

/**
 * Filters accounts whose balance is greater than or equal to a given threshold.
 */
public class AccountBalanceFilter extends BaseFilter<Account> {

    private final BigDecimal minBalance;

    public AccountBalanceFilter(BigDecimal minBalance) {
        super("Minimum Balance >= " + minBalance);
        this.minBalance = minBalance;
    }

    @Override
    public boolean matches(Account item) {
        return item != null && item.getBalance().compareTo(minBalance) >= 0;
    }
}

