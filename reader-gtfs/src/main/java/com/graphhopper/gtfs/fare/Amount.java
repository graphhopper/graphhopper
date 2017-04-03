package com.graphhopper.gtfs.fare;

import java.math.BigDecimal;

public class Amount {

    private final BigDecimal amount;
    private final String currencyType;

    public Amount(BigDecimal amount, String gtfs_currency_type) {
        this.amount = amount;
        this.currencyType = gtfs_currency_type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrencyType() {
        return currencyType;
    }

}
