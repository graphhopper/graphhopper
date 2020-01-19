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

import com.graphhopper.routing.DijkstraBidirectionRef;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.SPTEntry;

import java.awt.*;

/**
 * @author Peter Karich
 */
public class DebugDijkstraBidirection extends DijkstraBidirectionRef implements DebugAlgo {
    private final GraphicsWrapper mg;
    private Graphics2D g2;
    private NodeAccess na;

    public DebugDijkstraBidirection(Graph graph, Weighting type, TraversalMode tMode, GraphicsWrapper mg) {
        super(graph, type, tMode);
        this.mg = mg;
        na = graph.getNodeAccess();
    }

    @Override
    public void setGraphics2D(Graphics2D g2) {
        this.g2 = g2;
    }

    @Override
    public void updateBestPath(double edgeWeight, SPTEntry entry, int origEdgeId, int traversalId, boolean reverse) {
        if (g2 != null) {
            mg.plotEdge(g2, na.getLat(entry.parent.adjNode), na.getLon(entry.parent.adjNode), na.getLat(entry.adjNode), na.getLon(entry.adjNode), .8f);
        }
        // System.out.println("new node:" + currLoc);
        super.updateBestPath(edgeWeight, entry, origEdgeId, traversalId, reverse);
    }
}
