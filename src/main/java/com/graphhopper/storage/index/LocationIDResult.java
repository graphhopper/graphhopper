/*
 *  Licensed to Peter Karich under one or more contributor license
 *  agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  Peter Karich licenses this file to you under the Apache License,
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

/**
 * Result of Location2IDIndex lookup
 *
 * @author Peter Karich
 */
public class LocationIDResult {

    double weight = Double.MAX_VALUE;    
    int wayIndex = -3;
    private int closestNode = -1;

    public LocationIDResult() {
    }

    void closestNode(int node) {
        closestNode = node;
    }

    public int closestNode() {
        return closestNode;
    }

    @Override
    public String toString() {
        return closestNode + ", " + weight + ", " + wayIndex;
    }
}
