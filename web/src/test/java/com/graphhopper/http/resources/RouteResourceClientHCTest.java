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
package com.graphhopper.http.resources;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.PathWrapper;
import com.graphhopper.api.GraphHopperWeb;
import com.graphhopper.config.CHProfileConfig;
import com.graphhopper.config.ProfileConfig;
import com.graphhopper.http.GraphHopperApplication;
import com.graphhopper.http.GraphHopperServerConfiguration;
import com.graphhopper.http.util.GraphHopperServerTestConfiguration;
import com.graphhopper.http.util.TestUtils;
import com.graphhopper.util.Helper;
import com.graphhopper.util.Instruction;
import com.graphhopper.util.InstructionList;
import com.graphhopper.util.RoundaboutInstruction;
import com.graphhopper.util.details.PathDetail;
import com.graphhopper.util.exceptions.PointNotFoundException;
import com.graphhopper.util.exceptions.PointOutOfBoundsException;
import com.graphhopper.util.shapes.GHPoint;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
                putObject("graph.flag_encoders", "car,bike").
                putObject("routing.ch.disabling_allowed", true).
                putObject("prepare.min_network_size", 0).
                putObject("prepare.min_one_way_network_size", 0).
                putObject("graph.elevation.provider", "srtm").
                putObject("graph.elevation.cachedir", "../core/files/").
                putObject("datareader.file", "../core/files/andorra.osm.pbf").
                putObject("graph.encoded_values", "road_class,surface,road_environment,max_speed").
                putObject("graph.location", DIR)
                .setProfiles(Arrays.asList(new ProfileConfig("my_car").setVehicle("car").setWeighting("fastest"),
                        new ProfileConfig("my_bike").setVehicle("bike").setWeighting("fastest")))
                .setCHProfiles(Arrays.asList(new CHProfileConfig("my_car"), new CHProfileConfig("my_bike")));
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
                putHint("vehicle", "car").
                putHint("elevation", false).
                putHint("instructions", true).
                putHint("calc_points", true);
        GHResponse res = gh.route(req);
        assertFalse(res.hasErrors(), "errors:" + res.getErrors().toString());
        PathWrapper alt = res.getBest();
        isBetween(60, 70, alt.getPoints().size());
        isBetween(2900, 3000, alt.getDistance());
        isBetween(110, 120, alt.getAscend());
        isBetween(70, 80, alt.getDescend());
        isBetween(190, 200, alt.getRouteWeight());

        // change vehicle
        res = gh.route(new GHRequest(42.5093, 1.5274, 42.5126, 1.5410).
                putHint("vehicle", "bike"));
        alt = res.getBest();
        assertFalse(res.hasErrors(), "errors:" + res.getErrors().toString());
        isBetween(2500, 2600, alt.getDistance());

        assertEquals("[0, 1]", alt.getPointsOrder().toString());
    }

    @ParameterizedTest
    @EnumSource(value = TestParam.class)
    public void testAlternativeRoute(TestParam p) {
        GraphHopperWeb gh = createGH(p);
        GHRequest req = new GHRequest().
                addPoint(new GHPoint(42.505041, 1.521864)).
                addPoint(new GHPoint(42.509074, 1.537936)).
                putHint("vehicle", "car").
                setAlgorithm("alternative_route").
                putHint("instructions", true).
                putHint("calc_points", true).
                putHint("ch.disable", true);
        GHResponse res = gh.route(req);
        assertFalse(res.hasErrors(), "errors:" + res.getErrors().toString());
        List<PathWrapper> paths = res.getAll();
        assertEquals(2, paths.size());

        PathWrapper path = paths.get(0);
        isBetween(31, 37, path.getPoints().size());
        isBetween(1670, 1710, path.getDistance());
        assertTrue("Avinguda Carlemany".contains(path.getDescription().get(0)), "expected: " + path.getDescription().get(0));

        path = paths.get(1);
        isBetween(26, 31, path.getPoints().size());
        isBetween(1740, 1790, path.getDistance());
        assertTrue("Carrer Doctor Vilanova".contains(path.getDescription().get(0)), "expected: " + path.getDescription().get(0));
    }

    @ParameterizedTest
    @EnumSource(value = TestParam.class)
    public void testNoPoints(TestParam p) {
        GraphHopperWeb gh = createGH(p);
        GHRequest req = new GHRequest().
                addPoint(new GHPoint(42.509225, 1.534728)).
                addPoint(new GHPoint(42.512602, 1.551558)).
                putHint("vehicle", "car");

        req.putHint("instructions", false);
        req.putHint("calc_points", false);
        GHResponse res = gh.route(req);
        assertFalse(res.hasErrors(), "errors:" + res.getErrors().toString());
        PathWrapper alt = res.getBest();
        assertEquals(0, alt.getPoints().size());
        isBetween(1750, 1800, alt.getDistance());
    }

    @ParameterizedTest
    @EnumSource(value = TestParam.class)
    public void readRoundabout(TestParam p) {
        GraphHopperWeb gh = createGH(p);
        GHRequest req = new GHRequest().
                addPoint(new GHPoint(42.509644, 1.532958)).
                addPoint(new GHPoint(42.510383, 1.533392)).
                putHint("vehicle", "car");

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
                putHint("vehicle", "car");

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

    private List<String> extractInstructionNames(PathWrapper path, int count) {
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
                putHint("vehicle", "car");

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
                putHint("vehicle", "car");

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
                putHint("vehicle", "car");

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
                putHint("vehicle", "car").
                putHint("turn_description", false);
        GHResponse res = gh.route(req);
        InstructionList instructions = res.getBest().getInstructions();
        String finishInstructionName = instructions.get(instructions.size() - 1).getName();
        assertEquals("", finishInstructionName);
    }

    void isBetween(double from, double to, double expected) {
        assertTrue(expected >= from, "expected value " + expected + " was smaller than limit " + from);
        assertTrue(expected <= to, "expected value " + expected + " was bigger than limit " + to);
    }

    @ParameterizedTest
    @EnumSource(value = TestParam.class)
    public void testPathDetails(TestParam p) {
        GraphHopperWeb gh = createGH(p);
        GHRequest req = new GHRequest().
                addPoint(new GHPoint(42.507065, 1.529846)).
                addPoint(new GHPoint(42.510383, 1.533392)).
                setProfile("my_car");
        req.getPathDetails().add("average_speed");
        GHResponse res = gh.route(req);
        assertFalse(res.hasErrors(), "errors:" + res.getErrors().toString());
        PathWrapper alt = res.getBest();
        assertEquals(1, alt.getPathDetails().size());
        List<PathDetail> details = alt.getPathDetails().get("average_speed");
        assertFalse(details.isEmpty());
        assertTrue((Double) details.get(0).getValue() > 20);
        assertTrue((Double) details.get(0).getValue() < 70);
    }

    @ParameterizedTest
    @EnumSource(value = TestParam.class)
    public void testPointHints(TestParam p) {
        GraphHopperWeb gh = createGH(p);
        GHRequest req = new GHRequest().
                addPoint(new GHPoint(42.50856, 1.528451)).
                addPoint(new GHPoint(42.510383, 1.533392)).
                setProfile("my_car");

        GHResponse response = gh.route(req);
        isBetween(890, 900, response.getBest().getDistance());

        req.setPointHints(Arrays.asList("Carrer Bonaventura Armengol", ""));
        response = gh.route(req);
        isBetween(520, 550, response.getBest().getDistance());
    }
}
