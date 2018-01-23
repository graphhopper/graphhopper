package com.graphhopper.routing;

import static com.graphhopper.util.GHUtility.getEdge;
import static org.junit.Assert.assertEquals;

import java.util.function.BiFunction;

import com.graphhopper.routing.util.BikeFlagEncoder;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.TurnCostExtension;

/**
 * @author Michael Reichert
 */
public class TurnFlagsReadWriteTest {

    // 0---1
    // |   /
    // 2--3
    // |
    // 4
    public static void initGraph(Graph g) {
        g.edge(0, 1, 3, true);
        g.edge(0, 2, 1, true);
        g.edge(1, 3, 1, true);
        g.edge(2, 3, 1, true);
        g.edge(2, 4, 1, true);
    }

    /**
     * Test if multiple turn costs can be safely written to the storage and read from it.
     */
    public void testMultipleTurnCosts() {
        FlagEncoder carEncoder1 = new CarFlagEncoder(5, 5, 3);
        FlagEncoder carEncoder2 = new BikeFlagEncoder(5, 5, 3);
        EncodingManager manager = new EncodingManager(carEncoder1, carEncoder2);
        GraphHopperStorage g = new GraphBuilder(manager).create();
        initGraph(g);
        TurnCostExtension tcs = (TurnCostExtension) g.getExtension();

        // introduce some turn costs
        long tflags = carEncoder1.getTurnFlags(true, 0);
        long tflagsLess = carEncoder1.getTurnFlags(false, 2);
        long tflags2 = carEncoder2.getTurnFlags(true, 0);
        long tflags2Less = carEncoder2.getTurnFlags(false, 2);

        // merge functions
        // overwrite
        BiFunction<Long, Long, Long> overwriteFunc = (oldFlags, newFlags) -> {
            return newFlags;
        };
        // binary OR
        // This is a suitable merge strategy if you have multiple flag encoders
        // and all flag encoders use their own bits in the integer which stores the flags.
        BiFunction<Long, Long, Long> orFunc = (oldFlags, newFlags) -> {
            return oldFlags | newFlags;
        };

        int edge42 = getEdge(g, 4, 2).getEdge();
        int edge23 = getEdge(g, 2, 3).getEdge();
        int edge31 = getEdge(g, 3, 1).getEdge();
        int edge10 = getEdge(g, 1, 0).getEdge();
        int edge02 = getEdge(g, 0, 2).getEdge();
        int edge24 = getEdge(g, 2, 4).getEdge();

        tcs.setAndMergeTurnInfo(edge42, 2, edge23, tflags, orFunc);
        tcs.setAndMergeTurnInfo(edge42, 2, edge23, tflags2, orFunc);
        tcs.setAndMergeTurnInfo(edge23, 3, edge31, tflags, orFunc);
        tcs.setAndMergeTurnInfo(edge23, 3, edge31, tflags2Less, orFunc);
        tcs.setAndMergeTurnInfo(edge31, 1, edge10, tflagsLess, orFunc);
        tcs.setAndMergeTurnInfo(edge31, 1, edge10, tflags2, orFunc);
        tcs.setAndMergeTurnInfo(edge02, 2, edge24, tflags, overwriteFunc);
        tcs.setAndMergeTurnInfo(edge02, 2, edge24, tflags2, overwriteFunc);

        long flags423 = tcs.getTurnCostFlags(edge42, 2, edge23);
        long flags231 = tcs.getTurnCostFlags(edge23, 3, edge31);
        long flags310 = tcs.getTurnCostFlags(edge31, 1, edge10);
        long flags024 = tcs.getTurnCostFlags(edge02, 2, edge24);
        assertEquals(3L, flags423 & 3L);
        assertEquals(12L, flags423 & 12L);
        assertEquals(3L, flags231 & 3L);
        assertEquals(8L, flags231 & 12L);
        assertEquals(2L, flags310 & 3L);
        assertEquals(12L, flags310 & 12L);
        assertEquals(0L, flags024 & 3L);
        assertEquals(12L, flags024 & 12L);
        assertEquals(Double.POSITIVE_INFINITY, carEncoder1.getTurnCost(flags423), 0);
        assertEquals(Double.POSITIVE_INFINITY, carEncoder2.getTurnCost(flags423), 0);
        assertEquals(Double.POSITIVE_INFINITY, carEncoder1.getTurnCost(flags231), 0);
        assertEquals(2.0, carEncoder2.getTurnCost(flags231), 0);
        assertEquals(2.0, carEncoder1.getTurnCost(flags310), 0);
        assertEquals(Double.POSITIVE_INFINITY, carEncoder2.getTurnCost(flags310), 0);
        assertEquals(0.0, carEncoder1.getTurnCost(flags024), 0);
        assertEquals(Double.POSITIVE_INFINITY, carEncoder2.getTurnCost(flags024), 0);
    }
}
