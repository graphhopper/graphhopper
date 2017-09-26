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
package com.graphhopper.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.json.GHJson;
import com.graphhopper.json.GHJsonFactory;
import com.graphhopper.routing.Dijkstra;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.PathExtract;
import com.graphhopper.routing.profiles.BooleanEncodedValue;
import com.graphhopper.routing.profiles.DecimalEncodedValue;
import com.graphhopper.routing.profiles.TagParserFactory;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.ShortestWeighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.NodeAccess;
import org.junit.Before;
import org.junit.Test;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.StringReader;
import java.util.*;

import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
public class InstructionListTest {
    private final GHJson json = new GHJsonFactory().create();
    private final TranslationMap trMap = TranslationMapTest.SINGLETON;
    private final Translation usTR = trMap.getWithFallBack(Locale.US);
    private final TraversalMode tMode = TraversalMode.NODE_BASED;
    private EncodingManager carManager;
    private FlagEncoder carEncoder;
    private BooleanEncodedValue carAccessEnc;
    private DecimalEncodedValue carAverageSpeedEnc;

    @Before
    public void setUp() {
        carEncoder = new CarFlagEncoder();
        carManager = new EncodingManager.Builder().addGlobalEncodedValues().addAll(carEncoder).build();
        carAccessEnc = carManager.getBooleanEncodedValue(TagParserFactory.CAR_ACCESS);
        carAverageSpeedEnc = carManager.getDecimalEncodedValue(TagParserFactory.CAR_AVERAGE_SPEED);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testWayList() {
        Graph g = new GraphBuilder(carManager, json).create();
        // 0-1-2
        // | | |
        // 3-4-5  9-10
        // | | |  |
        // 6-7-8--*
        NodeAccess na = g.getNodeAccess();
        na.setNode(0, 1.2, 1.0);
        na.setNode(1, 1.2, 1.1);
        na.setNode(2, 1.2, 1.2);
        na.setNode(3, 1.1, 1.0);
        na.setNode(4, 1.1, 1.1);
        na.setNode(5, 1.1, 1.2);
        na.setNode(9, 1.1, 1.3);
        na.setNode(10, 1.1, 1.4);

        na.setNode(6, 1.0, 1.0);
        na.setNode(7, 1.0, 1.1);
        na.setNode(8, 1.0, 1.2);
        GHUtility.createEdge(g, carAverageSpeedEnc, 60, carAccessEnc, 0, 1, true, 10000).setName("0-1");
        GHUtility.createEdge(g, carAverageSpeedEnc, 60, carAccessEnc, 1, 2, true, 11000).setName("1-2");

        GHUtility.createEdge(g, carAverageSpeedEnc, 60, carAccessEnc, 0, 3, true, 11000);
        GHUtility.createEdge(g, carAverageSpeedEnc, 60, carAccessEnc, 1, 4, true, 10000).setName("1-4");
        GHUtility.createEdge(g, carAverageSpeedEnc, 60, carAccessEnc, 2, 5, true, 11000).setName("5-2");

        GHUtility.createEdge(g, carAverageSpeedEnc, 60, carAccessEnc, 3, 6, true, 11000).setName("3-6");
        GHUtility.createEdge(g, carAverageSpeedEnc, 60, carAccessEnc, 4, 7, true, 10000).setName("4-7");
        GHUtility.createEdge(g, carAverageSpeedEnc, 60, carAccessEnc, 5, 8, true, 10000).setName("5-8");

        GHUtility.createEdge(g, carAverageSpeedEnc, 60, carAccessEnc, 6, 7, true, 11000).setName("6-7");
        EdgeIteratorState iter = GHUtility.createEdge(g, carAverageSpeedEnc, 60, carAccessEnc, 7, 8, true, 10000);
        PointList list = new PointList();
        list.add(1.0, 1.15);
        list.add(1.0, 1.16);
        iter.setWayGeometry(list);
        iter.setName("7-8");
        // missing edge name
        GHUtility.createEdge(g, carAverageSpeedEnc, 60, carAccessEnc, 9, 10, true, 10000);
        EdgeIteratorState iter2 = GHUtility.createEdge(g, carAverageSpeedEnc, 60, carAccessEnc, 8, 9, true, 20000);
        list.clear();
        list.add(1.0, 1.3);
        iter2.setName("8-9");
        iter2.setWayGeometry(list);

        Path p = new Dijkstra(g, new ShortestWeighting(carEncoder), tMode).calcPath(0, 10);
        PathExtract pathExtract = p.createPathExtract(carManager, false);
        InstructionList wayList = pathExtract.calcInstructions(usTR);
        List<String> tmpList = pick("text", wayList.createJson());
        assertEquals(Arrays.asList("Continue onto 0-1", "Turn right onto 1-4", "Turn left onto 7-8", "Arrive at destination"),
                tmpList);

        wayList = pathExtract.calcInstructions(trMap.getWithFallBack(Locale.GERMAN));
        tmpList = pick("text", wayList.createJson());
        assertEquals(Arrays.asList("Dem Straßenverlauf von 0-1 folgen", "Rechts abbiegen auf 1-4", "Links abbiegen auf 7-8", "Ziel erreicht"),
                tmpList);

        assertEquals(70000.0, sumDistances(wayList), 1e-1);

        List<GPXEntry> gpxes = wayList.createGPXList();
        assertEquals(10, gpxes.size());
        // check order of tower nodes        
        assertEquals(1, gpxes.get(0).getLon(), 1e-6);
        assertEquals(1.4, gpxes.get(gpxes.size() - 1).getLon(), 1e-6);

        // check order of pillar nodes        
        assertEquals(1.15, gpxes.get(4).getLon(), 1e-6);
        assertEquals(1.16, gpxes.get(5).getLon(), 1e-6);
        assertEquals(1.16, gpxes.get(5).getLon(), 1e-6);

        compare(Arrays.asList(asL(1.2d, 1.0d), asL(1.2d, 1.1), asL(1.0, 1.1), asL(1.1, 1.4)),
                wayList.createStartPoints());

        p = new Dijkstra(g, new ShortestWeighting(carEncoder), tMode).calcPath(6, 2);
        assertEquals(42000, p.getDistance(), 1e-2);
        assertEquals(Helper.createTList(6, 7, 8, 5, 2), p.calcNodes());

        wayList = p.createPathExtract(carManager, false).calcInstructions(usTR);
        tmpList = pick("text", wayList.createJson());
        assertEquals(Arrays.asList("Continue onto 6-7", "Turn left onto 5-8", "Arrive at destination"),
                tmpList);

        compare(Arrays.asList(asL(1d, 1d), asL(1d, 1.2), asL(1.2, 1.2)),
                wayList.createStartPoints());

        // special case of identical start and end
        p = new Dijkstra(g, new ShortestWeighting(carEncoder), tMode).calcPath(0, 0);
        wayList = p.createPathExtract(carManager, false).calcInstructions(usTR);
        assertEquals(1, wayList.size());
        assertEquals("arrive at destination", wayList.get(0).getTurnDescription(usTR));
    }

    List<String> pick(String key, List<Map<String, Object>> instructionJson) {
        List<String> list = new ArrayList<String>();

        for (Map<String, Object> json : instructionJson) {
            list.add(json.get(key).toString());
        }
        return list;
    }

    List<List<Double>> createList(PointList pl, List<Integer> integs) {
        List<List<Double>> list = new ArrayList<List<Double>>();
        for (int i : integs) {
            List<Double> entryList = new ArrayList<Double>(2);
            entryList.add(pl.getLatitude(i));
            entryList.add(pl.getLongitude(i));
            list.add(entryList);
        }
        return list;
    }

    void compare(List<List<Double>> expected, List<List<Double>> actual) {
        for (int i = 0; i < expected.size(); i++) {
            List<Double> e = expected.get(i);
            List<Double> wasE = actual.get(i);
            for (int j = 0; j < e.size(); j++) {
                assertEquals("at index " + i + " value index " + j + " and value " + e + " vs " + wasE + "\n" + "Expected: " + expected + "\n" + "Actual: " + actual
                        , e.get(j),
                        wasE.get(j),
                        1e-5d);
            }
        }
    }

    List<Double> asL(Double... list) {
        return Arrays.asList(list);
    }

    double sumDistances(InstructionList il) {
        double val = 0;
        for (Instruction i : il) {
            val += i.getDistance();
        }
        return val;
    }

    @Test
    public void testWayList2() {
        Graph g = new GraphBuilder(carManager, json).create();
        //   2
        //    \.  5
        //      \/
        //      4
        //     /
        //    3
        NodeAccess na = g.getNodeAccess();
        na.setNode(2, 10.3, 10.15);
        na.setNode(3, 10.0, 10.08);
        na.setNode(4, 10.1, 10.10);
        na.setNode(5, 10.2, 10.13);
        GHUtility.createEdge(g, carAverageSpeedEnc, 60, carAccessEnc, 3, 4, true, 100).setName("3-4");
        GHUtility.createEdge(g, carAverageSpeedEnc, 60, carAccessEnc, 4, 5, true, 100).setName("4-5");

        EdgeIteratorState iter = GHUtility.createEdge(g, carAverageSpeedEnc, 60, carAccessEnc, 2, 4, true, 100);
        iter.setName("2-4");
        PointList list = new PointList();
        list.add(10.20, 10.05);
        iter.setWayGeometry(list);

        Path p = new Dijkstra(g, new ShortestWeighting(carEncoder), tMode).calcPath(2, 3);

        InstructionList wayList = p.createPathExtract(carManager, false).calcInstructions(usTR);
        List<String> tmpList = pick("text", wayList.createJson());
        assertEquals(Arrays.asList("Continue onto 2-4", "Turn slight right onto 3-4", "Arrive at destination"),
                tmpList);

        p = new Dijkstra(g, new ShortestWeighting(carEncoder), tMode).calcPath(3, 5);
        wayList = p.createPathExtract(carManager, false).calcInstructions(usTR);
        tmpList = pick("text", wayList.createJson());
        assertEquals(Arrays.asList("Continue onto 3-4", "Turn slight right onto 4-5", "Arrive at destination"),
                tmpList);
    }

    // TODO is this problem fixed with the new instructions?
    // problem: we normally don't want instructions if streetname stays but here it is suboptimal:
    @Test
    public void testNoInstructionIfSameStreet() {
        Graph g = new GraphBuilder(carManager, json).create();
        //   2
        //    \.  5
        //      \/
        //      4
        //     /
        //    3
        NodeAccess na = g.getNodeAccess();
        na.setNode(2, 10.3, 10.15);
        na.setNode(3, 10.0, 10.05);
        na.setNode(4, 10.1, 10.10);
        na.setNode(5, 10.2, 10.15);
        GHUtility.createEdge(g, carAverageSpeedEnc, 60, carAccessEnc, 3, 4, true, 100).setName("street");
        GHUtility.createEdge(g, carAverageSpeedEnc, 60, carAccessEnc, 4, 5, true, 100).setName("4-5");

        EdgeIteratorState iter = GHUtility.createEdge(g, carAverageSpeedEnc, 60, carAccessEnc, 2, 4, true, 100);
        iter.setName("street");
        PointList list = new PointList();
        list.add(10.20, 10.05);
        iter.setWayGeometry(list);

        Path p = new Dijkstra(g, new ShortestWeighting(carEncoder), tMode).calcPath(2, 3);
        InstructionList wayList = p.createPathExtract(carManager, false).calcInstructions(usTR);
        List<String> tmpList = pick("text", wayList.createJson());
        assertEquals(Arrays.asList("Continue onto street", "Turn right onto street", "Arrive at destination"), tmpList);
    }

    @Test
    public void testInstructionsWithTimeAndPlace() {
        Graph g = new GraphBuilder(carManager, json).create();
        //   n-4-5   (n: pillar node)
        //   |
        // 7-3-2-6
        //     |
        //     1
        NodeAccess na = g.getNodeAccess();
        na.setNode(1, 15.0, 10);
        na.setNode(2, 15.1, 10);
        na.setNode(3, 15.1, 9.9);
        na.setNode(4, 15.2, 9.9);
        na.setNode(5, 15.2, 10);
        na.setNode(6, 15.1, 10.1);
        na.setNode(7, 15.1, 9.8);

        GHUtility.createEdge(g, carAverageSpeedEnc, 60, carAccessEnc, 1, 2, true, 7000).setName("1-2").set(carAverageSpeedEnc, 70d);
        GHUtility.createEdge(g, carAverageSpeedEnc, 60, carAccessEnc, 2, 3, true, 8000).setName("2-3").set(carAverageSpeedEnc, 80d);
        GHUtility.createEdge(g, carAverageSpeedEnc, 60, carAccessEnc, 2, 6, true, 10000).setName("2-6").set(carAverageSpeedEnc, 10d);
        GHUtility.createEdge(g, carAverageSpeedEnc, 60, carAccessEnc, 3, 4, true, 9000).setName("3-4").set(carAverageSpeedEnc, 90d);
        GHUtility.createEdge(g, carAverageSpeedEnc, 60, carAccessEnc, 3, 7, true, 10000).setName("3-7").set(carAverageSpeedEnc, 10d);
        GHUtility.createEdge(g, carAverageSpeedEnc, 60, carAccessEnc, 4, 5, true, 10000).setName("4-5").set(carAverageSpeedEnc, 100d);

        Path p = new Dijkstra(g, new ShortestWeighting(carEncoder), tMode).calcPath(1, 5);
        InstructionList wayList = p.createPathExtract(carManager, false).calcInstructions(usTR);
        assertEquals(4, wayList.size());

        List<GPXEntry> gpxList = wayList.createGPXList();
        assertEquals(34000, p.getDistance(), 1e-1);
        assertEquals(34000, sumDistances(wayList), 1e-1);
        assertEquals(5, gpxList.size());
        assertEquals(1604120, p.getTime());
        assertEquals(1604120, gpxList.get(gpxList.size() - 1).getTime());

        assertEquals(Instruction.CONTINUE_ON_STREET, wayList.get(0).getSign());
        assertEquals(15, wayList.get(0).getFirstLat(), 1e-3);
        assertEquals(10, wayList.get(0).getFirstLon(), 1e-3);

        assertEquals(Instruction.TURN_LEFT, wayList.get(1).getSign());
        assertEquals(15.1, wayList.get(1).getFirstLat(), 1e-3);
        assertEquals(10, wayList.get(1).getFirstLon(), 1e-3);

        assertEquals(Instruction.TURN_RIGHT, wayList.get(2).getSign());
        assertEquals(15.1, wayList.get(2).getFirstLat(), 1e-3);
        assertEquals(9.9, wayList.get(2).getFirstLon(), 1e-3);

        String gpxStr = wayList.createGPX("test", 0);
        verifyGPX(gpxStr);

        assertTrue(gpxStr, gpxStr.contains("<trkpt lat=\"15.0\" lon=\"10.0\"><time>1970-01-01T00:00:00Z</time>"));
        assertTrue(gpxStr, gpxStr.contains("<extensions>") && gpxStr.contains("</extensions>"));
        assertTrue(gpxStr, gpxStr.contains("<rtept lat=\"15.1\" lon=\"10.0\">"));
        assertTrue(gpxStr, gpxStr.contains("<gh:distance>8000.0</gh:distance>"));
        assertTrue(gpxStr, gpxStr.contains("<desc>turn left onto 2-3</desc>"));
        assertTrue(gpxStr, gpxStr.contains("<gh:sign>-2</gh:sign>"));

        assertTrue(gpxStr, gpxStr.contains("<gh:direction>N</gh:direction>"));
        assertTrue(gpxStr, gpxStr.contains("<gh:azimuth>0.0</gh:azimuth>"));

        assertFalse(gpxStr, gpxStr.contains("NaN"));
    }

    @Test
    public void testRoundaboutJsonIntegrity() {
        InstructionList il = new InstructionList(usTR);

        PointList pl = new PointList();
        pl.add(52.514, 13.349);
        pl.add(52.5135, 13.35);
        pl.add(52.514, 13.351);
        RoundaboutInstruction instr = new RoundaboutInstruction(Instruction.USE_ROUNDABOUT, "streetname",
                new InstructionAnnotation(0, ""), pl)
                .setDirOfRotation(-0.1)
                .setRadian(-Math.PI + 1)
                .setExitNumber(2)
                .setExited();
        il.add(instr);

        Map<String, Object> json = il.createJson().get(0);
        // assert that all information is present in map for JSON
        assertEquals("At roundabout, take exit 2 onto streetname", json.get("text").toString());
        assertEquals(-1, (Double) json.get("turn_angle"), 0.01);
        assertEquals("2", json.get("exit_number").toString());
        // assert that a valid JSON object can be written
        assertNotNull(write(json));
    }

    private String write(Map<String, Object> json) {
        try {
            return new ObjectMapper().writeValueAsString(json);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    // Roundabout with unknown dir of rotation
    @Test
    public void testRoundaboutJsonNaN() {
        InstructionList il = new InstructionList(usTR);

        PointList pl = new PointList();
        pl.add(52.514, 13.349);
        pl.add(52.5135, 13.35);
        pl.add(52.514, 13.351);
        RoundaboutInstruction instr = new RoundaboutInstruction(Instruction.USE_ROUNDABOUT, "streetname",
                new InstructionAnnotation(0, ""), pl)
                .setRadian(-Math.PI + 1)
                .setExitNumber(2)
                .setExited();
        il.add(instr);

        Map<String, Object> json = il.createJson().get(0);
        assertEquals("At roundabout, take exit 2 onto streetname", json.get("text").toString());
        assertNull(json.get("turn_angle"));
        // assert that a valid JSON object can be written
        assertNotNull(write(json));
    }

    @Test
    public void testCreateGPXWithEle() {
        final List<GPXEntry> fakeList = new ArrayList<GPXEntry>();
        fakeList.add(new GPXEntry(12, 13, 0));
        fakeList.add(new GPXEntry(12.5, 13, 1000));
        InstructionList il = new InstructionList(usTR) {
            @Override
            public List<GPXEntry> createGPXList() {
                return fakeList;
            }
        };
        String gpxStr = il.createGPX("test", 0);
        verifyGPX(gpxStr);
        assertFalse(gpxStr, gpxStr.contains("NaN"));
        assertFalse(gpxStr, gpxStr.contains("<ele>"));

        fakeList.clear();
        fakeList.add(new GPXEntry(12, 13, 11, 0));
        fakeList.add(new GPXEntry(12.5, 13, 10, 1000));
        gpxStr = il.createGPX("test", 0, true, true, true, true);

        assertTrue(gpxStr, gpxStr.contains("<ele>11.0</ele>"));
        assertFalse(gpxStr, gpxStr.contains("NaN"));
    }

    @Test
    public void testCreateGPX() {
        InstructionAnnotation ea = InstructionAnnotation.EMPTY;
        InstructionList instructions = new InstructionList(usTR);
        PointList pl = new PointList();
        pl.add(49.942576, 11.580384);
        pl.add(49.941858, 11.582422);
        instructions.add(new Instruction(Instruction.CONTINUE_ON_STREET, "temp", ea, pl).setDistance(240).setTime(15000));

        pl = new PointList();
        pl.add(49.941575, 11.583501);
        instructions.add(new Instruction(Instruction.TURN_LEFT, "temp2", ea, pl).setDistance(25).setTime(4000));

        pl = new PointList();
        pl.add(49.941389, 11.584311);
        instructions.add(new Instruction(Instruction.TURN_LEFT, "temp2", ea, pl).setDistance(25).setTime(3000));
        instructions.add(new FinishInstruction(49.941029, 11.584514, 0));

        List<GPXEntry> result = instructions.createGPXList();
        assertEquals(5, result.size());

        assertEquals(0, result.get(0).getTime());
        assertEquals(10391, result.get(1).getTime());
        assertEquals(15000, result.get(2).getTime());
        assertEquals(19000, result.get(3).getTime());
        assertEquals(22000, result.get(4).getTime());

        verifyGPX(instructions.createGPX());
    }

    @Test
    public void testEmptyList() {
        Graph g = new GraphBuilder(carManager, json).create();
        Path p = new Dijkstra(g, new ShortestWeighting(carEncoder), tMode).calcPath(0, 1);
        InstructionList il = p.createPathExtract(carManager, false).calcInstructions(usTR);
        assertEquals(0, il.size());
        assertEquals(0, il.createStartPoints().size());
    }

    public void verifyGPX(String gpx) {
        SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        Schema schema = null;
        try {
            Source schemaFile = new StreamSource(getClass().getResourceAsStream("gpx-schema.xsd"));
            schema = schemaFactory.newSchema(schemaFile);

            // using more schemas: http://stackoverflow.com/q/1094893/194609
        } catch (SAXException e1) {
            throw new IllegalStateException("There was a problem with the schema supplied for validation. Message:" + e1.getMessage());
        }
        Validator validator = schema.newValidator();
        try {
            validator.validate(new StreamSource(new StringReader(gpx)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testFind() {
        Graph g = new GraphBuilder(carManager, json).create();
        //   n-4-5   (n: pillar node)
        //   |
        // 7-3-2-6
        //     |
        //     1
        NodeAccess na = g.getNodeAccess();
        na.setNode(1, 15.0, 10);
        na.setNode(2, 15.1, 10);
        na.setNode(3, 15.1, 9.9);
        PointList waypoint = new PointList();
        waypoint.add(15.2, 9.9);
        na.setNode(4, 15.2, 10);
        na.setNode(5, 15.2, 10.1);
        na.setNode(6, 15.1, 10.1);
        na.setNode(7, 15.1, 9.8);

        GHUtility.createEdge(g, carAverageSpeedEnc, 60, carAccessEnc, 1, 2, true, 10000).setName("1-2");
        GHUtility.createEdge(g, carAverageSpeedEnc, 60, carAccessEnc, 2, 3, true, 10000).setName("2-3");
        GHUtility.createEdge(g, carAverageSpeedEnc, 60, carAccessEnc, 2, 6, true, 10000).setName("2-6");
        GHUtility.createEdge(g, carAverageSpeedEnc, 60, carAccessEnc, 3, 4, true, 10000).setName("3-4").setWayGeometry(waypoint);
        GHUtility.createEdge(g, carAverageSpeedEnc, 60, carAccessEnc, 3, 7, true, 10000).setName("3-7");
        GHUtility.createEdge(g, carAverageSpeedEnc, 60, carAccessEnc, 4, 5, true, 10000).setName("4-5");

        Path p = new Dijkstra(g, new ShortestWeighting(carEncoder), tMode).calcPath(1, 5);
        InstructionList wayList = p.createPathExtract(carManager, false).calcInstructions(usTR);

        // query on first edge, get instruction for second edge
        assertEquals("2-3", wayList.find(15.05, 10, 1000).getName());

        // query east of first edge, get instruction for second edge
        assertEquals("2-3", wayList.find(15.05, 10.001, 1000).getName());

        // query south-west of node 3, get instruction for third edge
        assertEquals("3-4", wayList.find(15.099, 9.9, 1000).getName());
    }

    @Test
    public void testXMLEscape_issue572() {
        assertEquals("_", InstructionList.simpleXMLEscape("<"));
        assertEquals("_blup_", InstructionList.simpleXMLEscape("<blup>"));
        assertEquals("a&amp;b", InstructionList.simpleXMLEscape("a&b"));
    }
}
