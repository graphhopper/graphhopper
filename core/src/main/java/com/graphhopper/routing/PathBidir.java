/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except in 
 *  compliance with the License. You may obtain a copy of the License at
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

import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.EdgeWrapper;

/**
 * This class creates a Path from two Edge's resulting from a BidirectionalDijkstra
 * <p/>
 * @author Peter Karich
 */
public class PathBidir extends Path
{
    public boolean switchWrapper = false;
    public int fromRef = -1;
    public int toRef = -1;
    private EdgeWrapper edgeWFrom;
    private EdgeWrapper edgeWTo;

    public PathBidir( Graph g, FlagEncoder encoder,
                      EdgeWrapper edgesFrom, EdgeWrapper edgesTo )
    {
        super(g, encoder);
        this.edgeWFrom = edgesFrom;
        this.edgeWTo = edgesTo;
    }

    /**
     * Extracts path from two shortest-path-tree
     */
    @Override
    public Path extract()
    {
        if (fromRef < 0 || toRef < 0)
            return this;

        if (switchWrapper)
        {
            int tmp = fromRef;
            fromRef = toRef;
            toRef = tmp;
        }

        int nodeFrom = edgeWFrom.getNode(fromRef);
        int nodeTo = edgeWTo.getNode(toRef);
        if (nodeFrom != nodeTo)
            throw new IllegalStateException("'to' and 'from' have to be the same. " + toString());

        int currRef = fromRef;
        while (currRef > 0)
        {
            int edgeId = edgeWFrom.getEdgeId(currRef);
            if (edgeId < 0)
                break;

            processEdge(edgeId, nodeFrom);
            currRef = edgeWFrom.getParent(currRef);
            nodeFrom = edgeWFrom.getNode(currRef);
        }
        reverseOrder();
        setFromNode(nodeFrom);
        // skip node of toRef (equal to fromRef)
        currRef = toRef;
        while (currRef > 0)
        {
            int edgeId = edgeWTo.getEdgeId(currRef);
            if (edgeId < 0)
                break;

            int tmpRef = edgeWTo.getParent(currRef);
            nodeTo = edgeWTo.getNode(tmpRef);
            processEdge(edgeId, nodeTo);
            currRef = tmpRef;
        }
        setEndNode(nodeTo);
        return setFound(true);
    }
}
