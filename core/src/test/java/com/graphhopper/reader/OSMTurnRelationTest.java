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
package com.graphhopper.reader;

import com.graphhopper.reader.OSMTurnRelation.Type;
import com.graphhopper.routing.EdgeBasedRoutingAlgorithmTest;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.util.EdgeExplorer;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public class OSMTurnRelationTest
{
    @Test
    public void testGetRestrictionAsEntries()
    {
        CarFlagEncoder encoder = new CarFlagEncoder(5, 5, 3);
        final Map<Long, Integer> osmNodeToInternal = new HashMap<Long, Integer>();
        final Map<Integer, Long> internalToOSMEdge = new HashMap<Integer, Long>();
                
        osmNodeToInternal.put(3L, 3);
        // edge ids are only stored if they occured before in an OSMRelation
        internalToOSMEdge.put(3, 3L);        
        internalToOSMEdge.put(4, 4L);        

        GraphStorage graph = new GraphBuilder(new EncodingManager(encoder)).create();
        EdgeBasedRoutingAlgorithmTest.initGraph(graph);
        OSMReader osmReader = new OSMReader(graph)
        {

            @Override
            public int getInternalNodeIdOfOsmNode( long nodeOsmId )
            {
                return osmNodeToInternal.get(nodeOsmId);
            }

            @Override
            public long getOsmIdOfInternalEdge( int edgeId )
            {
                Long l = internalToOSMEdge.get(edgeId);
                if(l == null)
                    return -1;
                return l;
            }
        };

        EdgeExplorer edgeExplorer = graph.createEdgeExplorer();

        // TYPE == ONLY
        OSMTurnRelation instance = new OSMTurnRelation(4, 3, 3, Type.ONLY);
        Collection<OSMTurnRelation.TurnCostTableEntry> result
                = instance.getRestrictionAsEntries(encoder, edgeExplorer, edgeExplorer, osmReader);

        assertEquals(2, result.size());
        Iterator<OSMTurnRelation.TurnCostTableEntry> iter = result.iterator();
        OSMTurnRelation.TurnCostTableEntry entry = iter.next();
        assertEquals(4, entry.edgeFrom);
        assertEquals(6, entry.edgeTo);
        assertEquals(3, entry.nodeVia);
        
        entry = iter.next();
        assertEquals(4, entry.edgeFrom);
        assertEquals(2, entry.edgeTo);
        assertEquals(3, entry.nodeVia);
        
        
        // TYPE == NOT
        instance = new OSMTurnRelation(4, 3, 3, Type.NOT);
        result = instance.getRestrictionAsEntries(encoder, edgeExplorer, edgeExplorer, osmReader);

        assertEquals(1, result.size());
        iter = result.iterator();
        entry = iter.next();
        assertEquals(4, entry.edgeFrom);
        assertEquals(3, entry.edgeTo);
        assertEquals(3, entry.nodeVia);       
    }

}
