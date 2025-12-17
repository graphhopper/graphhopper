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

import java.util.function.IntUnaryOperator;

/**
 * Builds a valid {@link CHStorage}, i.e. makes sure that
 * - a valid level is already set for nodeA/B when adding a shortcut nodeA-nodeB
 * - level(nodeB) > level(nodeA) for all added shortcuts, unless nodeA == nodeB, then level(nodeA) == level(nodeB)
 * - shortcuts are added such that they are sorted by level(nodeA)
 * - the 'last shortcut' for node n points to the last shortcut for which nodeA == n
 */
public class CHStorageBuilder {
    private final CHStorage storage;

    public CHStorageBuilder(CHStorage chStorage) {
        // todo: maybe CHStorageBuilder should create CHStorage, not receive it here
        this.storage = chStorage;
    }

    public void setLevel(int node, int level) {
        storage.setLevel(storage.toNodePointer(node), level);
    }

    public void setLevelForAllNodes(int level) {
        for (int node = 0; node < storage.getNodes(); node++)
            setLevel(node, level);
    }

    public void setIdentityLevels() {
        for (int node = 0; node < storage.getNodes(); node++)
            setLevel(node, node);
    }

    public int addShortcutNodeBased(int a, int b, int accessFlags, double weight, int skippedEdge1, int skippedEdge2) {
        checkNewShortcut(a, b);
        int shortcut = storage.shortcutNodeBased(a, b, accessFlags, weight, skippedEdge1, skippedEdge2);
        // we keep track of the last shortcut for each node (-1 if there are no shortcuts), but
        // we do not register the shortcut at node b, because b is the higher level node (so no need to 'see' the lower
        // level node a)
        setLastShortcut(a, shortcut);
        return shortcut;
    }

    /**
     * @param origKeyFirst The first original edge key that is skipped by this shortcut *in the direction of the shortcut*.
     *                     This definition assumes that edge-based shortcuts are one-directional, and they are.
     *                     For example for the following shortcut edge from x to y: x->u->v->w->y,
     *                     which skips the shortcuts x->v and v->y, the first original edge key would be the one of the edge x->u
     * @param origKeyLast  like origKeyFirst, but the last orig edge key, i.e. the key of w->y in above example
     */
    public int addShortcutEdgeBased(int a, int b, int accessFlags, double weight, int skippedEdge1, int skippedEdge2,
                                    int origKeyFirst, int origKeyLast) {
        checkNewShortcut(a, b);
        int shortcut = storage.shortcutEdgeBased(a, b, accessFlags, weight, skippedEdge1, skippedEdge2, origKeyFirst, origKeyLast);
        setLastShortcut(a, shortcut);
        return shortcut;
    }

    public void replaceSkippedEdges(IntUnaryOperator mapping) {
        for (int i = 0; i < storage.getShortcuts(); ++i) {
            long shortcutPointer = storage.toShortcutPointer(i);
            int skip1 = storage.getSkippedEdge1(shortcutPointer);
            int skip2 = storage.getSkippedEdge2(shortcutPointer);
            storage.setSkippedEdges(shortcutPointer, mapping.applyAsInt(skip1), mapping.applyAsInt(skip2));
        }
    }

    private void checkNewShortcut(int a, int b) {
        checkNodeId(a);
        checkNodeId(b);
        if (getLevel(a) >= storage.getNodes() || getLevel(a) < 0)
            throw new IllegalArgumentException("Invalid level for node " + a + ": " + getLevel(a) + ". Node a must" +
                    " be assigned a valid level before we add shortcuts a->b or a<-b");
        if (a != b && getLevel(a) == getLevel(b))
            throw new IllegalArgumentException("Different nodes must not have the same level, got levels " + getLevel(a)
                    + " and " + getLevel(b) + " for nodes " + a + " and " + b);
        if (a != b && getLevel(a) > getLevel(b))
            throw new IllegalArgumentException("The level of nodeA must be smaller than the level of nodeB, but got: " +
                    getLevel(a) + " and " + getLevel(b) + ". When inserting shortcut: " + a + "-" + b);
        if (storage.getShortcuts() > 0) {
            int prevNodeA = storage.getNodeA(storage.toShortcutPointer(storage.getShortcuts() - 1));
            int prevLevelA = getLevel(prevNodeA);
            if (getLevel(a) < prevLevelA) {
                throw new IllegalArgumentException("Invalid level for node " + a + ": " + getLevel(a) + ". The level " +
                        "must be equal to or larger than the lower level node of the previous shortcut (node: " + prevNodeA +
                        ", level: " + prevLevelA + ")");
            }
        }
    }

    private void setLastShortcut(int node, int shortcut) {
        storage.setLastShortcut(storage.toNodePointer(node), shortcut);
    }

    private int getLevel(int node) {
        checkNodeId(node);
        return storage.getLevel(storage.toNodePointer(node));
    }

    private void checkNodeId(int node) {
        if (node >= storage.getNodes() || node < 0)
            throw new IllegalArgumentException("node " + node + " is invalid. Not in [0," + storage.getNodes() + ")");
    }
}
