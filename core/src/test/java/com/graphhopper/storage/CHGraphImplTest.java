package com.graphhopper.storage;

import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.FootFlagEncoder;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.TurnCostProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CHGraphImplTest {

    @Test
    public void testBigWeight() {
        FlagEncoder foot = new FootFlagEncoder();
        GraphHopperStorage graph = new GraphBuilder(EncodingManager.create(foot)).setDir(new RAMDirectory()).build();
        FastestWeighting weighting = new FastestWeighting(foot, TurnCostProvider.NO_TURN_COST_PROVIDER);
        graph.addCHGraph(new CHConfig("p_" + foot.toString(), weighting, false));

        graph.create(1000);
        graph.getNodeAccess().setNode(0, 10, 10);
        graph.getNodeAccess().setNode(1, 10.1, 10.1);
        graph.freeze();

        CHGraphImpl g = (CHGraphImpl) graph.getCHGraph();
        g.shortcut(0, 0, 0, 10, 0, 1);

        g.setShortcutWeight(0, (Integer.MAX_VALUE >> 2) / 1000d + 1000);
        assertEquals((Integer.MAX_VALUE >> 2) / 1000d + 1000, g.getShortcutWeight(0));

        g.setShortcutWeight(0, (Integer.MAX_VALUE >> 1) / 1000d - 0.001);
        assertEquals((Integer.MAX_VALUE >> 1) / 1000d - 0.001, g.getShortcutWeight(0), 0.001);

        g.setShortcutWeight(0, (Integer.MAX_VALUE >> 1) / 1000d);
        assertTrue(Double.isInfinite(g.getShortcutWeight(0)));
        g.setShortcutWeight(0, (Integer.MAX_VALUE >> 1) / 1000d + 1);
        assertTrue(Double.isInfinite(g.getShortcutWeight(0)));
        g.setShortcutWeight(0, (Integer.MAX_VALUE >> 1) / 1000d + 100);
        assertTrue(Double.isInfinite(g.getShortcutWeight(0)));
    }
}