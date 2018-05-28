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

/**
 * This class is used to create the shortest-path-tree from linked entities.
 * <p>
 *
 * @author Peter Karich
 */
public class SPTEntry implements Cloneable, Comparable<SPTEntry> {
    public int edge;
    public int adjNode;
    public double weight;
    public SPTEntry parent;

    public SPTEntry(int edgeId, int adjNode, double weight) {
        this.edge = edgeId;
        this.adjNode = adjNode;
        this.weight = weight;
    }

    public SPTEntry(int node, double weight) {
        this(EdgeIterator.NO_EDGE, node, weight);
    }

    /**
     * This method returns the weight to the origin e.g. to the start for the forward SPT and to the
     * destination for the backward SPT. Where the variable 'weight' is used to let heap select
     * smallest *full* weight (from start to destination).
     */
    public double getWeightOfVisitedPath() {
        return weight;
    }

    public SPTEntry getParent() {
        return parent;
    }

    @Override
    public SPTEntry clone() {
        return new SPTEntry(edge, adjNode, weight);
    }

    public SPTEntry cloneFull() {
        SPTEntry de = clone();
        SPTEntry tmpPrev = parent;
        SPTEntry cl = de;
        while (tmpPrev != null) {
            cl.parent = tmpPrev.clone();
            cl = cl.parent;
            tmpPrev = tmpPrev.parent;
        }
        return de;
    }

    @Override
    public int compareTo(SPTEntry o) {
        if (weight < o.weight)
            return -1;

        // assumption no NaN and no -0        
        return weight > o.weight ? 1 : 0;
    }

    @Override
    public String toString() {
        return adjNode + " (" + edge + ") weight: " + weight;
    }
}
