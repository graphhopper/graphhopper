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

import com.graphhopper.reader.OSMTurnRelation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Peter Karich
 */
public class OSMTurnRelationTest {
    @Test
    public void testAcceptsTurnRelation() {
        List<String> vehicleTypes = new ArrayList<>(Arrays.asList("motorcar", "motor_vehicle", "vehicle"));
        List<String> vehicleTypesExcept = new ArrayList<>();
        OSMTurnRelation osmTurnRelation = new OSMTurnRelation(1, 1, 1, OSMTurnRelation.Type.NOT);
        assertTrue(osmTurnRelation.isVehicleTypeConcernedByTurnRestriction(vehicleTypes));

        vehicleTypesExcept.add("bus");
        osmTurnRelation.setVehicleTypesExcept(vehicleTypesExcept);
        assertTrue(osmTurnRelation.isVehicleTypeConcernedByTurnRestriction(vehicleTypes));

        vehicleTypesExcept.clear();
        vehicleTypesExcept.add("vehicle");
        osmTurnRelation.setVehicleTypesExcept(vehicleTypesExcept);
        assertFalse(osmTurnRelation.isVehicleTypeConcernedByTurnRestriction(vehicleTypes));

        vehicleTypesExcept.clear();
        vehicleTypesExcept.add("motor_vehicle");
        vehicleTypesExcept.add("vehicle");
        osmTurnRelation.setVehicleTypesExcept(vehicleTypesExcept);
        assertFalse(osmTurnRelation.isVehicleTypeConcernedByTurnRestriction(vehicleTypes));

        vehicleTypesExcept.clear();
        osmTurnRelation.setVehicleTypeRestricted("bus");
        osmTurnRelation.setVehicleTypesExcept(vehicleTypesExcept);
        assertFalse(osmTurnRelation.isVehicleTypeConcernedByTurnRestriction(vehicleTypes));

        osmTurnRelation.setVehicleTypeRestricted("vehicle");
        assertTrue(osmTurnRelation.isVehicleTypeConcernedByTurnRestriction(vehicleTypes));
    }
}
