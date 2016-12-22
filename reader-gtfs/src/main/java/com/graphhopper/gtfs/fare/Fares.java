package com.graphhopper.gtfs.fare;

import com.conveyal.gtfs.model.Fare;
import com.conveyal.gtfs.model.FareRule;
import org.optaplanner.core.api.solver.SolverFactory;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        return fares.values().stream().filter(fare -> applies(fare, segment)).collect(Collectors.toList());
    }

    private static boolean applies(Fare fare, Trip.Segment segment) {
        return fare.fare_rules.isEmpty() || fare.fare_rules.stream().anyMatch(rule -> applies(rule, segment));
    }

    private static boolean applies(FareRule rule, Trip.Segment segment) {
        if (rule.route_id != null && !rule.route_id.equals(segment.getRoute())) {
            return false;
        } else if (rule.origin_id != null && !rule.origin_id.equals(segment.getOriginId())) {
            return false;
        } else if (rule.destination_id != null && !rule.destination_id.equals(segment.getDestinationId())) {
            return false;
        } else {
            return true;
        }
    }

}
