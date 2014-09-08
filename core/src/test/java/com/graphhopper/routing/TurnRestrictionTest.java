package com.graphhopper.routing;

import gnu.trove.list.TIntList;

import java.util.Arrays;

import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;

import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.ShortestWeighting;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.util.TurnWeighting;
import com.graphhopper.routing.util.Weighting;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.storage.TurnCostStorage;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;

public class TurnRestrictionTest {

	Logger logger=Logger.getLogger(TurnRestrictionTest.class);
	
	
    // test helpers
    
    EdgeIteratorState addEdge( GraphStorage graph,FlagEncoder encoder,int from,int to){
    	 return graph.edge(from, to).setDistance(100).setFlags(encoder.setProperties(10, true,true));
         
    }
    void addTurnRestriction(GraphStorage graph,int from,int via,int to){
        TurnCostStorage turnCostStorage=(TurnCostStorage)graph.getExtendedStorage();
    	FlagEncoder encoder=graph.getEncodingManager().getSingle();
        turnCostStorage.addTurnInfo(via, getEdge(graph, from, via).getEdge(), getEdge(graph, via, to).getEdge(),encoder.getTurnFlags(true,0));
     
        
   }
   
    
    EdgeIteratorState getEdge(GraphStorage graph,int baseNode,int adjNode){
    	EdgeIterator edgeIterator=graph.createEdgeExplorer().setBaseNode(baseNode);
    	
    	while (edgeIterator.next())
    		if (edgeIterator.getAdjNode()==adjNode)
    			return edgeIterator;
    	return null;
    	
    }

    
    
    public void test(GraphStorage graph,int from,int to,int[] expected,TraversalMode tMode){
    	
//    	GraphStorage graph=createTestGraph(true);
    	FlagEncoder encoder=graph.getEncodingManager().getSingle();
    	
    	Weighting weighting=new TurnWeighting(new ShortestWeighting(), encoder, (TurnCostStorage)graph.getExtendedStorage());
    	RoutingAlgorithm dijkstra=new DijkstraOneToManyRef(graph, encoder, weighting, tMode);
    	
    	
    	Path path=dijkstra.calcPath(from, to);
    	path=dijkstra.calcPath(from, to);
    	// show path
    	
    	logger.info("route from "+from+" to "+to+" tMode:"+tMode);
    			
    	TIntList nodes=path.calcNodes();
    	logger.info(path);
    	for(int i=0;i<nodes.size();i++)
    		logger.info(nodes.get(i));
    	
    	// check
    	Assert.assertTrue(path.isFound());
    	Assert.assertTrue(Arrays.equals(nodes.toArray(),expected));
    }
    
  
    
    
    /*
	 * 0 ---- 1 ------ 2
	 *        |        |
	 *        5        | 
	 *        |        |
	 *        4--------3
	 */
    protected GraphStorage createSimpleTestGraph(boolean withTurnRestriction)
    {
    	
    	FlagEncoder encoder=new CarFlagEncoder(5, 5, 1);
    	EncodingManager em=new EncodingManager(encoder);
    	
        GraphStorage graph = new GraphBuilder(em).set3D(false).create();

        addEdge(graph,encoder,0, 1);
        addEdge(graph,encoder,1, 2);
        addEdge(graph,encoder,1, 5);
        addEdge(graph,encoder,2, 3);
        addEdge(graph,encoder,3, 4);
        addEdge(graph,encoder,4, 5);
        
        if (withTurnRestriction)
        	addTurnRestriction(graph, 0, 1, 5);
        
        return graph;
    }
    
    

    @Test
    public void testSimple(){
    	
    	
    	test(createSimpleTestGraph(true) ,0,4,new int[]{0,1,2,3,4},TraversalMode.EDGE_BASED_2DIR);
    	
    	// the simple sample works for all traversal modes 
    	for(TraversalMode tMode:TraversalMode.values()) {
	    	test(createSimpleTestGraph(true) ,0,4,new int[]{0,1,2,3,4},tMode);
	    	test(createSimpleTestGraph(false),0,4,new int[]{0,1,5,4},tMode);
    	}
    }
    
    
    
    
	/*
	 *
	 *    4----3      0
	 *    |    |      | 
	 *    |    ^      |
 	 *    |    |      |
	 *    5----2------1
	 *                |
	 *                |
	 *                6   
	 */
    protected GraphStorage createNamestiMiruGraph(boolean withTurnRestriction)
    {
    	
    	FlagEncoder encoder=new CarFlagEncoder(5, 5, 1);
    	EncodingManager em=new EncodingManager(encoder);
    	
        GraphStorage graph = new GraphBuilder(em).set3D(false).create();

        addEdge(graph,encoder,0, 1);
        addEdge(graph,encoder,1, 2);
        addEdge(graph,encoder,1, 6);
        addEdge(graph,encoder,2, 3).setFlags(encoder.setProperties(10, true,false));
        addEdge(graph,encoder,3, 4);
        addEdge(graph,encoder,4, 5);
        addEdge(graph,encoder,5, 2);
        
        if (withTurnRestriction)
        	addTurnRestriction(graph, 0, 1, 6);
        
        return graph;
    }
    
    
    
    @Test
    public void testNamestiMiruWithTurnRestrictionsEdgeBased2Dir(){
    	//test(createNamestiMiruGraph(false),0,6,new int[]{0,1,6},TraversalMode.EDGE_BASED_2DIR);
    	test(createNamestiMiruGraph(true),0,6,new int[]{0,1,2,3,4,5,2,1,6},TraversalMode.EDGE_BASED_2DIR);
    	
    }
//    
//    @Test
//    public void testNamestiMiruWithTurnRestrictionsNodeBased(){
//    	// fails to find a route, same as edgeBased1dir
//    	test(createNamestiMiruGraph(true),0,6,new int[]{0,1,2,3,4,5,2,1,6},TraversalMode.NODE_BASED);
//    	
//    }
//    
//    @Test
//    public void testNamestiMiruWithTurnRestrictionsEdgeBased2DirUturn(){
//    	// fails, create the invalid route. basically it does a u turn on the first node found, which is probably not realistic
//    	test(createNamestiMiruGraph(true),0,6,new int[]{0,1,2,3,4,5,2,1,6},TraversalMode.EDGE_BASED_2DIR_UTURN);
//    }
//    
//    
//    @Test
//    public void testNamestiMiruWithTurnRestrictionsEdgeBasedMode1Dir(){
//    	// fails to find a route, probably cause the node 1 is already marked as explored when the routing algo comes back to 1 after loop 2,3,4,5
//    	test(createNamestiMiruGraph(true),0,6,new int[]{0,1,2,3,4,5,2,1,6},TraversalMode.EDGE_BASED_1DIR);
//
//    }
//    
}
