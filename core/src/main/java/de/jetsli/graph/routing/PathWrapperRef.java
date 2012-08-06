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

import de.jetsli.graph.storage.EdgeEntry;
import de.jetsli.graph.storage.Graph;

/**
 * This class creates a DijkstraPath from two Edge's resulting from a BidirectionalDijkstra
 *
 * @author Peter Karich, info@jetsli.de
 */
public class PathWrapperRef {

    public EdgeEntry edgeFrom;
    public EdgeEntry edgeTo;
    public double weight;
    public boolean switchWrapper = false;
    private Graph g;

    public PathWrapperRef(Graph g) {
        this.g = g;
    }

    /**
     * Extracts path from two shortest-path-tree
     */
    public Path extract() {
        if (edgeFrom == null || edgeTo == null)
            return null;

        if (edgeFrom.node != edgeTo.node)
            throw new IllegalStateException("Locations of the 'to'- and 'from'-Edge has to be the same." + toString());

        if (switchWrapper) {
            EdgeEntry ee = edgeFrom;
            edgeFrom = edgeTo;
            edgeTo = ee;
        }
        
        Path path = new Path();
        EdgeEntry currEdge = edgeFrom;
        while (currEdge.prevEntry != null) {
            int tmpFrom = currEdge.node;
            path.add(tmpFrom);
            currEdge = currEdge.prevEntry;
            path.updateProperties(g.getIncoming(tmpFrom), currEdge.node);
        }
        path.add(currEdge.node);
        path.reverseOrder();

        currEdge = edgeTo;
        while (currEdge.prevEntry != null) {
            int tmpTo = currEdge.node;
            currEdge = currEdge.prevEntry;
            path.add(currEdge.node);
            path.updateProperties(g.getIncoming(currEdge.node), tmpTo);
        }

        return path;
    }

    @Override public String toString() {
        return "distance:" + weight + ", from:" + edgeFrom + ", to:" + edgeTo;
    }
}
