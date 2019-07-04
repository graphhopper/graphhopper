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
package com.graphhopper.routing.ar;

import com.carrotsearch.hppc.IntArrayList;

/**
 * This class stores all data concerning computed viaNodes.
 *
 * @author Maximilian Sturm
 */
public class ViaNodeSet {
    private int[] area;
    private IntArrayList viaNodes[][];

    public ViaNodeSet(int[] area, IntArrayList[][] viaNodes) {
        this.area = area;
        this.viaNodes = viaNodes;
    }

    /**
     * @param from
     * @param to
     * @return the list of viaNodes between from and to
     */
    public IntArrayList get(int from, int to) {
        if (from >= area.length || to >= area.length)
            return null;
        return viaNodes[area[from]][area[to]];
    }
}
