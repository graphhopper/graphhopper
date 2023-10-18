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
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.graphhopper.gtfs.fare.FareTest.map;
import static com.graphhopper.gtfs.fare.FareTest.parseFares;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

public class MoreFareTests {

    @Test
    public void wurst() {
        Map<String, Map<String, Fare>> twoFeeds = map(
                "only_feed_id", parseFares("only_feed_id", "only_fare,1.00,USD,0,0\n", ""),
                "feed_id_2", parseFares("feed_id_2","only_fare,2.00,USD,0,0\n", "")
        );
        for (Map.Entry<String, Map<String, Fare>> entry : twoFeeds.entrySet()) {
            for (Fare fare : entry.getValue().values()) {
                Assumptions.assumeTrue(fare.fare_attribute.feed_id.equals(entry.getKey()));
            }
        }
        Trip twoLegsWithDistinctFeeds = new Trip();
        twoLegsWithDistinctFeeds.segments.add(new Trip.Segment("only_feed_id", "Route_1",0, "S1", "S4", new HashSet<>()));
        twoLegsWithDistinctFeeds.segments.add(new Trip.Segment("feed_id_2", "Route_2",5000, "T", "T", new HashSet<>()));
        List<TicketPurchase> ticketPurchases = Fares.allShoppingCarts(twoFeeds, twoLegsWithDistinctFeeds).collect(Collectors.toList());
        for (TicketPurchase ticketPurchase : ticketPurchases) {
            for (FareAssignment fareAssignment : ticketPurchase.fareAssignments) {
                assertThat(fareAssignment.segment.feed_id, equalTo(fareAssignment.fare.fare_attribute.feed_id));
            }
            assertThat(ticketPurchase.getNSchwarzfahrTrips(), equalTo(0));
            List<Ticket> tickets = ticketPurchase.getTickets();
            assertThat(tickets.stream().map(t -> t.feed_id).collect(Collectors.toSet()), equalTo(new HashSet<>(Arrays.asList("only_feed_id","feed_id_2"))));
        }
        assertThat(Fares.cheapestFare(twoFeeds, twoLegsWithDistinctFeeds).get().getAmount(), equalTo(new BigDecimal("3.0")));
    }

}
