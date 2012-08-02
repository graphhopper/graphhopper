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
import de.jetsli.graph.util.EdgeWrapper;

/**
 * This class creates a Path from two Edge's resulting from a BidirectionalDijkstra
 *
 * @author Peter Karich, info@jetsli.de
 */
public class PathWrapper {

    public boolean switchWrapper = false;
    public int fromEdgeId = -1;
    public int toEdgeId = -1;
    public double distance;
    private EdgeWrapper edgeFrom;
    private EdgeWrapper edgeTo;

    public PathWrapper(EdgeWrapper edgesFrom, EdgeWrapper edgesTo) {
        this.edgeFrom = edgesFrom;
        this.edgeTo = edgesTo;
    }

    /**
     * Extracts path from two shortest-path-tree
     */
    public Path extract() {
        if (fromEdgeId < 0 || toEdgeId < 0)
            return null;
        
        if (switchWrapper) {
            EdgeWrapper tmp = edgeFrom;
            edgeFrom = edgeTo;
            edgeTo = tmp;
        }

        int nodeFrom = edgeFrom.getNode(fromEdgeId);
        int nodeTo = edgeTo.getNode(toEdgeId);
        if (nodeFrom != nodeTo)
            throw new IllegalStateException("Locations of 'to' and 'from' DistEntries has to be the same." + toString());

        Path path = new Path();
        int currEdgeId = fromEdgeId;
        while (currEdgeId > 0) {
            int tmpNode = edgeFrom.getNode(currEdgeId);
            double dist = edgeFrom.getDistance(currEdgeId);
            path.add(new Edge(tmpNode, dist));
            currEdgeId = edgeFrom.getLink(currEdgeId);
        }
        path.reverseOrder();

        double fromDistance = path.distance();
        double toDistance = edgeTo.getDistance(toEdgeId);
        currEdgeId = edgeTo.getLink(toEdgeId);
        while (currEdgeId > 0) {
            int tmpNode = edgeTo.getNode(currEdgeId);
            double dist = edgeTo.getDistance(currEdgeId);
            path.add(new Edge(tmpNode, dist));
            currEdgeId = edgeTo.getLink(currEdgeId);
        }
        // we didn't correct the distances of the other to-DistEntry for performance reasons
        path.setDistance(fromDistance + toDistance);
        return path;
    }

    @Override public String toString() {
        return "distance:" + distance + ", from:" + edgeFrom.getNode(fromEdgeId) + ", to:" + edgeTo.getNode(toEdgeId);
    }
}
