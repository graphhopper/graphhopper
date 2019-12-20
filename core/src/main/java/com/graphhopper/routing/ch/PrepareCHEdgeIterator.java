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
import com.graphhopper.util.CHEdgeIterator;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;

public class PrepareCHEdgeIterator implements PrepareCHEdgeExplorer {
    private final EdgeExplorer edgeExplorer;
    private final Weighting weighting;
    private EdgeIterator chIterator;

    public PrepareCHEdgeIterator(EdgeExplorer edgeExplorer, Weighting weighting) {
        this.edgeExplorer = edgeExplorer;
        this.weighting = weighting;
    }

    @Override
    public PrepareCHEdgeIterator setBaseNode(int node) {
        chIterator = edgeExplorer.setBaseNode(node);
        return this;
    }

    public boolean next() {
        return iter().next();
    }

    public int getEdge() {
        return iter().getEdge();
    }

    public int getBaseNode() {
        return iter().getBaseNode();
    }

    public int getAdjNode() {
        return iter().getAdjNode();
    }

    public int getOrigEdgeFirst() {
        return iter().getOrigEdgeFirst();
    }

    public int getOrigEdgeLast() {
        return iter().getOrigEdgeLast();
    }

    public boolean isShortcut() {
        final EdgeIterator iter = iter();
        return iter instanceof CHEdgeIterator && ((CHEdgeIterator) iter).isShortcut();
    }

    public double getWeight(boolean reverse) {
        if (isShortcut()) {
            return chIter().getWeight();
        } else {
            return weighting.calcWeight(iter(), reverse, EdgeIterator.NO_EDGE);
        }
    }

    public void setWeight(double weight) {
        chIter().setWeight(weight);
    }

    @Override
    public String toString() {
        if (chIterator == null) {
            return "not initialized";
        } else {
            return getBaseNode() + "->" + getAdjNode() + " (" + getEdge() + ")";
        }
    }

    int getMergeStatus(int flags) {
        return chIter().getMergeStatus(flags);
    }

    void setFlagsAndWeight(int flags, double weight) {
        chIter().setFlagsAndWeight(flags, weight);
    }

    void setSkippedEdges(int skippedEdge1, int skippedEdge2) {
        chIter().setSkippedEdges(skippedEdge1, skippedEdge2);
    }

    private EdgeIterator iter() {
        if (chIterator == null) {
            throw new IllegalStateException("You need to call setBaseNode() first");
        }
        return chIterator;
    }

    private CHEdgeIterator chIter() {
        EdgeIterator iter = iter();
        if (!(iter instanceof CHEdgeIterator)) {
            throw new IllegalStateException("Expected a CH edge iterator, but was: " + iter.getClass().getSimpleName());
        }
        return (CHEdgeIterator) iter;
    }
}
