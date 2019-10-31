package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.OSMTurnRelation;
import com.graphhopper.routing.EdgeBasedRoutingAlgorithmTest;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphHopperStorage;
import org.junit.Test;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class OSMTurnCostParserTest {

    @Test
    public void testGetRestrictionAsEntries() {
        CarFlagEncoder encoder = new CarFlagEncoder(5, 5, 1);
        final Map<Long, Integer> osmNodeToInternal = new HashMap<>();
        final Map<Integer, Long> internalToOSMEdge = new HashMap<>();

        osmNodeToInternal.put(3L, 3);
        // edge ids are only stored if they occurred before in an OSMRelation
        internalToOSMEdge.put(3, 3L);
        internalToOSMEdge.put(4, 4L);

        OSMTurnCostParser parser = new OSMTurnCostParser(encoder.toString(), 1);
        GraphHopperStorage ghStorage = new GraphBuilder(new EncodingManager.Builder().add(encoder).addTurnCostParser(parser).build()).create();
        EdgeBasedRoutingAlgorithmTest.initGraph(ghStorage);
        OSMTurnCostParser.OSMInternalMap map = new OSMTurnCostParser.OSMInternalMap() {

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

        // TYPE == ONLY
        OSMTurnRelation instance = new OSMTurnRelation(4, 3, 3, OSMTurnRelation.Type.ONLY);
        Collection<TurnCostParser.TCEntry> result = parser.getRestrictionAsEntries(instance,
                ghStorage.getEncodingManager().createTurnCostFlags(), map, ghStorage);

        assertEquals(2, result.size());
        Iterator<TurnCostParser.TCEntry> iter = result.iterator();
        TurnCostParser.TCEntry entry = iter.next();
        assertEquals(4, entry.edgeFrom);
        assertEquals(6, entry.edgeTo);
        assertEquals(3, entry.nodeVia);

        entry = iter.next();
        assertEquals(4, entry.edgeFrom);
        assertEquals(2, entry.edgeTo);
        assertEquals(3, entry.nodeVia);

        // TYPE == NOT
        instance = new OSMTurnRelation(4, 3, 3, OSMTurnRelation.Type.NOT);
        result = parser.getRestrictionAsEntries(instance, ghStorage.getEncodingManager().createTurnCostFlags(), map, ghStorage);

        assertEquals(1, result.size());
        iter = result.iterator();
        entry = iter.next();
        assertEquals(4, entry.edgeFrom);
        assertEquals(3, entry.edgeTo);
        assertEquals(3, entry.nodeVia);
    }
}