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

import com.graphhopper.core.GraphHopperConfig;
import com.graphhopper.config.Profile;
import com.graphhopper.gtfs.*;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.Subnetwork;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.util.Helper;
import com.graphhopper.core.util.TranslationMap;
import com.graphhopper.util.details.PathDetail;
import com.graphhopper.util.shapes.GHPoint;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;

import java.io.File;
import java.time.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

public class GraphHopperMultimodalIT {

    private static final String GRAPH_LOC = "target/GraphHopperMultimodalIT";
    private static PtRouter graphHopper;
    private static final ZoneId zoneId = ZoneId.of("America/Los_Angeles");
    private static GraphHopperGtfs graphHopperGtfs;
    private static LocationIndex locationIndex;

    @BeforeAll
    public static void init() {
        GraphHopperConfig ghConfig = new GraphHopperConfig();
        ghConfig.putObject("datareader.file", "files/beatty.osm");
        ghConfig.putObject("import.osm.ignored_highways", "");
        ghConfig.putObject("gtfs.file", "files/sample-feed");
        ghConfig.putObject("graph.location", GRAPH_LOC);
        ghConfig.setProfiles(Arrays.asList(
                new Profile("foot").setVehicle("foot").setWeighting("fastest"),
                new Profile("car").setVehicle("car").setWeighting("fastest")));
        Helper.removeDir(new File(GRAPH_LOC));
        graphHopperGtfs = new GraphHopperGtfs(ghConfig);
        graphHopperGtfs.init(ghConfig);
        graphHopperGtfs.importOrLoad();

        graphHopperGtfs.close();
        // Re-load read only
        graphHopperGtfs = new GraphHopperGtfs(ghConfig);
        graphHopperGtfs.init(ghConfig);
        graphHopperGtfs.importOrLoad();

        locationIndex = graphHopperGtfs.getLocationIndex();
        graphHopper = new PtRouterImpl.Factory(ghConfig, new TranslationMap().doImport(), graphHopperGtfs.getBaseGraph(), graphHopperGtfs.getEncodingManager(), locationIndex, graphHopperGtfs.getGtfsStorage())
                .createWithoutRealtimeFeed();
    }

    @AfterAll
    public static void close() {
        graphHopperGtfs.close();
        locationIndex.close();
    }

    @Test
    public void testDepartureTimeOfAccessLegInProfileQuery() {
        Request ghRequest = new Request(
                36.91311729030539, -116.76769495010377,
                36.91260259593356, -116.76149368286134
        );
        ghRequest.setEarliestDepartureTime(LocalDateTime.of(2007, 1, 1, 6, 40, 0).atZone(zoneId).toInstant());
        ghRequest.setBetaStreetTime(2.0);
        ghRequest.setProfileQuery(true);
        ghRequest.setMaxProfileDuration(Duration.ofHours(1));

        GHResponse response = graphHopper.route(ghRequest);
        assertThat(response.getHints().getInt("visited_nodes.sum", Integer.MAX_VALUE)).isLessThanOrEqualTo(263);

        ResponsePath firstTransitSolution = response.getAll().stream().filter(p -> p.getLegs().size() > 1).findFirst().get();
        assertThat(firstTransitSolution.getLegs().get(0).getDepartureTime().toInstant().atZone(zoneId).toLocalTime())
                .isEqualTo(LocalTime.parse("06:41:04.826"));
        assertThat(firstTransitSolution.getLegs().get(0).getArrivalTime().toInstant())
                .isEqualTo(firstTransitSolution.getLegs().get(1).getDepartureTime().toInstant());
        assertThat(firstTransitSolution.getLegs().get(2).getArrivalTime().toInstant().atZone(zoneId).toLocalTime())
                .isEqualTo(LocalTime.parse("06:52:02.641"));

        // I like walking exactly as I like riding a bus (per travel time unit)
        // Now we get a walk solution which arrives earlier than the transit solutions.
        // If this wasn't a profile query, they would be dominated and we would only get a walk
        // solution. But at the exact time where the transit route departs, the transit route is superior,
        // since it is very slightly faster.
        ghRequest.setBetaStreetTime(1.0);
        response = graphHopper.route(ghRequest);
        ResponsePath walkSolution = response.getAll().stream().filter(p -> p.getLegs().size() == 1).findFirst().get();
        firstTransitSolution = response.getAll().stream().filter(p -> p.getLegs().size() > 1).findFirst().get();
        assertThat(arrivalTime(walkSolution.getLegs().get(0))).isBefore(arrivalTime(firstTransitSolution.getLegs().get(firstTransitSolution.getLegs().size()-1)));
        assertThat(routeDuration(firstTransitSolution)).isLessThanOrEqualTo(routeDuration(walkSolution));

        assertThat(response.getHints().getInt("visited_nodes.sum", Integer.MAX_VALUE)).isLessThanOrEqualTo(271);
    }

    @Test
    public void testDepartureTimeOfAccessLeg() {
        Request ghRequest = new Request(
                36.91311729030539, -116.76769495010377,
                36.91260259593356, -116.76149368286134
        );
        ghRequest.setEarliestDepartureTime(LocalDateTime.of(2007, 1, 1, 6, 40, 0).atZone(zoneId).toInstant());
        ghRequest.setBetaStreetTime(2.0); // I somewhat dislike walking
        ghRequest.setPathDetails(Arrays.asList("distance"));

        GHResponse response = graphHopper.route(ghRequest);
        assertThat(response.getHints().getInt("visited_nodes.sum", Integer.MAX_VALUE)).isLessThanOrEqualTo(129);

        ResponsePath firstTransitSolution = response.getAll().stream().filter(p -> p.getLegs().size() > 1).findFirst().get(); // There can be a walk-only trip.
        assertThat(firstTransitSolution.getLegs().get(0).getDepartureTime().toInstant().atZone(zoneId).toLocalTime())
                .isEqualTo(LocalTime.parse("06:41:04.826"));
        assertThat(firstTransitSolution.getLegs().get(0).getArrivalTime().toInstant())
                .isEqualTo(firstTransitSolution.getLegs().get(1).getDepartureTime().toInstant());
        assertThat(firstTransitSolution.getLegs().get(2).getArrivalTime().toInstant().atZone(zoneId).toLocalTime())
                .isEqualTo(LocalTime.parse("06:52:02.641"));

        double EXPECTED_TOTAL_WALKING_DISTANCE = 496.96631386761055;
        assertThat(firstTransitSolution.getLegs().get(0).distance + firstTransitSolution.getLegs().get(2).distance)
                .isEqualTo(EXPECTED_TOTAL_WALKING_DISTANCE);
        List<PathDetail> distances = firstTransitSolution.getPathDetails().get("distance");
        assertThat(distances.stream().mapToDouble(d -> (double) d.getValue()).sum())
                .isEqualTo(EXPECTED_TOTAL_WALKING_DISTANCE); // Also total walking distance -- PathDetails only cover access/egress for now
        assertThat(distances.get(0).getFirst()).isEqualTo(0); // PathDetails start and end with PointList
        assertThat(distances.get(distances.size()-1).getLast()).isEqualTo(10);

        List<PathDetail> accessDistances = ((Trip.WalkLeg) firstTransitSolution.getLegs().get(0)).details.get("distance");
        assertThat(accessDistances.get(0).getFirst()).isEqualTo(0);
        assertThat(accessDistances.get(accessDistances.size()-1).getLast()).isEqualTo(2);

        List<PathDetail> egressDistances = ((Trip.WalkLeg) firstTransitSolution.getLegs().get(2)).details.get("distance");
        assertThat(egressDistances.get(0).getFirst()).isEqualTo(0);
        assertThat(egressDistances.get(egressDistances.size()-1).getLast()).isEqualTo(5);

        ResponsePath walkSolution = response.getAll().stream().filter(p -> p.getLegs().size() == 1).findFirst().get();
        assertThat(walkSolution.getLegs().get(0).getDepartureTime().toInstant().atZone(zoneId).toLocalTime())
                .isEqualTo(LocalTime.parse("06:40"));
        // In principle, this would dominate the transit solution, since it's faster, but
        // walking gets a penalty.
        assertThat(walkSolution.getLegs().get(0).getArrivalTime().toInstant().atZone(zoneId).toLocalTime())
                .isEqualTo(LocalTime.parse("06:51:10.306"));
        assertThat(walkSolution.getLegs().size()).isEqualTo(1);
        assertThat(walkSolution.getNumChanges()).isEqualTo(-1);

        // I like walking exactly as I like riding a bus (per travel time unit)
        // Now, the walk solution dominates, and we get no transit solution.
        ghRequest.setBetaStreetTime(1.0);
        response = graphHopper.route(ghRequest);
        assertThat(response.getHints().getInt("visited_nodes.sum", Integer.MAX_VALUE)).isLessThanOrEqualTo(138);
        assertThat(response.getAll().stream().filter(p -> p.getLegs().size() > 1).findFirst()).isEmpty();
    }

    @Test
    public void testArriveBy() {
        Request ghRequest = new Request(
                36.92311729030539, -116.76769495010377,
                36.91260259593356, -116.76149368286134
        );
        ghRequest.setEarliestDepartureTime(LocalDateTime.of(2007, 1, 1, 7, 0, 0).atZone(zoneId).toInstant());
        ghRequest.setArriveBy(true);

        GHResponse response = graphHopper.route(ghRequest);
        assertThat(response.getAll()).isNotEmpty();
    }

    @Test
    public void testFastWalking() {
        Request ghRequest = new Request(
                36.91311729030539, -116.76769495010377,
                36.91260259593356, -116.76149368286134
        );
        ghRequest.setEarliestDepartureTime(LocalDateTime.of(2007, 1, 1, 6, 40, 0).atZone(zoneId).toInstant());
        ghRequest.setWalkSpeedKmH(50); // Yes, I can walk very fast, 50 km/h. Problem?

        GHResponse response = graphHopper.route(ghRequest);

        ResponsePath walkSolution = response.getAll().stream().filter(p -> p.getLegs().size() == 1).findFirst().get();
        assertThat(walkSolution.getLegs().get(0).getDepartureTime().toInstant().atZone(zoneId).toLocalTime())
                .isEqualTo(LocalTime.parse("06:40"));
        assertThat(walkSolution.getLegs().get(0).getArrivalTime().toInstant().atZone(zoneId).toLocalTime())
                .isEqualTo(LocalTime.parse("06:41:07.025"));
        assertThat(walkSolution.getLegs().size()).isEqualTo(1);
        assertThat(walkSolution.getNumChanges()).isEqualTo(-1);
    }

    @Test
    public void testFastWalkingInProfileQuery() {
        Request ghRequest = new Request(
                36.91311729030539, -116.76769495010377,
                36.91260259593356, -116.76149368286134
        );
        ghRequest.setEarliestDepartureTime(LocalDateTime.of(2007, 1, 1, 6, 40, 0).atZone(zoneId).toInstant());
        ghRequest.setWalkSpeedKmH(50); // Yes, I can walk very fast, 50 km/h. Problem?
        ghRequest.setProfileQuery(true);

        GHResponse response = graphHopper.route(ghRequest);

        ResponsePath walkSolution = response.getAll().get(0);
        assertThat(walkSolution.getLegs().get(0).getDepartureTime().toInstant().atZone(zoneId).toLocalTime())
                .isEqualTo(LocalTime.parse("06:40"));
        assertThat(walkSolution.getLegs().get(0).getArrivalTime().toInstant().atZone(zoneId).toLocalTime())
                .isEqualTo(LocalTime.parse("06:41:07.025"));
        assertThat(walkSolution.getLegs().size()).isEqualTo(1);
        assertThat(walkSolution.getNumChanges()).isEqualTo(-1);
    }

    @Test
    public void testProfileQueryDoesntEndPrematurely() {
        Request ghRequest = new Request(
                36.91311729030539, -116.76769495010377,
                36.91260259593356, -116.76149368286134
        );
        ghRequest.setEarliestDepartureTime(LocalDateTime.of(2007, 1, 1, 6, 40, 0).atZone(zoneId).toInstant());
        // Provoke a situation where solutions which are later dominated will be found early.
        // If everything is right, the n-th solution should be the same, no matter if I ask for n, or for n+m solutions.
        ghRequest.setWalkSpeedKmH(1); // No, I cannot walk very fast, 1 km/h. Problem?
        ghRequest.setProfileQuery(true);

        ghRequest.setLimitSolutions(1);
        GHResponse response1 = graphHopper.route(ghRequest);
        assertThat(response1.getHints().getInt("visited_nodes.sum", Integer.MAX_VALUE)).isLessThanOrEqualTo(142);
        ghRequest.setLimitSolutions(3);
        GHResponse response3 = graphHopper.route(ghRequest);
        assertThat(response3.getHints().getInt("visited_nodes.sum", Integer.MAX_VALUE)).isLessThanOrEqualTo(234);
        assertThat(response1.getAll().get(0).getTime()).isEqualTo(response3.getAll().get(0).getTime());
        ghRequest.setLimitSolutions(5);
        GHResponse response5 = graphHopper.route(ghRequest);
        assertThat(response5.getHints().getInt("visited_nodes.sum", Integer.MAX_VALUE)).isLessThanOrEqualTo(334);
        assertThat(response3.getAll().get(2).getTime()).isEqualTo(response5.getAll().get(2).getTime());
    }

    @Test
    public void testHighDisutilityOfWalking() {
        Request ghRequest = new Request(
                36.91311729030539, -116.76769495010377,
                36.91260259593356, -116.76149368286134
        );
        ghRequest.setEarliestDepartureTime(LocalDateTime.of(2007, 1, 1, 6, 40, 0).atZone(zoneId).toInstant());
        ghRequest.setWalkSpeedKmH(50); // Yes, I can walk very fast, 50 km/h. Problem?
        ghRequest.setBetaStreetTime(20); // But I dislike walking a lot.

        GHResponse response = graphHopper.route(ghRequest);

        ResponsePath walkSolution = response.getAll().stream().filter(p -> p.getLegs().size() == 1).findFirst().get();
        assertThat(walkSolution.getRouteWeight()).isEqualTo(legDuration(walkSolution.getLegs().get(0)).toMillis() * 20L);

        ResponsePath transitSolution = response.getAll().stream().filter(p -> p.getLegs().size() > 1).findFirst().get();
        Instant actualDepartureTime = departureTime(transitSolution.getLegs().get(0));
        assertThat(transitSolution.getRouteWeight()).isEqualTo(
                Duration.between(ghRequest.getEarliestDepartureTime(), actualDepartureTime).toMillis() +
                        legDuration(transitSolution.getLegs().get(0)).toMillis() * 20L +
                        legDuration(transitSolution.getLegs().get(1)).toMillis() +
                        legDuration(transitSolution.getLegs().get(2)).toMillis() * 20L
        );
    }

    @Test
    public void testSubnetworkRemoval() {
        Profile foot = graphHopperGtfs.getProfile("foot");
        BooleanEncodedValue footSub = graphHopperGtfs.getEncodingManager().getBooleanEncodedValue(Subnetwork.key(foot.getName()));

        // Go through all edges on removed foot subnetworks, and check that we can get to our destination station from there
        List<GHResponse> responses = new ArrayList<>();
        AllEdgesIterator edges = graphHopperGtfs.getBaseGraph().getAllEdges();
        while (edges.next()) {
            if (edges.get(footSub)) {
                Request ghRequest = new Request(Arrays.asList(
                        new GHPointLocation(new GHPoint(graphHopperGtfs.getBaseGraph().getNodeAccess().getLat(edges.getBaseNode()), graphHopperGtfs.getBaseGraph().getNodeAccess().getLon(edges.getBaseNode()))),
                        new GHStationLocation("EMSI")),
                        LocalDateTime.of(2007, 1, 1, 6, 40, 0).atZone(zoneId).toInstant());
                ghRequest.setWalkSpeedKmH(50); // Yes, I can walk very fast, 50 km/h. Problem?
                ghRequest.setBetaStreetTime(20); // But I dislike walking a lot.
                GHResponse response = graphHopper.route(ghRequest);
                responses.add(response);
            }
        }

        assumeThat(responses).isNotEmpty(); // There is a removed subnetwork -- otherwise there's nothing to check
        assertThat(responses).allMatch(r -> !r.getAll().isEmpty());
    }

    @Test
    public void testLineStringWhenWalking() {
        Request ghRequest = new Request(
                36.90662748004327, -116.76506702494832,
                36.90814220713894, -116.76219285567532
        );
        ghRequest.setEarliestDepartureTime(LocalDateTime.of(2007, 1, 1, 6, 40, 0).atZone(zoneId).toInstant());

        GHResponse response = graphHopper.route(ghRequest);
        Geometry routeGeometry = response.getAll().get(0).getPoints().toLineString(false);
        Geometry legGeometry = response.getAll().get(0).getLegs().get(0).geometry;
        assertThat(routeGeometry).isEqualTo(readWktLineString("LINESTRING (-116.765169 36.906693, -116.764614 36.907243, -116.763438 36.908382, -116.762615 36.907825, -116.762241 36.908175)"));
        assertThat(legGeometry).isEqualTo(readWktLineString("LINESTRING (-116.765169 36.906693, -116.764614 36.907243, -116.763438 36.908382, -116.762615 36.907825, -116.762241 36.908175)"));
    }

    private Duration legDuration(Trip.Leg leg) {
        return Duration.between(departureTime(leg), arrivalTime(leg));
    }

    private Duration routeDuration(ResponsePath route) {
        Trip.Leg firstLeg = route.getLegs().get(0);
        Trip.Leg lastLeg = route.getLegs().get(route.getLegs().size() - 1);
        return Duration.between(departureTime(firstLeg), arrivalTime(lastLeg));
    }

    private Instant departureTime(Trip.Leg firstLeg) {
        return Instant.ofEpochMilli(firstLeg.getDepartureTime().getTime());
    }

    private Instant arrivalTime(Trip.Leg leg) {
        return Instant.ofEpochMilli(leg.getArrivalTime().getTime());
    }

    private LineString readWktLineString(String wkt) {
        WKTReader wktReader = new WKTReader();
        LineString expectedGeometry = null;
        try {
            expectedGeometry = (LineString) wktReader.read(wkt);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return expectedGeometry;
    }

}
