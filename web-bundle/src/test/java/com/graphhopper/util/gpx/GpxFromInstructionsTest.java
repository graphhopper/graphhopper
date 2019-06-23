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

package com.graphhopper.util.gpx;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.Dijkstra;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.profiles.BooleanEncodedValue;
import com.graphhopper.routing.profiles.Roundabout;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.ShortestWeighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.*;
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
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.*;

public class GpxFromInstructionsTest {

    private EncodingManager carManager;
    private FlagEncoder carEncoder;
    private BooleanEncodedValue roundaboutEnc;
    private TranslationMap trMap;

    @Before
    public void setUp() {
        carEncoder = new CarFlagEncoder();
        carManager = EncodingManager.create(carEncoder);
        roundaboutEnc = carManager.getBooleanEncodedValue(Roundabout.KEY);
        trMap = new TranslationMap().doImport();
    }

    @Test
    public void testInstructionsWithTimeAndPlace() {
        Graph g = new GraphBuilder(carManager).create();
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

        g.edge(1, 2, 7000, true).setName("1-2").setFlags(flagsForSpeed(carManager, 70));
        g.edge(2, 3, 8000, true).setName("2-3").setFlags(flagsForSpeed(carManager, 80));
        g.edge(2, 6, 10000, true).setName("2-6").setFlags(flagsForSpeed(carManager, 10));
        g.edge(3, 4, 9000, true).setName("3-4").setFlags(flagsForSpeed(carManager, 90));
        g.edge(3, 7, 10000, true).setName("3-7").setFlags(flagsForSpeed(carManager, 10));
        g.edge(4, 5, 10000, true).setName("4-5").setFlags(flagsForSpeed(carManager, 100));

        Path p = new Dijkstra(g, new ShortestWeighting(carEncoder), TraversalMode.NODE_BASED).calcPath(1, 5);
        InstructionList wayList = p.calcInstructions(roundaboutEnc, trMap.getWithFallBack(Locale.US));
        PointList points = p.calcPoints();
        assertEquals(4, wayList.size());

        assertEquals(34000, p.getDistance(), 1e-1);
        assertEquals(34000, sumDistances(wayList), 1e-1);
        assertEquals(5, points.size());
        assertEquals(1604120, p.getTime());

        assertEquals(Instruction.CONTINUE_ON_STREET, wayList.get(0).getSign());
        assertEquals(15, wayList.get(0).getPoints().getLatitude(0), 1e-3);
        assertEquals(10, wayList.get(0).getPoints().getLongitude(0), 1e-3);

        assertEquals(Instruction.TURN_LEFT, wayList.get(1).getSign());
        assertEquals(15.1, wayList.get(1).getPoints().getLatitude(0), 1e-3);
        assertEquals(10, wayList.get(1).getPoints().getLongitude(0), 1e-3);

        assertEquals(Instruction.TURN_RIGHT, wayList.get(2).getSign());
        assertEquals(15.1, wayList.get(2).getPoints().getLatitude(0), 1e-3);
        assertEquals(9.9, wayList.get(2).getPoints().getLongitude(0), 1e-3);

        String gpxStr = GpxFromInstructions.createGPX(wayList, "test", (long) 0, false, true, true, true, Constants.VERSION, trMap.getWithFallBack(Locale.US));
        verifyGPX(gpxStr);
//        System.out.println(gpxStr);

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
    public void testCreateGPX() {
        InstructionAnnotation ea = InstructionAnnotation.EMPTY;
        InstructionList instructions = new InstructionList(trMap.getWithFallBack(Locale.US));
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

        List<GPXEntry> result = GpxFromInstructions.createGPXList(instructions);
        assertEquals(5, result.size());

        assertEquals(0, result.get(0).getTime().longValue());
        assertNull(result.get(1).getTime());
        assertEquals(15000, result.get(2).getTime().longValue());
        assertEquals(19000, result.get(3).getTime().longValue());
        assertEquals(22000, result.get(4).getTime().longValue());

        verifyGPX(GpxFromInstructions.createGPX(instructions, "GraphHopper", new Date().getTime(), false, true, true, true, Constants.VERSION, trMap.getWithFallBack(Locale.US)));
    }

    @Test
    public void testCreateGPXIncludesRoundaboutExitNumber() {
        InstructionList instructions = new InstructionList(trMap.getWithFallBack(Locale.US));

        PointList pl = new PointList();
        pl.add(52.555423473315, 13.43890086052345);
        pl.add(52.555550691982, 13.43946393816465);
        pl.add(52.555619423589, 13.43886994061328);
        RoundaboutInstruction instr = new RoundaboutInstruction(Instruction.USE_ROUNDABOUT, "streetname",
                InstructionAnnotation.EMPTY, pl)
                .setRadian(2.058006514284998d)
                .setExitNumber(3)
                .setExited();
        instructions.add(instr);
        instructions.add(new FinishInstruction(52.555619423589, 13.43886994061328, 0));

        String gpxStr = GpxFromInstructions.createGPX(instructions, "test", 0, true, true, false, false, Constants.VERSION, trMap.getWithFallBack(Locale.US));

        assertTrue(gpxStr, gpxStr.contains("<gh:exit_number>3</gh:exit_number>"));
        verifyGPX(gpxStr);
    }

    @Test
    public void testCreateGPXCorrectFormattingSmallNumbers() {
        InstructionList instructions = new InstructionList(trMap.getWithFallBack(Locale.US));

        PointList pl = new PointList();
        pl.add(0.000001, 0.000001);
        pl.add(-0.000123, -0.000125);
        Instruction instruction = new Instruction(0, "do it", null, pl);
        instructions.add(instruction);
        instructions.add(new FinishInstruction(0.000852, 0.000852, 0));

        String gpxStr = GpxFromInstructions.createGPX(instructions, "test", 0, true, true, true, true, Constants.VERSION, trMap.getWithFallBack(Locale.US));

        assertFalse(gpxStr, gpxStr.contains("E-"));
        assertTrue(gpxStr, gpxStr.contains("0.000001"));
        assertTrue(gpxStr, gpxStr.contains("-0.000125"));
        verifyGPX(gpxStr);
    }

    @Test
    public void testXMLEscape_issue572() {
        assertEquals("_", GpxFromInstructions.simpleXMLEscape("<"));
        assertEquals("_blup_", GpxFromInstructions.simpleXMLEscape("<blup>"));
        assertEquals("a&amp;b", GpxFromInstructions.simpleXMLEscape("a&b"));
    }

    private IntsRef flagsForSpeed(EncodingManager encodingManager, int speedKmPerHour) {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "motorway");
        way.setTag("maxspeed", String.format("%d km/h", speedKmPerHour));
        EncodingManager.AcceptWay map = new EncodingManager.AcceptWay();
        encodingManager.acceptWay(way, map);
        return encodingManager.handleWayTags(way, map, 0);
    }

    private void verifyGPX(String gpx) {
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

    private double sumDistances(InstructionList il) {
        double val = 0;
        for (Instruction i : il) {
            val += i.getDistance();
        }
        return val;
    }

}
