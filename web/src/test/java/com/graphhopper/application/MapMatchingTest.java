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
import com.graphhopper.util.*;
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

import static com.graphhopper.json.Statement.If;
import static com.graphhopper.json.Statement.Op.MULTIPLY;
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
        graphHopper.setProfiles(new Profile("my_profile").
                setCustomModel(new CustomModel().
                        addToPriority(If("road_access == DESTINATION", MULTIPLY, "0.1"))).
                setVehicle("car"));
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
        MapMatching mapMatching = MapMatching.fromGraphHopper(graphHopper, hints);
        ResponsePath route2 = graphHopper.route(new GHRequest(
                new GHPoint(51.358735, 12.360574),
                new GHPoint(51.358594, 12.360032))
                .setProfile("my_profile")).getBest();
        List<Observation> inputGPXEntries = createRandomGPXEntriesAlongRoute(route2);
        MatchResult mr = mapMatching.match(inputGPXEntries);

        // make sure no virtual edges are returned
        int edgeCount = graphHopper.getBaseGraph().getAllEdges().length();
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
        mapMatching = MapMatching.fromGraphHopper(graphHopper, hints);
        mapMatching.setMeasurementErrorSigma(20);
        mr = mapMatching.match(inputGPXEntries);

        assertEquals(route.getDistance(), mr.getMatchLength(), 0.5);
        // GraphHopper travel times aren't exactly additive
        assertThat(Math.abs(route.getTime() - mr.getMatchMillis()), is(lessThan(1000L)));
        assertEquals(208, mr.getEdgeMatches().size());
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testLongTrackWithLotsOfPoints(PMap hints) {
        MapMatching mapMatching = MapMatching.fromGraphHopper(graphHopper, hints);
        ResponsePath route = graphHopper.route(new GHRequest(
                new GHPoint(51.23, 12.18),
                new GHPoint(51.45, 12.59))
                .setProfile("my_profile")).getBest();
        List<Observation> inputGPXEntries = createRandomGPXEntriesAlongRoute(route);
        MatchResult mr = mapMatching.match(inputGPXEntries);

        assertEquals(route.getDistance(), mr.getMatchLength(), 2);
        // GraphHopper travel times aren't exactly additive
        assertThat(Math.abs(route.getTime() - mr.getMatchMillis()), is(lessThan(1000L)));
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testLongTrackWithTwoPoints(PMap hints) {
        MapMatching mapMatching = MapMatching.fromGraphHopper(graphHopper, hints);
        List<Observation> inputGPXEntries = Arrays.asList(
                new Observation(new GHPoint(51.23, 12.18)),
                new Observation(new GHPoint(51.45, 12.59)));
        MatchResult mr = mapMatching.match(inputGPXEntries);
        assertEquals(57553.0, mr.getMatchLength(), 1.0);
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testClosePoints(PMap hints) {
        MapMatching mapMatching = MapMatching.fromGraphHopper(graphHopper, hints);
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

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testTour3WithLongEdge(PMap hints) throws IOException {
        Gpx gpx = xmlMapper.readValue(getClass().getResourceAsStream("/tour3-with-long-edge.gpx"), Gpx.class);
        MapMatching mapMatching = MapMatching.fromGraphHopper(graphHopper, hints);
        mapMatching.setMeasurementErrorSigma(20);
        MatchResult mr = mapMatching.match(GpxConversions.getEntries(gpx.trk.get(0)));
        assertEquals(Arrays.asList("Marbachstraße", "Weinligstraße", "Fechnerstraße"), fetchStreets(mr.getEdgeMatches()));
        assertEquals(mr.getGpxEntriesLength(), mr.getMatchLength(), 11); // TODO: this should be around 300m according to Google ... need to check
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testSimplification(PMap hints) throws IOException {
        Gpx gpx = xmlMapper.readValue(getClass().getResourceAsStream("/tour3-with-long-edge.gpx"), Gpx.class);
        MapMatching mapMatching = MapMatching.fromGraphHopper(graphHopper, hints);
        mapMatching.setMeasurementErrorSigma(20);
        List<Observation> observations = GpxConversions.getEntries(gpx.trk.get(0));
        // Warning, this has to be calculated before filtering, because (of course) observations
        // are mutable and are mutated.
        double expectedLinearDistance = linearDistance(observations);

        // This is the testee
        List<Observation> filteredObservations = mapMatching.filterObservations(observations);

        // Make sure something is actually filtered, i.e. filtered size is smaller, otherwise we are not
        // testing anything.
        assertEquals(7, observations.size());
        assertEquals(5, filteredObservations.size());
        assertEquals(expectedLinearDistance, linearDistance(filteredObservations));
    }

    private double linearDistance(List<Observation> observations) {
        DistanceCalc distanceCalc = new DistancePlaneProjection();
        double result = 0.0;
        for (int i = 1; i < observations.size(); i++) {
            Observation observation = observations.get(i);
            Observation prevObservation = observations.get(i - 1);
            result += distanceCalc.calcDist(
                    prevObservation.getPoint().getLat(), prevObservation.getPoint().getLon(),
                    observation.getPoint().getLat(), observation.getPoint().getLon());
            result += observation.getAccumulatedLinearDistanceToPrevious();
        }
        return result;
    }

    /**
     * This test is to check that loops are maintained. GPX input:
     * https://graphhopper.com/maps/?point=51.343657%2C12.360708&point=51.344982%2C12.364066&point=51.344841%2C12.361223&point=51.342781%2C12.361867&layer=Lyrk
     */
    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testLoop(PMap hints) throws IOException {
        MapMatching mapMatching = MapMatching.fromGraphHopper(graphHopper, hints);

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
        MapMatching mapMatching = MapMatching.fromGraphHopper(graphHopper, hints);
        // TODO smaller sigma like 40m leads to U-turn at Tschaikowskistraße
        mapMatching.setMeasurementErrorSigma(50);
        Gpx gpx = xmlMapper.readValue(getClass().getResourceAsStream("/tour-with-loop.gpx"), Gpx.class);
        MatchResult mr = mapMatching.match(GpxConversions.getEntries(gpx.trk.get(0)));
        assertEquals(Arrays.asList("Jahnallee", "Funkenburgstraße",
                "Gustav-Adolf-Straße", "Tschaikowskistraße", "Jahnallee",
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

        MapMatching mapMatching = MapMatching.fromGraphHopper(graphHopper, hints);
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
