package com.graphhopper.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.PathWrapper;
import com.graphhopper.util.Instruction;
import com.graphhopper.util.InstructionList;
import com.graphhopper.util.RoundaboutInstruction;
import com.graphhopper.util.details.PathDetail;
import com.graphhopper.util.exceptions.PointNotFoundException;
import com.graphhopper.util.exceptions.PointOutOfBoundsException;
import com.graphhopper.util.shapes.GHPoint;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
public class GraphHopperWebIT {

    public static final String KEY = System.getProperty("key", "78da6e9a-273e-43d1-bdda-8f24e007a1fa");

    private final GraphHopperWeb gh = new GraphHopperWeb();
    private final GraphHopperMatrixWeb ghMatrix = new GraphHopperMatrixWeb();

    @Before
    public void setUp() {
        gh.setKey(KEY);
        ghMatrix.setKey(KEY);
    }

    @Test
    public void testSimpleRoute() {
        // https://graphhopper.com/maps/?point=49.6724%2C11.3494&point=49.655%2C11.418
        GHRequest req = new GHRequest().
                addPoint(new GHPoint(49.6724, 11.3494)).
                addPoint(new GHPoint(49.6550, 11.4180));
        req.getHints().put("elevation", false);
        req.getHints().put("instructions", true);
        req.getHints().put("calc_points", true);
        GHResponse res = gh.route(req);
        assertFalse("errors:" + res.getErrors().toString(), res.hasErrors());
        PathWrapper alt = res.getBest();
        isBetween(200, 250, alt.getPoints().size());
        isBetween(10500, 12000, alt.getDistance());
        isBetween(240, 270, alt.getAscend());
        isBetween(180, 200, alt.getDescend());
        isBetween(1000, 1500, alt.getRouteWeight());

        // change vehicle
        res = gh.route(new GHRequest(49.6724, 11.3494, 49.6550, 11.4180).
                setVehicle("bike"));
        alt = res.getBest();
        assertFalse("errors:" + res.getErrors().toString(), res.hasErrors());
        isBetween(9000, 9500, alt.getDistance());
    }

    @Test
    public void testAlternativeRoute() {
        // https://graphhopper.com/maps/?point=52.042989%2C10.373926&point=52.042289%2C10.384043&algorithm=alternative_route&ch.disable=true
        GHRequest req = new GHRequest().
                addPoint(new GHPoint(52.042989, 10.373926)).
                addPoint(new GHPoint(52.042289, 10.384043));
        req.setAlgorithm("alternative_route");
        req.getHints().put("instructions", true);
        req.getHints().put("calc_points", true);
        req.getHints().put("ch.disable", true);
        GHResponse res = gh.route(req);
        assertFalse("errors:" + res.getErrors().toString(), res.hasErrors());
        List<PathWrapper> paths = res.getAll();
        assertEquals(2, paths.size());

        PathWrapper path = paths.get(1);
        isBetween(5, 20, path.getPoints().size());
        isBetween(1000, 1100, path.getDistance());
        assertTrue("expected: " + path.getDescription().get(0), Arrays.asList("Wiesenstraße", "Hasenspringweg").contains(path.getDescription().get(0)));

        path = paths.get(0);
        isBetween(20, 30, path.getPoints().size());
        isBetween(800, 900, path.getDistance());
        assertTrue("expected: " + path.getDescription().get(0), Arrays.asList("Jacobistraße", "Ludwig-Gercke-Straße", "Eichendorffplatz").contains(path.getDescription().get(0)));
    }

    @Test
    public void testTimeout() {
        GHRequest req = new GHRequest().
                addPoint(new GHPoint(49.6724, 11.3494)).
                addPoint(new GHPoint(49.6550, 11.4180));
        GHResponse res = gh.route(req);
        assertFalse("errors:" + res.getErrors().toString(), res.hasErrors());

        req.getHints().put(GraphHopperWeb.TIMEOUT, 1);
        try {
            res = gh.route(req);
            fail();
        } catch (RuntimeException e) {
            assertEquals(SocketTimeoutException.class, e.getCause().getClass());
        }
    }

    @Test
    public void testNoPoints() {
        GHRequest req = new GHRequest().
                addPoint(new GHPoint(49.6724, 11.3494)).
                addPoint(new GHPoint(49.6550, 11.4180));

        req.getHints().put("instructions", false);
        req.getHints().put("calc_points", false);
        GHResponse res = gh.route(req);
        assertFalse("errors:" + res.getErrors().toString(), res.hasErrors());
        PathWrapper alt = res.getBest();
        assertEquals(0, alt.getPoints().size());
        isBetween(10500, 12000, alt.getDistance());
    }

    @Test
    public void readRoundabout() {
        GHRequest req = new GHRequest().
                addPoint(new GHPoint(52.261434, 13.485718)).
                addPoint(new GHPoint(52.399067, 13.469238));

        GHResponse res = gh.route(req);
        int counter = 0;
        for (Instruction i : res.getBest().getInstructions()) {
            if (i instanceof RoundaboutInstruction) {
                counter++;
                RoundaboutInstruction ri = (RoundaboutInstruction) i;
                assertEquals("turn_angle was incorrect:" + ri.getTurnAngle(), -1.5, ri.getTurnAngle(), 0.1);
                // This route contains only one roundabout and no (via) point in a roundabout
                assertEquals("exited was incorrect:" + ri.isExited(), ri.isExited(), true);
            }
        }
        assertTrue("no roundabout in route?", counter > 0);
    }

    @Test
    public void testRetrieveOnlyStreetname() {
        GHRequest req = new GHRequest().
                addPoint(new GHPoint(52.261434, 13.485718)).
                addPoint(new GHPoint(52.399067, 13.469238));

        GHResponse res = gh.route(req);
        assertEquals("Continue onto B 96", res.getBest().getInstructions().get(4).getName());

        req.getHints().put("turn_description", false);
        res = gh.route(req);
        assertEquals("B 96", res.getBest().getInstructions().get(4).getName());
    }

    @Test
    public void testCannotFindPointException() {
        GHRequest req = new GHRequest().
                addPoint(new GHPoint(-4.214943, -130.078125)).
                addPoint(new GHPoint(39.909736, -91.054687));

        GHResponse res = gh.route(req);
        assertTrue("no erros found?", res.hasErrors());
        assertTrue(res.getErrors().get(0) instanceof PointNotFoundException);
    }

    @Test
    public void testOutOfBoundsException() {
        GHRequest req = new GHRequest().
                addPoint(new GHPoint(-400.214943, -130.078125)).
                addPoint(new GHPoint(39.909736, -91.054687));

        GHResponse res = gh.route(req);
        assertTrue("no erros found?", res.hasErrors());
        assertTrue(res.getErrors().get(0) instanceof PointOutOfBoundsException);
    }

    @Test
    public void readFinishInstruction() {
        GHRequest req = new GHRequest().
                addPoint(new GHPoint(52.261434, 13.485718)).
                addPoint(new GHPoint(52.399067, 13.469238));

        GHResponse res = gh.route(req);
        InstructionList instructions = res.getBest().getInstructions();
        String finishInstructionName = instructions.get(instructions.size() - 1).getName();
        assertEquals("Arrive at destination", finishInstructionName);
    }

    @Test
    public void doNotReadFinishInstruction() {
        GHRequest req = new GHRequest().
                addPoint(new GHPoint(52.261434, 13.485718)).
                addPoint(new GHPoint(52.399067, 13.469238));
        req.getHints().put("turn_description", false);
        GHResponse res = gh.route(req);
        InstructionList instructions = res.getBest().getInstructions();
        String finishInstructionName = instructions.get(instructions.size() - 1).getName();
        assertEquals("", finishInstructionName);
    }

    @Test
    public void testSimpleExport() {
        GHRequest req = new GHRequest().
                addPoint(new GHPoint(49.6724, 11.3494)).
                addPoint(new GHPoint(49.6550, 11.4180));
        req.getHints().put("elevation", false);
        req.getHints().put("instructions", true);
        req.getHints().put("calc_points", true);
        req.getHints().put("type", "gpx");
        String res = gh.export(req);
        assertTrue(res.contains("<gpx"));
        assertTrue(res.contains("<rtept lat="));
        assertTrue(res.contains("<trk><name>GraphHopper Track</name><trkseg>"));
        assertTrue(res.endsWith("</gpx>"));
    }

    @Test
    public void testExportWithoutTrack() {
        GHRequest req = new GHRequest().
                addPoint(new GHPoint(49.6724, 11.3494)).
                addPoint(new GHPoint(49.6550, 11.4180));
        req.getHints().put("elevation", false);
        req.getHints().put("instructions", true);
        req.getHints().put("calc_points", true);
        req.getHints().put("type", "gpx");
        req.getHints().put("gpx.track", "false");
        String res = gh.export(req);
        assertTrue(res.contains("<gpx"));
        assertTrue(res.contains("<rtept lat="));
        assertTrue(!res.contains("<trk><name>GraphHopper Track</name><trkseg>"));
        assertTrue(res.endsWith("</gpx>"));
    }

    @Test
    public void testCreateGPXFromInstructionList() {
        GHRequest req = new GHRequest().
                addPoint(new GHPoint(49.6724, 11.3494)).
                addPoint(new GHPoint(49.6550, 11.4180));
        req.getHints().put("elevation", false);
        req.getHints().put("instructions", true);
        req.getHints().put("calc_points", true);
        GHResponse ghResponse = gh.route(req);
        String gpx = ghResponse.getBest().getInstructions().createGPX("wurst");
        assertTrue(gpx.contains("<gpx"));
        assertTrue(gpx.contains("<rtept lat="));
        assertTrue(gpx.contains("<trk><name>"));
        assertTrue(gpx.endsWith("</gpx>"));
    }

    void isBetween(double from, double to, double expected) {
        assertTrue("expected value " + expected + " was smaller than limit " + from, expected >= from);
        assertTrue("expected value " + expected + " was bigger than limit " + to, expected <= to);
    }

    @Test
    public void testMatrix() {
        GHMRequest req = AbstractGHMatrixWebTester.createRequest();
        MatrixResponse res = ghMatrix.route(req);

        // no distances available
        try {
            assertEquals(0, res.getDistance(1, 2), 1);
            assertTrue(false);
        } catch (Exception ex) {
        }

        // ... only weight:
        assertEquals(1840, res.getWeight(1, 2), 5);

        req = AbstractGHMatrixWebTester.createRequest();
        req.addOutArray("weights");
        req.addOutArray("distances");
        res = ghMatrix.route(req);

        assertEquals(9834, res.getDistance(1, 2), 20);
        assertEquals(1840, res.getWeight(1, 2), 10);
    }

    @Test
    public void testMatrix_DoNotWrapHints() {
        final GraphHopperMatrixWeb ghMatrix = new GraphHopperMatrixWeb(new GHMatrixBatchRequester() {
            @Override
            protected String postJson(String url, JsonNode data) throws IOException {
                assertFalse(data.has("hints"));
                assertTrue(data.has("something"));
                return super.postJson(url, data);
            }
        });
        ghMatrix.setKey(System.getProperty("graphhopper.key", KEY));

        GHMRequest req = new GHMRequest();
        req.addPoint(new GHPoint(49.6724, 11.3494));
        req.addPoint(new GHPoint(49.6550, 11.4180));
        req.getHints().put("something", "xy");
        ghMatrix.route(req);

        // clashing parameter will overwrite!
        req.getHints().put("vehicle", "xy");
        assertEquals("xy", req.getVehicle());
    }

    @Test
    public void testUnknownInstructionSign() throws IOException {
        // Actual path for the request: point=48.354413%2C8.676335&point=48.35442%2C8.676345
        // Modified the sign though
        JsonNode json = new ObjectMapper().readTree("{\"instructions\":[{\"distance\":1.073,\"sign\":741,\"interval\":[0,1],\"text\":\"Continue onto A 81\",\"time\":32,\"street_name\":\"A 81\"},{\"distance\":0,\"sign\":4,\"interval\":[1,1],\"text\":\"Finish!\",\"time\":0,\"street_name\":\"\"}],\"descend\":0,\"ascend\":0,\"distance\":1.073,\"bbox\":[8.676286,48.354446,8.676297,48.354453],\"weight\":0.032179,\"time\":32,\"points_encoded\":true,\"points\":\"gfcfHwq}s@}c~AAA?\",\"snapped_waypoints\":\"gfcfHwq}s@}c~AAA?\"}");
        PathWrapper wrapper = new GraphHopperWeb().createPathWrapper(json, true, true);

        assertEquals(741, wrapper.getInstructions().get(0).getSign());
        assertEquals("Continue onto A 81", wrapper.getInstructions().get(0).getName());
    }

    @Test
    public void testPathDetails() {
        GHRequest req = new GHRequest().
                addPoint(new GHPoint(49.6724, 11.3494)).
                addPoint(new GHPoint(49.6550, 11.4180));
        req.getPathDetails().add("average_speed");
        GHResponse res = gh.route(req);
        assertFalse("errors:" + res.getErrors().toString(), res.hasErrors());
        PathWrapper alt = res.getBest();
        assertEquals(1, alt.getPathDetails().size());
        List<PathDetail> details = alt.getPathDetails().get("average_speed");
        assertFalse(details.isEmpty());
        assertTrue((Double) details.get(0).getValue() > 20);
        assertTrue((Double) details.get(0).getValue() < 70);
    }

    @Test
    public void testPointHints() {
        GHRequest ghRequest = new GHRequest();
        ghRequest.addPoint(new GHPoint(52.50977, 13.371971));
        ghRequest.addPoint(new GHPoint(52.509842, 13.369761));

        ghRequest.setPointHints(Arrays.asList("Ben-Gurion", ""));
        GHResponse response = gh.route(ghRequest);
        assertTrue(response.getBest().getDistance() + "m", response.getBest().getDistance() < 500);
    }
}
