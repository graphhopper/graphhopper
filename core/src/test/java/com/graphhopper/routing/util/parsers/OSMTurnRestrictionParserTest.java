package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.osm.OSMTurnRestriction;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.TransportationMode;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.util.GHUtility;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OSMTurnRestrictionParserTest {
    static BooleanEncodedValue accessEnc = new SimpleBooleanEncodedValue("access", true);
    static DecimalEncodedValue speedEnc = new DecimalEncodedValueImpl("speed", 5, 5, false);
    static DecimalEncodedValue turnCostEnc = TurnCost.create("car", 1);
    static EncodingManager em = EncodingManager.start().add(accessEnc).add(speedEnc).addTurnCostEncodedValue(turnCostEnc).build();

    @Test
    public void testOnlyRestriction() {
        OSMTurnRestriction restriction = new OSMTurnRestriction(1, 4, 3, 3, OSMTurnRestriction.RestrictionType.ONLY, OSMTurnRestriction.ViaType.NODE);
        BaseGraph graph = parseRestrictionOnTestGraph(restriction);

        assertTrue(Double.isInfinite(graph.getTurnCostStorage().get(turnCostEnc, 4, 3, 6)));
        assertEquals(0, graph.getTurnCostStorage().get(turnCostEnc, 4, 3, 3), .1);
        assertTrue(Double.isInfinite(graph.getTurnCostStorage().get(turnCostEnc, 4, 3, 2)));
    }

    @Test
    public void testNotRestriction() {
        OSMTurnRestriction restriction = new OSMTurnRestriction(1, 4, 3, 3, OSMTurnRestriction.RestrictionType.NOT, OSMTurnRestriction.ViaType.NODE);
        BaseGraph graph = parseRestrictionOnTestGraph(restriction);

        assertTrue(Double.isInfinite(graph.getTurnCostStorage().get(turnCostEnc, 4, 3, 3)));
    }


    private static BaseGraph parseRestrictionOnTestGraph(OSMTurnRestriction restriction) {
        OSMTurnRestrictionParser parser = new OSMTurnRestrictionParser(accessEnc, turnCostEnc, OSMRoadAccessParser.toOSMRestrictions(TransportationMode.CAR));
        BaseGraph graph = new BaseGraph.Builder(em.getIntsForFlags()).withTurnCosts(true).create();
        initGraph(graph, accessEnc, speedEnc);
        TurnCostParser.ExternalInternalMap map = initMap();
        parser.addRestrictionToTCStorage(restriction, map, graph);
        return graph;
    }

    //  0--[0]--1
    //  |       |
    // [1]     [2] 
    //  |       |
    //  2--[3]--3--[4]--4
    //  |       |       |
    // [5]     [6]     [7]
    //  |       |       |
    //  5--[8]--6--[9]--7
    private static void initGraph(BaseGraph graph, BooleanEncodedValue accessEnc, DecimalEncodedValue speedEnc) {
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(0, 1).setDistance(3));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(0, 2).setDistance(1));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(1, 3).setDistance(1));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(2, 3).setDistance(1));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(3, 4).setDistance(1));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(2, 5).setDistance(0.5));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(3, 6).setDistance(1));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(4, 7).setDistance(1));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(5, 6).setDistance(1));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(6, 7).setDistance(1));
    }

    private static TurnCostParser.ExternalInternalMap initMap() {
        final Map<Long, Integer> osmNodeToInternal = new HashMap<>();
        final Map<Integer, Long> internalToOSMEdge = new HashMap<>();

        osmNodeToInternal.put(3L, 3);
        // edge ids are only stored if they occurred before in an OSMRelation
        internalToOSMEdge.put(3, 3L);
        internalToOSMEdge.put(4, 4L);

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

        return map;
    }
}