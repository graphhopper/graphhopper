/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor license
 *  agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the
 *  License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.storage.index;

import com.graphhopper.util.EdgeIterator;

/**
 * Result of Location2IDIndex lookup
 *
 * @author Peter Karich
 */
public class LocationIDResult {

    private double weight = Double.MAX_VALUE;
    private int wayIndex = -3;
    private int closestNode = -1;
    private EdgeIterator closestEdge = null;

    public LocationIDResult() {
    }

    public void closestNode(int node) {
        closestNode = node;
    }

    public int closestNode() {
        return closestNode;
    }

    public void weight(double dist) {
        weight = dist;
    }

    public void wayIndex(int wayIndex) {
        this.wayIndex = wayIndex;
    }

    public double weight() {
        return weight;
    }

    /**
     * @return true if a close node was found
     */
    public boolean isValid() {
        return closestNode >= 0;
    }
    
    public void closestEdge(EdgeIterator edge) {
    	this.closestEdge = edge;
    }
    
    public EdgeIterator closestEdge() {
    	return this.closestEdge;
    }
    @Override
    public String toString() {
        return closestNode + ", " + weight + ", " + wayIndex;
    }
}
