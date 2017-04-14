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
import org.junit.Assert;
import org.junit.experimental.theories.DataPoint;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeThat;

@RunWith(Theories.class)
public class FareTest {

    // See https://code.google.com/archive/p/googletransitdatafeed/wikis/FareExamples.wiki

    public static @DataPoint Map<String, Fare> oneDollarUnlimitedTransfers = parseFares("only_fare,1.00,USD,0\n", "");
    public static @DataPoint Map<String, Fare> oneDollarNoTransfers = parseFares("only_fare,1.00,USD,0,0\n", "");
    public static @DataPoint Map<String, Fare> oneDollarTimeLimitedTransfers = parseFares("only_fare,1.00,USD,0,,5400\n", "");
    public static @DataPoint Map<String, Fare> regularAndExpress = parseFares("local_fare,1.75,USD,0,0\n"+"express_fare,5.00,USD,0,0\n", "local_fare,Route_1\nexpress_fare,Route_2\nexpress_fare,Route3\n");
    public static @DataPoint Map<String, Fare> withTransfersOrWithout = parseFares("simple_fare,1.75,USD,0,0\n"+"plustransfer_fare,2.00,USD,0,,5400", "");
    public static @DataPoint Map<String, Fare> stationPairs = parseFares("!S1_to_S2,1.75,USD,0\n!S1_to_S3,3.25,USD,0\n!S1_to_S4,4.55,USD,0\n!S4_to_S1,5.65,USD,0\n", "!S1_to_S2,,S1,S2\n!S1_to_S3,,S1,S3\n!S1_to_S4,,S1,S4\n!S4_to_S1,,S4,S1\n");
    public static @DataPoint Map<String, Fare> zones = parseFares("F1,4.15,USD,0\nF2,2.20,USD,0\nF3,2.20,USD,0\nF4,2.95,USD,0\nF5,1.25,USD,0\nF6,1.95,USD,0\nF7,1.95,USD,0\n", "F1,,,,1\nF1,,,,2\nF1,,,,3\nF2,,,,1\nF2,,,,2\nF3,,,,1\nF3,,,,3\nF4,,,,2\nF4,,,,3\nF5,,,,1\nF6,,,,2\nF7,,,,3\n");


    public static @DataPoint Trip tripWithOneSegment;
    public static @DataPoint Trip tripWithTwoSegments;
    public static @DataPoint Trip shortTripWithTwoSegments;


    static {
        tripWithOneSegment = new Trip();
        tripWithOneSegment.segments.add(new Trip.Segment("Route_1", 0, "S1", "S2", new HashSet<>(Arrays.asList("1","2","3"))));

        tripWithTwoSegments = new Trip();
        tripWithTwoSegments.segments.add(new Trip.Segment("Route_1", 0, "S1", "S4", new HashSet<>(Arrays.asList("1"))));
        tripWithTwoSegments.segments.add(new Trip.Segment("Route_2", 6000, "S4", "S1", new HashSet<>(Arrays.asList("1"))));

        shortTripWithTwoSegments = new Trip();
        shortTripWithTwoSegments.segments.add(new Trip.Segment("Route_1",0, "S1", "S4", new HashSet<>(Arrays.asList("2", "3"))));
        shortTripWithTwoSegments.segments.add(new Trip.Segment("Route_2",5000, "S4", "S1", new HashSet<>(Arrays.asList("2", "3"))));
    }

    @Theory
    public void irrelevantAlternatives(Map<String, Fare> fares, Trip trip) {
        assumeThat("There are at least two fares.", fares.entrySet().size(), is(greaterThanOrEqualTo(2)));

        // If we only use one fare, say, the most expensive one...
        Fare mostExpensiveFare = fares.values().stream().max(Comparator.comparingDouble(f -> f.fare_attribute.price)).get();
        HashMap<String, Fare> singleFare = new HashMap<>();
        singleFare.put(mostExpensiveFare.fare_id, mostExpensiveFare);

        // ..and that still works for our trip..
        assumeThat("There is at least one fare for each segment.",
                trip.segments.stream().map(segment -> Fares.possibleFares(singleFare, segment)).collect(Collectors.toList()),
                everyItem(is(not(empty()))));
        double priceWithOneOption = Fares.cheapestFare(singleFare, trip).get().getAmount().doubleValue();

        double priceWithAllOptions = Fares.cheapestFare(fares, trip).get().getAmount().doubleValue();


        assertThat("...it shouldn't get more expensive when we put the cheaper options back.", priceWithAllOptions, lessThanOrEqualTo(priceWithOneOption));
    }

    @Theory
    public void everySegmentHasAFare(Map<String, Fare> fares, Trip trip) {
        assumeThat("There are fares.", fares.entrySet(), not(empty()));
        assertThat("There is at least one fare for each segment.",
                trip.segments.stream().map(segment -> Fares.possibleFares(fares, segment)).collect(Collectors.toList()),
                everyItem(is(not(empty()))));
    }

    @Theory
    public void withNoTransfersAndNoAlternativesBuyOneTicketForEachSegment(Map<String, Fare> fares, Trip trip) throws IOException {
        fares.values().forEach(fare -> {
            assumeThat("No Transfers allowed.", fare.fare_attribute.transfers, equalTo(0));
        });
        trip.segments.stream()
                .map(segment -> Fares.possibleFares(fares, segment))
                .forEach(candidateFares -> assertThat("Only one fare candidate per segment.", candidateFares.size(), equalTo(1)));
        assertThat("Total fare is the sum of all individual fares.",
                Fares.cheapestFare(fares, trip).get().getAmount().doubleValue(),
                equalTo(trip.segments.stream().flatMap(segment -> Fares.possibleFares(fares, segment).stream()).mapToDouble(fare -> fare.fare_attribute.price).sum()));
    }

    @Theory
    public void canGoAllTheWayOnOneTicket(Map<String, Fare> fares, Trip trip) throws IOException {
        assumeThat("Only one fare.", fares.size(), equalTo(1));
        Fare onlyFare = fares.values().iterator().next();
        assumeThat("Fare allows the number of transfers we need for our trip.", onlyFare.fare_attribute.transfers, greaterThanOrEqualTo(trip.segments.size()));
        assumeThat("Fare allows the time we need for our trip.", (long) onlyFare.fare_attribute.transfer_duration, greaterThanOrEqualTo(trip.segments.get(trip.segments.size()-1).getStartTime() - trip.segments.get(0).getStartTime()));

        Amount amount = Fares.cheapestFare(fares, trip).get();
        Assert.assertEquals(BigDecimal.valueOf(onlyFare.fare_attribute.price), amount.getAmount());
    }

    @Theory
    public void buyMoreThanOneTicketIfTripIsLongerThanAllowedOnOne(Map<String, Fare> fares, Trip trip) throws IOException {
        assumeThat("Only one fare.", fares.size(), equalTo(1));
        Fare onlyFare = fares.values().iterator().next();
        assumeThat("We have a transfer", trip.segments.size(), greaterThan(1));
        assumeThat("Fare allows the number of transfers we need for our trip.", onlyFare.fare_attribute.transfers, greaterThanOrEqualTo(trip.segments.size()));
        assumeThat("Fare does not allow the time we need for our trip.", (long) onlyFare.fare_attribute.transfer_duration, lessThan(trip.segments.get(trip.segments.size()-1).getStartTime() - trip.segments.get(0).getStartTime()));

        Amount amount = Fares.cheapestFare(fares, trip).get();
        assertThat(amount.getAmount().doubleValue(), greaterThan(onlyFare.fare_attribute.price));
    }


    private static Map<String, Fare> parseFares(String fareAttributes, String fareRules) {
        GTFSFeed feed = new GTFSFeed();
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

}
