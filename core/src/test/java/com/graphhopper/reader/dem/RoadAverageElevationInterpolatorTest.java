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

import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PointList;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Robin Boldt
 */
public class RoadAverageElevationInterpolatorTest {

    Graph graph;
    NodeAccess na;

    @Before
    public void setup() {
        CarFlagEncoder encoder = new CarFlagEncoder();
        EncodingManager carManager = new EncodingManager(encoder);
        graph = new GraphBuilder(carManager).set3D(true).create();
        na = graph.getNodeAccess();
    }

    @Test
    public void interpolatesElevationOfPillarNodes() {

        na.setNode(0, 0, 0, 0);
        na.setNode(1, 0.001, 0.001, 50);
        na.setNode(2, 0.002, 0.002, 20);

        EdgeIteratorState edge1 = graph.edge(0, 1, 10, true);
        EdgeIteratorState edge2 = graph.edge(1, 2, 10, true);

        PointList pl1 = new PointList(3, true);
        pl1.add(0.0005, 0.0005, 100);
        edge1.setWayGeometry(pl1);

        PointList pl2 = new PointList(3, true);
        pl2.add(0.0015, 0.0015, 160);
        pl2.add(0.0016, 0.0015, 150);
        pl2.add(0.0017, 0.0015, 220);
        edge2.setWayGeometry(pl2);

        new RoadAverageElevationInterpolator().smoothElevation(graph);

        edge1 = graph.getEdgeIteratorState(0, 1);
        pl1 = edge1.fetchWayGeometry(3);
        assertEquals(3, pl1.size());
        assertEquals(50, pl1.getElevation(1), .1);

        edge2 = graph.getEdgeIteratorState(1, 2);
        pl2 = edge2.fetchWayGeometry(3);
        assertEquals(5, pl2.size());
        assertEquals(120, pl2.getElevation(1), .1);
        // This is not 120 anymore, as the point at index 1 was smoothed from 160=>120
        assertEquals(112, pl2.getElevation(2), .1);

        assertEquals(50, pl2.getEle(0), .1);
    }

    @Test
    public void interpolatesElevationOfTowerNodes() {

        na.setNode(0, 0, 0, 0);
        // Massive tower node outlier
        na.setNode(1, 0.001, 0.001, 500);
        na.setNode(2, 0.002, 0.002, 0);

        EdgeIteratorState edge1 = graph.edge(0, 1, 10, true);
        EdgeIteratorState edge2 = graph.edge(1, 2, 10, true);

        PointList pl1 = new PointList(3, true);
        pl1.add(0.0005, 0.0005, 10);
        edge1.setWayGeometry(pl1);

        PointList pl2 = new PointList(3, true);
        pl2.add(0.0016, 0.0015, 10);
        edge2.setWayGeometry(pl2);

        new RoadAverageElevationInterpolator().smoothElevation(graph);

        edge1 = graph.getEdgeIteratorState(0, 1);
        pl1 = edge1.fetchWayGeometry(3);
        assertEquals(3, pl1.size());
        assertEquals(170, pl1.getElevation(1), .1);

        edge2 = graph.getEdgeIteratorState(1, 2);
        pl2 = edge2.fetchWayGeometry(3);
        assertEquals(3, pl2.size());
        assertEquals(170, pl2.getElevation(1), .1);

        // Smooth from 500 to 335
        assertEquals(335, pl2.getEle(0), .1);
    }
}
