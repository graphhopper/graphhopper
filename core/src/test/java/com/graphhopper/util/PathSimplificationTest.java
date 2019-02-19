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
package com.graphhopper.util;

import com.graphhopper.PathWrapper;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.Dijkstra;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.ShortestWeighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.Parameters.DETAILS;
import com.graphhopper.util.details.PathDetail;
import com.graphhopper.util.details.PathDetailsBuilderFactory;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Robin Boldt
 */
public class PathSimplificationTest {

    private final TranslationMap trMap = TranslationMapTest.SINGLETON;
    private final Translation usTR = trMap.getWithFallBack(Locale.US);
    private final TraversalMode tMode = TraversalMode.NODE_BASED;
    private EncodingManager carManager;
    private FlagEncoder carEncoder;

    @Before
    public void setUp() {
        carEncoder = new CarFlagEncoder();
        carManager = EncodingManager.create(carEncoder);
    }

    @Test
    public void testScenario() {
        Graph g = new GraphBuilder(carManager).create();
        // 0-1-2
        // | | |
        // 3-4-5  9-10
        // | | |  |
        // 6-7-8--*
        NodeAccess na = g.getNodeAccess();
        na.setNode(0, 1.2, 1.0);
        na.setNode(1, 1.2, 1.1);
        na.setNode(2, 1.2, 1.2);
        na.setNode(3, 1.1, 1.0);
        na.setNode(4, 1.1, 1.1);
        na.setNode(5, 1.1, 1.2);
        na.setNode(9, 1.1, 1.3);
        na.setNode(10, 1.1, 1.4);

        na.setNode(6, 1.0, 1.0);
        na.setNode(7, 1.0, 1.1);
        na.setNode(8, 1.0, 1.2);

        ReaderWay w = new ReaderWay(1);
        w.setTag("highway", "tertiary");
        w.setTag("maxspeed", "10");

        EdgeIteratorState tmpEdge;
        tmpEdge = g.edge(0, 1, 10000, true).setName("0-1");
        EncodingManager.AcceptWay map = new EncodingManager.AcceptWay();
        assertTrue(carManager.acceptWay(w, map));
        tmpEdge.setFlags(carManager.handleWayTags(w, map, 0));
        tmpEdge = g.edge(1, 2, 11000, true).setName("1-2");
        tmpEdge.setFlags(carManager.handleWayTags(w, map, 0));

        w.setTag("maxspeed", "20");
        tmpEdge = g.edge(0, 3, 11000, true);
        tmpEdge.setFlags(carManager.handleWayTags(w, map, 0));
        tmpEdge = g.edge(1, 4, 10000, true).setName("1-4");
        tmpEdge.setFlags(carManager.handleWayTags(w, map, 0));
        tmpEdge = g.edge(2, 5, 11000, true).setName("5-2");
        tmpEdge.setFlags(carManager.handleWayTags(w, map, 0));

        w.setTag("maxspeed", "30");
        tmpEdge = g.edge(3, 6, 11000, true).setName("3-6");
        tmpEdge.setFlags(carManager.handleWayTags(w, map, 0));
        tmpEdge = g.edge(4, 7, 10000, true).setName("4-7");
        tmpEdge.setFlags(carManager.handleWayTags(w, map, 0));
        tmpEdge = g.edge(5, 8, 10000, true).setName("5-8");
        tmpEdge.setFlags(carManager.handleWayTags(w, map, 0));

        w.setTag("maxspeed", "40");
        tmpEdge = g.edge(6, 7, 11000, true).setName("6-7");
        tmpEdge.setFlags(carManager.handleWayTags(w, map, 0));
        tmpEdge = g.edge(7, 8, 10000, true);
        PointList list = new PointList();
        list.add(1.0, 1.15);
        list.add(1.0, 1.16);
        tmpEdge.setWayGeometry(list);
        tmpEdge.setName("7-8");
        tmpEdge.setFlags(carManager.handleWayTags(w, map, 0));

        w.setTag("maxspeed", "50");
        // missing edge name
        tmpEdge = g.edge(9, 10, 10000, true);
        tmpEdge.setFlags(carManager.handleWayTags(w, map, 0));
        tmpEdge = g.edge(8, 9, 20000, true);
        list.clear();
        list.add(1.0, 1.3);
        list.add(1.0, 1.3001);
        list.add(1.0, 1.3002);
        list.add(1.0, 1.3003);
        tmpEdge.setName("8-9");
        tmpEdge.setWayGeometry(list);
        tmpEdge.setFlags(carManager.handleWayTags(w, map, 0));

        // Path is: [0 0-1, 3 1-4, 6 4-7, 9 7-8, 11 8-9, 10 9-10]
        Path p = new Dijkstra(g, new ShortestWeighting(carEncoder), tMode).calcPath(0, 10);
        InstructionList wayList = p.calcInstructions(carManager.getBooleanEncodedValue(EncodingManager.ROUNDABOUT), usTR);
        Map<String, List<PathDetail>> details = p.calcDetails(Arrays.asList(DETAILS.AVERAGE_SPEED), new PathDetailsBuilderFactory(), 0);

        PathWrapper pathWrapper = new PathWrapper();
        pathWrapper.setInstructions(wayList);
        pathWrapper.addPathDetails(details);
        pathWrapper.setPoints(p.calcPoints());

        int numberOfPoints = p.calcPoints().size();

        DouglasPeucker douglasPeucker = new DouglasPeucker();
        // Do not simplify anything
        douglasPeucker.setMaxDistance(0);

        PathSimplification ps = new PathSimplification(pathWrapper, douglasPeucker, true);
        ps.simplify();

        assertEquals(numberOfPoints, pathWrapper.getPoints().size());

        pathWrapper = new PathWrapper();
        pathWrapper.setInstructions(wayList);
        pathWrapper.addPathDetails(details);
        pathWrapper.setPoints(p.calcPoints());

        douglasPeucker.setMaxDistance(100000000);
        ps = new PathSimplification(pathWrapper, douglasPeucker, true);
        ps.simplify();

        assertTrue(numberOfPoints > pathWrapper.getPoints().size());
    }

}
