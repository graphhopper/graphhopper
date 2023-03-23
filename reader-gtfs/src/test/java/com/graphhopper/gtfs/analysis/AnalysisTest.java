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

package com.graphhopper.gtfs.analysis;

import com.conveyal.gtfs.GTFSFeed;
import com.conveyal.gtfs.model.Frequency;
import com.conveyal.gtfs.model.Trip;
import com.google.transit.realtime.GtfsRealtime;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.config.Profile;
import com.graphhopper.gtfs.GraphHopperGtfs;
import com.graphhopper.gtfs.GtfsStorage;
import com.graphhopper.gtfs.PtGraph;
import com.graphhopper.gtfs.RealtimeFeed;
import com.graphhopper.util.Helper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.*;

import static com.conveyal.gtfs.model.Entity.Writer.convertToGtfsTime;
import static org.assertj.core.api.Assertions.assertThat;

public class AnalysisTest {

    private static final String GRAPH_LOC = "target/AnotherAgencyIT";
    private static GraphHopperGtfs graphHopperGtfs;

    @BeforeAll
    public static void init() {
        GraphHopperConfig ghConfig = new GraphHopperConfig();
        ghConfig.putObject("graph.location", GRAPH_LOC);
        ghConfig.putObject("datareader.file", "files/beatty.osm");
        ghConfig.putObject("gtfs.file", "files/sample-feed,files/another-sample-feed");
        ghConfig.putObject("import.osm.ignored_highways", "");
        ghConfig.setProfiles(Arrays.asList(
                new Profile("foot").setVehicle("foot").setWeighting("fastest"),
                new Profile("car").setVehicle("car").setWeighting("fastest")));
        Helper.removeDir(new File(GRAPH_LOC));
        graphHopperGtfs = new GraphHopperGtfs(ghConfig);
        graphHopperGtfs.init(ghConfig);
        graphHopperGtfs.importOrLoad();
    }

    @AfterAll
    public static void close() {
        graphHopperGtfs.close();
    }

    @Test
    public void testStronglyConnectedComponentsOfStopGraph() {
        PtGraph ptGraph = graphHopperGtfs.getPtGraph();
        List<List<GtfsStorage.FeedIdWithStopId>> stronglyConnectedComponentsOfStopGraph = Analysis.findStronglyConnectedComponentsOfStopGraph(ptGraph);
        List<GtfsStorage.FeedIdWithStopId> largestComponent = stronglyConnectedComponentsOfStopGraph.get(0);

        assertThat(largestComponent)
                .extracting("stopId")
                .containsExactlyInAnyOrder("EMSI", "DADAN", "NADAV", "NANAA", "STAGECOACH", "AMV", "FUR_CREEK_RES", "BULLFROG", "BEATTY_AIRPORT", "AIRPORT");

        List<List<GtfsStorage.FeedIdWithStopId>> singleElementComponents = stronglyConnectedComponentsOfStopGraph.subList(1, 4);
        assertThat(singleElementComponents.stream().map(it -> it.get(0)))
                .extracting("stopId")
                .containsExactlyInAnyOrder("JUSTICE_COURT", "MUSEUM", "NEXT_TO_MUSEUM");
    }

    @Test
    public void testComputeTransfers() {
        for (Map.Entry<String, GTFSFeed> e : graphHopperGtfs.getGtfsStorage().getGtfsFeeds().entrySet()) {
            GTFSFeed feed = e.getValue();
            for (Trip trip : feed.trips.values()) {
                Collection<Frequency> frequencies = feed.getFrequencies(trip.trip_id);
                List<GtfsRealtime.TripDescriptor> actualTrips = new ArrayList<>();
                GtfsRealtime.TripDescriptor.Builder builder = GtfsRealtime.TripDescriptor.newBuilder().setTripId(trip.trip_id).setRouteId(trip.route_id);
                if (frequencies.isEmpty()) {
                    actualTrips.add(builder.build());
                } else {
                    for (Frequency frequency : frequencies) {
                        for (int time = frequency.start_time; time < frequency.end_time; time += frequency.headway_secs) {
                            actualTrips.add(builder.setStartTime(convertToGtfsTime(time)).build());
                        }
                    }
                }
                for (GtfsRealtime.TripDescriptor tripDescriptor : actualTrips) {
                    Map<Trips.TripAtStopTime, Collection<Trips.TripAtStopTime>> tripTransfers = new HashMap<>();
                    int[] alightEdgesForTrip = RealtimeFeed.findAlightEdgesForTrip(graphHopperGtfs.getGtfsStorage(), e.getKey(), e.getValue(), RealtimeFeed.normalize(tripDescriptor));
                    for (int edge : alightEdgesForTrip) {
                        if (edge == -1)
                            continue;
                        PtGraph.PtEdge ptEdge = graphHopperGtfs.getPtGraph().edge(edge);
                        ArrayList<Trips.TripAtStopTime> transferTrips = Trips.listTransfers(graphHopperGtfs.getPtGraph(), ptEdge.getAdjNode());
                        tripTransfers.put(new Trips.TripAtStopTime(e.getKey(), ptEdge.getAttrs().tripDescriptor, ptEdge.getAttrs().stop_sequence), transferTrips);
                    }
                    Trips.computeReducedTransfers(tripTransfers, e, feed, tripDescriptor);
                }
            }
        }
    }

}
