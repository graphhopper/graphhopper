package com.graphhopper.gtfs.fare;

import com.conveyal.gtfs.model.Fare;
import com.conveyal.gtfs.model.FareRule;
import org.optaplanner.core.api.solver.SolverFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

public class Fares {
    public static Amount calculate(Map<String, Fare> fares, Trip trip) {
        return tickets(fares, trip)
                .map(ticket -> {
                    Fare fare = fares.get(ticket.getFare().fare_id);
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
        SolverFactory<TicketPurchase> sf = SolverFactory.createFromXmlResource("com/graphhopper/gtfs/fare/fareSolverConfig.xml");
        TicketPurchase problem = new TicketPurchase(fares, trip);
        TicketPurchase solution = sf.buildSolver().solve(problem);
        return solution.getTickets().stream();
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
