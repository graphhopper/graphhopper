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
package com.graphhopper.storage;

import com.graphhopper.util.Helper;

/**
 * @author Peter Karich
 */
public class GraphStorage3D extends GraphStorage implements Graph3D {

    private static final int I_HEIGHT = 3;

    public GraphStorage3D(Directory dir) {
        super(dir);
        nodeEntrySize = 4;
    }

    @Override
    public GraphStorage3D create(long nodeCount) {
        return (GraphStorage3D) super.create(nodeCount);
    }

    @Override
    public void setNode(int index, double lat, double lon, double height) {
        setNode(index, lat, lon);
        // TODO 1 bounds for index
        // TODO 2 location to id index
        // we need to avoid rewriting every algorithm like A*
        // TODO 3 currWeightToGoal = dist.calcDistKm(toLat, toLon, tmpLat, tmpLon);
        nodes.setInt((long) index * nodeEntrySize + I_HEIGHT, Helper.doubleToInt(height));
    }

    @Override
    public double getHeight(int index) {
        ensureNodeIndex(index);
        return Helper.intToDouble(nodes.getInt((long) index * nodeEntrySize + I_HEIGHT));
    }
}
