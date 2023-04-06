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

import com.graphhopper.util.EdgeIterator;

public interface RoutingCHEdgeIteratorState {
    /**
     * The CH edge ID of this edge state. This is generally not the same as {@link #getOrigEdge()}
     */
    int getEdge();

    /**
     * The original/base/query graph edge ID of the edge this CH edge state represents or {@link EdgeIterator#NO_EDGE}
     * if this is edge state is a shortcut
     */
    int getOrigEdge();

    /**
     * For shortcuts of an edge-based CH graph this is the ID of the first original edge of this edge state, otherwise
     * it is the same as {@link #getOrigEdge()}}
     */
    int getOrigEdgeFirst();

    /**
     * For shortcuts of an edge-based CH graph this is the ID of the last original edge of this edge state, otherwise
     * it is the same as {@link #getOrigEdge()}}
     */
    int getOrigEdgeLast();

    int getBaseNode();

    int getAdjNode();

    boolean isShortcut();

    /**
     * The CH edge ID of the first skipped edge/shortcut of this edge state
     */
    int getSkippedEdge1();

    /**
     * The CH edge ID of the second skipped edge/shortcut of this edge state
     */
    int getSkippedEdge2();

    double getWeight(boolean reverse);

    int getTime(boolean reverse);
}
