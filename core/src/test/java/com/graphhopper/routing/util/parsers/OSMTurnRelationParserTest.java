package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.OSMTurnRelation;
import com.graphhopper.routing.EdgeBasedRoutingAlgorithmTest;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.TransportationMode;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.TurnCostStorage;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OSMTurnRelationParserTest {

    @Test
    public void testGetRestrictionAsEntries() {
        CarFlagEncoder encoder = new CarFlagEncoder(5, 5, 1);
        final Map<Long, Integer> osmNodeToInternal = new HashMap<>();
        final Map<Integer, Long> internalToOSMEdge = new HashMap<>();

        osmNodeToInternal.put(3L, 3);
        // edge ids are only stored if they occurred before in an OSMRelation
        internalToOSMEdge.put(3, 3L);
        internalToOSMEdge.put(4, 4L);

        OSMTurnRelationParser parser = new OSMTurnRelationParser(encoder.toString(), 1, OSMRoadAccessParser.toOSMRestrictions(TransportationMode.CAR));
        GraphHopperStorage ghStorage = new GraphBuilder(new EncodingManager.Builder().add(encoder).addTurnCostParser(parser).build()).create();
        EdgeBasedRoutingAlgorithmTest.initGraph(ghStorage, encoder);
        TurnCostParser.ExternalInternalMap map = new TurnCostParser.ExternalInternalMap() {

            @Override
            public int getInternalNodeIdOfOsmNode(long nodeOsmId) {
                return osmNodeToInternal.getOrDefault(nodeOsmId, -1);
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
        parser.addRelationToTCStorage(instance, map, ghStorage);

        TurnCostStorage tcs = ghStorage.getTurnCostStorage();
        DecimalEncodedValue tce = parser.getTurnCostEnc();
        assertTrue(Double.isInfinite(tcs.get(tce, 4, 3, 6)));
        assertEquals(0, tcs.get(tce, 4, 3, 3), .1);
        assertTrue(Double.isInfinite(tcs.get(tce, 4, 3, 2)));

        // TYPE == NOT
        instance = new OSMTurnRelation(4, 3, 3, OSMTurnRelation.Type.NOT);
        parser.addRelationToTCStorage(instance, map, ghStorage);
        assertTrue(Double.isInfinite(tcs.get(tce, 4, 3, 3)));
    }
}