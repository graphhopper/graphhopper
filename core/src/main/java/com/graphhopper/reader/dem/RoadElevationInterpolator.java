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
package com.graphhopper.reader.dem;

import com.graphhopper.coll.GHBitSet;
import com.graphhopper.coll.GHBitSetImpl;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.storage.Graph;

/**
 * This class smooths the elevation data of all edges by calculating the average elevation over
 * multiple points of that edge.
 * <p>
 * The ElevationData is read from rectangular tiles. Especially when going along a cliff,
 * valley, or pass, it can happen that a small part of the road contains incorrect elevation data.
 * This is because the elevation data can is coarse and sometimes contains errors.
 *
 * @author Robin Boldt
 */
public abstract class RoadElevationInterpolator {

    public void smoothElevation(Graph graph) {
        final AllEdgesIterator edge = graph.getAllEdges();
        final GHBitSet visitedEdgeIds = new GHBitSetImpl(edge.getMaxId());

        while (edge.next()) {
            final int edgeId = edge.getEdge();
            if (!visitedEdgeIds.contains(edgeId)) {
                smooth(edge);
            }
            visitedEdgeIds.add(edgeId);
        }
    }

    protected abstract void smooth(AllEdgesIterator edge);

}
