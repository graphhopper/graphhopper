/*
 *  Copyright 2012 Peter Karich 
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.storage;

import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeWriteIterator;
import com.graphhopper.util.shapes.BBox;

/**
 * An interface to represent a (geo) graph - suited for efficient storage as it can be requested via
 * ids. Querying via lat,lon can be done via with a Location2IDIndex implementation
 *
 * @author Peter Karich,
 */
public interface Graph {

    /**
     * @return the number of indirectly created locations. E.g. via setNode() or edge()
     */
    int getNodes();

    /**
     * This method ensures that the node with the specified index exists and sets the lat+lon to the
     * specified values. The index goes from 0 (inclusive) to getNodes() (exclusive)
     */
    void setNode(int index, double lat, double lon);

    double getLatitude(int index);

    double getLongitude(int index);

    /**
     * Returns the implicit bounds of this graph calculated from the lat,lon input of setNode
     */
    BBox getBounds();

    /**
     * @param a index of the starting node of the edge
     * @param b index of the ending node of the edge
     * @param distance necessary if no setNode is called - e.g. if the graph is not a geo-graph
     * @param flags see EdgeFlags - involves velocity and direction
     */
    void edge(int a, int b, double distance, int flags);

    void edge(int a, int b, double distance, boolean bothDirections);

    /**
     * @return an edge iterator over one element where the method next() has no meaning and will
     * return false. The edge will point to the bigger node if endNode is negative otherwise it'll
     * be used as the end node.
     * @throws IllegalStateException if edgeId is not valid
     */
    EdgeIterator getEdgeProps(int edgeId, int endNode);

    EdgeIterator getAllEdges();
    
    /**
     * Returns an iterator which makes it possible to traverse all edges of the specified node
     * index. Hint: use GraphUtility to go straight to certain neighbor nodes. Hint: edges with both
     * directions will returned only once!
     */
    EdgeIterator getEdges(int index);

    EdgeIterator getIncoming(int index);

    EdgeIterator getOutgoing(int index);

    /**
     * @return the specified graph g
     */
    Graph copyTo(Graph g);

    /**
     * Schedule the deletion of the specified node until an optimize() call happens
     */
    void markNodeDeleted(int index);

    boolean isDeleted(int index);

    /**
     * Performs optimization routines like deletion or node rearrangements.
     */
    void optimize();
}
