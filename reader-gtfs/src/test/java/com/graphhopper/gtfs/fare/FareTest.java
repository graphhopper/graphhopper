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
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeThat;

@RunWith(Theories.class)
public class FareTest {

    public static @DataPoint Map<String, Fare> oneDollarUnlimitedTransfers = parseFares("only_fare,1.00,USD,0\n", "");
    public static @DataPoint Map<String, Fare> oneDollarNoTransfers = parseFares("only_fare,1.00,USD,0,0\n", "");
    public static @DataPoint Map<String, Fare> oneDollarTimeLimitedTransfers = parseFares("only_fare,1.00,USD,0,,5400\n", "");
    public static @DataPoint Map<String, Fare> regularAndExpress = parseFares("local_fare,1.75,USD,0,0\n"+"express_fare,5.00,USD,0,0\n", "local_fare,Route_1\nexpress_fare,Route_2\nexpress_fare,Route3");


    public static @DataPoint Trip tripWithOneSegment;
    public static @DataPoint Trip tripWithTwoSegments;


    static {
        tripWithOneSegment = new Trip();
        tripWithOneSegment.segments.add(new Trip.Segment("Route_1"));

        tripWithTwoSegments = new Trip();
        tripWithTwoSegments.segments.add(new Trip.Segment("Route_1"));
        tripWithTwoSegments.segments.add(new Trip.Segment("Route_2"));
    }

    @Theory
    public void everySegmentHasAFare(Map<String, Fare> fares, Trip trip) {
        assumeThat("There are fares.", fares.entrySet(), not(empty()));
        assertThat("There is at least one fare for each segment.",
                trip.segments.stream().map(segment -> Fares.calculate(fares, segment)).collect(Collectors.toList()),
                everyItem(is(not(empty()))));
    }

    @Theory
    public void withNoTransfersAndNoAlternativesBuyOneTicketForEachSegment(Map<String, Fare> fares, Trip trip) throws IOException {
        fares.values().forEach(fare -> {
            assumeThat("No Transfers allowed.", fare.fare_attribute.transfers, equalTo(0));
        });
        trip.segments.stream()
                .map(segment -> Fares.calculate(fares, segment))
                .forEach(candidateFares -> assertThat("Only one fare candidate per segment.", candidateFares.size(), equalTo(1)));
        assertThat("Total fare is the sum of all individual fares.",
                Fares.calculate(fares, trip).getAmount().doubleValue(),
                equalTo(trip.segments.stream().flatMap(segment -> Fares.calculate(fares, segment).stream()).mapToDouble(fare -> fare.fare_attribute.price).sum()));
    }

    @Theory
    public void canGoAllTheWayOnOneTicket(Map<String, Fare> fares, Trip trip) throws IOException {
        assumeThat("Only one fare.", fares.size(), equalTo(1));
        Fare onlyFare = fares.values().iterator().next();
        assumeThat("Fare allows the number of transfers we need for our trip.", onlyFare.fare_attribute.transfers, greaterThanOrEqualTo(trip.segments.size()));
        assumeThat("Fare allows the time we need for our trip.", (long) onlyFare.fare_attribute.transfer_duration, greaterThanOrEqualTo(trip.duration()));

        Amount amount = Fares.calculate(fares, trip);
        Assert.assertEquals(BigDecimal.valueOf(onlyFare.fare_attribute.price), amount.getAmount());
    }

    @Theory
    public void buyMoreThanOneTicketIfTripIsLongerThanAllowedOnOne(Map<String, Fare> fares, Trip trip) throws IOException {
        assumeThat("Only one fare.", fares.size(), equalTo(1));
        Fare onlyFare = fares.values().iterator().next();
        assumeThat("Fare allows the number of transfers we need for our trip.", onlyFare.fare_attribute.transfers, greaterThanOrEqualTo(trip.segments.size()));
        assumeThat("Fare does not allow the time we need for our trip.", (long) onlyFare.fare_attribute.transfer_duration, lessThan(trip.duration()));

        Amount amount = Fares.calculate(fares, trip);
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
                reader.setHeaders(new String[]{"fare_id","route_id"});
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
