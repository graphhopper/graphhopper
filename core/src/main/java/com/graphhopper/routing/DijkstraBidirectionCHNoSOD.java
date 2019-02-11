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

import com.graphhopper.routing.ch.Path4CH;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;

public class DijkstraBidirectionCHNoSOD extends DijkstraBidirectionRef {
    public DijkstraBidirectionCHNoSOD(Graph graph, Weighting weighting) {
        super(graph, weighting, TraversalMode.NODE_BASED);
    }

    @Override
    protected void initCollections(int size) {
        super.initCollections(Math.min(size, 2000));
    }

    @Override
    public boolean finished() {
        // we need to finish BOTH searches for CH!
        if (finishedFrom && finishedTo)
            return true;

        // changed also the final finish condition for CH
        return currFrom.weight >= bestPath.getWeight() && currTo.weight >= bestPath.getWeight();
    }

    @Override
    protected Path createAndInitPath() {
        bestPath = new Path4CH(graph, graph.getBaseGraph(), weighting);
        return bestPath;
    }

    @Override
    public String getName() {
        return "dijkstrabi|ch|no_sod";
    }

    @Override
    public String toString() {
        return getName() + "|" + weighting;
    }
}
