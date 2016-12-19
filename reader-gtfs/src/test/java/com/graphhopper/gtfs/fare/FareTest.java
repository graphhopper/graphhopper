package com.graphhopper.gtfs.fare;

import com.conveyal.gtfs.GTFSFeed;
import com.conveyal.gtfs.model.Fare;
import com.conveyal.gtfs.model.FareAttribute;
import com.csvreader.CsvReader;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.experimental.theories.DataPoint;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.Assert.assertThat;

@RunWith(Theories.class)
public class FareTest {

    public static @DataPoint Map<String, Fare> oneDollarUnlimitedTransfers = parseFares("only_fare,1.00,USD,0\n");
    public static @DataPoint Map<String, Fare> oneDollarNoTransfers = parseFares("only_fare,1.00,USD,0,0\n");
    public static @DataPoint Map<String, Fare> oneDollarTimeLimitedTransfers = parseFares("only_fare,1.00,USD,0,,5400\n");

    public static @DataPoint Trip tripWithOneSegment;
    public static @DataPoint Trip tripWithTwoSegments;


    static {
        tripWithOneSegment = new Trip();
        tripWithOneSegment.segments.add(new Trip.Segment());

        tripWithTwoSegments = new Trip();
        tripWithTwoSegments.segments.add(new Trip.Segment());
        tripWithTwoSegments.segments.add(new Trip.Segment());
    }


    @Theory
    public void buyOneTicketForEverySegment(Map<String, Fare> fares, Trip trip) throws IOException {
        Assume.assumeThat("Only one fare.", fares.size(), equalTo(1));
        Fare onlyFare = fares.values().iterator().next();
        Assume.assumeThat("No Transfers allowed.", onlyFare.fare_attribute.transfers, equalTo(0));

        Amount amount = Fares.calculate(fares, trip);
        Assert.assertEquals(BigDecimal.valueOf(onlyFare.fare_attribute.price).multiply(BigDecimal.valueOf(trip.segments.size())), amount.getAmount());
    }

    @Theory
    public void canGoAllTheWayOnOneTicket(Map<String, Fare> fares, Trip trip) throws IOException {
        Assume.assumeThat("Only one fare.", fares.size(), equalTo(1));
        Fare onlyFare = fares.values().iterator().next();
        Assume.assumeThat("Fare allows the number of transfers we need for our trip.", onlyFare.fare_attribute.transfers, greaterThanOrEqualTo(trip.segments.size()));
        Assume.assumeThat("Fare allows the time we need for our trip.", (long) onlyFare.fare_attribute.transfer_duration, greaterThanOrEqualTo(trip.duration()));

        Amount amount = Fares.calculate(fares, trip);
        Assert.assertEquals(BigDecimal.valueOf(onlyFare.fare_attribute.price), amount.getAmount());
    }

    @Theory
    public void buyMoreThanOneTicketIfTripIsLongerThanAllowedOnOne(Map<String, Fare> fares, Trip trip) throws IOException {
        Assume.assumeThat("Only one fare.", fares.size(), equalTo(1));
        Fare onlyFare = fares.values().iterator().next();
        Assume.assumeThat("Fare allows the number of transfers we need for our trip.", onlyFare.fare_attribute.transfers, greaterThanOrEqualTo(trip.segments.size()));
        Assume.assumeThat("Fare does not allow the time we need for our trip.", (long) onlyFare.fare_attribute.transfer_duration, lessThan(trip.duration()));

        Amount amount = Fares.calculate(fares, trip);
        assertThat(amount.getAmount().doubleValue(), greaterThan(onlyFare.fare_attribute.price));
    }


    private static Map<String, Fare> parseFares(String input) {
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
        }.load(input);
        return fares;
    }

}
