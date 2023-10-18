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

/**
 * Builds a {@link Path} from the two fwd- and bwd-shortest path tree entries of a bidirectional search
 *
 * @author Peter Karich
 * @author easbar
 */
public class DefaultBidirPathExtractor implements BidirPathExtractor {
    private final Graph graph;
    private final Weighting weighting;
    protected final Path path;

    public static Path extractPath(Graph graph, Weighting weighting, SPTEntry fwdEntry, SPTEntry bwdEntry, double weight) {
        return new DefaultBidirPathExtractor(graph, weighting).extract(fwdEntry, bwdEntry, weight);
    }

    protected DefaultBidirPathExtractor(Graph graph, Weighting weighting) {
        this.graph = graph;
        this.weighting = weighting;
        this.path = new Path(graph);
    }

    @Override
    public Path extract(SPTEntry fwdEntry, SPTEntry bwdEntry, double weight) {
        if (fwdEntry == null || bwdEntry == null) {
            // path not found
            return path;
        }
        if (fwdEntry.adjNode != bwdEntry.adjNode)
            throw new IllegalStateException("forward and backward entries must have same adjacent nodes, fwdEntry:" + fwdEntry + ", bwdEntry:" + bwdEntry);

        StopWatch sw = new StopWatch().start();
        extractFwdPath(fwdEntry);
        processMeetingPoint(fwdEntry, bwdEntry);
        extractBwdPath(bwdEntry);
        setExtractionTime(sw.stop().getNanos());
        path.setFound(true);
        path.setWeight(weight);
        return path;
    }

    protected void extractFwdPath(SPTEntry sptEntry) {
        SPTEntry fwdRoot = followParentsUntilRoot(sptEntry, false);
        onFwdTreeRoot(fwdRoot.adjNode);
        // since we followed the fwd path in backward direction we need to reverse the edge ids
        ArrayUtil.reverse(path.getEdges());
    }

    protected void extractBwdPath(SPTEntry sptEntry) {
        SPTEntry bwdRoot = followParentsUntilRoot(sptEntry, true);
        onBwdTreeRoot(bwdRoot.adjNode);
    }

    protected void processMeetingPoint(SPTEntry fwdEntry, SPTEntry bwdEntry) {
        int inEdge = getIncEdge(fwdEntry);
        int outEdge = getIncEdge(bwdEntry);
        onMeetingPoint(inEdge, fwdEntry.adjNode, outEdge);
    }

    protected SPTEntry followParentsUntilRoot(SPTEntry sptEntry, boolean reverse) {
        SPTEntry currEntry = sptEntry;
        SPTEntry parentEntry = currEntry.parent;
        while (EdgeIterator.Edge.isValid(currEntry.edge)) {
            onEdge(currEntry.edge, currEntry.adjNode, reverse, getIncEdge(parentEntry));
            currEntry = parentEntry;
            parentEntry = currEntry.parent;
        }
        return currEntry;
    }

    protected void setExtractionTime(long nanos) {
        path.setDebugInfo("path extraction: " + nanos / 1000 + " μs");
    }

    protected int getIncEdge(SPTEntry entry) {
        return entry.edge;
    }

    protected void onFwdTreeRoot(int node) {
        path.setFromNode(node);
    }

    protected void onBwdTreeRoot(int node) {
        path.setEndNode(node);
    }

    protected void onEdge(int edge, int adjNode, boolean reverse, int prevOrNextEdge) {
        EdgeIteratorState edgeState = graph.getEdgeIteratorState(edge, adjNode);
        path.addDistance(edgeState.getDistance());
        path.addTime(GHUtility.calcMillisWithTurnMillis(weighting, edgeState, reverse, prevOrNextEdge));
        path.addEdge(edge);
    }

    protected void onMeetingPoint(int inEdge, int viaNode, int outEdge) {
        if (!EdgeIterator.Edge.isValid(inEdge) || !EdgeIterator.Edge.isValid(outEdge)) {
            return;
        }
        path.addTime(weighting.calcTurnMillis(inEdge, viaNode, outEdge));
    }

}
