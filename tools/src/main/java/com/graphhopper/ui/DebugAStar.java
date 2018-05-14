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
package com.graphhopper.ui;

import com.graphhopper.routing.AStar;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.SPTEntry;
import com.graphhopper.util.EdgeIteratorState;

import java.awt.*;

/**
 * @author Peter Karich
 */
public class DebugAStar extends AStar implements DebugAlgo {
    private final GraphicsWrapper mg;
    private Graphics2D g2;
    private NodeAccess na;

    public DebugAStar(Graph graph, Weighting type, TraversalMode tMode, GraphicsWrapper mg) {
        super(graph, type, tMode);
        this.mg = mg;
        na = graph.getNodeAccess();
    }

    @Override
    public void setGraphics2D(Graphics2D g2) {
        this.g2 = g2;
    }

    @Override
    public void updateBestPath(EdgeIteratorState es, SPTEntry bestEE, int currLoc) {
        if (g2 != null) {
            mg.plotEdge(g2, na.getLat(bestEE.parent.adjNode), na.getLon(bestEE.parent.adjNode), na.getLat(currLoc), na.getLon(currLoc), .8f);
        }
        super.updateBestPath(es, bestEE, currLoc);
    }
}
