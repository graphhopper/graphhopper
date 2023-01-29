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

package com.graphhopper.reader.osm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.graphhopper.reader.osm.RestrictionType.NO;
import static com.graphhopper.reader.osm.RestrictionType.ONLY;
import static org.junit.jupiter.api.Assertions.*;

class RestrictionTagParserTest {
    private final Map<String, Object> tags = new LinkedHashMap<>();

    @BeforeEach
    void setup() {
        tags.clear();
        tags.put("type", "restriction");
    }

    private RestrictionTagParser.Result parseForVehicleTypes(String... vehicleTypes) throws OSMRestrictionException {
        return new RestrictionTagParser(Arrays.asList(vehicleTypes), null).parseRestrictionTags(tags);
    }

    @Test
    void noRestrictions() {
        OSMRestrictionException e = assertThrows(OSMRestrictionException.class, () -> parseForVehicleTypes("motorcar"));
        assertTrue(e.getMessage().contains("neither has a 'restriction' nor 'restriction:' tags"), e.getMessage());
    }

    @Test
    void exceptButNoRestriction() {
        tags.put("restriction:bicycle", "no_right_turn");
        tags.put("except", "psv");
        OSMRestrictionException e = assertThrows(OSMRestrictionException.class, () -> parseForVehicleTypes("psv"));
        assertTrue(e.getMessage().contains("has an 'except', but no 'restriction' or 'restriction:conditional' tag"), e.getMessage());
    }

    @Test
    void exceptButOnlyLimitedRestriction() {
        tags.put("restriction:hgv:conditional", "no_right_turn @ (weight > 3.5)");
        tags.put("except", "psv");
        OSMRestrictionException e = assertThrows(OSMRestrictionException.class, () -> parseForVehicleTypes("psv"));
        assertTrue(e.getMessage().contains("has an 'except', but no 'restriction' or 'restriction:conditional' tag"), e.getMessage());
    }

    @Test
    void exceptWithConditional() throws OSMRestrictionException {
        tags.put("restriction:conditional", "no_right_turn @ (weight > 3.5)");
        tags.put("except", "psv");
        // we do not handle conditional restrictions yet, but no warning for except+restriction:conditional,
        // because this combination of tags could make sense
        assertNull(parseForVehicleTypes("psv"));
    }

    @Test
    void restrictionAndLimitedRestriction() {
        // restriction and restriction:vehicle
        tags.put("restriction", "no_left_turn");
        tags.put("restriction:psv", "no_left_turn");
        OSMRestrictionException e = assertThrows(OSMRestrictionException.class, () -> parseForVehicleTypes("psv"));
        assertTrue(e.getMessage().contains("has a 'restriction' tag, but also 'restriction:' tags"), e.getMessage());
    }

    @Test
    void restrictionAndLimitedRestriction_giveWay() throws OSMRestrictionException {
        // this could happen for real, or at least it wouldn't be nonsensical. we ignore give_way so far, but do not
        // ignore or warn about the entire relation
        tags.put("restriction:bicycle", "give_way");
        tags.put("restriction", "no_left_turn");
        RestrictionTagParser.Result res = parseForVehicleTypes("bicycle");
        assertEquals("no_left_turn", res.getRestriction());
        assertEquals(NO, res.getRestrictionType());
    }

    @Test
    void bicycle_giveWay() {
        tags.put("restriction:bicycle", "give_way");
        OSMRestrictionException e = assertThrows(OSMRestrictionException.class, () -> parseForVehicleTypes("bicycle"));
        assertTrue(e.isWithoutWarning());
    }

    @Test
    void conditional() throws OSMRestrictionException {
        // So far we are ignoring conditional restrictions, even though for example weight restrictions could
        // be interesting, even though some of them could probably be tagged as restriction:hgv
        tags.put("restriction:conditional", "no_left_turn @ (weight > 3.5)");
        assertNull(parseForVehicleTypes("motorcar"));
    }

    @Test
    void except() throws OSMRestrictionException {
        tags.put("restriction", "no_left_turn");
        tags.put("except", "psv");
        assertNull(parseForVehicleTypes("psv"));
    }

    @Test
    void exceptOther() throws OSMRestrictionException {
        tags.put("restriction", "only_left_turn");
        tags.put("except", "psv");
        RestrictionTagParser.Result res = parseForVehicleTypes("motorcar");
        assertEquals("only_left_turn", res.getRestriction());
        assertEquals(ONLY, res.getRestrictionType());
    }

    @Test
    void limitedToVehicle() throws OSMRestrictionException {
        tags.put("restriction:motorcar", "no_left_turn");
        RestrictionTagParser.Result res = parseForVehicleTypes("motorcar");
        assertEquals("no_left_turn", res.getRestriction());
        assertEquals(NO, res.getRestrictionType());
    }

    @Test
    void limitedToOtherVehicle() throws OSMRestrictionException {
        tags.put("restriction:motorcar", "no_left_turn");
        RestrictionTagParser.Result res = parseForVehicleTypes("bicycle");
        assertNull(res);
    }

    @Test
    void limitedMultiple() throws OSMRestrictionException {
        tags.put("restriction:motorcar", "no_left_turn");
        tags.put("restriction:psv", "no_left_turn");
        RestrictionTagParser.Result res = parseForVehicleTypes("motorcar");
        assertEquals("no_left_turn", res.getRestriction());
        assertEquals(NO, res.getRestrictionType());
    }

    @Test
    void limitedMultipleOther() throws OSMRestrictionException {
        tags.put("restriction:motorcar", "no_left_turn");
        tags.put("restriction:psv", "no_left_turn");
        RestrictionTagParser.Result res = parseForVehicleTypes("bicycle");
        assertNull(res);
    }

    @Test
    void multipleVehicleTypes() throws OSMRestrictionException {
        tags.put("restriction", "only_right_turn");
        RestrictionTagParser.Result res = parseForVehicleTypes("motorcar", "motor_vehicle", "vehicle");
        assertEquals("only_right_turn", res.getRestriction());
        assertEquals(ONLY, res.getRestrictionType());
    }

    @Test
    void exceptBus() throws OSMRestrictionException {
        tags.put("restriction", "only_right_turn");
        // todo: how should we handle except=bus? should it be excluded for psv?
        tags.put("except", "psv");
        RestrictionTagParser.Result res = parseForVehicleTypes("motorcar", "motor_vehicle", "vehicle");
        assertEquals("only_right_turn", res.getRestriction());
        assertEquals(ONLY, res.getRestrictionType());
        assertNull(parseForVehicleTypes("psv"));
    }
}