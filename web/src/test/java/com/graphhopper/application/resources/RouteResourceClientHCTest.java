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
package com.graphhopper.application.resources;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.ResponsePath;
import com.graphhopper.api.GraphHopperWeb;
import com.graphhopper.application.GraphHopperApplication;
import com.graphhopper.application.GraphHopperServerConfiguration;
import com.graphhopper.application.util.GraphHopperServerTestConfiguration;
import com.graphhopper.application.util.TestUtils;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.json.Statement;
import com.graphhopper.routing.weighting.custom.CustomProfile;
import com.graphhopper.util.*;
import com.graphhopper.util.details.PathDetail;
import com.graphhopper.util.exceptions.PointNotFoundException;
import com.graphhopper.util.exceptions.PointOutOfBoundsException;
import com.graphhopper.util.shapes.GHPoint;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.File;
import java.util.*;

import static com.graphhopper.util.Instruction.FINISH;
import static com.graphhopper.util.Instruction.REACHED_VIA;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Peter Karich
 */
@ExtendWith(DropwizardExtensionsSupport.class)
public class RouteResourceClientHCTest {
    private static final String DIR = "./target/andorra-gh/";
    private static final DropwizardAppExtension<GraphHopperServerConfiguration> app = new DropwizardAppExtension<>(GraphHopperApplication.class, createConfig());

    private static GraphHopperServerConfiguration createConfig() {
        GraphHopperServerConfiguration config = new GraphHopperServerTestConfiguration();
        config.getGraphHopperConfiguration().
                putObject("graph.vehicles", "car,bike").
                putObject("prepare.min_network_size", 0).
                putObject("graph.elevation.provider", "srtm").
                putObject("graph.elevation.cache_dir", "../core/files/").
                putObject("datareader.file", "../core/files/andorra.osm.pbf").
                putObject("graph.encoded_values", "road_class,surface,road_environment,max_speed").
                putObject("import.osm.ignored_highways", "").
                putObject("graph.location", DIR)
                .setProfiles(Arrays.asList(
                        new Profile("car").setVehicle("car").setWeighting("fastest"),
                        new Profile("bike").setVehicle("bike").setWeighting("fastest"),
                        new CustomProfile("my_custom_car").setCustomModel(new CustomModel()).setVehicle("car")
                ))
                .setCHProfiles(Arrays.asList(new CHProfile("car"), new CHProfile("bike")));
        return config;
    }

    // dropwizard extension does not work with @RunWith(Parameterized.class), but we can use an @EnumSource or similar
    // and on each test method. see https://github.com/graphhopper/graphhopper/pull/2003
    private enum TestParam {
        GET(false, -1),
        POST_MAX_UNZIPPED_0(true, 0),
        POST_MAX_UNZIPPED_1000(true, 1000);

        public boolean usePost;
        public int maxUnzippedLength;

        TestParam(boolean usePost, int maxUnzippedLength) {
            this.usePost = usePost;
            this.maxUnzippedLength = maxUnzippedLength;
        }
    }

    private GraphHopperWeb createGH(TestParam p) {
        return new GraphHopperWeb(TestUtils.clientUrl(app, "/route")).setPostRequest(p.usePost).setMaxUnzippedLength(p.maxUnzippedLength);
    }

    @BeforeAll
    @AfterAll
    public static void cleanUp() {
        Helper.removeDir(new File(DIR));
    }

    @ParameterizedTest
    @EnumSource(value = TestParam.class)
    public void testSimpleRoute(TestParam p) {
        GraphHopperWeb gh = createGH(p);
        GHRequest req = new GHRequest().
                addPoint(new GHPoint(42.5093, 1.5274)).
                addPoint(new GHPoint(42.5126, 1.5410)).
                setProfile("car").
                putHint("elevation", false).
                putHint("instructions", true).
                putHint("calc_points", true);
        GHResponse rsp = gh.route(req);
        assertFalse(rsp.hasErrors(), "errors:" + rsp.getErrors().toString());
        ResponsePath res = rsp.getBest();
        isBetween(60, 70, res.getPoints().size());
        isBetween(2900, 3000, res.getDistance());
        isBetween(110, 120, res.getAscend());
        isBetween(70, 80, res.getDescend());
        isBetween(190, 200, res.getRouteWeight());

        // change vehicle
        rsp = gh.route(new GHRequest(42.5093, 1.5274, 42.5126, 1.5410).
                setProfile("bike"));
        res = rsp.getBest();
        assertFalse(rsp.hasErrors(), "errors:" + rsp.getErrors().toString());
        isBetween(2500, 2600, res.getDistance());

        assertEquals("[0, 1]", res.getPointsOrder().toString());
    }

    @ParameterizedTest
    @EnumSource(value = TestParam.class)
    public void testAlternativeRoute(TestParam p) {
        GraphHopperWeb gh = createGH(p);
        GHRequest req = new GHRequest().
                addPoint(new GHPoint(42.505041, 1.521864)).
                addPoint(new GHPoint(42.509074, 1.537936)).
                setProfile("car").
                setAlgorithm("alternative_route").
                putHint("instructions", true).
                putHint("calc_points", true).
                putHint("ch.disable", true);
        GHResponse res = gh.route(req);
        assertFalse(res.hasErrors(), "errors:" + res.getErrors().toString());
        List<ResponsePath> paths = res.getAll();
        assertEquals(2, paths.size());

        ResponsePath path = paths.get(0);
        assertEquals(35, path.getPoints().size());
        assertEquals(1689, path.getDistance(), 1);
        assertTrue(path.getInstructions().toString().contains("Avinguda de Tarragona"), path.getInstructions().toString());

        path = paths.get(1);
        assertEquals(30, path.getPoints().size());
        assertEquals(1759, path.getDistance(), 1);
        assertTrue(path.getInstructions().toString().contains("Avinguda Prat de la Creu"), path.getInstructions().toString());
    }

    @ParameterizedTest
    @EnumSource(value = TestParam.class)
    public void testNoPoints(TestParam p) {
        GraphHopperWeb gh = createGH(p);
        GHRequest req = new GHRequest().
                addPoint(new GHPoint(42.509225, 1.534728)).
                addPoint(new GHPoint(42.512602, 1.551558)).
                setProfile("car");

        req.putHint("instructions", false);
        req.putHint("calc_points", false);
        GHResponse rsp = gh.route(req);
        assertFalse(rsp.hasErrors(), "errors:" + rsp.getErrors().toString());
        ResponsePath res = rsp.getBest();
        assertEquals(0, res.getPoints().size());
        isBetween(1750, 1800, res.getDistance());
    }

    @ParameterizedTest
    @EnumSource(value = TestParam.class)
    public void readRoundabout(TestParam p) {
        GraphHopperWeb gh = createGH(p);
        GHRequest req = new GHRequest().
                addPoint(new GHPoint(42.509644, 1.532958)).
                addPoint(new GHPoint(42.510383, 1.533392)).
                setProfile("car");

        GHResponse res = gh.route(req);
        int counter = 0;
        for (Instruction i : res.getBest().getInstructions()) {
            if (i instanceof RoundaboutInstruction) {
                counter++;
                RoundaboutInstruction ri = (RoundaboutInstruction) i;
                assertEquals(-5, ri.getTurnAngle(), 0.1, "turn_angle was incorrect:" + ri.getTurnAngle());
                // This route contains only one roundabout and no (via) point in a roundabout
                assertTrue(ri.isExited(), "exited was incorrect:" + ri.isExited());
            }
        }
        assertTrue(counter > 0, "no roundabout in route?");
    }

    @ParameterizedTest
    @EnumSource(value = TestParam.class)
    public void testRetrieveOnlyStreetname(TestParam p) {
        GraphHopperWeb gh = createGH(p);
        GHRequest req = new GHRequest().
                addPoint(new GHPoint(42.507065, 1.529846)).
                addPoint(new GHPoint(42.510383, 1.533392)).
                setProfile("car");

        GHResponse res = gh.route(req);
        List<String> given = extractInstructionNames(res.getBest(), 5);
        assertEquals(Arrays.asList("Continue onto Carrer de l'Aigüeta", "Turn right onto Carrer Pere d'Urg",
                "Turn right onto Carrer Bonaventura Armengol", "Keep right onto Avinguda Consell d'Europa", "At roundabout, take exit 4"
        ), given);

        req.putHint("turn_description", false);
        res = gh.route(req);
        given = extractInstructionNames(res.getBest(), 5);
        assertEquals(Arrays.asList("Carrer de l'Aigüeta", "Carrer Pere d'Urg", "Carrer Bonaventura Armengol", "Avinguda Consell d'Europa", ""), given);
    }

    private List<String> extractInstructionNames(ResponsePath path, int count) {
        List<String> result = new ArrayList<>();
        for (Instruction instruction : path.getInstructions()) {
            result.add(instruction.getName());
            if (result.size() >= count) {
                return result;
            }
        }
        return result;
    }

    @ParameterizedTest
    @EnumSource(value = TestParam.class)
    public void testCannotFindPointException(TestParam p) {
        GraphHopperWeb gh = createGH(p);
        GHRequest req = new GHRequest().
                addPoint(new GHPoint(42.49058, 1.602974)).
                addPoint(new GHPoint(42.510383, 1.533392)).
                setProfile("car");

        GHResponse res = gh.route(req);
        assertTrue(res.hasErrors(), "no errors found?");
        assertTrue(res.getErrors().get(0) instanceof PointNotFoundException);
    }

    @ParameterizedTest
    @EnumSource(value = TestParam.class)
    public void testOutOfBoundsException(TestParam p) {
        GraphHopperWeb gh = createGH(p);
        GHRequest req = new GHRequest().
                addPoint(new GHPoint(-400.214943, -130.078125)).
                addPoint(new GHPoint(39.909736, -91.054687)).
                setProfile("car");

        GHResponse res = gh.route(req);
        assertTrue(res.hasErrors(), "no errors found?");
        assertTrue(res.getErrors().get(0) instanceof PointOutOfBoundsException);
    }

    @ParameterizedTest
    @EnumSource(value = TestParam.class)
    public void readFinishInstruction(TestParam p) {
        GraphHopperWeb gh = createGH(p);
        GHRequest req = new GHRequest().
                addPoint(new GHPoint(42.507065, 1.529846)).
                addPoint(new GHPoint(42.510383, 1.533392)).
                setProfile("car");

        GHResponse res = gh.route(req);
        InstructionList instructions = res.getBest().getInstructions();
        String finishInstructionName = instructions.get(instructions.size() - 1).getName();
        assertEquals("Arrive at destination", finishInstructionName);
    }

    @ParameterizedTest
    @EnumSource(value = TestParam.class)
    public void doNotReadFinishInstruction(TestParam p) {
        GraphHopperWeb gh = createGH(p);
        GHRequest req = new GHRequest().
                addPoint(new GHPoint(42.507065, 1.529846)).
                addPoint(new GHPoint(42.510383, 1.533392)).
                setProfile("car").
                putHint("turn_description", false);
        GHResponse res = gh.route(req);
        InstructionList instructions = res.getBest().getInstructions();
        String finishInstructionName = instructions.get(instructions.size() - 1).getName();
        assertEquals("", finishInstructionName);
    }

    void isBetween(double from, double to, double value) {
        assertTrue(value >= from, "value " + value + " was smaller than expected limit " + from);
        assertTrue(value <= to, "value " + value + " was bigger than expected limit " + to);
    }

    @ParameterizedTest
    @EnumSource(value = TestParam.class)
    public void testPathDetails(TestParam p) {
        GraphHopperWeb gh = createGH(p);
        GHRequest req = new GHRequest().
                addPoint(new GHPoint(42.507065, 1.529846)).
                addPoint(new GHPoint(42.510383, 1.533392)).
                setProfile("car");
        req.getPathDetails().add("average_speed");
        req.getPathDetails().add("intersection");
        GHResponse res = gh.route(req);
        assertFalse(res.hasErrors(), "errors:" + res.getErrors().toString());
        ResponsePath alt = res.getBest();
        assertEquals(2, alt.getPathDetails().size());
        List<PathDetail> details = alt.getPathDetails().get("average_speed");
        assertFalse(details.isEmpty());
        assertTrue((Double) details.get(0).getValue() > 20);
        assertTrue((Double) details.get(0).getValue() < 70);
        details = alt.getPathDetails().get("intersection");
        assertFalse(details.isEmpty());
        assertTrue(((Map) details.get(0).getValue()).containsKey("bearings"));
    }

    @ParameterizedTest
    @EnumSource(value = TestParam.class)
    public void testPointHints(TestParam p) {
        GraphHopperWeb gh = createGH(p);
        GHRequest req = new GHRequest().
                addPoint(new GHPoint(42.50856, 1.528451)).
                addPoint(new GHPoint(42.510383, 1.533392)).
                setProfile("car");

        GHResponse response = gh.route(req);
        isBetween(890, 900, response.getBest().getDistance());

        req.setPointHints(Arrays.asList("Carrer Bonaventura Armengol", ""));
        response = gh.route(req);
        isBetween(520, 550, response.getBest().getDistance());
    }


    @ParameterizedTest
    @EnumSource(value = TestParam.class)
    public void testHeadings(TestParam p) {
        GraphHopperWeb gh = createGH(p);
        GHRequest req = new GHRequest().
                addPoint(new GHPoint(42.509331, 1.536965)).
                addPoint(new GHPoint(42.507065, 1.532443)).
                setProfile("bike").
                putHint("ch.disable", true);

        // starting in eastern direction results in a longer way
        req.setHeadings(Collections.singletonList(90.0));
        GHResponse response = gh.route(req);
        assertEquals(945, response.getBest().getDistance(), 5);

        // ... than going west
        req.setHeadings(Arrays.asList(270.0));
        response = gh.route(req);
        assertEquals(553, response.getBest().getDistance(), 5);
    }

    @Test
    public void testCustomModel() {
        GraphHopperWeb gh = createGH(TestParam.POST_MAX_UNZIPPED_0);
        GHRequest req = new GHRequest().
                addPoint(new GHPoint(42.5179, 1.555574)).
                addPoint(new GHPoint(42.532022, 1.519504)).
                setCustomModel(new CustomModel().setDistanceInfluence(70d)
                        // we reduce the speed in the long tunnel
                        .addToSpeed(Statement.If("road_environment == TUNNEL", Statement.Op.MULTIPLY, "0.1"))).
                setProfile("my_custom_car").
                putHint("ch.disable", true);
        GHResponse rsp = gh.route(req);
        assertFalse(rsp.hasErrors(), "errors:" + rsp.getErrors().toString());
        assertEquals(1_638_000, rsp.getBest().getTime(), 1000);

        // ... and again without the custom model, using the tunnel -> we are much faster
        req.setCustomModel(null);
        rsp = gh.route(req);
        assertFalse(rsp.hasErrors(), "errors:" + rsp.getErrors().toString());
        assertEquals(218_000, rsp.getBest().getTime(), 1000);
    }

    @ParameterizedTest
    @EnumSource(value = TestParam.class)
    public void testWaypointIndices(TestParam p) {
        GraphHopperWeb gh = createGH(p);
        GHRequest req = new GHRequest().
                addPoint(new GHPoint(42.509141, 1.546063)).
                addPoint(new GHPoint(42.507173, 1.531902)).
                addPoint(new GHPoint(42.505435, 1.515943)).
                addPoint(new GHPoint(42.499062, 1.506067)).
                addPoint(new GHPoint(42.498801, 1.505568)).
                addPoint(new GHPoint(42.498465, 1.504898)).
                addPoint(new GHPoint(42.49833, 1.504619)).
                addPoint(new GHPoint(42.498217, 1.504377)).
                addPoint(new GHPoint(42.495611, 1.498368)).
                setProfile("bike");

        GHResponse response = gh.route(req);
        ResponsePath path = response.getBest();
        assertEquals(5333, path.getDistance(), 5);

        assertEquals(9, path.getWaypoints().size());
        assertEquals(9, path.getWaypointIndices().size());
        // explicitly check one of the waypoints
        assertEquals(42.50539, path.getWaypoints().get(2).lat);
        assertEquals(42.50539, path.getPoints().get(path.getWaypointIndices().get(2)).getLat());
        // check all the waypoints
        assertEquals(path.getWaypoints().get(0), path.getPoints().get(path.getWaypointIndices().get(0)));
        for (int i = 0; i < path.getWaypointIndices().size(); ++i)
            assertEquals(path.getWaypoints().get(i), path.getPoints().get(path.getWaypointIndices().get(i)));

        List<PointList> pointListFromInstructions = getPointListFromInstructions(path);
        List<PointList> pointListFromWayPointIndices = getPointListFromWayPointIndices(path);

        assertEquals(pointListFromInstructions.size(), pointListFromWayPointIndices.size());
        assertEquals(8, pointListFromWayPointIndices.size());
        for (int i = 0; i < pointListFromWayPointIndices.size(); i++) {
            assertEquals(pointListFromInstructions.get(i).size(), pointListFromWayPointIndices.get(i).size());
            for (int j = 0; j < pointListFromWayPointIndices.get(i).size(); j++)
                assertEquals(pointListFromInstructions.get(i).get(j), pointListFromWayPointIndices.get(i).get(j));
        }
    }

    private List<PointList> getPointListFromInstructions(ResponsePath path) {
        List<PointList> legs = new ArrayList<>();
        PointList perLeg = new PointList();
        for (Instruction instruction : path.getInstructions()) {
            perLeg.add(instruction.getPoints());
            if (instruction.getSign() == REACHED_VIA || instruction.getSign() == FINISH) {
                legs.add(perLeg);
                perLeg = new PointList();
            } else {
                perLeg.removeLastPoint();
            }
        }
        return legs;
    }

    private List<PointList> getPointListFromWayPointIndices(ResponsePath path) {
        List<PointList> legs = new ArrayList<>();
        for (int i = 1; i < path.getWaypointIndices().size(); i++) {
            int start = path.getWaypointIndices().get(i - 1);
            int end = path.getWaypointIndices().get(i);
            PointList leg = new PointList(end - start + 1, path.getPoints().is3D());
            for (int j = start; j <= end; j++)
                leg.add(path.getPoints(), j);
            legs.add(leg);
        }
        return legs;
    }
}
