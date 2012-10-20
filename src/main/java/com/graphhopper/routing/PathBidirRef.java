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
package com.graphhopper.routing;

import com.graphhopper.routing.util.WeightCalculation;
import com.graphhopper.storage.EdgeEntry;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.GraphUtility;

/**
 * This class creates a DijkstraPath from two Edge's resulting from a BidirectionalDijkstra
 *
 * @author Peter Karich,
 */
public class PathBidirRef extends Path {

    public EdgeEntry edgeFrom;
    public EdgeEntry edgeTo;
    public boolean switchWrapper = false;

    public PathBidirRef(Graph g, WeightCalculation weightCalculation) {
        super(g, weightCalculation);
    }

    /**
     * Extracts path from two shortest-path-tree
     */
    @Override
    public Path extract() {
        weight = 0;
        if (edgeFrom == null || edgeTo == null)
            return null;

        int from = GraphUtility.getToNode(g, edgeFrom.edge, edgeFrom.endNode);
        int to = GraphUtility.getToNode(g, edgeTo.edge, edgeTo.endNode);
        if (from != to)
            throw new IllegalStateException("Locations of the 'to'- and 'from'-Edge has to be the same." + toString());

        if (switchWrapper) {
            EdgeEntry ee = edgeFrom;
            edgeFrom = edgeTo;
            edgeTo = ee;
        }

        EdgeEntry currEdge = edgeFrom;
        while (currEdge.edge != EdgeIterator.NO_EDGE) {
            add(currEdge.endNode);
            calcWeight(g.getEdgeProps(currEdge.edge, currEdge.endNode));
            currEdge = currEdge.parent;
        }
        addFrom(currEdge.endNode);
        reverseOrder();
        currEdge = edgeTo;
        int tmpEdge = currEdge.edge;
        while (tmpEdge != EdgeIterator.NO_EDGE) {
            calcWeight(g.getEdgeProps(tmpEdge, currEdge.endNode));
            currEdge = currEdge.parent;
            add(currEdge.endNode);            
            tmpEdge = currEdge.edge;
        }
        return this;
    }
}
