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

import java.util.ArrayList;
import java.util.List;

class NodeBasedShortcutHandler implements NodeBasedNodeContractor.ShortcutHandler {
    private final CHGraph chGraph;
    private final int origEdges;
    // todo: maybe use a set to prevent duplicates instead?
    private final List<Shortcut> shortcuts;
    private final IntArrayList shortcutsByPrepareEdges;

    NodeBasedShortcutHandler(CHGraph chGraph) {
        this.chGraph = chGraph;
        this.shortcuts = new ArrayList<>();
        this.origEdges = chGraph.getOriginalEdges();
        shortcutsByPrepareEdges = new IntArrayList();
    }

    @Override
    public void startContractingNode() {
        shortcuts.clear();
    }

    @Override
    public void addOutShortcut(int prepareEdge, int node, int adjNode, int skipped1, int skipped2, double weight) {
        shortcuts.add(new Shortcut(prepareEdge, -1, node, adjNode, skipped1, skipped2, PrepareEncoder.getScFwdDir(), weight));
    }

    @Override
    public void addInShortcut(int prepareEdge, int node, int adjNode, int skipped1, int skipped2, double weight) {
        // we check if this shortcut already exists (with the same weight) for the other direction and if so we can use
        // it for both ways instead of adding another one
        boolean bidir = false;
        for (Shortcut sc : shortcuts) {
            if (sc.to == adjNode && Double.doubleToLongBits(sc.weight) == Double.doubleToLongBits(weight)) {
                if (getShortcutForPrepareEdge(sc.skippedEdge1) == getShortcutForPrepareEdge(skipped1) && getShortcutForPrepareEdge(sc.skippedEdge2) == getShortcutForPrepareEdge(skipped2)) {
                    if (sc.flags == PrepareEncoder.getScFwdDir()) {
                        sc.flags = PrepareEncoder.getScDirMask();
                        sc.prepareEdgeBwd = prepareEdge;
                        bidir = true;
                        break;
                    }
                }
            }
        }
        if (!bidir) {
            shortcuts.add(new Shortcut(-1, prepareEdge, node, adjNode, skipped1, skipped2, PrepareEncoder.getScBwdDir(), weight));
        }
    }

    @Override
    public int finishContractingNode() {
        int shortcutCount = 0;
        for (Shortcut sc : shortcuts) {
            int scId = chGraph.shortcut(sc.from, sc.to, sc.flags, sc.weight, sc.skippedEdge1, sc.skippedEdge2);
            if (sc.flags == PrepareEncoder.getScFwdDir()) {
                setShortcutForPrepareEdge(sc.prepareEdgeFwd, scId);
            } else if (sc.flags == PrepareEncoder.getScBwdDir()) {
                setShortcutForPrepareEdge(sc.prepareEdgeBwd, scId);
            } else {
                setShortcutForPrepareEdge(sc.prepareEdgeFwd, scId);
                setShortcutForPrepareEdge(sc.prepareEdgeBwd, scId);
            }
            shortcutCount++;
        }
        return shortcutCount;
    }

    @Override
    public void finishContraction() {
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

    private static class Shortcut {
        int prepareEdgeFwd;
        int prepareEdgeBwd;
        int from;
        int to;
        int skippedEdge1;
        int skippedEdge2;
        double weight;
        int flags;

        public Shortcut(int prepareEdgeFwd, int prepareEdgeBwd, int from, int to, int skippedEdge1, int skippedEdge2, int flags, double weight) {
            this.prepareEdgeFwd = prepareEdgeFwd;
            this.prepareEdgeBwd = prepareEdgeBwd;
            this.from = from;
            this.to = to;
            this.skippedEdge1 = skippedEdge1;
            this.skippedEdge2 = skippedEdge2;
            this.flags = flags;
            this.weight = weight;
        }

        @Override
        public String toString() {
            String str;
            if (flags == PrepareEncoder.getScDirMask())
                str = from + "<->";
            else
                str = from + "->";

            return str + to + ", weight:" + weight + " (" + skippedEdge1 + "," + skippedEdge2 + ")";
        }
    }
}
