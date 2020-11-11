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

import com.carrotsearch.hppc.IntArrayList;
import com.graphhopper.routing.util.AllCHEdgesIterator;
import com.graphhopper.storage.CHGraph;

/**
 * Shortcut handler that inserts the given shortcuts into a CHGraph
 */
public class EdgeBasedShortcutInserter implements EdgeBasedNodeContractor.ShortcutHandler {
    private final CHGraph chGraph;
    private final int origEdges;
    private final IntArrayList shortcutsByPrepareEdges;
    private int shortcutCount;

    public EdgeBasedShortcutInserter(CHGraph chGraph) {
        this.chGraph = chGraph;
        this.origEdges = chGraph.getOriginalEdges();
        this.shortcutsByPrepareEdges = new IntArrayList();
    }

    @Override
    public void startContractingNode() {
        shortcutCount = 0;
    }

    @Override
    public void addShortcut(int prepareEdge, int from, int to, int origEdgeFirst, int origEdgeLast, int skipped1, int skipped2, double weight, boolean reverse) {
        int flags = reverse ? PrepareEncoder.getScBwdDir() : PrepareEncoder.getScFwdDir();
        int scId = chGraph.shortcutEdgeBased(from, to, flags, weight, skipped1, skipped2, origEdgeFirst, origEdgeLast);
        shortcutCount++;
        setShortcutForPrepareEdge(prepareEdge, scId);
    }

    @Override
    public int finishContractingNode() {
        return shortcutCount;
    }

    @Override
    public void finishContraction() {
        // during contraction the skip1/2 edges of shortcuts refer to the prepare edge-ids *not* the CHGraph (shortcut)
        // ids (because they are not known before the insertion) -> we need to re-map these ids here
        AllCHEdgesIterator iter = chGraph.getAllEdges();
        while (iter.next()) {
            // todo: performance, ideally this loop would start with the first *shortcut* not edge
            if (!iter.isShortcut())
                continue;
            int skip1 = getShortcutForPrepareEdge(iter.getSkippedEdge1());
            int skip2 = getShortcutForPrepareEdge(iter.getSkippedEdge2());
            iter.setSkippedEdges(skip1, skip2);
        }
    }

    private void setShortcutForPrepareEdge(int prepareEdge, int shortcut) {
        int index = prepareEdge - origEdges;
        if (index >= shortcutsByPrepareEdges.size())
            shortcutsByPrepareEdges.resize(index + 1);
        shortcutsByPrepareEdges.set(index, shortcut);
    }

    private int getShortcutForPrepareEdge(int prepareEdge) {
        if (prepareEdge < origEdges)
            return prepareEdge;
        int index = prepareEdge - origEdges;
        return shortcutsByPrepareEdges.get(index);
    }

}
