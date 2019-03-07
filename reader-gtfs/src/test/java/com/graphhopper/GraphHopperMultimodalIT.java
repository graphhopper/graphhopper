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

package com.graphhopper;

import com.graphhopper.reader.gtfs.GraphHopperGtfs;
import com.graphhopper.reader.gtfs.GtfsStorage;
import com.graphhopper.reader.gtfs.PtFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FootFlagEncoder;
import com.graphhopper.storage.GHDirectory;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.util.Helper;
import com.graphhopper.util.Parameters;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

public class GraphHopperMultimodalIT {

    private static final String GRAPH_LOC = "target/GraphHopperMultimodalIT";
    private static GraphHopperGtfs graphHopper;
    private static final ZoneId zoneId = ZoneId.of("America/Los_Angeles");
    private static GraphHopperStorage graphHopperStorage;
    private static LocationIndex locationIndex;

    @BeforeClass
    public static void init() {
        Helper.removeDir(new File(GRAPH_LOC));
        final PtFlagEncoder ptFlagEncoder = new PtFlagEncoder();
        EncodingManager encodingManager = EncodingManager.create(Arrays.asList(ptFlagEncoder, new FootFlagEncoder()), 8);
        GHDirectory directory = GraphHopperGtfs.createGHDirectory(GRAPH_LOC);
        GtfsStorage gtfsStorage = GraphHopperGtfs.createGtfsStorage();
        graphHopperStorage = GraphHopperGtfs.createOrLoad(directory, encodingManager, ptFlagEncoder, gtfsStorage, Collections.singleton("files/sample-feed.zip"), Collections.singleton("files/beatty.osm"));
        locationIndex = GraphHopperGtfs.createOrLoadIndex(directory, graphHopperStorage);
        graphHopper = GraphHopperGtfs.createFactory(ptFlagEncoder, GraphHopperGtfs.createTranslationMap(), graphHopperStorage, locationIndex, gtfsStorage)
                .createWithoutRealtimeFeed();
    }

    @AfterClass
    public static void close() {
        graphHopperStorage.close();
        locationIndex.close();
    }

    @Test
    public void testDepartureTimeOfAccessLegInProfileQuery() {
        GHRequest ghRequest = new GHRequest(
                36.91311729030539, -116.76769495010377,
                36.91260259593356, -116.76149368286134
        );
        ghRequest.getHints().put(Parameters.PT.EARLIEST_DEPARTURE_TIME, LocalDateTime.of(2007, 1, 1, 6, 40, 0).atZone(zoneId).toInstant());
        ghRequest.getHints().put(Parameters.PT.PROFILE_QUERY, true);

        GHResponse response = graphHopper.route(ghRequest);
        assertThat(response.getHints().getInt("visited_nodes.sum", Integer.MAX_VALUE)).isLessThanOrEqualTo(243);

        PathWrapper firstTransitSolution = response.getAll().stream().filter(p -> p.getLegs().size() > 1).findFirst().get(); // There can be a walk-only trip.
        assertThat(firstTransitSolution.getLegs().get(0).getDepartureTime().toInstant().atZone(zoneId).toLocalTime())
                .isEqualTo(LocalTime.parse("06:41:04.833"));
        assertThat(firstTransitSolution.getLegs().get(0).getArrivalTime().toInstant())
                .isEqualTo(firstTransitSolution.getLegs().get(1).getDepartureTime().toInstant());
        assertThat(firstTransitSolution.getLegs().get(2).getArrivalTime().toInstant().atZone(zoneId).toLocalTime())
                .isEqualTo(LocalTime.parse("06:52:02.729"));
    }

    @Test
    public void testDepartureTimeOfAccessLeg() {
        GHRequest ghRequest = new GHRequest(
                36.91311729030539, -116.76769495010377,
                36.91260259593356, -116.76149368286134
        );
        ghRequest.getHints().put(Parameters.PT.EARLIEST_DEPARTURE_TIME, LocalDateTime.of(2007, 1, 1, 6, 40, 0).atZone(zoneId).toInstant());
        ghRequest.getHints().put("beta_walk_time", 2.0); // I somewhat dislike walking

        GHResponse response = graphHopper.route(ghRequest);
        assertThat(response.getHints().getInt("visited_nodes.sum", Integer.MAX_VALUE)).isLessThanOrEqualTo(129);

        PathWrapper firstTransitSolution = response.getAll().stream().filter(p -> p.getLegs().size() > 1).findFirst().get(); // There can be a walk-only trip.
        assertThat(firstTransitSolution.getLegs().get(0).getDepartureTime().toInstant().atZone(zoneId).toLocalTime())
                .isEqualTo(LocalTime.parse("06:41:04.833"));
        assertThat(firstTransitSolution.getLegs().get(0).getArrivalTime().toInstant())
                .isEqualTo(firstTransitSolution.getLegs().get(1).getDepartureTime().toInstant());
        assertThat(firstTransitSolution.getLegs().get(2).getArrivalTime().toInstant().atZone(zoneId).toLocalTime())
                .isEqualTo(LocalTime.parse("06:52:02.729"));

        PathWrapper walkSolution = response.getAll().stream().filter(p -> p.getLegs().size() == 1).findFirst().get();
        assertThat(walkSolution.getLegs().get(0).getDepartureTime().toInstant().atZone(zoneId).toLocalTime())
                .isEqualTo(LocalTime.parse("06:40"));
        // In principle, this would dominate the transit solution, since it's faster, but
        // by default, walking gets a slight penalty.
        assertThat(walkSolution.getLegs().get(0).getArrivalTime().toInstant().atZone(zoneId).toLocalTime())
                .isEqualTo(LocalTime.parse("06:51:10.370"));
        assertThat(walkSolution.getLegs().size()).isEqualTo(1);
        assertThat(walkSolution.getNumChanges()).isEqualTo(-1);

        // I like walking exactly as I like riding a bus (per travel time unit)
        // Now, the walk solution dominates, and we get no transit solution.
        ghRequest.getHints().put("beta_walk_time", 1.0);
        response = graphHopper.route(ghRequest);
        assertThat(response.getHints().getInt("visited_nodes.sum", Integer.MAX_VALUE)).isLessThanOrEqualTo(138);
        assertThat(response.getAll().stream().filter(p -> p.getLegs().size() > 1).findFirst()).isEmpty();
    }

    @Test
    public void testFastWalking() {
        GHRequest ghRequest = new GHRequest(
                36.91311729030539, -116.76769495010377,
                36.91260259593356, -116.76149368286134
        );
        ghRequest.getHints().put(Parameters.PT.EARLIEST_DEPARTURE_TIME, LocalDateTime.of(2007, 1, 1, 6, 40, 0).atZone(zoneId).toInstant());
        ghRequest.getHints().put(Parameters.PT.WALK_SPEED, 50); // Yes, I can walk very fast, 50 km/h. Problem?

        GHResponse response = graphHopper.route(ghRequest);

        PathWrapper walkSolution = response.getAll().stream().filter(p -> p.getLegs().size() == 1).findFirst().get();
        assertThat(walkSolution.getLegs().get(0).getDepartureTime().toInstant().atZone(zoneId).toLocalTime())
                .isEqualTo(LocalTime.parse("06:40"));
        assertThat(walkSolution.getLegs().get(0).getArrivalTime().toInstant().atZone(zoneId).toLocalTime())
                .isEqualTo(LocalTime.parse("06:41:07.031"));
        assertThat(walkSolution.getLegs().size()).isEqualTo(1);
        assertThat(walkSolution.getNumChanges()).isEqualTo(-1);
    }

    @Test
    public void testFastWalkingInProfileQuery() {
        GHRequest ghRequest = new GHRequest(
                36.91311729030539, -116.76769495010377,
                36.91260259593356, -116.76149368286134
        );
        ghRequest.getHints().put(Parameters.PT.EARLIEST_DEPARTURE_TIME, LocalDateTime.of(2007, 1, 1, 6, 40, 0).atZone(zoneId).toInstant());
        ghRequest.getHints().put(Parameters.PT.WALK_SPEED, 50); // Yes, I can walk very fast, 50 km/h. Problem?
        ghRequest.getHints().put(Parameters.PT.PROFILE_QUERY, true);

        GHResponse response = graphHopper.route(ghRequest);

        PathWrapper walkSolution = response.getAll().get(0);
        assertThat(walkSolution.getLegs().get(0).getDepartureTime().toInstant().atZone(zoneId).toLocalTime())
                .isEqualTo(LocalTime.parse("06:40"));
        assertThat(walkSolution.getLegs().get(0).getArrivalTime().toInstant().atZone(zoneId).toLocalTime())
                .isEqualTo(LocalTime.parse("06:41:07.031"));
        assertThat(walkSolution.getLegs().size()).isEqualTo(1);
        assertThat(walkSolution.getNumChanges()).isEqualTo(-1);
    }

    @Test
    public void testProfileQueryDoesntEndPrematurely() {
        GHRequest ghRequest = new GHRequest(
                36.91311729030539, -116.76769495010377,
                36.91260259593356, -116.76149368286134
        );
        ghRequest.getHints().put(Parameters.PT.EARLIEST_DEPARTURE_TIME, LocalDateTime.of(2007, 1, 1, 6, 40, 0).atZone(zoneId).toInstant());
        // Provoke a situation where solutions which are later dominated will be found early.
        // If everything is right, the n-th solution should be the same, no matter if I ask for n, or for n+m solutions.
        ghRequest.getHints().put(Parameters.PT.WALK_SPEED, 1); // No, I cannot walk very fast, 1 km/h. Problem?
        ghRequest.getHints().put(Parameters.PT.PROFILE_QUERY, true);

        ghRequest.getHints().put(Parameters.PT.LIMIT_SOLUTIONS, 1);
        GHResponse response1 = graphHopper.route(ghRequest);
        assertThat(response1.getHints().getInt("visited_nodes.sum", Integer.MAX_VALUE)).isLessThanOrEqualTo(142);
        ghRequest.getHints().put(Parameters.PT.LIMIT_SOLUTIONS, 3);
        GHResponse response3 = graphHopper.route(ghRequest);
        assertThat(response3.getHints().getInt("visited_nodes.sum", Integer.MAX_VALUE)).isLessThanOrEqualTo(230);
        assertThat(response1.getAll().get(0).getTime()).isEqualTo(response3.getAll().get(0).getTime());
        ghRequest.getHints().put(Parameters.PT.LIMIT_SOLUTIONS, 5);
        GHResponse response5 = graphHopper.route(ghRequest);
        assertThat(response5.getHints().getInt("visited_nodes.sum", Integer.MAX_VALUE)).isLessThanOrEqualTo(334);
        assertThat(response3.getAll().get(2).getTime()).isEqualTo(response5.getAll().get(2).getTime());
    }

    @Test
    public void testHighDisutilityOfWalking() {
        GHRequest ghRequest = new GHRequest(
                36.91311729030539, -116.76769495010377,
                36.91260259593356, -116.76149368286134
        );
        ghRequest.getHints().put(Parameters.PT.EARLIEST_DEPARTURE_TIME, LocalDateTime.of(2007, 1, 1, 6, 40, 0).atZone(zoneId).toInstant());
        ghRequest.getHints().put(Parameters.PT.WALK_SPEED, 50); // Yes, I can walk very fast, 50 km/h. Problem?
        ghRequest.getHints().put("beta_walk_time", 20); // But I dislike walking a lot.

        GHResponse response = graphHopper.route(ghRequest);

        PathWrapper transitSolution = response.getAll().stream().filter(p -> p.getLegs().size() > 1).findFirst().get();
        assertThat(transitSolution.getLegs().size()).isEqualTo(3);
    }
}
