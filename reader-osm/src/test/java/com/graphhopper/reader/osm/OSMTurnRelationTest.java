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
import com.graphhopper.reader.OSMTurnRelation.Type;
import com.graphhopper.routing.EdgeBasedRoutingAlgorithmTest;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.EdgeExplorer;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Peter Karich
 */
public class OSMTurnRelationTest {
    @Test
    public void testGetRestrictionAsEntries() {
        CarFlagEncoder encoder = new CarFlagEncoder(5, 5, 1);
        final Map<Long, Integer> osmNodeToInternal = new HashMap<>();
        final Map<Integer, Long> internalToOSMEdge = new HashMap<>();

        osmNodeToInternal.put(3L, 3);
        // edge ids are only stored if they occured before in an OSMRelation
        internalToOSMEdge.put(3, 3L);
        internalToOSMEdge.put(4, 4L);

        GraphHopperStorage ghStorage = new GraphBuilder(EncodingManager.create(encoder)).create();
        EdgeBasedRoutingAlgorithmTest.initGraph(ghStorage);
        OSMReader osmReader = new OSMReader(ghStorage) {

            @Override
            public int getInternalNodeIdOfOsmNode(long nodeOsmId) {
                return osmNodeToInternal.get(nodeOsmId);
            }

            @Override
            public long getOsmIdOfInternalEdge(int edgeId) {
                Long l = internalToOSMEdge.get(edgeId);
                if (l == null)
                    return -1;
                return l;
            }
        };

        EdgeExplorer edgeExplorer = ghStorage.createEdgeExplorer();

        // TYPE == ONLY
        OSMTurnRelation instance = new OSMTurnRelation(4, 3, 3, Type.ONLY);
        Collection<OSMReader.TurnCostTableEntry> result
                = OSMReader.getRestrictionAsEntries(instance, encoder, edgeExplorer, edgeExplorer, osmReader);

        assertEquals(2, result.size());
        Iterator<OSMReader.TurnCostTableEntry> iter = result.iterator();
        OSMReader.TurnCostTableEntry entry = iter.next();
        assertEquals(4, entry.edgeFrom);
        assertEquals(6, entry.edgeTo);
        assertEquals(3, entry.nodeVia);

        entry = iter.next();
        assertEquals(4, entry.edgeFrom);
        assertEquals(2, entry.edgeTo);
        assertEquals(3, entry.nodeVia);

        // TYPE == NOT
        instance = new OSMTurnRelation(4, 3, 3, Type.NOT);
        result = OSMReader.getRestrictionAsEntries(instance, encoder, edgeExplorer, edgeExplorer, osmReader);

        assertEquals(1, result.size());
        iter = result.iterator();
        entry = iter.next();
        assertEquals(4, entry.edgeFrom);
        assertEquals(3, entry.edgeTo);
        assertEquals(3, entry.nodeVia);
    }

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
