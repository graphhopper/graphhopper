/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.graphhopper.gtfs.fare;

import com.conveyal.gtfs.model.Fare;
import com.conveyal.gtfs.model.FareRule;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

public class Fares {
    public static Optional<Amount> cheapestFare(Map<String, Fare> fares, Trip trip) {
        return ticketsBruteForce(fares, trip)
                .flatMap(tickets -> tickets.stream()
                        .map(ticket -> {
                            Fare fare = fares.get(ticket.getFare().fare_id);
                            final BigDecimal priceOfOneTicket = BigDecimal.valueOf(fare.fare_attribute.price);
                            return new Amount(priceOfOneTicket, fare.fare_attribute.currency_type);
                        })
                        .collect(Collectors.groupingBy(Amount::getCurrencyType, Collectors.mapping(Amount::getAmount, Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))))
                        .entrySet()
                        .stream()
                        .findFirst() // TODO: Tickets in different currencies for one trip
                        .map(e -> new Amount(e.getValue(), e.getKey())));
    }

    private static Optional<List<Ticket>> ticketsBruteForce(Map<String, Fare> fares, Trip trip) {
        // Recursively enumerate all packages of tickets with which the trip can be done.
        // Take the cheapest.
        TicketPurchaseScoreCalculator ticketPurchaseScoreCalculator = new TicketPurchaseScoreCalculator();
        return allShoppingCarts(fares, trip)
                .max(Comparator.comparingInt(ticketPurchaseScoreCalculator::calculateScore))
                .map(TicketPurchase::getTickets);
    }

    private static Stream<TicketPurchase> allShoppingCarts(Map<String, Fare> fares, Trip trip) {
        // Recursively enumerate all packages of tickets with which the trip can be done.
        List<Trip.Segment> segments = trip.segments;
        List<List<FareAssignment>> result = allFareAssignments(fares, segments);
        return result.stream().map(TicketPurchase::new);
    }

    private static List<List<FareAssignment>> allFareAssignments(Map<String, Fare> fares, List<Trip.Segment> segments) {
        // Recursively enumerate all possible ways of assigning trip segments to fares.
        if (segments.isEmpty()) {
            ArrayList<List<FareAssignment>> emptyList = new ArrayList<>();
            emptyList.add(Collections.emptyList());
            return emptyList;
        } else {
            List<List<FareAssignment>> result = new ArrayList<>();
            Trip.Segment segment = segments.get(0);
            List<List<FareAssignment>> tail = allFareAssignments(fares, segments.subList(1, segments.size()));
            Collection<Fare> possibleFares = Fares.possibleFares(fares, segment);
            for (Fare fare : possibleFares) {
                for (List<FareAssignment> tailFareAssignments : tail) {
                    ArrayList<FareAssignment> fairAssignments = new ArrayList<>(tailFareAssignments);
                    FareAssignment fareAssignment = new FareAssignment(segment);
                    fareAssignment.setFare(fare);
                    fairAssignments.add(0, fareAssignment);
                    result.add(fairAssignments);
                }
            }
            return result;
        }
    }

    static Collection<Fare> possibleFares(Map<String, Fare> fares, Trip.Segment segment) {
        return fares.values().stream().filter(fare -> applies(fare, segment)).collect(toList());
    }

    private static boolean applies(Fare fare, Trip.Segment segment) {
        return fare.fare_rules.isEmpty() || sanitizeFareRules(fare.fare_rules).stream().anyMatch(rule -> rule.appliesTo(segment));
    }

    private static List<SanitizedFareRule> sanitizeFareRules(List<FareRule> gtfsFareRules) {
        // Make proper fare rule objects from the CSV-like FareRule
        ArrayList<SanitizedFareRule> result = new ArrayList<>();
        result.addAll(gtfsFareRules.stream().filter(rule -> rule.route_id != null).map(rule -> new RouteRule(rule.route_id)).collect(toList()));
        result.addAll(gtfsFareRules.stream().filter(rule -> rule.origin_id != null && rule.destination_id != null).map(rule -> new OriginDestinationRule(rule.origin_id, rule.destination_id)).collect(toList()));
        result.add(gtfsFareRules.stream().filter(rule -> rule.contains_id != null).map(rule -> rule.contains_id).collect(Collectors.collectingAndThen(toList(), ZoneRule::new)));
        return result;
    }

}
