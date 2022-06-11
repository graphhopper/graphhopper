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

import com.graphhopper.routing.AlternativeRoute;
import com.graphhopper.routing.SPTEntry;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.NodeAccess;

import java.awt.*;

import static java.awt.Color.*;

/**
 * @author Peter Karich
 */
public class DebugAlternativeRoute extends AlternativeRoute implements DebugAlgo {
    private final GraphicsWrapper mg;
    private Graphics2D g2;
    private NodeAccess na;

    public DebugAlternativeRoute(Graph graph, Weighting weighting, TraversalMode tMode, double exploreFactor, GraphicsWrapper mg) {
        super(graph, weighting, tMode);
        setExplorationFactor(exploreFactor);
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
            if (entry != null && entry.parent != null) {
//                Color col = g2.getColor();
//                g2.setColor(reverse ? BLUE : Color.YELLOW.darker().darker().darker());
//                mg.plotEdge(g2, na.getLat(entry.parent.adjNode), na.getLon(entry.parent.adjNode), na.getLat(entry.adjNode), na.getLon(entry.adjNode), .8f);
//                g2.setColor(col);
            }
        }
        super.updateBestPath(edgeWeight, entry, origEdgeId, traversalId, reverse);
    }

    Color[] colors = new Color[] {ORANGE, ORANGE.darker().darker(), ORANGE.brighter().brighter(),
            GREEN, GREEN.darker().darker(), GREEN.brighter().brighter(),
            YELLOW, YELLOW.brighter().brighter(),
            BLUE.brighter().brighter(),
            CYAN, CYAN.brighter().brighter(), CYAN.darker().darker(),
            MAGENTA, MAGENTA.brighter().brighter(), MAGENTA.darker().darker(),
            PINK,
            RED, RED.darker().darker(), RED.brighter().brighter()
            };

    public void plot(SPTEntry entry, int hash) {
        if (g2 != null && entry != null) {
            Color col = g2.getColor();
            g2.setColor(colors[hash % colors.length]);
            mg.plotEdge(g2, na.getLat(entry.parent.adjNode), na.getLon(entry.parent.adjNode), na.getLat(entry.adjNode), na.getLon(entry.adjNode), 6f);
            g2.setColor(col);
        }
    }
}
