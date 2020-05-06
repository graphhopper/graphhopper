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
package com.graphhopper.matching;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.graphhopper.GraphHopper;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.matching.gpx.Gpx;
import com.graphhopper.reader.osm.GraphHopperOSM;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PMap;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import static com.graphhopper.matching.MapMatchingTest.fetchStreets;
import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
public class MapMatching2Test {
    private static final String GH_LOCATION = "../target/mapmatchingtest2-gh";
    private XmlMapper xmlMapper = new XmlMapper();

    @Before
    @After
    public void clean() {
        Helper.removeDir(new File(GH_LOCATION));
    }

    @Test
    public void testIssue13() throws IOException {
        CarFlagEncoder encoder = new CarFlagEncoder();
        GraphHopper hopper = new GraphHopperOSM();
        hopper.setDataReaderFile("../map-data/map-issue13.osm.gz");
        hopper.setGraphHopperLocation(GH_LOCATION);
        hopper.setEncodingManager(EncodingManager.create(encoder));
        hopper.setProfiles(new Profile("my_profile").setVehicle("car").setWeighting("fastest"));
        hopper.getCHPreparationHandler().setCHProfiles(new CHProfile("my_profile"));
        hopper.getCHPreparationHandler().setDisablingAllowed(true);
        hopper.importOrLoad();

        MapMatching mapMatching = new MapMatching(hopper, new PMap().putObject("profile", "my_profile"));

        Gpx gpx = xmlMapper.readValue(getClass().getResourceAsStream("/issue-13.gpx"), Gpx.class);
        MatchResult mr = mapMatching.doWork(gpx.trk.get(0).getEntries());

        // make sure no virtual edges are returned
        int edgeCount = hopper.getGraphHopperStorage().getAllEdges().length();
        for (EdgeMatch em : mr.getEdgeMatches()) {
            assertTrue("result contains virtual edges:" + em.getEdgeState().toString(),
                    em.getEdgeState().getEdge() < edgeCount);
            validateEdgeMatch(em);
        }

        // create street names
        assertEquals(Arrays.asList("", "", "", "", "", ""),
                fetchStreets(mr.getEdgeMatches()));
        assertEquals(mr.getGpxEntriesLength(), mr.getMatchLength(), 2.5);
        assertEquals(28790, mr.getMatchMillis(), 50);
    }

    @Test
    public void testIssue70() throws IOException {
        CarFlagEncoder encoder = new CarFlagEncoder();
        GraphHopper hopper = new GraphHopperOSM();
        hopper.setDataReaderFile("../map-data/issue-70.osm.gz");
        hopper.setGraphHopperLocation(GH_LOCATION);
        hopper.setEncodingManager(EncodingManager.create(encoder));
        hopper.setProfiles(new Profile("my_profile").setVehicle("car").setWeighting("fastest"));
        hopper.getCHPreparationHandler().setCHProfiles(new CHProfile("my_profile"));
        hopper.getCHPreparationHandler().setDisablingAllowed(true);
        hopper.importOrLoad();

        MapMatching mapMatching = new MapMatching(hopper, new PMap().putObject("profile", "my_profile"));

        Gpx gpx = xmlMapper.readValue(getClass().getResourceAsStream("/issue-70.gpx"), Gpx.class);
        MatchResult mr = mapMatching.doWork(gpx.trk.get(0).getEntries());

        assertEquals(Arrays.asList("Милана Видака", "Милана Видака", "Милана Видака",
                "Бранка Радичевића", "Бранка Радичевића", "Здравка Челара"),
                fetchStreets(mr.getEdgeMatches()));
        // TODO: length/time

        for (EdgeMatch edgeMatch : mr.getEdgeMatches()) {
            validateEdgeMatch(edgeMatch);
        }
    }

    @Test
    public void testIssue127() throws IOException {
        CarFlagEncoder encoder = new CarFlagEncoder();
        GraphHopper hopper = new GraphHopperOSM();
        hopper.setDataReaderFile("../map-data/map-issue13.osm.gz");
        hopper.setGraphHopperLocation(GH_LOCATION);
        hopper.setEncodingManager(EncodingManager.create(encoder));
        hopper.setProfiles(new Profile("my_profile").setVehicle("car").setWeighting("fastest"));
        hopper.getCHPreparationHandler().setCHProfiles(new CHProfile("my_profile"));
        hopper.getCHPreparationHandler().setDisablingAllowed(true);
        hopper.importOrLoad();

        MapMatching mapMatching = new MapMatching(hopper, new PMap().putObject("profile", "my_profile"));

        // query with two identical points
        Gpx gpx = xmlMapper.readValue(getClass().getResourceAsStream("/issue-127.gpx"), Gpx.class);
        MatchResult mr = mapMatching.doWork(gpx.trk.get(0).getEntries());

        // make sure no virtual edges are returned
        int edgeCount = hopper.getGraphHopperStorage().getAllEdges().length();
        for (EdgeMatch em : mr.getEdgeMatches()) {
            assertTrue("result contains virtual edges:" + em.getEdgeState().toString(),
                    em.getEdgeState().getEdge() < edgeCount);
            validateEdgeMatch(em);
        }

        assertEquals(0, mr.getMatchMillis(), 50);
    }

    private void validateEdgeMatch(EdgeMatch edgeMatch) {
        for (State state : edgeMatch.getStates()) {
            if (state.getQueryResult().getSnappedPosition() == QueryResult.Position.TOWER) {
                if (state.getQueryResult().getClosestNode() != edgeMatch.getEdgeState().getAdjNode()
                        && state.getQueryResult().getClosestNode() != edgeMatch.getEdgeState().getAdjNode()) {
                    fail();
                }
            } else {
                if (state.getQueryResult().getClosestEdge().getEdge() != edgeMatch.getEdgeState().getEdge()) {
                    fail();
                }
            }
        }
    }

}
