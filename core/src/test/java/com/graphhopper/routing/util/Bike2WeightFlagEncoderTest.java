/*
 *  Licensed to Peter Karich under one or more contributor license
 *  agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  Peter Karich licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the
 *  License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.routing.util;

import com.graphhopper.reader.OSMWay;
import com.graphhopper.storage.*;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.Helper;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public class Bike2WeightFlagEncoderTest extends BikeFlagEncoderTest
{
    private Graph initExampleGraph( FlagEncoder instance )
    {
        EncodingManager em = new EncodingManager(instance);
        GraphStorage gs = new GraphHopperStorage(new RAMDirectory(), em, true).create(1000);
        NodeAccess na = gs.getNodeAccess();
        // 50--(0.0001)-->49--(0.0004)-->55--(0.0005)-->60
        na.setNode(0, 51.1, 12.001, 50);
        na.setNode(1, 51.1, 12.002, 60);
        EdgeIteratorState edge = gs.edge(0, 1).
                setWayGeometry(Helper.createPointList3D(51.1, 12.0011, 49, 51.1, 12.0015, 55));
        edge.setDistance(100);

        edge.setFlags(instance.setReverseSpeed(instance.setProperties(10, true, true), 15));
        return gs;
    }

    @Test
    public void testApplyWayTags()
    {
        Bike2WeightFlagEncoder instance = new Bike2WeightFlagEncoder();
        Graph graph = initExampleGraph(instance);
        EdgeIteratorState edge = GHUtility.getEdge(graph, 0, 1);
        OSMWay way = new OSMWay(1);
        instance.applyWayTags(way, edge);

        long flags = edge.getFlags();
        // decrease speed
        assertEquals(2, instance.getSpeed(flags), 1e-1);
        // increase speed but use maximum speed (calculated was 24)
        assertEquals(18, instance.getReverseSpeed(flags), 1e-1);
    }
        
    @Test
    public void testUnchangedForStepsBridgeAndTunnel()
    {
        Bike2WeightFlagEncoder instance = new Bike2WeightFlagEncoder();
        Graph graph = initExampleGraph(instance);
        EdgeIteratorState edge = GHUtility.getEdge(graph, 0, 1);
        long oldFlags = edge.getFlags();
        OSMWay way = new OSMWay(1);
        way.setTag("highway", "steps");
        instance.applyWayTags(way, edge);

        assertEquals(oldFlags, edge.getFlags());
    }
}
