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

package com.graphhopper.routing.ch;

import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.*;
import com.graphhopper.util.CHEdgeExplorer;

abstract class AbstractNodeContractor implements NodeContractor {
    final CHGraph prepareGraph;
    final FlagEncoder encoder;
    CHEdgeExplorer inEdgeExplorer;
    CHEdgeExplorer outEdgeExplorer;
    private final DataAccess originalEdges;
    int maxLevel;
    private int maxEdgesCount;

    public AbstractNodeContractor(CHGraph prepareGraph, Weighting weighting) {
        this.prepareGraph = prepareGraph;
        this.encoder = weighting.getFlagEncoder();
        originalEdges = new GHDirectory("", DAType.RAM_INT).find("");
        originalEdges.create(1000);
    }

    @Override
    public void initFromGraph() {
        inEdgeExplorer = prepareGraph.createEdgeExplorer(DefaultEdgeFilter.inEdges(encoder));
        outEdgeExplorer = prepareGraph.createEdgeExplorer(DefaultEdgeFilter.outEdges(encoder));
        maxLevel = prepareGraph.getNodes();
        maxEdgesCount = prepareGraph.getOriginalEdges();
    }

    @Override
    public void close() {
        originalEdges.close();
    }

    boolean isContracted(int node) {
        return prepareGraph.getLevel(node) != maxLevel;
    }

    void setOrigEdgeCount(int edgeId, int value) {
        edgeId -= maxEdgesCount;
        if (edgeId < 0) {
            // ignore setting as every normal edge has original edge count of 1
            if (value != 1)
                throw new IllegalStateException("Trying to set original edge count for normal edge to a value = " + value
                        + ", edge:" + (edgeId + maxEdgesCount) + ", max:" + maxEdgesCount + ", graph.max:" +
                        prepareGraph.getEdges());
            return;
        }

        long tmp = (long) edgeId * 4;
        originalEdges.ensureCapacity(tmp + 4);
        originalEdges.setInt(tmp, value);
    }

    int getOrigEdgeCount(int edgeId) {
        edgeId -= maxEdgesCount;
        if (edgeId < 0)
            return 1;

        long tmp = (long) edgeId * 4;
        originalEdges.ensureCapacity(tmp + 4);
        return originalEdges.getInt(tmp);
    }

    abstract boolean isEdgeBased();
}
