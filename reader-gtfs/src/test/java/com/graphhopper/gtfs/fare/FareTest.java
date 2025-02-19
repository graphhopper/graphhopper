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

import com.conveyal.gtfs.GTFSFeed;
import com.conveyal.gtfs.util.CsvReader;
import com.conveyal.gtfs.model.Fare;
import com.conveyal.gtfs.model.FareAttribute;
import com.conveyal.gtfs.model.FareRule;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class FareTest {

    // See https://code.google.com/archive/p/googletransitdatafeed/wikis/FareExamples.wiki
    private static final List<Map<String, Map<String, Fare>>> fares = Arrays.asList(
            // 0: 1$, unlimited transfers
            map(
                    "only_feed_id", parseFares("only_feed_id", "only_fare,1.00,USD,0\n", ""),
                    "feed_id_2", parseFares("feed_id_2", "", "")
            ),
            // 1: 1$, no transfers
            map(
                    "only_feed_id", parseFares("only_feed_id", "only_fare,1.00,USD,0,0\n", ""),
                    "feed_id_2", parseFares("feed_id_2", "", "")
            ),
            // 2: 1$, time limited transfers
            map(
                    "only_feed_id", parseFares("only_feed_id", "only_fare,1.00,USD,0,,5400\n", ""),
                    "feed_id_2", parseFares("feed_id_2", "", "")
            ),
            // 3: regular and express
            map(
                    "only_feed_id", parseFares("only_feed_id", "local_fare,1.75,USD,0,0\n" + "express_fare,5.00,USD,0,0\n", "local_fare,Route_1\nexpress_fare,Route_2\nexpress_fare,Route3\n"),
                    "feed_id_2", parseFares("feed_id_2", "", "")
            ),
            // 4: with transfers or without
            map(
                    "only_feed_id", parseFares("only_feed_id", "simple_fare,2.00,USD,0,0\n" + "plustransfer_fare,2.50,USD,0,,5400", ""),
                    "feed_id_2", parseFares("feed_id_2", "", "")
            ),
            // 5: station pairs
            map(
                    "only_feed_id", parseFares("only_feed_id", "!S1_to_S2,1.75,USD,0\n!S1_to_S3,3.25,USD,0\n!S1_to_S4,4.55,USD,0\n!S4_to_S1,5.65,USD,0\n", "!S1_to_S2,,S1,S2\n!S1_to_S3,,S1,S3\n!S1_to_S4,,S1,S4\n!S4_to_S1,,S4,S1\n"),
                    "feed_id_2", parseFares("feed_id_2", "", "")
            ),
            // 6: zones
            map(
                    "only_feed_id", parseFares("only_feed_id", "F1,4.15,USD,0\nF2,2.20,USD,0\nF3,2.20,USD,0\nF4,2.95,USD,0\nF5,1.25,USD,0\nF6,1.95,USD,0\nF7,1.95,USD,0\n", "F1,,,,1\nF1,,,,2\nF1,,,,3\nF2,,,,1\nF2,,,,2\nF3,,,,1\nF3,,,,3\nF4,,,,2\nF4,,,,3\nF5,,,,1\nF6,,,,2\nF7,,,,3\n"),
                    "feed_id_2", parseFares("feed_id_2", "", "")
            ),
            // 7: two feeds
            map(
                    "only_feed_id", parseFares("only_feed_id", "only_fare,1.00,USD,0,0\n", ""),
                    "feed_id_2", parseFares("feed_id_2", "only_fare,2.00,USD,0,0\n", "")
            )
    );

    private static final List<Trip> trips = new ArrayList<>();

    static {
        Trip trip;
        trips.add(trip = new Trip());
        trip.segments.add(new Trip.Segment("only_feed_id", "Route_1", 0, "S1", "S2", new HashSet<>(Arrays.asList("1", "2", "3"))));

        trips.add(trip = new Trip());
        trip.segments.add(new Trip.Segment("only_feed_id", "Route_1", 0, "S1", "S4", new HashSet<>(Arrays.asList("1"))));
        trip.segments.add(new Trip.Segment("only_feed_id", "Route_2", 6000, "S4", "S1", new HashSet<>(Arrays.asList("1"))));

        trips.add(trip = new Trip());
        trip.segments.add(new Trip.Segment("only_feed_id", "Route_1", 0, "S1", "S4", new HashSet<>(Arrays.asList("2", "3"))));
        trip.segments.add(new Trip.Segment("only_feed_id", "Route_2", 5000, "S4", "S1", new HashSet<>(Arrays.asList("2", "3"))));

        trips.add(trip = new Trip());
        trip.segments.add(new Trip.Segment("only_feed_id", "Route_1", 0, "S1", "S4", new HashSet<>(Arrays.asList("1"))));
        trip.segments.add(new Trip.Segment("only_feed_id", "Route_2", 5000, "S4", "S1", new HashSet<>(Arrays.asList("2"))));
        trip.segments.add(new Trip.Segment("only_feed_id", "Route_1", 6000, "S1", "S4", new HashSet<>(Arrays.asList("3"))));

        trips.add(trip = new Trip());
        trip.segments.add(new Trip.Segment("only_feed_id", "Route_1", 0, "S1", "S4", new HashSet<>()));
        trip.segments.add(new Trip.Segment("feed_id_2", "Route_2", 5000, "T", "T", new HashSet<>()));
    }

    private static class DataPointProvider implements ArgumentsProvider {
        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
            List<Object[]> dataPoints = new ArrayList<>();
            for (int i = 0; i < fares.size(); i++) {
                Map<String, Map<String, Fare>> fare = fares.get(i);
                for (int j = 0; j < trips.size(); j++) {
                    Trip trip = trips.get(j);
                    dataPoints.add(new Object[]{fare, trip, "fare " + i + ", trip: " + j});
                }
            }
            return dataPoints.stream().map(Arguments::of);
        }
    }

    @ParameterizedTest(name = "{2}")
    @ArgumentsSource(DataPointProvider.class)
    public void irrelevantAlternatives(Map<String, Map<String, Fare>> fares, Trip trip, String displayName) {
        assumeTrue(fares.get("only_feed_id").entrySet().size() >= 2, "There are at least two fares.");

        // If we only use one fare, say, the most expensive one...
        Fare mostExpensiveFare = fares.get("only_feed_id").values().stream().max(Comparator.comparingDouble(f -> f.fare_attribute.price)).get();
        Map<String, Map<String, Fare>> singleFare = map(
                "only_feed_id", map(mostExpensiveFare.fare_id, mostExpensiveFare),
                "feed_id_2", parseFares("feed_id_2", "", "")
        );

        // ..and that still works for our trip..
        assumeTrue(
                trip.segments.stream()
                        .map(segment -> Fares.possibleFares(singleFare.get(segment.feed_id), segment))
                        .noneMatch(Collection::isEmpty), "There is at least one fare for each segment");
        double priceWithOneOption = Fares.cheapestFare(singleFare, trip).get().getAmount().doubleValue();

        double priceWithAllOptions = Fares.cheapestFare(fares, trip).get().getAmount().doubleValue();


        assertTrue(priceWithAllOptions <= priceWithOneOption, "...it shouldn't get more expensive when we put the cheaper options back.");
    }

    @ParameterizedTest(name = "{2}")
    @ArgumentsSource(DataPointProvider.class)
    public void everySegmentHasAFare(Map<String, Map<String, Fare>> fares, Trip trip, String displayName) {
        assumeEachFeedHasAFare(fares);
        assertTrue(trip.segments.stream()
                        .map(segment -> Fares.possibleFares(fares.get(segment.feed_id), segment))
                        .noneMatch(Collection::isEmpty),
                "There is at least one fare for each segment.");
    }

    @ParameterizedTest(name = "{2}")
    @ArgumentsSource(DataPointProvider.class)
    public void withNoTransfersAndNoAlternativesBuyOneTicketForEachSegment(Map<String, Map<String, Fare>> fares, Trip trip, String displayName) {
        assumeEachFeedHasAFare(fares);
        fares.values().stream().flatMap(fs -> fs.values().stream()).forEach(fare ->
                assumeTrue(0 == fare.fare_attribute.transfers, "No Transfers allowed."));
        trip.segments.stream()
                .map(segment -> Fares.possibleFares(fares.get(segment.feed_id), segment))
                .forEach(candidateFares -> assertEquals(1, candidateFares.size(), "Only one fare candidate per segment."));
        double totalFare = Fares.cheapestFare(fares, trip).get().getAmount().doubleValue();
        double sumOfIndividualFares = trip.segments.stream().flatMap(segment -> Fares.possibleFares(fares.get(segment.feed_id), segment).stream()).mapToDouble(fare -> fare.fare_attribute.price).sum();
        assertEquals(sumOfIndividualFares, totalFare, "Total fare is the sum of all individual fares.");
    }

    @ParameterizedTest(name = "{2}")
    @ArgumentsSource(DataPointProvider.class)
    public void canGoAllTheWayOnOneTicket(Map<String, Map<String, Fare>> fares, Trip trip, String displayName) {
        assumeTrue(1L == trip.segments.stream().map(s -> s.feed_id).distinct().count());
        Optional<Fare> obviouslyCheapestFare = fares.values().stream().flatMap(fs -> fs.values().stream())
                .filter(fare -> fare.fare_rules.isEmpty()) // Fare has no restrictions except transfer count/duration
                .filter(fare -> fare.fare_attribute.transfers >= trip.segments.size() - 1) // Fare allows the number of transfers we need for our trip
                .filter(fare -> fare.fare_attribute.transfer_duration >= trip.segments.get(trip.segments.size() - 1).getStartTime() - trip.segments.get(0).getStartTime())
                .min(Comparator.comparingDouble(fare -> fare.fare_attribute.price));
        assumeTrue(obviouslyCheapestFare.isPresent(), "There is an obviously cheapest fare.");
        Amount amount = Fares.cheapestFare(fares, trip).get();
        assertEquals(BigDecimal.valueOf(obviouslyCheapestFare.get().fare_attribute.price), amount.getAmount(), "The fare calculator agrees");
    }

    @ParameterizedTest(name = "{2}")
    @ArgumentsSource(DataPointProvider.class)
    public void buyMoreThanOneTicketIfTripIsLongerThanAllowedOnOne(Map<String, Map<String, Fare>> fares, Trip trip, String displayName) {
        assumeTrue(1L == fares.values().stream().flatMap(fs -> fs.values().stream()).count(), "Only one fare.");
        Fare onlyFare = fares.values().stream().flatMap(fs -> fs.values().stream()).findFirst().get();
        assumeTrue(trip.segments.size() > 1, "We have a transfer");
        assumeTrue(onlyFare.fare_attribute.transfers >= trip.segments.size(), "Fare allows the number of transfers we need for our trip.");
        assumeTrue((long) onlyFare.fare_attribute.transfer_duration <= trip.segments.get(trip.segments.size() - 1).getStartTime() - trip.segments.get(0).getStartTime(), "Fare does not allow the time we need for our trip.");

        Amount amount = Fares.cheapestFare(fares, trip).get();
        assertTrue(amount.getAmount().doubleValue() > onlyFare.fare_attribute.price);
    }

    @ParameterizedTest(name = "{2}")
    @ArgumentsSource(DataPointProvider.class)
    public void ifAllLegsPassThroughAllZonesOfTheTripItCantGetCheaper(Map<String, Map<String, Fare>> fares, Trip trip, String displayName) {
        assumeEachFeedHasAFare(fares);
        double cheapestFare = Fares.cheapestFare(fares, trip).get().getAmount().doubleValue();
        Set<String> allZones = trip.segments.stream().flatMap(seg -> seg.getZones().stream()).collect(Collectors.toSet());
        Trip otherTrip = new Trip();
        for (Trip.Segment segment : trip.segments) {
            otherTrip.segments.add(new Trip.Segment(segment.feed_id, segment.getRoute(), segment.getStartTime(), segment.getOriginId(), segment.getDestinationId(), allZones));
        }
        double cheapestFareWhereEveryLegGoesThroughAllZones = Fares.cheapestFare(fares, otherTrip).get().getAmount().doubleValue();
        assertTrue(cheapestFareWhereEveryLegGoesThroughAllZones >= cheapestFare);
    }

    @ParameterizedTest(name = "{2}")
    @ArgumentsSource(DataPointProvider.class)
    public void ifIOnlyHaveOneTicketAndItIsZoneBasedItMustBeGoodForAllZonesOnMyTrip(Map<String, Map<String, Fare>> fares, Trip trip, String displayName) {
        Fares.allShoppingCarts(fares, trip)
                .filter(purchase -> purchase.getTickets().size() == 1)
                .filter(purchase -> purchase.getTickets().get(0).getFare().fare_rules.stream().anyMatch(rule -> rule.contains_id != null))
                .forEach(purchase -> {
                    Set<String> zonesICanUse = purchase.getTickets().get(0).getFare().fare_rules.stream().filter(rule -> rule.contains_id != null).map(rule -> rule.contains_id).collect(Collectors.toSet());
                    Set<String> zonesINeed = trip.segments.stream().flatMap(segment -> segment.getZones().stream()).collect(Collectors.toSet());
                    assertTrue(zonesICanUse.containsAll(zonesINeed));
                });
    }

    public static Map<String, Fare> parseFares(String feedId, String fareAttributes, String fareRules) {
        GTFSFeed feed = new GTFSFeed();
        feed.feedId = feedId;
        HashMap<String, Fare> fares = new HashMap<>();
        new FareAttribute.Loader(feed, fares) {
            void load(String input) {
                reader = new CsvReader(new StringReader(input));
                reader.setHeaders(new String[]{"fare_id", "price", "currency_type", "payment_method", "transfers", "transfer_duration"});
                try {
                    while (reader.readRecord()) {
                        loadOneRow();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }.load(fareAttributes);
        new FareRule.Loader(feed, fares) {
            void load(String input) {
                reader = new CsvReader(new StringReader(input));
                reader.setHeaders(new String[]{"fare_id", "route_id", "origin_id", "destination_id", "contains_id"});
                try {
                    while (reader.readRecord()) {
                        loadOneRow();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }.load(fareRules);
        return fares;
    }

    private void assumeEachFeedHasAFare(Map<String, Map<String, Fare>> fares) {
        fares.values().forEach(faresForOneFeed -> assumeFalse(faresForOneFeed.entrySet().isEmpty(), "There are fares."));
    }

    // https://bitbucket.org/assylias/bigblue-utils/src/master/src/main/java/com/assylias/bigblue/utils/Maps.java?at=master
    public static <K, V> Map<K, V> map(K key, V value, Object... kvs) {
        return map(HashMap::new, key, value, kvs);
    }

    public static <K, V, T extends Map<K, V>> T map(BiFunction<Integer, Float, T> mapFactory, K key, V value, Object... kvs) {
        T m = mapFactory.apply(kvs.length / 2 + 1, 1f);
        m.put(key, value);
        for (int i = 0; i < kvs.length; ) {
            K k = (K) kvs[i++];
            V v = (V) kvs[i++];
            if (k != null && v != null) {
                m.put(k, v);
            }
        }
        return m;
    }

}
