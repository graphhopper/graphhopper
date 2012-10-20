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
import com.graphhopper.storage.Graph;
import com.graphhopper.util.EdgeWrapper;

/**
 * This class creates a Path from two Edge's resulting from a BidirectionalDijkstra
 *
 * @author Peter Karich,
 */
public class PathBidir extends Path {

    public boolean switchWrapper = false;
    public int fromRef = -1;
    public int toRef = -1;
    private EdgeWrapper edgeFrom;
    private EdgeWrapper edgeTo;
    protected Graph g;

    public PathBidir(Graph g, EdgeWrapper edgesFrom, EdgeWrapper edgesTo,
            WeightCalculation weightCalculation) {
        super(g, weightCalculation);
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
        if (fromRef < 0 || toRef < 0)
            return null;

        if (switchWrapper) {
            int tmp = fromRef;
            fromRef = toRef;
            toRef = tmp;
        }

        int nodeFrom = edgeFrom.getNode(fromRef);
        int nodeTo = edgeTo.getNode(toRef);
        if (nodeFrom != nodeTo)
            throw new IllegalStateException("Locations of 'to' and 'from' DistEntries has to be the same." + toString());

        int currRef = fromRef;
        while (currRef > 0) {
            add(currRef, nodeFrom);
            calcWeight(g.getEdgeProps(edgeFrom.getEdgeId_(currRef), nodeFrom));
            currRef = edgeFrom.getParent(currRef);
            nodeFrom = edgeFrom.getNode(currRef);
        }
        addFrom(nodeFrom);
        reverseOrder();

        // skip node of toRef (equal to fromRef)
        currRef = toRef;
        if (currRef > 0) {
            while (true) {             
                calcWeight(g.getEdgeProps(edgeTo.getEdgeId_(currRef), nodeTo));
                currRef = edgeTo.getParent(currRef);
                nodeTo = edgeTo.getNode(currRef);
                if(currRef <= 0)
                    break;
                                
                add(currRef, nodeTo);
            }
            addFrom(nodeTo);
        }
        return this;
    }
}
