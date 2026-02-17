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

package com.graphhopper.routing;

import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.*;

public class PathExtractor {
    private final Graph graph;
    private final Weighting weighting;
    protected final Path path;

    public static Path extractPath(Graph graph, Weighting weighting, SPTEntry sptEntry) {
        return new PathExtractor(graph, weighting).extract(sptEntry);
    }

    protected PathExtractor(Graph graph, Weighting weighting) {
        this.graph = graph;
        this.weighting = weighting;
        path = new Path(graph);
    }

    protected Path extract(SPTEntry sptEntry) {
        if (sptEntry == null) {
            // path not found
            return path;
        }
        StopWatch sw = new StopWatch().start();
        extractPath(sptEntry);
        path.setFound(true);
        path.setWeight(sptEntry.weight);
        setExtractionTime(sw.stop().getNanos());
        return path;
    }

    private void extractPath(SPTEntry sptEntry) {
        SPTEntry currEdge = followParentsUntilRoot(sptEntry);
        ArrayUtil.reverse(path.getEdges());
        path.setFromNode(currEdge.adjNode);
        path.setEndNode(sptEntry.adjNode);
    }

    private SPTEntry followParentsUntilRoot(SPTEntry sptEntry) {
        SPTEntry currEntry = sptEntry;
        SPTEntry parentEntry = currEntry.parent;
        while (EdgeIterator.Edge.isValid(currEntry.edge)) {
            onEdge(currEntry.edge, currEntry.adjNode, parentEntry.edge);
            currEntry = currEntry.parent;
            parentEntry = currEntry.parent;
        }
        return currEntry;
    }

    private void setExtractionTime(long nanos) {
        path.setDebugInfo("path extraction: " + nanos / 1000 + " μs");
    }

    protected void onEdge(int edge, int adjNode, int prevEdge) {
        EdgeIteratorState edgeState = graph.getEdgeIteratorState(edge, adjNode);
        path.addDistanceMM(edgeState.getDistanceMM());
        path.addTime(GHUtility.calcMillisWithTurnMillis(weighting, edgeState, false, prevEdge));
        path.addEdge(edge);
    }

}
