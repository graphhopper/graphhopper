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
import com.conveyal.gtfs.model.Fare;
import com.conveyal.gtfs.model.FareAttribute;
import com.conveyal.gtfs.model.FareRule;
import com.csvreader.CsvReader;
import org.hamcrest.Matcher;
import org.junit.Assert;
import org.junit.experimental.theories.DataPoint;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeThat;
import static org.junit.Assume.assumeTrue;

@RunWith(Theories.class)
public class FareTest {

    // See https://code.google.com/archive/p/googletransitdatafeed/wikis/FareExamples.wiki

    public static @DataPoint Map<String, Map<String, Fare>> oneDollarUnlimitedTransfers = map(
            "only_feed_id", parseFares("only_feed_id","only_fare,1.00,USD,0\n", ""),
            "feed_id_2", parseFares("feed_id_2","", "")
    );
    public static @DataPoint Map<String, Map<String, Fare>> oneDollarNoTransfers = map(
            "only_feed_id", parseFares("only_feed_id","only_fare,1.00,USD,0,0\n", ""),
            "feed_id_2", parseFares("feed_id_2","", "")
    );
    public static @DataPoint Map<String, Map<String, Fare>> oneDollarTimeLimitedTransfers = map(
            "only_feed_id", parseFares("only_feed_id","only_fare,1.00,USD,0,,5400\n", ""),
            "feed_id_2", parseFares("feed_id_2","", "")
    );
    public static @DataPoint Map<String, Map<String, Fare>> regularAndExpress = map(
            "only_feed_id", parseFares("only_feed_id","local_fare,1.75,USD,0,0\n"+"express_fare,5.00,USD,0,0\n", "local_fare,Route_1\nexpress_fare,Route_2\nexpress_fare,Route3\n"),
            "feed_id_2", parseFares("feed_id_2","", "")
    );
    public static @DataPoint Map<String, Map<String, Fare>> withTransfersOrWithout = map(
            "only_feed_id", parseFares("only_feed_id","simple_fare,2.00,USD,0,0\n"+"plustransfer_fare,2.50,USD,0,,5400", ""),
            "feed_id_2", parseFares("feed_id_2","", "")
    );
    public static @DataPoint Map<String, Map<String, Fare>> stationPairs = map(
            "only_feed_id", parseFares("only_feed_id","!S1_to_S2,1.75,USD,0\n!S1_to_S3,3.25,USD,0\n!S1_to_S4,4.55,USD,0\n!S4_to_S1,5.65,USD,0\n", "!S1_to_S2,,S1,S2\n!S1_to_S3,,S1,S3\n!S1_to_S4,,S1,S4\n!S4_to_S1,,S4,S1\n"),
            "feed_id_2", parseFares("feed_id_2","", "")
    );
    public static @DataPoint Map<String, Map<String, Fare>> zones = map(
            "only_feed_id", parseFares("only_feed_id","F1,4.15,USD,0\nF2,2.20,USD,0\nF3,2.20,USD,0\nF4,2.95,USD,0\nF5,1.25,USD,0\nF6,1.95,USD,0\nF7,1.95,USD,0\n", "F1,,,,1\nF1,,,,2\nF1,,,,3\nF2,,,,1\nF2,,,,2\nF3,,,,1\nF3,,,,3\nF4,,,,2\nF4,,,,3\nF5,,,,1\nF6,,,,2\nF7,,,,3\n"),
            "feed_id_2", parseFares("feed_id_2","", "")
    );
    public static @DataPoint Map<String, Map<String, Fare>> twoFeeds = map(
            "only_feed_id", parseFares("only_feed_id","only_fare,1.00,USD,0,0\n", ""),
            "feed_id_2", parseFares("feed_id_2","only_fare,2.00,USD,0,0\n", "")
    );

    public static @DataPoint Trip tripWithOneSegment;
    public static @DataPoint Trip tripWithTwoSegments;
    public static @DataPoint Trip shortTripWithTwoSegments;
    public static @DataPoint Trip twoLegsWithDistinctZones;
    public static @DataPoint Trip twoLegsWithDistinctFeeds;

    static {
        tripWithOneSegment = new Trip();
        tripWithOneSegment.segments.add(new Trip.Segment("only_feed_id", "Route_1", 0, "S1", "S2", new HashSet<>(Arrays.asList("1","2","3"))));

        tripWithTwoSegments = new Trip();
        tripWithTwoSegments.segments.add(new Trip.Segment("only_feed_id", "Route_1", 0, "S1", "S4", new HashSet<>(Arrays.asList("1"))));
        tripWithTwoSegments.segments.add(new Trip.Segment("only_feed_id", "Route_2", 6000, "S4", "S1", new HashSet<>(Arrays.asList("1"))));

        shortTripWithTwoSegments = new Trip();
        shortTripWithTwoSegments.segments.add(new Trip.Segment("only_feed_id", "Route_1",0, "S1", "S4", new HashSet<>(Arrays.asList("2", "3"))));
        shortTripWithTwoSegments.segments.add(new Trip.Segment("only_feed_id", "Route_2",5000, "S4", "S1", new HashSet<>(Arrays.asList("2", "3"))));

        twoLegsWithDistinctZones = new Trip();
        twoLegsWithDistinctZones.segments.add(new Trip.Segment("only_feed_id", "Route_1",0, "S1", "S4", new HashSet<>(Arrays.asList("1"))));
        twoLegsWithDistinctZones.segments.add(new Trip.Segment("only_feed_id", "Route_2",5000, "S4", "S1", new HashSet<>(Arrays.asList("2"))));
        twoLegsWithDistinctZones.segments.add(new Trip.Segment("only_feed_id", "Route_1",6000, "S1", "S4", new HashSet<>(Arrays.asList("3"))));

        twoLegsWithDistinctFeeds = new Trip();
        twoLegsWithDistinctFeeds.segments.add(new Trip.Segment("only_feed_id", "Route_1",0, "S1", "S4", new HashSet<>()));
        twoLegsWithDistinctFeeds.segments.add(new Trip.Segment("feed_id_2", "Route_2",5000, "T", "T", new HashSet<>()));

    }

    @Theory
    public void irrelevantAlternatives(Map<String, Map<String, Fare>> fares, Trip trip) {
        assumeThat("There are at least two fares.", fares.get("only_feed_id").entrySet().size(), is(greaterThanOrEqualTo(2)));

        // If we only use one fare, say, the most expensive one...
        Fare mostExpensiveFare = fares.get("only_feed_id").values().stream().max(Comparator.comparingDouble(f -> f.fare_attribute.price)).get();
        Map<String, Map<String, Fare>> singleFare = map(
                "only_feed_id", map(mostExpensiveFare.fare_id, mostExpensiveFare),
                "feed_id_2", parseFares("feed_id_2","", "")
        );

        // ..and that still works for our trip..
        assumeThat("There is at least one fare for each segment.",
                trip.segments.stream().map(segment -> Fares.possibleFares(singleFare.get(segment.feed_id), segment)).collect(Collectors.toList()),
                everyItem(is(not(empty()))));
        double priceWithOneOption = Fares.cheapestFare(singleFare, trip).get().getAmount().doubleValue();

        double priceWithAllOptions = Fares.cheapestFare(fares, trip).get().getAmount().doubleValue();


        assertThat("...it shouldn't get more expensive when we put the cheaper options back.", priceWithAllOptions, lessThanOrEqualTo(priceWithOneOption));
    }

    @Theory
    public void everySegmentHasAFare(Map<String, Map<String, Fare>> fares, Trip trip) {
        assumeEachFeedHasAFare(fares);
        assertThat("There is at least one fare for each segment.",
                trip.segments.stream().map(segment -> Fares.possibleFares(fares.get(segment.feed_id), segment)).collect(Collectors.toList()),
                everyItem(is(not(empty()))));
    }

    @Theory
    public void withNoTransfersAndNoAlternativesBuyOneTicketForEachSegment(Map<String, Map<String, Fare>> fares, Trip trip) throws IOException {
        assumeEachFeedHasAFare(fares);
        fares.values().stream().flatMap(fs -> fs.values().stream()).forEach(fare -> {
            assumeThat("No Transfers allowed.", fare.fare_attribute.transfers, equalTo(0));
        });
        trip.segments.stream()
                .map(segment -> Fares.possibleFares(fares.get(segment.feed_id), segment))
                .forEach(candidateFares -> assertThat("Only one fare candidate per segment.", candidateFares.size(), equalTo(1)));
        double totalFare = Fares.cheapestFare(fares, trip).get().getAmount().doubleValue();
        double sumOfIndividualFares = trip.segments.stream().flatMap(segment -> Fares.possibleFares(fares.get(segment.feed_id), segment).stream()).mapToDouble(fare -> fare.fare_attribute.price).sum();
        assertThat("Total fare is the sum of all individual fares.", totalFare, equalTo(sumOfIndividualFares));
    }

    @Theory
    public void canGoAllTheWayOnOneTicket(Map<String, Map<String, Fare>> fares, Trip trip) throws IOException {
        assumeThat(trip.segments.stream().map(s -> s.feed_id).distinct().count(), equalTo(1L));
        Optional<Fare> obviouslyCheapestFare = fares.values().stream().flatMap(fs -> fs.values().stream())
                .filter(fare -> fare.fare_rules.isEmpty()) // Fare has no restrictions except transfer count/duration
                .filter(fare -> fare.fare_attribute.transfers >= trip.segments.size()-1) // Fare allows the number of transfers we need for our trip
                .filter(fare -> fare.fare_attribute.transfer_duration >= trip.segments.get(trip.segments.size() - 1).getStartTime() - trip.segments.get(0).getStartTime())
                .min(Comparator.comparingDouble(fare -> fare.fare_attribute.price));
        assumeTrue("There is an obviously cheapest fare.", obviouslyCheapestFare.isPresent());
        Amount amount = Fares.cheapestFare(fares, trip).get();
        Assert.assertEquals("The fare calculator agrees", BigDecimal.valueOf(obviouslyCheapestFare.get().fare_attribute.price), amount.getAmount());
    }

    @Theory
    public void buyMoreThanOneTicketIfTripIsLongerThanAllowedOnOne(Map<String, Map<String, Fare>> fares, Trip trip) throws IOException {
        assumeThat("Only one fare.", fares.values().stream().flatMap(fs -> fs.values().stream()).count(), equalTo(1L));
        Fare onlyFare = fares.values().stream().flatMap(fs -> fs.values().stream()).findFirst().get();
        assumeThat("We have a transfer", trip.segments.size(), greaterThan(1));
        assumeThat("Fare allows the number of transfers we need for our trip.", onlyFare.fare_attribute.transfers, greaterThanOrEqualTo(trip.segments.size()));
        assumeThat("Fare does not allow the time we need for our trip.", (long) onlyFare.fare_attribute.transfer_duration, lessThan(trip.segments.get(trip.segments.size()-1).getStartTime() - trip.segments.get(0).getStartTime()));

        Amount amount = Fares.cheapestFare(fares, trip).get();
        assertThat(amount.getAmount().doubleValue(), greaterThan(onlyFare.fare_attribute.price));
    }

    @Theory
    public void ifAllLegsPassThroughAllZonesOfTheTripItCantGetCheaper(Map<String, Map<String, Fare>> fares, Trip trip) {
        assumeEachFeedHasAFare(fares);
        double cheapestFare = Fares.cheapestFare(fares, trip).get().getAmount().doubleValue();
        Set<String> allZones = trip.segments.stream().flatMap(seg -> seg.getZones().stream()).collect(Collectors.toSet());
        Trip otherTrip = new Trip();
        for (Trip.Segment segment : trip.segments) {
            otherTrip.segments.add(new Trip.Segment(segment.feed_id, segment.getRoute(), segment.getStartTime(), segment.getOriginId(), segment.getDestinationId(), allZones));
        }
        double cheapestFareWhereEveryLegGoesThroughAllZones = Fares.cheapestFare(fares, otherTrip).get().getAmount().doubleValue();
        assertThat(cheapestFareWhereEveryLegGoesThroughAllZones, not(lessThan(cheapestFare)));
    }

    @Theory
    public void ifIOnlyHaveOneTicketAndItIsZoneBasedItMustBeGoodForAllZonesOnMyTrip(Map<String, Map<String, Fare>> fares, Trip trip) {
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
            void load(String input){
                reader = new CsvReader(new StringReader(input));
                reader.setHeaders(new String[]{"fare_id","price","currency_type","payment_method","transfers","transfer_duration"});
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
            void load(String input){
                reader = new CsvReader(new StringReader(input));
                reader.setHeaders(new String[]{"fare_id","route_id","origin_id","destination_id","contains_id"});
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
        fares.values().forEach(faresForOneFeed -> assumeThat("There are fares.", faresForOneFeed.entrySet(), not(empty())));
    }

    // https://bitbucket.org/assylias/bigblue-utils/src/master/src/main/java/com/assylias/bigblue/utils/Maps.java?at=master
    public static <K, V> Map<K, V> map(K key, V value, Object... kvs) {
        return map(HashMap::new, key, value, kvs);
    }

    public static <K, V, T extends Map<K, V>> T map(BiFunction<Integer, Float, T> mapFactory, K key, V value, Object... kvs) {
        T m = mapFactory.apply(kvs.length / 2 + 1, 1f);
        m.put(key, value);
        for (int i = 0; i < kvs.length;) {
            K k = (K) kvs[i++];
            V v = (V) kvs[i++];
            if (k != null && v != null) {
                m.put(k, v);
            }
        }
        return m;
    }

}
