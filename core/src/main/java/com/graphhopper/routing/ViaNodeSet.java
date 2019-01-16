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

import java.util.ArrayList;

/**
 * This class stores all data concerning computed viaNodes.
 *
 * @author Maximilian Sturm
 */
public class ViaNodeSet {
    private final boolean empty;
    private int[] area;
    private boolean[][] directlyConnected;
    private ArrayList<Integer>[][] viaNodes;

    public ViaNodeSet() {
        empty = true;
    }

    public ViaNodeSet(int[] area, boolean[][] directlyConnected, ArrayList<Integer>[][] viaNodes) {
        empty = false;
        this.area = area;
        this.directlyConnected = directlyConnected;
        this.viaNodes = viaNodes;
    }

    /**
     * @return if the set is empty. This is the case if no precomputation has been done
     */
    public boolean isEmpty() {
        return empty;
    }

    /**
     * @param from
     * @param to
     * @return the list of viaNodes between from and to
     */
    public ArrayList<Integer> get(int from, int to) {
        if (!isEmpty()) {
            int area1 = area[from];
            int area2 = area[to];
            if (directlyConnected[area1][area2])
                return null;
            else
                return viaNodes[area1][area2];
        }
        return null;
    }
}
