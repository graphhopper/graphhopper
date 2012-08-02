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
package de.jetsli.graph.routing;

import de.jetsli.graph.storage.Edge;

/**
 * This class creates a DijkstraPath from two Edge's resulting from a BidirectionalDijkstra
 * 
 * @author Peter Karich, info@jetsli.de
 */
public class PathWrapperRef {

    public Edge edgeFrom;
    public Edge edgeTo;
    public double distance;

    public PathWrapperRef() {
    }

    /**
     * Extracts path from two shortest-path-tree
     */
    public Path extract() {
        if (edgeFrom == null || edgeTo == null)
            return null;
        
        if (edgeFrom.node != edgeTo.node)
            throw new IllegalStateException("Locations of the 'to'- and 'from'-Edge has to be the same." + toString());        

        Path path = new Path();
        Edge curr = edgeFrom;
        while (curr != null) {
            path.add(curr);
            curr = curr.prevEntry;
        }
        path.reverseOrder();

        double fromDistance = path.distance();
        double toDistance = edgeTo.weight;
        curr = edgeTo.prevEntry;
        while (curr != null) {
            path.add(curr);
            curr = curr.prevEntry;
        }
        // we didn't correct the distances of the other to-Edge for performance reasons
        path.setDistance(fromDistance + toDistance);
        return path;
    }

    @Override public String toString() {
        return "distance:" + distance + ", from:" + edgeFrom + ", to:" + edgeTo;
    }
}
