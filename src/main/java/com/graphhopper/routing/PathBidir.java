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
 * This class creates a Path from two Edge's resulting from a
 * BidirectionalDijkstra
 *
 * @author Peter Karich,
 */
public class PathBidir extends Path {

    public boolean switchWrapper = false;
    public int fromRef = -1;
    public int toRef = -1;
    private EdgeWrapper edgeWFrom;
    private EdgeWrapper edgeWTo;

    public PathBidir(Graph g, WeightCalculation weightCalculation,
            EdgeWrapper edgesFrom, EdgeWrapper edgesTo) {
        super(g, weightCalculation);
        this.edgeWFrom = edgesFrom;
        this.edgeWTo = edgesTo;
    }

    /**
     * Extracts path from two shortest-path-tree
     */
    @Override
    public Path extract() {
        weight = 0;
        if (fromRef < 0 || toRef < 0)
            return this;

        if (switchWrapper) {
            int tmp = fromRef;
            fromRef = toRef;
            toRef = tmp;
        }

        int nodeFrom = edgeWFrom.getNode(fromRef);
        int nodeTo = edgeWTo.getNode(toRef);
        if (nodeFrom != nodeTo)
            throw new IllegalStateException("Locations of 'to' and 'from' DistEntries has to be the same." + toString());

        int currRef = fromRef;
        while (currRef > 0) {
            int edgeId = edgeWFrom.getEdgeId(currRef);
            if (edgeId < 0)
                break;
            processWeight(edgeId, nodeFrom);
            currRef = edgeWFrom.getParent(currRef);
            nodeFrom = edgeWFrom.getNode(currRef);
        }
        setFromNode(nodeFrom);
        reverseOrder();

        // skip node of toRef (equal to fromRef)
        currRef = toRef;
        while (currRef > 0) {
            int edgeId = edgeWTo.getEdgeId(currRef);
            if (edgeId < 0)
                break;
            processWeight(edgeId, nodeTo);
            int tmpRef = edgeWTo.getParent(currRef);
            nodeTo = edgeWTo.getNode(tmpRef);            
            currRef = tmpRef;
        }        
        return found(true);
    }

    public void initWeight() {
        weight = INIT_VALUE;
    }
}
