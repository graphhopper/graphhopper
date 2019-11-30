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

import com.graphhopper.routing.profiles.BooleanEncodedValue;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.util.CHEdgeIterator;
import com.graphhopper.util.EdgeIterator;

public class PrepareCHEdgeIterator {
    private final CHEdgeIterator chIterator;
    private final Weighting weighting;
    private final BooleanEncodedValue accessEnc;

    public PrepareCHEdgeIterator(CHEdgeIterator chIterator, Weighting weighting) {
        this.chIterator = chIterator;
        this.weighting = weighting;
        this.accessEnc = weighting.getFlagEncoder().getAccessEnc();
    }

    public boolean next() {
        return chIterator.next();
    }

    public int getEdge() {
        return chIterator.getEdge();
    }

    public int getBaseNode() {
        return chIterator.getBaseNode();
    }

    public int getAdjNode() {
        return chIterator.getAdjNode();
    }

    public boolean isForward() {
        return chIterator.get(accessEnc);
    }

    public boolean isBackward() {
        return chIterator.getReverse(accessEnc);
    }

    public int getOrigEdgeFirst() {
        return chIterator.getOrigEdgeFirst();
    }

    public int getOrigEdgeLast() {
        return chIterator.getOrigEdgeLast();
    }

    public boolean isShortcut() {
        return chIterator.isShortcut();
    }

    public double getWeight(boolean reverse) {
        if (isShortcut()) {
            return chIterator.getWeight();
        } else {
            return weighting.calcWeight(chIterator, reverse, EdgeIterator.NO_EDGE);
        }
    }

    public void setWeight(double weight) {
        chIterator.setWeight(weight);
    }

    public int getMergeStatus(int flags) {
        return chIterator.getMergeStatus(flags);
    }

    public void setFlagsAndWeight(int flags, double weight) {
        chIterator.setFlagsAndWeight(flags, weight);
    }

    public void setSkippedEdges(int skippedEdge1, int skippedEdge2) {
        chIterator.setSkippedEdges(skippedEdge1, skippedEdge2);
    }
}
