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

import de.jetsli.graph.routing.util.WeightCalculation;
import de.jetsli.graph.storage.EdgeEntry;
import de.jetsli.graph.storage.Graph;

/**
 * This class creates a DijkstraPath from two Edge's resulting from a BidirectionalDijkstra
 *
 * @author Peter Karich, info@jetsli.de
 */
public class PathBidirRef extends Path {

    public EdgeEntry edgeFrom;
    public EdgeEntry edgeTo;
    public boolean switchWrapper = false;
    protected Graph g;

    public PathBidirRef(Graph g, WeightCalculation weightCalculation) {
        super(weightCalculation);
        this.g = g;
    }

    /**
     * Extracts path from two shortest-path-tree
     */
    @Override
    public Path extract() {
        weight = 0;
        if (edgeFrom == null || edgeTo == null)
            return null;

        if (edgeFrom.node != edgeTo.node)
            throw new IllegalStateException("Locations of the 'to'- and 'from'-Edge has to be the same." + toString());

        if (switchWrapper) {
            EdgeEntry ee = edgeFrom;
            edgeFrom = edgeTo;
            edgeTo = ee;
        }

        EdgeEntry currEdge = edgeFrom;
        while (currEdge.prevEntry != null) {
            int tmpFrom = currEdge.node;
            add(tmpFrom);
            currEdge = currEdge.prevEntry;
            calcWeight(g.getOutgoing(currEdge.node), tmpFrom);
        }
        add(currEdge.node);
        reverseOrder();
        currEdge = edgeTo;
        while (currEdge.prevEntry != null) {
            int tmpTo = currEdge.node;
            currEdge = currEdge.prevEntry;
            add(currEdge.node);
            calcWeight(g.getOutgoing(tmpTo), currEdge.node);
        }

        return this;
    }

//    @Override public String toString() {
//        return "distance:" + weight + ", from:" + edgeFrom + ", to:" + edgeTo;
//    }
}
