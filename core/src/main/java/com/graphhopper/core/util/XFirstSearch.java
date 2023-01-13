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
package com.graphhopper.core.util;

import com.graphhopper.coll.GHBitSet;

/**
 * This abstract class defines commonalities for BFS and DFS
 *
 * @author Jan Sölter
 */
public abstract class XFirstSearch {
    /**
     * Pick the BitSet implementation wisely. Use {@link com.graphhopper.coll.GHBitSetImpl} only if we are sure you visit a large portion of the graph.
     * And if you choose {@link com.graphhopper.coll.GHTBitSet} the initial capacity can be also important for performance.
     */
    protected abstract GHBitSet createBitSet();

    public abstract void start(EdgeExplorer explorer, int startNode);

    protected boolean goFurther(int nodeId) {
        return true;
    }

    protected boolean checkAdjacent(EdgeIteratorState edge) {
        return true;
    }
}
