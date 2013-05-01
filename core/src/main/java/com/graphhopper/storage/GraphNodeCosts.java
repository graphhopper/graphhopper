package com.graphhopper.storage;

import com.graphhopper.util.EdgeIterator;

/**
 * Provides the possibility to store node cost tables
 * for each node, which can be used for more exact routing.
 * Also for each node and each edge the OSMid will be stored 
 * provided.
 * 
 * @author Karl Hübner
 */
public interface GraphNodeCosts extends Graph {

    /**
     * when a node should be stored in the graph, this method should called
     * instead of {@link Graph#setNode(int, double, double)}. 
     * 
     * @see Graph#setNode(int, double, double)
     */
    void setNode(int index, double lat, double lon, long osmId);

    /**
     * @return the osm id of the node
     */
    long getOsmId(int index);
    
    /**
     * @return the osm id of the edge
     */
    long getEdgeOsmId(int edge);
    
    /**
     * adds a new cost entry for the node <code>nodeIndex</code>
     */
    void addNodeCostEntry(int nodeIndex, int from, int to, double costs); 
    
    /**
     * when an edge should be stored in the graph, this method should called
     * instead of {@link Graph#edge(int, int, double, int)}. 
     * 
     * @see Graph#edge(int, int, double, int)
     */
    EdgeIterator edge(int a, int b, double distance, int flags, long osmid);

    /**
     * @return the turn costs for the requested turn
     */
	double getTurnCosts(int currNode, int edgeFrom, int edgeTo);
}
