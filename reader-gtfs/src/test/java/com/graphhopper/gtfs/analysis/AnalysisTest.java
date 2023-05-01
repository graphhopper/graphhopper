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
import com.conveyal.gtfs.model.StopTime;
import com.google.transit.realtime.GtfsRealtime;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.config.Profile;
import com.graphhopper.gtfs.GraphHopperGtfs;
import com.graphhopper.gtfs.GtfsStorage;
import com.graphhopper.gtfs.PtGraph;
import com.graphhopper.util.Helper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
        GtfsStorage gtfsStorage = graphHopperGtfs.getGtfsStorage();
        Trips.TripAtStopTime origin = new Trips.TripAtStopTime("gtfs_1", GtfsRealtime.TripDescriptor.newBuilder().setTripId("MUSEUM1").setRouteId("COURT2MUSEUM").build(), 2);
        GTFSFeed gtfsFeed = graphHopperGtfs.getGtfsStorage().getGtfsFeeds().get("gtfs_1");
        Trips tripTransfers = new Trips(gtfsStorage);
        Map<Trips.TripAtStopTime, Collection<Trips.TripAtStopTime>> reducedTripTransfers = tripTransfers.findTripTransfers(origin.tripDescriptor, "gtfs_1", LocalDate.parse("2023-03-26"));
        Collection<Trips.TripAtStopTime> destinations = reducedTripTransfers.get(origin);

    }

    private static StopTime findStoptime(Iterable<StopTime> stopTimes, Trips.TripAtStopTime destination) {
        for (StopTime stopTime : stopTimes) {
            if (stopTime.stop_sequence == destination.stop_sequence)
                return stopTime;
        }
        return null;
    }

    @Test
    public void testSerialize() throws IOException, ClassNotFoundException {
        Trips.TripAtStopTime origin = new Trips.TripAtStopTime("gtfs_1", GtfsRealtime.TripDescriptor.newBuilder().setTripId("MUSEUM1").setRouteId("COURT2MUSEUM").build(), 2);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(out);
        objectOutputStream.writeObject(origin);
        objectOutputStream.close();
        byte[] bytes = out.toByteArray();

        ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(bytes));
        Trips.TripAtStopTime tripAtStopTime = (Trips.TripAtStopTime) objectInputStream.readObject();

        assertEquals(origin, tripAtStopTime);
    }

}
