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

import com.carrotsearch.hppc.IntArrayList;
import com.graphhopper.json.Statement;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.RoadClass;
import com.graphhopper.routing.util.*;
import com.graphhopper.routing.weighting.PriorityWeighting;
import com.graphhopper.routing.weighting.TurnCostProvider;
import com.graphhopper.routing.weighting.custom.CustomModelParser;
import com.graphhopper.routing.weighting.custom.CustomWeighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.CustomModel;
import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PriorityRoutingTest {

    @Test
    void testMaxPriority() {
        BikeFlagEncoder encoder = new BikeFlagEncoder();
        EncodingManager em = EncodingManager.create(encoder);
        BaseGraph graph = new BaseGraph.Builder(em).create();
        NodeAccess na = graph.getNodeAccess();
        na.setNode(0, 48.0, 11.0);
        na.setNode(1, 48.1, 11.1);
        na.setNode(2, 48.2, 11.2);
        na.setNode(3, 48.3, 11.3);
        na.setNode(4, 48.1, 11.0);
        na.setNode(5, 48.2, 11.1);
        // 0 - 1 - 2 - 3
        //  \- 4 - 5 -/
        double dist1 = 0;
        dist1 += maxSpeedEdge(em, graph, 0, 1, encoder, 1.0).getDistance();
        dist1 += maxSpeedEdge(em, graph, 1, 2, encoder, 1.0).getDistance();
        dist1 += maxSpeedEdge(em, graph, 2, 3, encoder, 1.0).getDistance();

        final double maxPrio = PriorityCode.getFactor(PriorityCode.BEST.getValue());
        double dist2 = 0;
        dist2 += maxSpeedEdge(em, graph, 0, 4, encoder, maxPrio).getDistance();
        dist2 += maxSpeedEdge(em, graph, 4, 5, encoder, maxPrio).getDistance();
        dist2 += maxSpeedEdge(em, graph, 5, 3, encoder, maxPrio).getDistance();

        // the routes 0-1-2-3 and 0-4-5-3 have similar distances (and use max speed everywhere)
        // ... but the shorter route 0-1-2-3 has smaller priority
        assertEquals(40101, dist1, 1);
        assertEquals(43005, dist2, 1);

        // A* and Dijkstra should yield the same path (the max priority must be taken into account by weighting.getMinWeight)
        {
            PriorityWeighting weighting = new PriorityWeighting(encoder, new PMap(), TurnCostProvider.NO_TURN_COST_PROVIDER);
            Path pathDijkstra = new Dijkstra(graph, weighting, TraversalMode.NODE_BASED).calcPath(0, 3);
            Path pathAStar = new AStar(graph, weighting, TraversalMode.NODE_BASED).calcPath(0, 3);
            assertEquals(pathDijkstra.calcNodes(), pathAStar.calcNodes());
            assertEquals(IntArrayList.from(0, 4, 5, 3), pathAStar.calcNodes());
        }

        {
            CustomModel customModel = new CustomModel();
            CustomWeighting weighting = CustomModelParser.createWeighting(encoder, em, TurnCostProvider.NO_TURN_COST_PROVIDER, customModel);
            Path pathDijkstra = new Dijkstra(graph, weighting, TraversalMode.NODE_BASED).calcPath(0, 3);
            Path pathAStar = new AStar(graph, weighting, TraversalMode.NODE_BASED).calcPath(0, 3);
            assertEquals(pathDijkstra.calcNodes(), pathAStar.calcNodes());
            assertEquals(IntArrayList.from(0, 4, 5, 3), pathAStar.calcNodes());
        }

        {
            CustomModel customModel = new CustomModel();
            // now we even increase the priority in the custom model, which also needs to be accounted for in weighting.getMinWeight
            customModel.addToPriority(Statement.If("road_class == MOTORWAY", Statement.Op.MULTIPLY, 3));
            CustomWeighting weighting = CustomModelParser.createWeighting(encoder, em, TurnCostProvider.NO_TURN_COST_PROVIDER, customModel);
            Path pathDijkstra = new Dijkstra(graph, weighting, TraversalMode.NODE_BASED).calcPath(0, 3);
            Path pathAStar = new AStar(graph, weighting, TraversalMode.NODE_BASED).calcPath(0, 3);
            assertEquals(pathDijkstra.calcNodes(), pathAStar.calcNodes());
            assertEquals(IntArrayList.from(0, 4, 5, 3), pathAStar.calcNodes());
        }
    }

    private EdgeIteratorState maxSpeedEdge(EncodingManager em, BaseGraph graph, int p, int q, FlagEncoder encoder, double prio) {
        BooleanEncodedValue accessEnc = encoder.getAccessEnc();
        DecimalEncodedValue speedEnc = encoder.getAverageSpeedEnc();
        DecimalEncodedValue priorityEnc = em.getDecimalEncodedValue(EncodingManager.getKey(encoder, "priority"));
        EnumEncodedValue<RoadClass> roadClassEnc = em.getEnumEncodedValue(RoadClass.KEY, RoadClass.class);
        return graph.edge(p, q)
                .set(accessEnc, true)
                .set(speedEnc, encoder.getMaxSpeed())
                .set(priorityEnc, prio)
                .set(roadClassEnc, RoadClass.MOTORWAY)
                .setDistance(calcDist(graph, p, q));
    }

    private double calcDist(BaseGraph graph, int p, int q) {
        NodeAccess na = graph.getNodeAccess();
        return DistanceCalcEarth.DIST_EARTH.calcDist(na.getLat(p), na.getLon(p), na.getLat(q), na.getLon(q));
    }

}
