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

import com.carrotsearch.hppc.IntHashSet;
import com.graphhopper.reader.gtfs.*;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.Helper;
import com.graphhopper.util.TranslationMap;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;

import static com.graphhopper.reader.gtfs.GtfsHelper.time;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class AnotherAgencyIT {

    private static final String GRAPH_LOC = "target/AnotherAgencyIT";
    private static PtRouteResource ptRouteResource;
    private static final ZoneId zoneId = ZoneId.of("America/Los_Angeles");
    private static GraphHopperGtfs graphHopperGtfs;

    @BeforeClass
    public static void init() {
        CmdArgs cmdArgs = new CmdArgs();
        cmdArgs.put("graph.flag_encoders", "car,foot");
        cmdArgs.put("graph.location", GRAPH_LOC);
        cmdArgs.put("datareader.file", "files/beatty.osm");
        cmdArgs.put("gtfs.file", "files/sample-feed.zip,files/another-sample-feed.zip");
        Helper.removeDir(new File(GRAPH_LOC));
        graphHopperGtfs = new GraphHopperGtfs(cmdArgs);
        graphHopperGtfs.init(cmdArgs);
        graphHopperGtfs.importOrLoad();
        ptRouteResource = PtRouteResource.createFactory(new TranslationMap().doImport(), graphHopperGtfs, graphHopperGtfs.getLocationIndex(), graphHopperGtfs.getGtfsStorage())
                .createWithoutRealtimeFeed();
    }

    @AfterClass
    public static void close() {
        graphHopperGtfs.close();
    }

    @Test
    public void testRoute1() {
        Request ghRequest = new Request(
                Arrays.asList(
                        new GHStationLocation("JUSTICE_COURT"),
                        new GHStationLocation("MUSEUM")
                ),
                LocalDateTime.of(2007,1,1,8,30,0).atZone(zoneId).toInstant()
        );
        ghRequest.setIgnoreTransfers(true);
        ghRequest.setWalkSpeedKmH(0.005); // Prevent walk solution
        GHResponse route = ptRouteResource.route(ghRequest);

        assertFalse(route.hasErrors());
        assertEquals(1, route.getAll().size());
        PathWrapper transitSolution = route.getBest();
        assertEquals("Expected total travel time == scheduled travel time + wait time", time(1, 30), transitSolution.getTime(), 0.1);
    }

    @Test
    public void noTransferEdgeBetweenFeeds() {
        // Make sure we don't accidentally create transfer edges between trips from different feeds.
        // The implementation doesn't allow it, and would produce subsequent failures, because
        // feed-specific things are encoded in edge attributes along routes.
        // We will model such transfers by going through the walk network.
        PtEncodedValues ptEncodedValues = PtEncodedValues.fromEncodingManager(graphHopperGtfs.getEncodingManager());
        AllEdgesIterator allEdges = graphHopperGtfs.getGraphHopperStorage().getAllEdges();
        while (allEdges.next()) {
            GtfsStorage.EdgeType edgeType = allEdges.get(ptEncodedValues.getTypeEnc());
            if (edgeType == GtfsStorage.EdgeType.TRANSFER) {
                IntHashSet feedIdsReachableOnPTNetworkFrom = findFeedIdsReachableOnPTNetworkFrom(allEdges.getAdjNode());
                assertEquals(1, feedIdsReachableOnPTNetworkFrom.size());
            }
        }
    }

    private IntHashSet findFeedIdsReachableOnPTNetworkFrom(int adjNode) {
        // TODO: Clean up those routers, so that tests like this are way easier to implement
        PtEncodedValues ptEncodedValues = PtEncodedValues.fromEncodingManager(graphHopperGtfs.getEncodingManager());
        GraphExplorer graphExplorer = new GraphExplorer(
                graphHopperGtfs.getGraphHopperStorage(),
                new FastestWeighting(graphHopperGtfs.getEncodingManager().getEncoder("foot")),
                ptEncodedValues,
                graphHopperGtfs.getGtfsStorage(),
                RealtimeFeed.empty(graphHopperGtfs.getGtfsStorage()),
                false,
                false,
                5.0,
                true);
        IntHashSet seenIds = new IntHashSet();
        MultiCriteriaLabelSetting router = new MultiCriteriaLabelSetting(
                graphExplorer,
                ptEncodedValues,
                false,
                true,
                false,
                false,
                Integer.MAX_VALUE,
                Collections.emptyList()
        );
        router.calcLabels(adjNode, Instant.now(), 0)
        .forEach(l -> {
            if (l.parent == null) return;
            EdgeIteratorState edgeIteratorState = graphHopperGtfs.getGraphHopperStorage().getEdgeIteratorState(l.edge, l.adjNode);
            Label.EdgeLabel edgeLabel = Label.getEdgeLabel(edgeIteratorState, ptEncodedValues);
            if (edgeLabel.edgeType == GtfsStorage.EdgeType.LEAVE_TIME_EXPANDED_NETWORK) {
                seenIds.add(edgeLabel.timeZoneId);
            }
        });
        graphExplorer = new GraphExplorer(
                graphHopperGtfs.getGraphHopperStorage(),
                new FastestWeighting(graphHopperGtfs.getEncodingManager().getEncoder("foot")),
                ptEncodedValues,
                graphHopperGtfs.getGtfsStorage(),
                RealtimeFeed.empty(graphHopperGtfs.getGtfsStorage()),
                true,
                false,
                5.0,
                true);
        router = new MultiCriteriaLabelSetting(
                graphExplorer,
                ptEncodedValues,
                true,
                true,
                false,
                false,
                Integer.MAX_VALUE,
                Collections.emptyList()
        );
        router.calcLabels(adjNode, Instant.now(), 0)
                .forEach(l -> {
                    if (l.parent == null) return;
                    EdgeIteratorState edgeIteratorState = graphHopperGtfs.getGraphHopperStorage().getEdgeIteratorState(l.edge, l.parent.adjNode);
                    Label.EdgeLabel edgeLabel = Label.getEdgeLabel(edgeIteratorState, ptEncodedValues);
                    if (edgeLabel.edgeType == GtfsStorage.EdgeType.ENTER_TIME_EXPANDED_NETWORK) {
                        seenIds.add(edgeLabel.timeZoneId);
                    }
                });

        return seenIds;
    }

}
