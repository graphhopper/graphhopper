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

import com.graphhopper.routing.weighting.TurnWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.SPTEntry;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;

/**
 * This class creates a DijkstraPath from two Edge's resulting from a BidirectionalDijkstra
 * <p>
 *
 * @author Peter Karich
 */
public class PathBidirRef extends Path {
    protected SPTEntry edgeTo;
    private boolean switchFromAndToSPTEntry = false;

    public PathBidirRef(Graph g, Weighting weighting) {
        super(g, weighting);
    }

    PathBidirRef(PathBidirRef p) {
        super(p);
        edgeTo = p.edgeTo;
        switchFromAndToSPTEntry = p.switchFromAndToSPTEntry;
    }

    public PathBidirRef setSwitchToFrom(boolean b) {
        switchFromAndToSPTEntry = b;
        return this;
    }

    public PathBidirRef setSPTEntryTo(SPTEntry edgeTo) {
        this.edgeTo = edgeTo;
        return this;
    }

    /**
     * Extracts path from two shortest-path-tree
     */
    @Override
    public Path extract() {
        if (sptEntry == null || edgeTo == null)
            return this;

        if (sptEntry.adjNode != edgeTo.adjNode)
            throw new IllegalStateException("Locations of the 'to'- and 'from'-Edge have to be the same. " + toString() + ", fromEntry:" + sptEntry + ", toEntry:" + edgeTo);

        extractSW.start();
        if (switchFromAndToSPTEntry) {
            SPTEntry ee = sptEntry;
            sptEntry = edgeTo;
            edgeTo = ee;
        }
        extractFwdPath();
        processTurnAtMeetingPoint();
        extractBwdPath();
        extractSW.stop();
        return setFound(true);
    }

    private void extractFwdPath() {
        // we take the 'edgeFrom'/sptEntry that points at the meeting node and follow its parent pointers back to
        // the source
        SPTEntry currEdge = sptEntry;
        SPTEntry prevEdge = currEdge.parent;
        while (EdgeIterator.Edge.isValid(currEdge.edge)) {
            processEdge(currEdge.edge, currEdge.adjNode, getIncEdge(prevEdge));
            currEdge = prevEdge;
            prevEdge = currEdge.parent;
        }
        setFromNode(currEdge.adjNode);
        // since we followed the fwd path in backward direction we need to reverse the edge ids
        reverseOrder();
    }

    private void extractBwdPath() {
        // we take the edgeTo at the meeting node and follow its parent pointers to the target
        SPTEntry currEdge = edgeTo;
        SPTEntry nextEdge = currEdge.parent;
        while (EdgeIterator.Edge.isValid(currEdge.edge)) {
            processEdgeBwd(currEdge.edge, currEdge.adjNode, getIncEdge(nextEdge));
            currEdge = nextEdge;
            nextEdge = nextEdge.parent;
        }
        setEndNode(currEdge.adjNode);
    }

    private void processTurnAtMeetingPoint() {
        processTurn(getIncEdge(sptEntry), sptEntry.adjNode, getIncEdge(edgeTo));
    }

    /**
     * Similar to {@link #processEdge(int, int, int)}, but with the situation we encounter when doing a backward
     * search: nextEdgeId--x<--edgeId--adjNode
     */
    protected void processEdgeBwd(int edgeId, int adjNode, int nextEdgeId) {
        EdgeIteratorState edge = graph.getEdgeIteratorState(edgeId, adjNode);
        distance += edge.getDistance();
        time += weighting.calcMillis(edge, true, nextEdgeId);
        addEdge(edgeId);
    }

    private void processTurn(int inEdge, int viaNode, int outEdge) {
        if (!EdgeIterator.Edge.isValid(inEdge) || !EdgeIterator.Edge.isValid(outEdge)) {
            return;
        }
        if (weighting instanceof TurnWeighting) {
            time += ((TurnWeighting) weighting).calcTurnWeight(inEdge, viaNode, outEdge) * 1000;
        }
    }

    protected int getIncEdge(SPTEntry entry) {
        return entry.edge;
    }
}
