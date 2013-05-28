package com.graphhopper.reader;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.graphhopper.GraphHopper;
import com.graphhopper.routing.util.AcceptWay;
import com.graphhopper.routing.util.TurnCostEncoder;
import com.graphhopper.storage.AbstractGraphTester;
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.storage.GraphStorageTurnCosts;
import com.graphhopper.storage.GraphTurnCosts;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.util.EdgeIterator;

/**
 * tests, if with {@link GraphStorageTurnCosts} everything stays the same,
 * except that turn relations will be imported
 * 
 * @author Karl Hübner
 *
 */
public class OSMReaderTurnCostsTest extends OSMReaderTest {
    
    private String file6 = "test-osm5.xml";

    @Override
    GraphStorage buildGraph(String directory) {
        return new GraphStorageTurnCosts(new RAMDirectory(directory, false));
    }
    
    @Test public void testImportTurnRestrictions() {
        GraphHopper hopper = new GraphHopperTest(file6).
                enableTurnCosts().
                acceptWay(new AcceptWay(true, false, true)).
                importOrLoad();
        GraphTurnCosts g = (GraphTurnCosts)hopper.graph();

        assertEquals(9, g.nodes());
        
        int n1 = AbstractGraphTester.getIdOf(g, 51.0, 9.0);
        int n2 = AbstractGraphTester.getIdOf(g, 51.2, 9.0);
        int n3 = AbstractGraphTester.getIdOf(g, 51.2, 9.1);        
        int n4 = AbstractGraphTester.getIdOf(g, 51.2, 9.2);
        int n5 = AbstractGraphTester.getIdOf(g, 51.0, 9.2);
        int n6 = AbstractGraphTester.getIdOf(g, 51.1, 9.1);
        //int n7 = AbstractGraphTester.getIdOf(g, 51.4, 9.2);
        int n8 = AbstractGraphTester.getIdOf(g, 51.4, 9.1);
        int n9 = AbstractGraphTester.getIdOf(g, 51.4, 9.0);

        //node3 : restriction for turn (2,3)->(3,8) and (2,3)->(3,9), since only (2,3)->(3,4) is allowed 
        //+ everything allowed from other directions, except (4,3)->(3,8) since there is a 'no_right_turn'restriction
        assertEquals(TurnCostEncoder.restriction(), g.turnCosts(n3, e(g, n2, n3), e(g, n3, n9)), 0);
        assertEquals(TurnCostEncoder.restriction(), g.turnCosts(n3, e(g, n2, n3), e(g, n3, n8)), 0);
        assertEquals(TurnCostEncoder.restriction(), g.turnCosts(n3, e(g, n4, n3), e(g, n3, n8)), 0);
        assertEquals(TurnCostEncoder.noCosts(), g.turnCosts(n3, e(g, n2, n3), e(g, n3, n4)), 0);
        assertEquals(TurnCostEncoder.noCosts(), g.turnCosts(n3, e(g, n4, n3), e(g, n3, n2)), 0);
        assertEquals(TurnCostEncoder.noCosts(), g.turnCosts(n3, e(g, n8, n3), e(g, n3, n9)), 0);
        assertEquals(TurnCostEncoder.noCosts(), g.turnCosts(n3, e(g, n8, n3), e(g, n3, n2)), 0);
        
        //node5 : restriction for turn (4,5)->(5,1) since there is a 'right_turn_only' restriction 
        //+ everything allowed from other directions
        assertEquals(TurnCostEncoder.noCosts(), g.turnCosts(n5, e(g, n4, n5), e(g, n5, n6)), 0);
        assertEquals(TurnCostEncoder.restriction(), g.turnCosts(n5, e(g, n4, n5), e(g, n5, n1)), 0);        
        assertEquals(TurnCostEncoder.noCosts(), g.turnCosts(n5, e(g, n1, n5), e(g, n5, n4)), 0);
        assertEquals(TurnCostEncoder.noCosts(), g.turnCosts(n5, e(g, n1, n5), e(g, n5, n6)), 0);
        assertEquals(TurnCostEncoder.noCosts(), g.turnCosts(n5, e(g, n6, n5), e(g, n5, n4)), 0);
        assertEquals(TurnCostEncoder.noCosts(), g.turnCosts(n5, e(g, n6, n5), e(g, n5, n1)), 0);
        
        //node1 : restriction for turn (6,1)->(1,5) since there is a 'no_u_turn' restriction 
        //+ everything allowed from other directions
        assertEquals(TurnCostEncoder.restriction(), g.turnCosts(n1, e(g, n6, n1), e(g, n1, n5)), 0);
        assertEquals(TurnCostEncoder.noCosts(), g.turnCosts(n1, e(g, n6, n1), e(g, n1, n2)), 0);                
        assertEquals(TurnCostEncoder.noCosts(), g.turnCosts(n1, e(g, n5, n1), e(g, n1, n2)), 0);
        assertEquals(TurnCostEncoder.noCosts(), g.turnCosts(n1, e(g, n5, n1), e(g, n1, n6)), 0);
        assertEquals(TurnCostEncoder.noCosts(), g.turnCosts(n1, e(g, n2, n1), e(g, n1, n5)), 0);
        assertEquals(TurnCostEncoder.noCosts(), g.turnCosts(n1, e(g, n2, n1), e(g, n1, n6)), 0);
    }
    
   

    private int e(GraphTurnCosts graph, int nodeStart, int nodeEnd) {
        EdgeIterator edges = graph.getEdges(nodeStart);
        while(edges.next()){
            if(edges.adjNode() == nodeEnd){
                return edges.edge();
            }
        }
        return EdgeIterator.NO_EDGE;
    }
    
}
