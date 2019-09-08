package com.graphhopper.routing.weighting;

import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.TurnCostExtension;
import com.graphhopper.util.EdgeIteratorState;
import org.junit.Before;
import org.junit.Test;

import static com.graphhopper.util.GHUtility.getEdge;
import static org.junit.Assert.assertEquals;

public class TurnWeightingTest {

    private Graph graph;
    private FlagEncoder encoder;
    private EncodingManager encodingManager;
    private Weighting weighting;
    private TurnCostExtension turnCostExt;

    @Before
    public void setup() {
        encoder = new CarFlagEncoder(5, 5, 10);
        encodingManager = EncodingManager.create(encoder);
        graph = new GraphBuilder(encodingManager).create();
        weighting = new FastestWeighting(encoder);
        turnCostExt = (TurnCostExtension) graph.getExtension();
    }

    @Test
    public void calcWeightAndTime_withTurnCosts() {
        graph.edge(0, 1, 100, true);
        EdgeIteratorState edge = graph.edge(1, 2, 100, true);
        // turn costs are given in seconds
        addTurnCost(0, 1, 2, 5);
        TurnWeighting turnWeighting = new TurnWeighting(weighting, turnCostExt);
        assertEquals(6 + 5, turnWeighting.calcWeight(edge, false, 0), 1.e-6);
        assertEquals(6000 + 5000, turnWeighting.calcMillis(edge, false, 0), 1.e-6);
    }

    @Test
    public void calcWeightAndTime_uTurnCosts() {
        EdgeIteratorState edge = graph.edge(0, 1, 100, true);
        TurnWeighting turnWeighting = new TurnWeighting(weighting, turnCostExt, 40);
        assertEquals(6 + 40, turnWeighting.calcWeight(edge, false, 0), 1.e-6);
        assertEquals((6 + 40) * 1000, turnWeighting.calcMillis(edge, false, 0), 1.e-6);
    }

    @Test
    public void calcWeightAndTime_withTurnCosts_shortest() {
        graph.edge(0, 1, 100, true);
        EdgeIteratorState edge = graph.edge(1, 2, 100, true);
        // turn costs are given in seconds
        addTurnCost(0, 1, 2, 5);
        TurnWeighting turnWeighting = new TurnWeighting(new ShortestWeighting(encoder), turnCostExt);
        // todo: for the shortest weighting turn costs cannot be interpreted as seconds ? at least when they are added
        // to the weight ? how much should they contribute ?
//        assertEquals(105, turnWeighting.calcWeight(edge, false, 0), 1.e-6);
        assertEquals(6000 + 5000, turnWeighting.calcMillis(edge, false, 0), 1.e-6);
    }

    private void addTurnCost(int from, int via, int to, double turnCost) {
        long turnFlags = encoder.getTurnFlags(false, turnCost);
        turnCostExt.addTurnInfo(getEdge(graph, from, via).getEdge(), via, getEdge(graph, via, to).getEdge(), turnFlags);
    }

}