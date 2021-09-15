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

package com.graphhopper.storage;

import com.carrotsearch.hppc.IntArrayList;
import com.graphhopper.routing.ch.PrepareEncoder;

public class CHBuilder {
    private final CHStorage storage;
    private final int origEdges;
    private final IntArrayList shortcutsByPrepareEdges;

    public CHBuilder(CHStorage chStorage, int origEdges) {
        this.storage = chStorage;
        this.origEdges = origEdges;
        shortcutsByPrepareEdges = new IntArrayList();
    }

    public void addShortcutNodeBased(int a, int b, int accessFlags, double weight, int skippedEdge1, int skippedEdge2, int prepareEdgeFwd, int prepareEdgeBwd) {
        checkNodeId(a);
        checkNodeId(b);
        // shortcuts must be inserted ordered by increasing level of node a
        if (getLevel(a) >= storage.nodeCount || getLevel(a) < 0)
            throw new IllegalArgumentException("Invalid level for node " + a + ": " + getLevel(a) + ". Node a must" +
                    " be assigned a valid level before we add shortcuts a->b or a<-b");
        if (a != b && getLevel(a) == getLevel(b))
            throw new IllegalArgumentException("Different nodes must not have the same level, got levels " + getLevel(a)
                    + " and " + getLevel(b) + " for nodes " + a + " and " + b);
        if (a != b && getLevel(a) > getLevel(b))
            throw new IllegalArgumentException("The level of nodeA must be smaller than the level of nodeB, but got: " +
                    getLevel(a) + " and " + getLevel(b) + ". When inserting shortcut: " + a + "-" + b);
        if (storage.shortcutCount > 0) {
            int prevNodeA = storage.getNodeA(storage.toShortcutPointer(storage.shortcutCount - 1));
            int prevLevelA = getLevel(prevNodeA);
            if (getLevel(a) < prevLevelA) {
                throw new IllegalArgumentException("Invalid level for node " + a + ": " + getLevel(a) + ". The level " +
                        "must be equal to or larger than the lower level node of the previous shortcut (node: " + prevNodeA +
                        ", level: " + prevLevelA + ")");
            }
        }
        storage.shortcutNodeBased(a, b, accessFlags, weight, skippedEdge1, skippedEdge2);
        int scId = storage.shortcutCount - 1;

        // we keep track of the last shortcut for each node (-1 if there are no shortcuts), but
        // we do not register the edge at node b which should be the higher level node (so no need to 'see' the lower
        // level node a)
        storage.setEdgeRef(storage.toNodePointer(a), origEdges + scId);

        if (accessFlags == PrepareEncoder.getScFwdDir()) {
            // todonow: get rid of origEdges here?
            setShortcutForPrepareEdge(prepareEdgeFwd, scId + origEdges);
        } else if (accessFlags == PrepareEncoder.getScBwdDir()) {
            setShortcutForPrepareEdge(prepareEdgeBwd, scId + origEdges);
        } else {
            setShortcutForPrepareEdge(prepareEdgeFwd, scId + origEdges);
            setShortcutForPrepareEdge(prepareEdgeBwd, scId + origEdges);
        }
    }

    /**
     * like shortcut(), but for edge-based CH
     *
     * @param origFirst The first original edge that is skipped by this shortcut. For example for the following shortcut
     *                  edge from x to y, which itself skips the shortcuts x->v and v->y the first original edge would
     *                  be x->u: x->u->v->w->y
     * @param origLast  like origFirst, but the last orig edge, i.e w->y in above example
     */
    public void addShortcutEdgeBased(int a, int b, int accessFlags, double weight, int skippedEdge1, int skippedEdge2, int origFirst, int origLast, int prepareEdgeFwd, int prepareEdgeBwd) {
        // todo: assert storage is edge based
        addShortcutNodeBased(a, b, accessFlags, weight, skippedEdge1, skippedEdge2, prepareEdgeFwd, prepareEdgeBwd);
        long shortcutPointer = storage.toShortcutPointer(storage.shortcutCount - 1);
        storage.setOrigEdges(shortcutPointer, origFirst, origLast);
    }

    public void setShortcutForPrepareEdge(int prepareEdge, int shortcut) {
        int index = prepareEdge - origEdges;
        if (index >= shortcutsByPrepareEdges.size())
            shortcutsByPrepareEdges.resize(index + 1);
        shortcutsByPrepareEdges.set(index, shortcut);
    }

    public int getShortcutForPrepareEdge(int prepareEdge) {
        if (prepareEdge < origEdges)
            return prepareEdge;
        int index = prepareEdge - origEdges;
        return shortcutsByPrepareEdges.get(index);
    }

    public void buildCH() {
        // during contraction the skip1/2 edges of shortcuts refer to the prepare edge-ids *not* the CHGraphImpl (shortcut)
        // ids (because they are not known before the insertion) -> we need to re-map these ids here
        for (int i = 0; i < storage.shortcutCount; ++i) {
            long shortcutPointer = storage.toShortcutPointer(i);
            int skip1 = getShortcutForPrepareEdge(storage.getSkippedEdge1(shortcutPointer));
            int skip2 = getShortcutForPrepareEdge(storage.getSkippedEdge2(shortcutPointer));
            storage.setSkippedEdges(shortcutPointer, skip1, skip2);
        }
    }

    private int getLevel(int node) {
        checkNodeId(node);
        long nodePointer = storage.toNodePointer(node);
        return storage.getLevel(nodePointer);
    }

    private void checkNodeId(int nodeId) {
        if (nodeId >= storage.nodeCount || nodeId < 0)
            throw new IllegalArgumentException("node " + nodeId + " is invalid. Not in [0," + storage.nodeCount + ")");
    }
}
