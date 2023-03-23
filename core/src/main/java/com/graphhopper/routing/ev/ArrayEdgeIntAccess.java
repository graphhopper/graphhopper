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

package com.graphhopper.routing.ev;

import com.carrotsearch.hppc.IntArrayList;

public class ArrayEdgeIntAccess implements EdgeIntAccess {
    private final int intsPerEdge;
    private final IntArrayList arr = new IntArrayList();

    public ArrayEdgeIntAccess(int intsPerEdge) {
        this.intsPerEdge = intsPerEdge;
    }

    @Override
    public int getInt(int edgeId, int index) {
        int arrIndex = edgeId * intsPerEdge + index;
        return arrIndex >= arr.size() ? 0 : arr.get(arrIndex);
    }

    @Override
    public void setInt(int edgeId, int index, int value) {
        int arrIndex = edgeId * intsPerEdge + index;
        if (arrIndex >= arr.size())
            arr.resize(arrIndex + 1);
        arr.set(arrIndex, value);
    }

}