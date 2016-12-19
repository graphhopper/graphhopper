package com.graphhopper.gtfs.fare;

import java.math.BigDecimal;

public class Amount {


    private final BigDecimal amount;
    private final String gtfsCurrencyType;

    public Amount(BigDecimal amount, String gtfs_currency_type) {
        this.amount = amount;
        this.gtfsCurrencyType = gtfs_currency_type;
    }

    public BigDecimal getAmount() {
        return amount;
    }
}
