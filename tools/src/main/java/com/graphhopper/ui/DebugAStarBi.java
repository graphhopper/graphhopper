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
import com.graphhopper.routing.AStarBidirection;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.SPTEntry;
import com.graphhopper.util.EdgeIteratorState;

import java.awt.*;

/**
 * @author Peter Karich
 */
public class DebugAStarBi extends AStarBidirection implements DebugAlgo {
    private final GraphicsWrapper mg;
    private Graphics2D g2;

    public DebugAStarBi(Graph graph, Weighting type, TraversalMode tMode, GraphicsWrapper mg) {
        super(graph, type, tMode);
        this.mg = mg;
    }

    @Override
    public void setGraphics2D(Graphics2D g2) {
        this.g2 = g2;
    }

    @Override
    public void updateBestPath(EdgeIteratorState edgeState, AStar.AStarEntry entryCurrent, int currLoc) {
        if (g2 != null) {
            mg.plotNode(g2, currLoc, Color.YELLOW);
        }
        super.updateBestPath(edgeState, entryCurrent, currLoc);
    }

    @Override
    public void updateBestPath(EdgeIteratorState es, SPTEntry bestEE, int currLoc) {
        throw new IllegalStateException("cannot happen");
    }

    @Override
    public String toString() {
        return "debugui|" + super.toString();
    }
}
