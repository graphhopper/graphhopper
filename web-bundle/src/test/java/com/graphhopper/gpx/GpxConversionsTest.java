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

package com.graphhopper.gpx;

import com.graphhopper.routing.Dijkstra;
import com.graphhopper.routing.InstructionsFromEdges;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValueImpl;
import com.graphhopper.routing.ev.SimpleBooleanEncodedValue;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.ShortestWeighting;
import com.graphhopper.routing.weighting.TurnCostProvider;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

import static com.graphhopper.search.KVStorage.KeyValue.STREET_NAME;
import static com.graphhopper.search.KVStorage.KeyValue.createKV;
import static org.junit.jupiter.api.Assertions.*;

public class GpxConversionsTest {

    private BooleanEncodedValue accessEnc;
    private DecimalEncodedValue speedEnc;
    private EncodingManager carManager;
    private TranslationMap trMap;

    @BeforeEach
    public void setUp() {
        accessEnc = new SimpleBooleanEncodedValue("access", true);
        speedEnc = new DecimalEncodedValueImpl("speed", 5, 5, false);
        carManager = EncodingManager.start().add(accessEnc).add(speedEnc).build();
        trMap = new TranslationMap().doImport();
    }

    @Test
    public void testInstructionsWithTimeAndPlace() {
        BaseGraph g = new BaseGraph.Builder(carManager).create();
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

        GHUtility.setSpeed(63, true, true, accessEnc, speedEnc, g.edge(1, 2).setDistance(7000).setKeyValues(createKV(STREET_NAME, "1-2")));
        GHUtility.setSpeed(72, true, true, accessEnc, speedEnc, g.edge(2, 3).setDistance(8000).setKeyValues(createKV(STREET_NAME, "2-3")));
        GHUtility.setSpeed(9, true, true, accessEnc, speedEnc, g.edge(2, 6).setDistance(10000).setKeyValues(createKV(STREET_NAME, "2-6")));
        GHUtility.setSpeed(81, true, true, accessEnc, speedEnc, g.edge(3, 4).setDistance(9000).setKeyValues(createKV(STREET_NAME, "3-4")));
        GHUtility.setSpeed(9, true, true, accessEnc, speedEnc, g.edge(3, 7).setDistance(10000).setKeyValues(createKV(STREET_NAME, "3-7")));
        GHUtility.setSpeed(90, true, true, accessEnc, speedEnc, g.edge(4, 5).setDistance(10000).setKeyValues(createKV(STREET_NAME, "4-5")));

        Weighting weighting = new ShortestWeighting(accessEnc, speedEnc);
        Path p = new Dijkstra(g, weighting, TraversalMode.NODE_BASED).calcPath(1, 5);
        InstructionList wayList = InstructionsFromEdges.calcInstructions(p, g, weighting, carManager, trMap.getWithFallBack(Locale.US));
        PointList points = p.calcPoints();
        assertEquals(4, wayList.size());

        assertEquals(34000, p.getDistance(), 1e-1);
        assertEquals(34000, sumDistances(wayList), 1e-1);
        assertEquals(5, points.size());
        assertEquals(1604121, p.getTime());

        assertEquals(Instruction.CONTINUE_ON_STREET, wayList.get(0).getSign());
        assertEquals("1-2", wayList.get(0).getName());
        assertEquals(15, wayList.get(0).getPoints().getLat(0), 1e-3);
        assertEquals(10, wayList.get(0).getPoints().getLon(0), 1e-3);

        assertEquals(Instruction.TURN_LEFT, wayList.get(1).getSign());
        assertEquals(15.1, wayList.get(1).getPoints().getLat(0), 1e-3);
        assertEquals(10, wayList.get(1).getPoints().getLon(0), 1e-3);

        assertEquals(Instruction.TURN_RIGHT, wayList.get(2).getSign());
        assertEquals(15.1, wayList.get(2).getPoints().getLat(0), 1e-3);
        assertEquals(9.9, wayList.get(2).getPoints().getLon(0), 1e-3);

        String gpxStr = GpxConversions.createGPX(wayList, "test", (long) 0, false, true, true, true, Constants.VERSION, trMap.getWithFallBack(Locale.US));
        verifyGPX(gpxStr);
//        System.out.println(gpxStr);

        assertTrue(gpxStr.contains("<trkpt lat=\"15.0\" lon=\"10.0\"><time>1970-01-01T00:00:00Z</time>"), gpxStr);
        assertTrue(gpxStr.contains("<extensions>") && gpxStr.contains("</extensions>"), gpxStr);
        assertTrue(gpxStr.contains("<rtept lat=\"15.1\" lon=\"10.0\">"), gpxStr);
        assertTrue(gpxStr.contains("<gh:distance>8000.0</gh:distance>"), gpxStr);
        assertTrue(gpxStr.contains("<desc>turn left onto 2-3</desc>"), gpxStr);
        assertTrue(gpxStr.contains("<gh:sign>-2</gh:sign>"), gpxStr);

        assertTrue(gpxStr.contains("<gh:direction>N</gh:direction>"), gpxStr);
        assertTrue(gpxStr.contains("<gh:azimuth>0.0</gh:azimuth>"), gpxStr);

        assertFalse(gpxStr.contains("NaN"));
    }

    @Test
    public void testCreateGPX() {
        InstructionList instructions = new InstructionList(trMap.getWithFallBack(Locale.US));
        PointList pl = new PointList();
        pl.add(49.942576, 11.580384);
        pl.add(49.941858, 11.582422);
        instructions.add(new Instruction(Instruction.CONTINUE_ON_STREET, "temp", pl).setDistance(240).setTime(15000));

        pl = new PointList();
        pl.add(49.941575, 11.583501);
        instructions.add(new Instruction(Instruction.TURN_LEFT, "temp2", pl).setDistance(25).setTime(4000));

        pl = new PointList();
        pl.add(49.941389, 11.584311);
        instructions.add(new Instruction(Instruction.TURN_LEFT, "temp2", pl).setDistance(25).setTime(3000));
        instructions.add(new FinishInstruction(49.941029, 11.584514, 0));

        List<GpxConversions.GPXEntry> result = GpxConversions.createGPXList(instructions);
        assertEquals(5, result.size());

        assertEquals(0, result.get(0).getTime().longValue());
        assertNull(result.get(1).getTime());
        assertEquals(15000, result.get(2).getTime().longValue());
        assertEquals(19000, result.get(3).getTime().longValue());
        assertEquals(22000, result.get(4).getTime().longValue());

        verifyGPX(GpxConversions.createGPX(instructions, "GraphHopper", new Date().getTime(), false, true, true, true, Constants.VERSION, trMap.getWithFallBack(Locale.US)));
    }

    @Test
    public void testCreateGPXIncludesRoundaboutExitNumber() {
        InstructionList instructions = new InstructionList(trMap.getWithFallBack(Locale.US));

        PointList pl = new PointList();
        pl.add(52.555423473315, 13.43890086052345);
        pl.add(52.555550691982, 13.43946393816465);
        pl.add(52.555619423589, 13.43886994061328);
        RoundaboutInstruction instr = new RoundaboutInstruction(Instruction.USE_ROUNDABOUT, "streetname", pl)
                .setRadian(2.058006514284998d)
                .setExitNumber(3)
                .setExited();
        instructions.add(instr);
        instructions.add(new FinishInstruction(52.555619423589, 13.43886994061328, 0));

        String gpxStr = GpxConversions.createGPX(instructions, "test", 0, true, true, false, false, Constants.VERSION, trMap.getWithFallBack(Locale.US));

        assertTrue(gpxStr.contains("<gh:exit_number>3</gh:exit_number>"), gpxStr);
        verifyGPX(gpxStr);
    }

    @Test
    public void testCreateGPXCorrectFormattingSmallNumbers() {
        InstructionList instructions = new InstructionList(trMap.getWithFallBack(Locale.US));

        PointList pl = new PointList();
        pl.add(0.000001, 0.000001);
        pl.add(-0.000123, -0.000125);
        Instruction instruction = new Instruction(0, "do it", pl);
        instructions.add(instruction);
        instructions.add(new FinishInstruction(0.000852, 0.000852, 0));

        String gpxStr = GpxConversions.createGPX(instructions, "test", 0, true, true, true, true, Constants.VERSION, trMap.getWithFallBack(Locale.US));

        assertFalse(gpxStr.contains("E-"), gpxStr);
        assertTrue(gpxStr.contains("0.000001"), gpxStr);
        assertTrue(gpxStr.contains("-0.000125"), gpxStr);
        verifyGPX(gpxStr);
    }

    @Test
    public void testXMLEscape_issue572() {
        assertEquals("_", GpxConversions.simpleXMLEscape("<"));
        assertEquals("_blup_", GpxConversions.simpleXMLEscape("<blup>"));
        assertEquals("a&amp;b", GpxConversions.simpleXMLEscape("a&b"));
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

    @Test
    public void testCalcAzimuthAndGetDirection() {
        PointList pl = new PointList();
        pl.add(49.942, 11.584);

        PointList nextPl = new PointList();
        nextPl.add(49.942, 11.582);
        Instruction currI = new Instruction(Instruction.CONTINUE_ON_STREET, "temp", pl);
        Instruction nextI = new Instruction(Instruction.CONTINUE_ON_STREET, "next", nextPl);

        assertEquals(270, GpxConversions.calcAzimuth(currI, nextI), .1);
        assertEquals("W", GpxConversions.calcDirection(currI, nextI));

        PointList p2 = new PointList();
        p2.add(49.942, 11.580);
        p2.add(49.944, 11.582);
        Instruction i2 = new Instruction(Instruction.CONTINUE_ON_STREET, "temp", p2);

        assertEquals(32.76, GpxConversions.calcAzimuth(i2, null), .1);
        assertEquals("NE", GpxConversions.calcDirection(i2, null));

        PointList p3 = new PointList();
        p3.add(49.942, 11.580);
        p3.add(49.944, 11.580);
        Instruction i3 = new Instruction(Instruction.CONTINUE_ON_STREET, "temp", p3);

        assertEquals(0, GpxConversions.calcAzimuth(i3, null), .1);
        assertEquals("N", GpxConversions.calcDirection(i3, null));

        PointList p4 = new PointList();
        p4.add(49.940, 11.580);
        p4.add(49.920, 11.586);
        Instruction i4 = new Instruction(Instruction.CONTINUE_ON_STREET, "temp", p4);

        assertEquals("S", GpxConversions.calcDirection(i4, null));

        PointList p5 = new PointList();
        p5.add(49.940, 11.580);
        Instruction i5 = new Instruction(Instruction.CONTINUE_ON_STREET, "temp", p5);

        assertTrue(Double.isNaN(GpxConversions.calcAzimuth(i5, null)));
        assertEquals("", GpxConversions.calcDirection(i5, null));
    }

}
