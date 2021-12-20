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
import com.graphhopper.GHRequest;
import com.graphhopper.GraphHopper;
import com.graphhopper.ResponsePath;
import com.graphhopper.config.LMProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.gpx.GpxConversions;
import com.graphhopper.jackson.Gpx;
import com.graphhopper.matching.EdgeMatch;
import com.graphhopper.matching.MapMatching;
import com.graphhopper.matching.MatchResult;
import com.graphhopper.matching.Observation;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PMap;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.shapes.GHPoint;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Peter Karich
 * @author kodonnell
 */
public class MapMatchingTest {

    private static final String GH_LOCATION = "../target/mapmatchingtest-ch";
    private final XmlMapper xmlMapper = new XmlMapper();

    private static GraphHopper graphHopper;

    @BeforeAll
    public static void setup() {
        Helper.removeDir(new File(GH_LOCATION));
        graphHopper = new GraphHopper();
        graphHopper.setOSMFile("../map-matching/files/leipzig_germany.osm.pbf");
        graphHopper.setGraphHopperLocation(GH_LOCATION);
        graphHopper.setProfiles(new Profile("my_profile").setVehicle("car").setWeighting("fastest"));
        graphHopper.getLMPreparationHandler().setLMProfiles(new LMProfile("my_profile"));
        graphHopper.importOrLoad();
    }

    @AfterAll
    public static void after() {
        Helper.removeDir(new File(GH_LOCATION));
        graphHopper = null;
    }

    private static class FixtureProvider implements ArgumentsProvider {
        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
            return Stream.of(
                    new PMap().putObject(Parameters.Landmark.DISABLE, true),
                    new PMap().putObject(Parameters.Landmark.DISABLE, false)

            )
                    .map(hints -> hints.putObject("profile", "my_profile"))
                    .map(Arguments::of);
        }
    }

    /**
     * TODO: split this test up into smaller units with better names?
     */
    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testDoWork(PMap hints) {
        MapMatching mapMatching = new MapMatching(graphHopper, hints);
        ResponsePath route2 = graphHopper.route(new GHRequest(
                new GHPoint(51.358735, 12.360574),
                new GHPoint(51.358594, 12.360032))
                .setProfile("my_profile")).getBest();
        List<Observation> inputGPXEntries = createRandomGPXEntriesAlongRoute(route2);
        MatchResult mr = mapMatching.match(inputGPXEntries);

        // make sure no virtual edges are returned
        int edgeCount = graphHopper.getGraphHopperStorage().getAllEdges().length();
        for (EdgeMatch em : mr.getEdgeMatches()) {
            assertTrue(em.getEdgeState().getEdge() < edgeCount, "result contains virtual edges:" + em.getEdgeState().toString());
        }

        // create street names
        assertEquals(Arrays.asList("Platnerstraße"),
                fetchStreets(mr.getEdgeMatches()));
        assertEquals(mr.getGpxEntriesLength(), mr.getMatchLength(), 1.5);

        ResponsePath route1 = graphHopper.route(new GHRequest(
                new GHPoint(51.33099, 12.380267),
                new GHPoint(51.330531, 12.380396))
                .setProfile("my_profile")).getBest();
        inputGPXEntries = createRandomGPXEntriesAlongRoute(route1);
        mapMatching.setMeasurementErrorSigma(5);
        mr = mapMatching.match(inputGPXEntries);

        assertEquals(Arrays.asList("Windmühlenstraße", "Bayrischer Platz"), fetchStreets(mr.getEdgeMatches()));
        assertEquals(mr.getGpxEntriesLength(), mr.getMatchLength(), .1);

        ResponsePath route = graphHopper.route(new GHRequest(
                new GHPoint(51.377781, 12.338333),
                new GHPoint(51.323317, 12.387085))
                .setProfile("my_profile")).getBest();
        inputGPXEntries = createRandomGPXEntriesAlongRoute(route);
        mapMatching = new MapMatching(graphHopper, hints);
        mapMatching.setMeasurementErrorSigma(20);
        mr = mapMatching.match(inputGPXEntries);

        assertEquals(route.getDistance(), mr.getMatchLength(), 0.5);
        // GraphHopper travel times aren't exactly additive
        assertThat(Math.abs(route.getTime() - mr.getMatchMillis()), is(lessThan(1000L)));
        assertEquals(142, mr.getEdgeMatches().size());
    }

    /**
     * This test is to check behavior over large separated routes: it should
     * work if the user sets the maxVisitedNodes large enough. Input path:
     * https://graphhopper.com/maps/?point=51.23%2C12.18&point=51.45%2C12.59&layer=Lyrk
     * <p>
     * Update: Seems to me that this test only tests a long route, not one with
     * distant input points. createRandomGPXEntries currently creates very close input points.
     * The length of the route doesn't seem to matter.
     */
    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testDistantPoints(PMap hints) {
        // OK with 1000 visited nodes:
        MapMatching mapMatching = new MapMatching(graphHopper, hints);
        ResponsePath route = graphHopper.route(new GHRequest(
                new GHPoint(51.23, 12.18),
                new GHPoint(51.45, 12.59))
                .setProfile("my_profile")).getBest();
        List<Observation> inputGPXEntries = createRandomGPXEntriesAlongRoute(route);
        MatchResult mr = mapMatching.match(inputGPXEntries);

        assertEquals(route.getDistance(), mr.getMatchLength(), 2);
        // GraphHopper travel times aren't exactly additive
        assertThat(Math.abs(route.getTime() - mr.getMatchMillis()), is(lessThan(1000L)));

        // not OK when we only allow a small number of visited nodes:
        PMap opts = new PMap(hints).putObject(Parameters.Routing.MAX_VISITED_NODES, 1);
        mapMatching = new MapMatching(graphHopper, opts);
        try {
            mr = mapMatching.match(inputGPXEntries);
            fail("Expected sequence to be broken due to maxVisitedNodes being too small");
        } catch (RuntimeException e) {
            assertTrue(e.getMessage().startsWith("Sequence is broken for submitted track"));
        }
    }

    /**
     * This test is to check behavior over short tracks. GPX input:
     * https://graphhopper.com/maps/?point=51.342422%2C12.3613358&point=51.3423281%2C12.3613358&layer=Lyrk
     */
    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testClosePoints(PMap hints) {
        MapMatching mapMatching = new MapMatching(graphHopper, hints);
        ResponsePath route = graphHopper.route(new GHRequest(
                new GHPoint(51.342422, 12.3613358),
                new GHPoint(51.342328, 12.3613358))
                .setProfile("my_profile")).getBest();
        List<Observation> inputGPXEntries = createRandomGPXEntriesAlongRoute(route);
        MatchResult mr = mapMatching.match(inputGPXEntries);

        assertFalse(mr.getEdgeMatches().isEmpty());
        assertEquals(3, mr.getMatchLength(), 1);
        // GraphHopper travel times aren't exactly additive
        assertThat(Math.abs(route.getTime() - mr.getMatchMillis()), is(lessThan(1000L)));
    }

    /**
     * This test is to check what happens when two GPX entries are on one edge
     * which is longer than 'separatedSearchDistance' - which is always 66m. GPX
     * input:
     * https://graphhopper.com/maps/?point=51.359723%2C12.360108&point=51.358748%2C12.358798&point=51.358001%2C12.357597&point=51.358709%2C12.356511&layer=Lyrk
     */
    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testSmallSeparatedSearchDistance(PMap hints) throws IOException {
        Gpx gpx = xmlMapper.readValue(getClass().getResourceAsStream("/tour3-with-long-edge.gpx"), Gpx.class);
        MapMatching mapMatching = new MapMatching(graphHopper, hints);
        mapMatching.setMeasurementErrorSigma(20);
        MatchResult mr = mapMatching.match(GpxConversions.getEntries(gpx.trk.get(0)));
        assertEquals(Arrays.asList("Weinligstraße", "Fechnerstraße"), fetchStreets(mr.getEdgeMatches()));
        assertEquals(mr.getGpxEntriesLength(), mr.getMatchLength(), 11); // TODO: this should be around 300m according to Google ... need to check
    }

    /**
     * This test is to check that loops are maintained. GPX input:
     * https://graphhopper.com/maps/?point=51.343657%2C12.360708&point=51.344982%2C12.364066&point=51.344841%2C12.361223&point=51.342781%2C12.361867&layer=Lyrk
     */
    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testLoop(PMap hints) throws IOException {
        MapMatching mapMatching = new MapMatching(graphHopper, hints);

        // Need to reduce GPS accuracy because too many GPX are filtered out otherwise.
        mapMatching.setMeasurementErrorSigma(40);

        Gpx gpx = xmlMapper.readValue(getClass().getResourceAsStream("/tour2-with-loop.gpx"), Gpx.class);
        MatchResult mr = mapMatching.match(GpxConversions.getEntries(gpx.trk.get(0)));
        assertEquals(
                Arrays.asList("Gustav-Adolf-Straße", "Leibnizstraße", "Hinrichsenstraße", "Tschaikowskistraße"),
                fetchStreets(mr.getEdgeMatches()));
        assertEquals(mr.getGpxEntriesLength(), mr.getMatchLength(), 5);
    }

    /**
     * This test is to check that loops are maintained. GPX input:
     * https://graphhopper.com/maps/?point=51.342439%2C12.361615&point=51.343719%2C12.362784&point=51.343933%2C12.361781&point=51.342325%2C12.362607&layer=Lyrk
     */
    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testLoop2(PMap hints) throws IOException {
        MapMatching mapMatching = new MapMatching(graphHopper, hints);
        // TODO smaller sigma like 40m leads to U-turn at Tschaikowskistraße
        mapMatching.setMeasurementErrorSigma(50);
        Gpx gpx = xmlMapper.readValue(getClass().getResourceAsStream("/tour-with-loop.gpx"), Gpx.class);
        MatchResult mr = mapMatching.match(GpxConversions.getEntries(gpx.trk.get(0)));
        assertEquals(Arrays.asList("Jahnallee, B 87, B 181", "Funkenburgstraße",
                "Gustav-Adolf-Straße", "Tschaikowskistraße", "Jahnallee, B 87, B 181",
                "Lessingstraße"), fetchStreets(mr.getEdgeMatches()));
    }

    /**
     * This test is to check that U-turns are avoided when it's just measurement
     * error, though do occur when a point goes up a road further than the
     * measurement error. GPX input:
     * https://graphhopper.com/maps/?point=51.343618%2C12.360772&point=51.34401%2C12.361776&point=51.343977%2C12.362886&point=51.344734%2C12.36236&point=51.345233%2C12.362055&layer=Lyrk
     */
    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testUTurns(PMap hints) throws IOException {
        hints = new PMap(hints)
                // Reduce penalty to allow U-turns
                .putObject(Parameters.Routing.HEADING_PENALTY, 50);

        MapMatching mapMatching = new MapMatching(graphHopper, hints);
        Gpx gpx = xmlMapper.readValue(getClass().getResourceAsStream("/tour4-with-uturn.gpx"), Gpx.class);

        // with large measurement error, we expect no U-turn
        mapMatching.setMeasurementErrorSigma(50);
        MatchResult mr = mapMatching.match(GpxConversions.getEntries(gpx.trk.get(0)));
        assertEquals(Arrays.asList("Gustav-Adolf-Straße", "Funkenburgstraße"), fetchStreets(mr.getEdgeMatches()));

        // with small measurement error, we expect the U-turn
        mapMatching.setMeasurementErrorSigma(10);
        mr = mapMatching.match(GpxConversions.getEntries(gpx.trk.get(0)));
        assertEquals(Arrays.asList("Gustav-Adolf-Straße", "Funkenburgstraße"), fetchStreets(mr.getEdgeMatches()));
    }

    static List<String> fetchStreets(List<EdgeMatch> emList) {
        List<String> list = new ArrayList<>();
        int prevNode = -1;
        List<String> errors = new ArrayList<>();
        for (EdgeMatch em : emList) {
            String str = em.getEdgeState().getName();// + ":" + em.getEdgeState().getBaseNode() +
            // "->" + em.getEdgeState().getAdjNode();
            if (list.size() == 0 || !list.get(list.size() - 1).equals(str))
                list.add(str);
            if (prevNode >= 0) {
                if (em.getEdgeState().getBaseNode() != prevNode) {
                    errors.add(str);
                }
            }
            prevNode = em.getEdgeState().getAdjNode();
        }

        if (!errors.isEmpty()) {
            throw new IllegalStateException("Errors:" + errors);
        }
        return list;
    }

    /**
     * This method does not in fact create random observations. It creates observations at nodes on a route.
     * This method _should_ be replaced by one that creates random observations along a route,
     * with random noise and random sampling.
     */
    private List<Observation> createRandomGPXEntriesAlongRoute(ResponsePath route) {
        return GpxConversions.createGPXList(route.getInstructions()).stream()
                .map(gpx -> new Observation(gpx.getPoint())).collect(Collectors.toList());
    }

}
