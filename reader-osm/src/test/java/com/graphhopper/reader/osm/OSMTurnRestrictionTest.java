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

import com.graphhopper.reader.OSMTurnRestriction;
import com.graphhopper.reader.ReaderRelation;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
public class OSMTurnRestrictionTest {

    public static final List<String> VEHICLE_TYPES = new ArrayList<>(Arrays.asList("motorcar", "motor_vehicle", "vehicle"));

    @Test
    public void testAcceptsTurnRelation1() {
        ReaderRelation readerRelation = new ReaderRelation(-1);
        readerRelation.add(new ReaderRelation.Member(ReaderRelation.Member.WAY, 1, "from"));
        readerRelation.add(new ReaderRelation.Member(ReaderRelation.Member.NODE, 1, "via"));
        readerRelation.add(new ReaderRelation.Member(ReaderRelation.Member.WAY, 1, "to"));
        readerRelation.setTag("restriction", "no_left_turn");
        assertTrue(new OSMTurnRestriction(readerRelation).isVehicleTypeConcernedByTurnRestriction(VEHICLE_TYPES));
    }

    @Test
    public void testAcceptsTurnRelation2() {
        ReaderRelation readerRelation = new ReaderRelation(-1);
        readerRelation.add(new ReaderRelation.Member(ReaderRelation.Member.WAY, 1, "from"));
        readerRelation.add(new ReaderRelation.Member(ReaderRelation.Member.NODE, 1, "via"));
        readerRelation.add(new ReaderRelation.Member(ReaderRelation.Member.WAY, 1, "to"));
        readerRelation.setTag("restriction", "no_left_turn");
        readerRelation.setTag("except", "bus");
        assertTrue(new OSMTurnRestriction(readerRelation).isVehicleTypeConcernedByTurnRestriction(VEHICLE_TYPES));
    }

    @Test
    public void testAcceptsTurnRelation3() {
        ReaderRelation readerRelation = new ReaderRelation(-1);
        readerRelation.add(new ReaderRelation.Member(ReaderRelation.Member.WAY, 1, "from"));
        readerRelation.add(new ReaderRelation.Member(ReaderRelation.Member.NODE, 1, "via"));
        readerRelation.add(new ReaderRelation.Member(ReaderRelation.Member.WAY, 1, "to"));
        readerRelation.setTag("restriction", "no_left_turn");
        readerRelation.setTag("except", "vehicle");
        assertFalse(new OSMTurnRestriction(readerRelation).isVehicleTypeConcernedByTurnRestriction(VEHICLE_TYPES));
    }

    @Test
    public void testAcceptsTurnRelation4() {
        ReaderRelation readerRelation = new ReaderRelation(-1);
        readerRelation.add(new ReaderRelation.Member(ReaderRelation.Member.WAY, 1, "from"));
        readerRelation.add(new ReaderRelation.Member(ReaderRelation.Member.NODE, 1, "via"));
        readerRelation.add(new ReaderRelation.Member(ReaderRelation.Member.WAY, 1, "to"));
        readerRelation.setTag("restriction", "no_left_turn");
        readerRelation.setTag("except", "motor_vehicle");
        readerRelation.setTag("except", "vehicle");
        assertFalse(new OSMTurnRestriction(readerRelation).isVehicleTypeConcernedByTurnRestriction(VEHICLE_TYPES));
    }

    @Test
    public void testAcceptsTurnRelation5() {
        ReaderRelation readerRelation = new ReaderRelation(-1);
        readerRelation.add(new ReaderRelation.Member(ReaderRelation.Member.WAY, 1, "from"));
        readerRelation.add(new ReaderRelation.Member(ReaderRelation.Member.NODE, 1, "via"));
        readerRelation.add(new ReaderRelation.Member(ReaderRelation.Member.WAY, 1, "to"));
        readerRelation.setTag("restriction:bus", "no_left_turn");
        assertFalse(new OSMTurnRestriction(readerRelation).isVehicleTypeConcernedByTurnRestriction(VEHICLE_TYPES));
    }

    @Test
    public void testAcceptsTurnRelation6() {
        ReaderRelation readerRelation = new ReaderRelation(-1);
        readerRelation.add(new ReaderRelation.Member(ReaderRelation.Member.WAY, 1, "from"));
        readerRelation.add(new ReaderRelation.Member(ReaderRelation.Member.NODE, 1, "via"));
        readerRelation.add(new ReaderRelation.Member(ReaderRelation.Member.WAY, 1, "to"));
        readerRelation.setTag("restriction:vehicle", "no_left_turn");
        assertTrue(new OSMTurnRestriction(readerRelation).isVehicleTypeConcernedByTurnRestriction(VEHICLE_TYPES));
    }
}
