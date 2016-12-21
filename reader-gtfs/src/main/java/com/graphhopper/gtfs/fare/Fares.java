package com.graphhopper.gtfs.fare;

import com.conveyal.gtfs.model.Fare;
import com.conveyal.gtfs.model.FareRule;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Fares {
    public static Amount calculate(Map<String, Fare> fares, Trip trip) {
        return tickets(fares, trip)
                .map(ticket -> {
                    Fare fare = fares.get(ticket.getFareId());
                    final BigDecimal priceOfOneTicket = BigDecimal.valueOf(fare.fare_attribute.price);
                    return new Amount(priceOfOneTicket, fare.fare_attribute.currency_type);
                })
                .collect(Collectors.groupingBy(Amount::getCurrencyType, Collectors.mapping(Amount::getAmount, Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))))
                .entrySet()
                .stream()
                .collect(Collectors.toMap(e -> e.getKey(), e -> new Amount(e.getValue(), e.getKey())))
                .get("USD");
    }

    private static Stream<Ticket> tickets(Map<String, Fare> fares, Trip trip) {
        return trip.segments.stream()
                .collect(Collectors.groupingBy(segment -> calculate(fares, segment).stream().min(Comparator.comparingDouble(fare -> fare.fare_attribute.price)).get()))
                .entrySet().stream()
                .flatMap(e -> {
                    Fare fare = e.getKey();
                    List<Trip.Segment> segmentsWithThisFare = e.getValue();
                    final int numberOfAllowedSegments;
                    if (fare.fare_attribute.transfers == Integer.MAX_VALUE) {
                        numberOfAllowedSegments = Integer.MAX_VALUE;
                    } else {
                        numberOfAllowedSegments = fare.fare_attribute.transfers + 1;
                    }

                    final int numberOfTicketsWeNeedForTransfers = (int) Math.ceil(Double.valueOf(segmentsWithThisFare.size()) / Double.valueOf(numberOfAllowedSegments));
                    final int numberOfTicketsWeNeedForDuration = (int) Math.ceil(Double.valueOf(trip.duration()) / Double.valueOf(fare.fare_attribute.transfer_duration));
                    final int numberOfTicketsWeNeed = Math.max(numberOfTicketsWeNeedForTransfers, numberOfTicketsWeNeedForDuration);
                    Stream.Builder<Ticket> tickets = Stream.builder();
                    for (int i=0;i<numberOfTicketsWeNeed; i++) {
                        tickets.accept(new Ticket(fare.fare_id));
                    }
                    return tickets.build();
                });
    }

    public static Collection<Ticket> calculateTickets(Map<String, Fare> fares, Trip trip) {
        return tickets(fares, trip).collect(Collectors.toList());
    }

    public static Collection<Fare> calculate(Map<String, Fare> fares, Trip.Segment segment) {
        return fares.values().stream().filter(fare -> applies(fare, segment)).collect(Collectors.toList());
    }

    private static boolean applies(Fare fare, Trip.Segment segment) {
        return fare.fare_rules.isEmpty() || fare.fare_rules.stream().anyMatch(rule -> applies(rule, segment));
    }

    private static boolean applies(FareRule rule, Trip.Segment segment) {
        return rule.route_id == null || rule.route_id.equals(segment.getRoute());
    }

}
