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
package com.graphhopper.util;

import com.graphhopper.routing.ch.PrepareEncoder;
import com.graphhopper.storage.CHGraph;

/**
 * The state returned from the EdgeIterator of a CHGraph
 * <p>
 *
 * @author Peter Karich
 * @see CHGraph
 * @see CHEdgeIterator
 */
public interface CHEdgeIteratorState extends EdgeIteratorState {
    int getSkippedEdge1();

    int getSkippedEdge2();

    /**
     * Sets the edges that this shortcut skips. Those skipped edges can be shortcuts too.
     */
    CHEdgeIteratorState setSkippedEdges(int edge1, int edge2);

    /**
     * Sets the first and last original edges that are skipped by this shortcut. For example for the following shortcut
     * edge from x to y, which itself skips the shortcuts x->v and v->y the first original edge would be x->u and the
     * last original edge would be w->y
     * <p>
     * x --> u --> v --> w --> y
     */
    CHEdgeIteratorState setFirstAndLastOrigEdges(int firstOrigEdge, int lastOrigEdge);

    /**
     * @return true if this edge is a shortcut, false otherwise.
     */
    boolean isShortcut();

    /**
     * This method is only used on preparation.
     *
     * @see PrepareEncoder#getScMergeStatus(int, int)
     */
    int getMergeStatus(int flags);

    /**
     * Returns the weight of this shortcut.
     */
    double getWeight();

    /**
     * Sets the weight calculated from Weighting.calcWeight, only applicable if isShortcut is true.
     */
    CHEdgeIteratorState setWeight(double weight);

    void setFlagsAndWeight(int flags, double weight);
}
