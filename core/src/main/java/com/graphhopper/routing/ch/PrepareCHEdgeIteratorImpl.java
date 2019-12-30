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

import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.util.CHEdgeExplorer;
import com.graphhopper.util.CHEdgeIterator;
import com.graphhopper.util.EdgeIterator;

public class PrepareCHEdgeIteratorImpl implements PrepareCHEdgeExplorer, PrepareCHEdgeIterator {
    private final CHEdgeExplorer edgeExplorer;
    private final Weighting weighting;
    private CHEdgeIterator chIterator;

    public PrepareCHEdgeIteratorImpl(CHEdgeExplorer edgeExplorer, Weighting weighting) {
        this.edgeExplorer = edgeExplorer;
        this.weighting = weighting;
    }

    @Override
    public PrepareCHEdgeIteratorImpl setBaseNode(int node) {
        chIterator = edgeExplorer.setBaseNode(node);
        return this;
    }

    @Override
    public boolean next() {
        assertBaseNodeSet();
        return chIterator.next();
    }

    @Override
    public int getEdge() {
        assertBaseNodeSet();
        return chIterator.getEdge();
    }

    @Override
    public int getBaseNode() {
        assertBaseNodeSet();
        return chIterator.getBaseNode();
    }

    @Override
    public int getAdjNode() {
        assertBaseNodeSet();
        return chIterator.getAdjNode();
    }

    @Override
    public int getOrigEdgeFirst() {
        assertBaseNodeSet();
        return chIterator.getOrigEdgeFirst();
    }

    @Override
    public int getOrigEdgeLast() {
        assertBaseNodeSet();
        return chIterator.getOrigEdgeLast();
    }

    @Override
    public boolean isShortcut() {
        assertBaseNodeSet();
        return chIterator.isShortcut();
    }

    @Override
    public double getWeight(boolean reverse) {
        if (isShortcut()) {
            return chIterator.getWeight();
        } else {
            assertBaseNodeSet();
            return weighting.calcWeight(chIterator, reverse, EdgeIterator.NO_EDGE);
        }
    }

    @Override
    public void setWeight(double weight) {
        assertBaseNodeSet();
        chIterator.setWeight(weight);
    }

    @Override
    public String toString() {
        if (chIterator == null) {
            return "not initialized";
        } else {
            return getBaseNode() + "->" + getAdjNode() + " (" + getEdge() + ")";
        }
    }

    @Override
    public int getMergeStatus(int flags) {
        assertBaseNodeSet();
        return chIterator.getMergeStatus(flags);
    }

    @Override
    public void setFlagsAndWeight(int flags, double weight) {
        assertBaseNodeSet();
        chIterator.setFlagsAndWeight(flags, weight);
    }

    @Override
    public void setSkippedEdges(int skippedEdge1, int skippedEdge2) {
        assertBaseNodeSet();
        chIterator.setSkippedEdges(skippedEdge1, skippedEdge2);
    }

    private void assertBaseNodeSet() {
        assert chIterator != null : "You need to call setBaseNode() before using the iterator";
    }
}
