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
package com.graphhopper.reader.osm.conditional;

import com.graphhopper.reader.ReaderWay;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * @author Andrzej Oles
 */
public class ConditionalOSMSpeedInspectorTest {

    private static List<String> getSampleConditionalTags() {
        List<String> conditionalTags = new ArrayList<>();
        conditionalTags.add("maxspeed");
        conditionalTags.add("maxspeed:hgv");
        return conditionalTags;
    }

    private static ConditionalOSMSpeedInspector getConditionalSpeedInspector() {
        ConditionalOSMSpeedInspector acceptor = new ConditionalOSMSpeedInspector(getSampleConditionalTags());
        acceptor.addValueParser(ConditionalParser.createDateTimeParser());
        return acceptor;
    }

    @Test
    public void testNotConditional() {
        ConditionalOSMSpeedInspector acceptor = getConditionalSpeedInspector();
        ReaderWay way = new ReaderWay(1);
        way.setTag("maxspeed:hgv", "80");
        assertFalse(acceptor.hasConditionalSpeed(way));
        assertFalse(acceptor.hasLazyEvaluatedConditions());
    }

    @Test
    public void testConditionalTime() {
        ConditionalOSMSpeedInspector acceptor = getConditionalSpeedInspector();
        ReaderWay way = new ReaderWay(1);
        String tagValue = "60 @ (23:00-05:00)";
        way.setTag("maxspeed:conditional", tagValue);
        assertTrue(acceptor.hasConditionalSpeed(way));
        assertTrue(acceptor.hasLazyEvaluatedConditions());
        assertEquals(tagValue, acceptor.getTagValue());
    }

    @Test
    public void testMultipleTimes() {
        ConditionalOSMSpeedInspector acceptor = getConditionalSpeedInspector();
        ReaderWay way = new ReaderWay(1);
        String tagValue = "50 @ (05:00-23:00); 60 @ (23:00-05:00)";
        way.setTag("maxspeed:conditional", tagValue);
        assertTrue(acceptor.hasConditionalSpeed(way));
        assertTrue(acceptor.hasLazyEvaluatedConditions());
        assertEquals(tagValue, acceptor.getTagValue());
    }

    @Test
    public void testConditionalWeather() {
        ConditionalOSMSpeedInspector acceptor = getConditionalSpeedInspector();
        ReaderWay way = new ReaderWay(1);
        way.setTag("maxspeed:conditional", "60 @ snow");
        assertFalse(acceptor.hasConditionalSpeed(way));
        assertFalse(acceptor.hasLazyEvaluatedConditions());
    }

    @Test
    public void testConditionalWeight() {
        ConditionalOSMSpeedInspector acceptor = getConditionalSpeedInspector();
        ReaderWay way = new ReaderWay(1);
        way.setTag("maxspeed:conditional", "90 @ (weight>7.5)");
        assertFalse(acceptor.hasConditionalSpeed(way));
        acceptor.addValueParser(ConditionalParser.createNumberParser("weight", 3.5));
        assertFalse(acceptor.hasConditionalSpeed(way));
        assertFalse(acceptor.hasLazyEvaluatedConditions());
    }

    @Test
    public void testConditionalWeightApplies() {
        ConditionalOSMSpeedInspector acceptor = getConditionalSpeedInspector();
        ReaderWay way = new ReaderWay(1);
        way.setTag("maxspeed:conditional", "90 @ (weight>7.5)");
        assertFalse(acceptor.hasConditionalSpeed(way));
        acceptor.addValueParser(ConditionalParser.createNumberParser("weight", 10));
        assertTrue(acceptor.hasConditionalSpeed(way));
        assertFalse(acceptor.hasLazyEvaluatedConditions());
        assertEquals("90", acceptor.getTagValue());
    }

    @Test
    public void testMultipleWeights() {
        ConditionalOSMSpeedInspector acceptor = getConditionalSpeedInspector();
        ReaderWay way = new ReaderWay(1);
        way.setTag("maxspeed:hgv:conditional", "90 @ (weight<=3.5); 70 @ (weight>3.5)");
        assertFalse(acceptor.hasConditionalSpeed(way));
        acceptor.addValueParser(ConditionalParser.createNumberParser("weight", 3));
        assertTrue(acceptor.hasConditionalSpeed(way));
        assertFalse(acceptor.hasLazyEvaluatedConditions());
        assertEquals("90", acceptor.getTagValue());
        acceptor.addValueParser(ConditionalParser.createNumberParser("weight", 10));
        assertTrue(acceptor.hasConditionalSpeed(way));
        assertFalse(acceptor.hasLazyEvaluatedConditions());
        assertEquals("70", acceptor.getTagValue());
    }

    @Test
    public void testCombinedTimeWeight() {
        ConditionalOSMSpeedInspector acceptor = getConditionalSpeedInspector();
        ReaderWay way = new ReaderWay(1);
        way.setTag("maxspeed:hgv:conditional", "60 @ (22:00-05:00 AND weight>7.5)");
        assertFalse(acceptor.hasConditionalSpeed(way));
        acceptor.addValueParser(ConditionalParser.createNumberParser("weight", 10));
        assertTrue(acceptor.hasConditionalSpeed(way));
        assertTrue(acceptor.hasLazyEvaluatedConditions());
        assertEquals(acceptor.getTagValue(), "60 @ (22:00-05:00)");
        acceptor.addValueParser(ConditionalParser.createNumberParser("weight", 3));
        assertFalse(acceptor.hasConditionalSpeed(way));
    }
}
