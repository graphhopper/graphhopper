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
import com.graphhopper.routing.ch.ShortcutUnpacker;
import com.graphhopper.util.CHEdgeIteratorState;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;

import static com.graphhopper.util.EdgeIterator.NO_EDGE;

/**
 * Path for time-dependent core-type algorithms
 * <p>
 *
 * @author Andrzej Oles
 */
// TODO ORS: Is this already coverd by TDPathExtractor? Otherwise, reimplement as PathExtractor
/*
public class PathTDCore extends PathBidirRef {
    private boolean switchFromAndToSPTEntry = false;
    private Graph routingGraph;

    private final ShortcutUnpacker shortcutUnpacker;

    public PathTDCore(Graph routingGraph, Graph baseGraph, final Weighting weighting) {
        super(baseGraph, weighting);
        this.routingGraph = routingGraph;
        this.shortcutUnpacker = getShortcutUnpacker(routingGraph, weighting);
    }

    @Override
    public PathBidirRef setSwitchToFrom(boolean b) {
        switchFromAndToSPTEntry = b;
        return this;
    }

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
        // no need to process any turns at meeting point
        extractBwdPath();
        extractSW.stop();
        return setFound(true);
    }

    private void extractFwdPath() {
        // we take the 'edgeFrom'/sptEntry that points at the meeting node and follow its parent pointers back to
        // the source
        setFromNode(extractPath(sptEntry,false));
        // since we followed the fwd path in backward direction we need to reverse the edge ids
        reverseOrder();
    }

    private void extractBwdPath() {
        // we take the edgeTo at the meeting node and follow its parent pointers to the target
        setEndNode(extractPath(edgeTo, true));
    }

    private int extractPath(SPTEntry currEdge, boolean bwd) {
        SPTEntry prevEdge = currEdge.parent;
        while (EdgeIterator.Edge.isValid(currEdge.edge)) {
            processEdge(currEdge, bwd);
            currEdge = prevEdge;
            prevEdge = currEdge.parent;
        }
        return currEdge.adjNode;
    }

    private void processEdge(SPTEntry currEdge, boolean bwd) {
        int edgeId = currEdge.edge;
        int adjNode = currEdge.adjNode;
        CHEdgeIteratorState iter = (CHEdgeIteratorState) routingGraph.getEdgeIteratorState(edgeId, adjNode);

        // Shortcuts do only contain valid weight so first expand before adding
        // to distance and time
        if (iter.isShortcut()) {
            if (bwd)
                shortcutUnpacker.visitOriginalEdgesBwd(edgeId, adjNode, true, currEdge.parent.edge);
            else
                shortcutUnpacker.visitOriginalEdgesFwd(edgeId, adjNode, true, currEdge.parent.edge);
        }
        else {
            distance += iter.getDistance();
            addTime((bwd ? -1 : 1) * (currEdge.time - currEdge.parent.time));
            addEdge(edgeId);
        }
    }

    protected ShortcutUnpacker getShortcutUnpacker(Graph routingGraph, final Weighting weighting) {
        return new ShortcutUnpacker(routingGraph, new ShortcutUnpacker.Visitor() {
            @Override
            public void visit(EdgeIteratorState edge, boolean reverse, int prevOrNextEdgeId) {
                distance += edge.getDistance();
                addTime(weighting.calcEdgeMillis(edge, reverse));
                addEdge(edge.getEdge());
            }
        }, false);
    }
}
*/