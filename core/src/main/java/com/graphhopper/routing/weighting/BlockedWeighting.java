/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
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
package com.graphhopper.routing.weighting;

import com.graphhopper.coll.GHIntHashSet;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphEdgeIdFinder;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.ConfigMap;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.shapes.Shape;

import java.util.Collections;
import java.util.List;

/**
 * Sets weights of blocked edges to infinity.
 *
 * @author Fedor Ermishin
 */
public class BlockedWeighting extends AbstractAdjustedWeighting {
    private final GHIntHashSet blockedEdges;
    private final List<Shape> blockedShapes;
    private NodeAccess na;

    public BlockedWeighting(Weighting superWeighting, ConfigMap cMap) {
        super(superWeighting);
        blockedEdges = cMap.get(GraphEdgeIdFinder.BLOCKED_EDGES, new GHIntHashSet(0));
        blockedShapes = cMap.get(GraphEdgeIdFinder.BLOCKED_SHAPES, Collections.EMPTY_LIST);
    }

    @Override
    public double getMinWeight(double distance) {
        return superWeighting.getMinWeight(distance);
    }

    @Override
    public double calcWeight(EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId) {
        if (!blockedEdges.isEmpty() && blockedEdges.contains(edgeState.getEdge())) {
            return Double.POSITIVE_INFINITY;
        }

        if (!blockedShapes.isEmpty() && na != null) {
            for (Shape shape : blockedShapes) {
                if (shape.contains(na.getLatitude(edgeState.getAdjNode()), na.getLongitude(edgeState.getAdjNode()))) {
                    return Double.POSITIVE_INFINITY;
                }
            }
        }

        return superWeighting.calcWeight(edgeState, reverse, prevOrNextEdgeId);
    }

    @Override
    public String getName() {
        return "blocked";
    }

    /**
     * Use this method to associate a graph with this weighting to calculate e.g. node locations too.
     */
    public void setGraph(Graph graph) {
        if (graph != null)
            this.na = graph.getNodeAccess();
    }
}
