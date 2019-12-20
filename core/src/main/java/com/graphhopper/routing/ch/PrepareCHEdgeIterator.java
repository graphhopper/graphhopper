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

public class PrepareCHEdgeIterator implements PrepareCHEdgeExplorer {
    private final CHEdgeExplorer edgeExplorer;
    private final Weighting weighting;
    private CHEdgeIterator chIterator;

    public PrepareCHEdgeIterator(CHEdgeExplorer edgeExplorer, Weighting weighting) {
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
        return iter().isShortcut();
    }

    public double getWeight(boolean reverse) {
        if (isShortcut()) {
            return iter().getWeight();
        } else {
            return weighting.calcWeight(iter(), reverse, EdgeIterator.NO_EDGE);
        }
    }

    public void setWeight(double weight) {
        iter().setWeight(weight);
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
        return iter().getMergeStatus(flags);
    }

    void setFlagsAndWeight(int flags, double weight) {
        iter().setFlagsAndWeight(flags, weight);
    }

    void setSkippedEdges(int skippedEdge1, int skippedEdge2) {
        iter().setSkippedEdges(skippedEdge1, skippedEdge2);
    }

    private CHEdgeIterator iter() {
        if (chIterator == null) {
            throw new IllegalStateException("You need to call setBaseNode() first");
        }
        return chIterator;
    }
}
