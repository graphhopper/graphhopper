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
import de.jetsli.graph.storage.Graph;
import de.jetsli.graph.util.EdgeWrapper;

/**
 * This class creates a Path from two Edge's resulting from a BidirectionalDijkstra
 *
 * @author Peter Karich, info@jetsli.de
 */
public class PathBidir extends Path {

    public boolean switchWrapper = false;
    public int fromEdgeId = -1;
    public int toEdgeId = -1;
    private EdgeWrapper edgeFrom;
    private EdgeWrapper edgeTo;
    protected Graph g;

    public PathBidir(Graph g, EdgeWrapper edgesFrom, EdgeWrapper edgesTo,
            WeightCalculation weightCalculation) {
        super(weightCalculation);
        this.g = g;
        this.edgeFrom = edgesFrom;
        this.edgeTo = edgesTo;
    }

    /**
     * Extracts path from two shortest-path-tree
     */
    @Override
    public Path extract() {
        weight = 0;
        if (fromEdgeId < 0 || toEdgeId < 0)
            return null;

        if (switchWrapper) {
            int tmp = fromEdgeId;
            fromEdgeId = toEdgeId;
            toEdgeId = tmp;
        }

        int nodeFrom = edgeFrom.getNode(fromEdgeId);
        int nodeTo = edgeTo.getNode(toEdgeId);
        if (nodeFrom != nodeTo)
            throw new IllegalStateException("Locations of 'to' and 'from' DistEntries has to be the same." + toString());

        int currEdgeId = fromEdgeId;
        add(nodeFrom);
        currEdgeId = edgeFrom.getLink(currEdgeId);
        while (currEdgeId > 0) {
            int tmpFrom = edgeFrom.getNode(currEdgeId);
            add(tmpFrom);
            calcWeight(g.getOutgoing(tmpFrom), nodeFrom);
            currEdgeId = edgeFrom.getLink(currEdgeId);
            nodeFrom = tmpFrom;
        }
        reverseOrder();

        // skip node of toEdgeId (equal to fromEdgeId)
        currEdgeId = edgeTo.getLink(toEdgeId);
        while (currEdgeId > 0) {
            int tmpTo = edgeTo.getNode(currEdgeId);
            add(tmpTo);
            calcWeight(g.getOutgoing(nodeTo), tmpTo);
            currEdgeId = edgeTo.getLink(currEdgeId);
            nodeTo = tmpTo;
        }
        return this;
    }

//    @Override public String toString() {
//        return "distance:" + weight + ", from:" + edgeFrom.getNode(fromEdgeId) + ", to:" + edgeTo.getNode(toEdgeId);
//    }
}
