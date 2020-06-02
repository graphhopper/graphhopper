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

import com.carrotsearch.hppc.IntArrayList;

import static com.graphhopper.util.EdgeIterator.ANY_EDGE;

public class EdgeRestrictions {
    private int sourceOutEdge = ANY_EDGE;
    private int targetInEdge = ANY_EDGE;
    private final IntArrayList unfavoredEdges = IntArrayList.from();

    public int getSourceOutEdge() {
        return sourceOutEdge;
    }

    public void setSourceOutEdge(int sourceOutEdge) {
        this.sourceOutEdge = sourceOutEdge;
    }

    public int getTargetInEdge() {
        return targetInEdge;
    }

    public void setTargetInEdge(int targetInEdge) {
        this.targetInEdge = targetInEdge;
    }

    public IntArrayList getUnfavoredEdges() {
        return unfavoredEdges;
    }
}
