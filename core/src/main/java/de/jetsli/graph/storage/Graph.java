/*
 *  Copyright 2012 Peter Karich info@jetsli.de
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
package de.jetsli.graph.storage;

import de.jetsli.graph.util.EdgeIterator;

/**
 * An interface to represent a (geo) graph - suited for efficient storage as it can be requested via
 * ids. Querying via lat,lon can be done via with a Location2IDIndex implementation
 *
 * @author Peter Karich, info@jetsli.de
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
     * @param a index of the starting node of the edge
     * @param b index of the ending node of the edge
     * @param distance necessary if no setNode is called - e.g. if the graph is not a geo-graph
     * @param flags see CarFlags - involves velocity and direction
     */    
    void edge(int a, int b, double distance, int flags);
    
    void edge(int a, int b, double distance, boolean bothDirections);

    EdgeIterator getEdges(int index);

    EdgeIterator getIncoming(int index);

    EdgeIterator getOutgoing(int index);

    Graph clone();

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
