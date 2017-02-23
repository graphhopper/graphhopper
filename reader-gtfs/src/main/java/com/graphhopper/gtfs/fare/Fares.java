package com.graphhopper.gtfs.fare;

import com.conveyal.gtfs.model.Fare;
import com.conveyal.gtfs.model.FareRule;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

public class Fares {
    public static Optional<Amount> calculate(Map<String, Fare> fares, Trip trip) {
        return ticketsBruteForce(fares, trip)
                .map(tickets -> tickets.stream()
                        .map(ticket -> {
                            Fare fare = fares.get(ticket.getFare().fare_id);
                            final BigDecimal priceOfOneTicket = BigDecimal.valueOf(fare.fare_attribute.price);
                            return new Amount(priceOfOneTicket, fare.fare_attribute.currency_type);
                        })
                        .collect(Collectors.groupingBy(Amount::getCurrencyType, Collectors.mapping(Amount::getAmount, Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))))
                        .entrySet()
                        .stream()
                        .collect(Collectors.toMap(e -> e.getKey(), e -> new Amount(e.getValue(), e.getKey())))
                        .get("USD"));
    }

    private static Optional<List<Ticket>> ticketsBruteForce(Map<String, Fare> fares, Trip trip) {
        TicketPurchaseScoreCalculator ticketPurchaseScoreCalculator = new TicketPurchaseScoreCalculator();
        return allTicketPurchases(fares, trip).max(Comparator.comparingInt(ticketPurchaseScoreCalculator::calculateScore)).map(solution -> solution.getTickets());
    }

    private static Stream<TicketPurchase> allTicketPurchases(Map<String, Fare> fares, Trip trip) {
        List<Trip.Segment> segments = trip.segments;
        List<List<FareAssignment>> result = allTicketPurchases(fares, segments);
        return result.stream().map(fareAssignment -> new TicketPurchase(fareAssignment));
    }

    private static List<List<FareAssignment>> allTicketPurchases(Map<String, Fare> fares, List<Trip.Segment> segments) {
        if (segments.isEmpty()) {
            ArrayList<List<FareAssignment>> emptyList = new ArrayList<>();
            emptyList.add(Collections.emptyList());
            return emptyList;
        } else {
            List<List<FareAssignment>> result = new ArrayList<>();
            Trip.Segment segment = segments.get(0);
            List<List<FareAssignment>> tail = allTicketPurchases(fares, segments.subList(1, segments.size()));
            Collection<Fare> possibleFares = Fares.calculate(fares, segment);
            for (Fare fare : possibleFares) {
                for (List<FareAssignment> fareAssignments : tail) {
                    ArrayList arrayList = new ArrayList(fareAssignments);
                    FareAssignment fareAssignment = new FareAssignment(segment);
                    fareAssignment.setFare(fare);
                    arrayList.add(0, fareAssignment);
                    result.add(arrayList);
                }
            }
            return result;
        }
    }

    public static Collection<Fare> calculate(Map<String, Fare> fares, Trip.Segment segment) {
        return fares.values().stream().filter(fare -> applies(fare, segment)).collect(toList());
    }

    private static boolean applies(Fare fare, Trip.Segment segment) {
        return fare.fare_rules.isEmpty() || sanitizeFareRules(fare.fare_rules).stream().anyMatch(rule -> rule.appliesTo(segment));
    }

    private static List<SanitizedFareRule> sanitizeFareRules(List<FareRule> gtfsFareRules) {
        ArrayList<SanitizedFareRule> result = new ArrayList<>();
        result.addAll(gtfsFareRules.stream().filter(rule -> rule.route_id != null).map(rule -> new RouteRule(rule.route_id)).collect(toList()));
        result.addAll(gtfsFareRules.stream().filter(rule -> rule.origin_id != null && rule.destination_id != null).map(rule -> new OriginDestinationRule(rule.origin_id, rule.destination_id)).collect(toList()));
        result.add(gtfsFareRules.stream().filter(rule -> rule.contains_id != null).map(rule -> rule.contains_id).collect(Collectors.collectingAndThen(toList(), ZoneRule::new)));
        return result;
    }

}
