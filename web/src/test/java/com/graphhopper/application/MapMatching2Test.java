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
package com.graphhopper.application;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.graphhopper.GraphHopper;
import com.graphhopper.config.LMProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.gpx.GpxConversions;
import com.graphhopper.jackson.Gpx;
import com.graphhopper.matching.EdgeMatch;
import com.graphhopper.matching.MapMatching;
import com.graphhopper.matching.MatchResult;
import com.graphhopper.matching.State;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.core.util.Helper;
import com.graphhopper.core.util.PMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import static com.graphhopper.application.MapMatchingTest.fetchStreets;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Peter Karich
 */
public class MapMatching2Test {
    private static final String GH_LOCATION = "../target/mapmatchingtest2-gh";
    private final XmlMapper xmlMapper = new XmlMapper();

    @BeforeEach
    @AfterEach
    public void clean() {
        Helper.removeDir(new File(GH_LOCATION));
    }

    @Test
    public void testIssue13() throws IOException {
        GraphHopper hopper = new GraphHopper();
        hopper.setOSMFile("../map-matching/files/map-issue13.osm.gz");
        hopper.setGraphHopperLocation(GH_LOCATION);
        hopper.setProfiles(new Profile("my_profile").setVehicle("car").setWeighting("fastest"));
        hopper.getLMPreparationHandler().setLMProfiles(new LMProfile("my_profile"));
        hopper.importOrLoad();

        MapMatching mapMatching = MapMatching.fromGraphHopper(hopper, new PMap().putObject("profile", "my_profile"));

        Gpx gpx = xmlMapper.readValue(getClass().getResourceAsStream("/issue-13.gpx"), Gpx.class);
        MatchResult mr = mapMatching.match(GpxConversions.getEntries(gpx.trk.get(0)));

        // make sure no virtual edges are returned
        int edgeCount = hopper.getBaseGraph().getAllEdges().length();
        for (EdgeMatch em : mr.getEdgeMatches()) {
            assertTrue(em.getEdgeState().getEdge() < edgeCount, "result contains virtual edges:" + em.getEdgeState().toString());
            validateEdgeMatch(em);
        }

        assertEquals(mr.getGpxEntriesLength(), mr.getMatchLength(), 2.5);
        assertEquals(28790, mr.getMatchMillis(), 50);
    }

    @Test
    public void testIssue70() throws IOException {
        GraphHopper hopper = new GraphHopper();
        hopper.setOSMFile("../map-matching/files/issue-70.osm.gz");
        hopper.setGraphHopperLocation(GH_LOCATION);
        hopper.setProfiles(new Profile("my_profile").setVehicle("car").setWeighting("fastest"));
        hopper.getLMPreparationHandler().setLMProfiles(new LMProfile("my_profile"));
        hopper.importOrLoad();

        MapMatching mapMatching = MapMatching.fromGraphHopper(hopper, new PMap().putObject("profile", "my_profile"));

        Gpx gpx = xmlMapper.readValue(getClass().getResourceAsStream("/issue-70.gpx"), Gpx.class);
        MatchResult mr = mapMatching.match(GpxConversions.getEntries(gpx.trk.get(0)));

        assertEquals(Arrays.asList("Милана Видака", "Бранка Радичевића", "Здравка Челара"), fetchStreets(mr.getEdgeMatches()));
        for (EdgeMatch edgeMatch : mr.getEdgeMatches()) {
            validateEdgeMatch(edgeMatch);
        }
    }

    @Test
    public void testIssue127() throws IOException {
        GraphHopper hopper = new GraphHopper();
        hopper.setOSMFile("../map-matching/files/map-issue13.osm.gz");
        hopper.setGraphHopperLocation(GH_LOCATION);
        hopper.setProfiles(new Profile("my_profile").setVehicle("car").setWeighting("fastest"));
        hopper.getLMPreparationHandler().setLMProfiles(new LMProfile("my_profile"));
        hopper.importOrLoad();

        MapMatching mapMatching = MapMatching.fromGraphHopper(hopper, new PMap().putObject("profile", "my_profile"));

        // query with two identical points
        Gpx gpx = xmlMapper.readValue(getClass().getResourceAsStream("/issue-127.gpx"), Gpx.class);
        MatchResult mr = mapMatching.match(GpxConversions.getEntries(gpx.trk.get(0)));

        // make sure no virtual edges are returned
        int edgeCount = hopper.getBaseGraph().getAllEdges().length();
        for (EdgeMatch em : mr.getEdgeMatches()) {
            assertTrue(em.getEdgeState().getEdge() < edgeCount, "result contains virtual edges:" + em.getEdgeState().toString());
            validateEdgeMatch(em);
        }

        assertEquals(0, mr.getMatchMillis(), 50);
    }

    private void validateEdgeMatch(EdgeMatch edgeMatch) {
        for (State state : edgeMatch.getStates()) {
            if (state.getSnap().getSnappedPosition() == Snap.Position.TOWER) {
                if (state.getSnap().getClosestNode() != edgeMatch.getEdgeState().getAdjNode()
                        && state.getSnap().getClosestNode() != edgeMatch.getEdgeState().getAdjNode()) {
                    fail();
                }
            } else {
                if (state.getSnap().getClosestEdge().getEdge() != edgeMatch.getEdgeState().getEdge()) {
                    fail();
                }
            }
        }
    }

}
