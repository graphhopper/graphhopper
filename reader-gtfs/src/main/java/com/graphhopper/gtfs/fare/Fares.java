package com.graphhopper.gtfs.fare;

import com.conveyal.gtfs.model.Fare;

import java.math.BigDecimal;
import java.util.Map;

public class Fares {
    public static Amount calculate(Map<String, Fare> fares, Trip trip) {
        Fare fare = fares.values().iterator().next();
        final int numberOfAllowedSegments;
        if (fare.fare_attribute.transfers == Integer.MAX_VALUE) {
            numberOfAllowedSegments = Integer.MAX_VALUE;
        } else {
            numberOfAllowedSegments = fare.fare_attribute.transfers + 1;
        }

        final int numberOfTicketsWeNeedForTransfers = (int) Math.ceil(Double.valueOf(trip.segments.size()) / Double.valueOf(numberOfAllowedSegments));
        final int numberOfTicketsWeNeedForDuration = (int) Math.ceil(Double.valueOf(trip.duration()) / Double.valueOf(fare.fare_attribute.transfer_duration));
        final int numberOfTicketsWeNeed = Math.max(numberOfTicketsWeNeedForTransfers, numberOfTicketsWeNeedForDuration);

        final BigDecimal priceOfOneTicket = BigDecimal.valueOf(fare.fare_attribute.price);

        return new Amount(priceOfOneTicket.multiply(BigDecimal.valueOf(numberOfTicketsWeNeed)), fare.fare_attribute.currency_type);
    }
}
