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

import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.FetchMode;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PointList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BridgeTunnelTowerCorrectionTest {

    private BaseGraph graph;
    private EnumEncodedValue<RoadEnvironment> roadEnvEnc;
    private BooleanEncodedValue accessEnc;
    private DecimalEncodedValue speedEnc;

    @BeforeEach
    void setUp() {
        accessEnc = new SimpleBooleanEncodedValue("access", true);
        speedEnc = new DecimalEncodedValueImpl("speed", 5, 5, false);
        EncodingManager em = EncodingManager.start().add(accessEnc).add(speedEnc).add(RoadEnvironment.create()).build();
        graph = new BaseGraph.Builder(em).set3D(true).create();
        roadEnvEnc = em.getEnumEncodedValue(RoadEnvironment.KEY, RoadEnvironment.class);
    }

    /** Bridge tower-end has bad DEM (way too low). Surrounding road is at the correct
     *  deck level. The tower must be lifted to ~the road's elevation. */
    @Test
    public void liftsBridgeTowerFromValleyDemToSurroundingRoad() {
        NodeAccess na = graph.getNodeAccess();
        na.setNode(0, 0, 0, 100);
        na.setNode(1, 1, 0, 100);
        na.setNode(2, 2, 0, 100);
        na.setNode(3, 3, 0, 80);   // BRIDGE outer, bad DEM (valley below deck)
        na.setNode(4, 4, 0, 100);  // bridge outer (good)
        na.setNode(5, 5, 0, 100);
        na.setNode(6, 6, 0, 100);

        EdgeIteratorState e01, e12, e23, e34, e45, e56;
        GHUtility.setSpeed(60, 60, accessEnc, speedEnc,
                e01 = graph.edge(0, 1).setDistance(15),
                e12 = graph.edge(1, 2).setDistance(15),
                e23 = graph.edge(2, 3).setDistance(15),
                e34 = graph.edge(3, 4).setDistance(15), // BRIDGE
                e45 = graph.edge(4, 5).setDistance(15),
                e56 = graph.edge(5, 6).setDistance(15));

        e01.set(roadEnvEnc, RoadEnvironment.ROAD);
        e12.set(roadEnvEnc, RoadEnvironment.ROAD);
        e23.set(roadEnvEnc, RoadEnvironment.ROAD);
        e34.set(roadEnvEnc, RoadEnvironment.BRIDGE);
        e45.set(roadEnvEnc, RoadEnvironment.ROAD);
        e56.set(roadEnvEnc, RoadEnvironment.ROAD);

        new BridgeTunnelTowerCorrection(graph, roadEnvEnc).execute();

        // Tower 3 should now be the median of nearby road nodes — 100.
        assertEquals(100, na.getEle(3), 0.5);
        // Tower 4 has plenty of good neighbours too, stays at 100.
        assertEquals(100, na.getEle(4), 0.5);
        // Ground untouched.
        assertEquals(100, na.getEle(0), 1e-9);
        assertEquals(100, na.getEle(6), 1e-9);
    }

    /** Pillar on the ramp edge immediately before the bridge has bad DEM. After
     *  the tower is corrected, the pillar must be re-interpolated linearly between
     *  the two endpoints of that ramp edge (mirroring how bridge interior pillars
     *  are interpolated). */
    @Test
    public void reinterpolatesRampPillarAfterTowerCorrection() {
        NodeAccess na = graph.getNodeAccess();
        na.setNode(0, 0, 0, 100);
        na.setNode(1, 1, 0, 100);
        na.setNode(2, 2, 0, 100);
        na.setNode(3, 3, 0, 100);  // ramp tower right before the bridge
        na.setNode(4, 4, 0, 100);  // bridge outer (correct)
        na.setNode(5, 5, 0, 100);
        na.setNode(6, 6, 0, 100);

        EdgeIteratorState e01, e12, e23, e34, e45, e56;
        GHUtility.setSpeed(60, 60, accessEnc, speedEnc,
                e01 = graph.edge(0, 1).setDistance(15),
                e12 = graph.edge(1, 2).setDistance(15),
                e23 = graph.edge(2, 3).setDistance(15),
                e34 = graph.edge(3, 4).setDistance(15), // ramp edge with bad pillar
                e45 = graph.edge(4, 5).setDistance(15), // BRIDGE
                e56 = graph.edge(5, 6).setDistance(15));

        // Pillar mid-ramp at bad DEM 85.
        e34.setWayGeometry(Helper.createPointList3D(3.5, 0, 85));

        e01.set(roadEnvEnc, RoadEnvironment.ROAD);
        e12.set(roadEnvEnc, RoadEnvironment.ROAD);
        e23.set(roadEnvEnc, RoadEnvironment.ROAD);
        e34.set(roadEnvEnc, RoadEnvironment.ROAD);
        e45.set(roadEnvEnc, RoadEnvironment.BRIDGE);
        e56.set(roadEnvEnc, RoadEnvironment.ROAD);

        new BridgeTunnelTowerCorrection(graph, roadEnvEnc).execute();

        // The ramp edge connects tower 3 (= bridge outer, was already correct at 100)
        // and tower 4 — so its pillar should be re-interpolated between 100 and 100.
        PointList pl = e34.fetchWayGeometry(FetchMode.ALL);
        assertEquals(3, pl.size());
        assertEquals(100, pl.getEle(1), 0.5);
    }

    /** Tunnel-end has bad DEM (high, sampling surface above). Surrounding road is at
     *  the correct road level. The tunnel-end must be pulled down to the road level. */
    @Test
    public void pullsTunnelEndDownFromSurfaceAbove() {
        NodeAccess na = graph.getNodeAccess();
        na.setNode(0, 0, 0, 100);
        na.setNode(1, 1, 0, 100);
        na.setNode(2, 2, 0, 100);
        na.setNode(3, 3, 0, 500); // tunnel outer, bad DEM (ridge above)
        na.setNode(4, 4, 0, 100); // tunnel outer
        na.setNode(5, 5, 0, 100);
        na.setNode(6, 6, 0, 100);

        EdgeIteratorState e01, e12, e23, e34, e45, e56;
        GHUtility.setSpeed(60, 60, accessEnc, speedEnc,
                e01 = graph.edge(0, 1).setDistance(15),
                e12 = graph.edge(1, 2).setDistance(15),
                e23 = graph.edge(2, 3).setDistance(15),
                e34 = graph.edge(3, 4).setDistance(15), // TUNNEL
                e45 = graph.edge(4, 5).setDistance(15),
                e56 = graph.edge(5, 6).setDistance(15));

        e01.set(roadEnvEnc, RoadEnvironment.ROAD);
        e12.set(roadEnvEnc, RoadEnvironment.ROAD);
        e23.set(roadEnvEnc, RoadEnvironment.ROAD);
        e34.set(roadEnvEnc, RoadEnvironment.TUNNEL);
        e45.set(roadEnvEnc, RoadEnvironment.ROAD);
        e56.set(roadEnvEnc, RoadEnvironment.ROAD);

        new BridgeTunnelTowerCorrection(graph, roadEnvEnc).execute();

        assertEquals(100, na.getEle(3), 0.5);
    }

    /** A bridge tower whose DEM is already consistent with nearby road must not be moved
     *  (median ≈ tower's own elevation). Real terrain at the bridge level. */
    @Test
    public void doesNotMoveTowerWhenAlreadyConsistent() {
        NodeAccess na = graph.getNodeAccess();
        na.setNode(0, 0, 0, 100);
        na.setNode(1, 1, 0, 100);
        na.setNode(2, 2, 0, 100);
        na.setNode(3, 3, 0, 100); // bridge tower at correct elevation
        na.setNode(4, 4, 0, 100);
        na.setNode(5, 5, 0, 100);
        na.setNode(6, 6, 0, 100);

        EdgeIteratorState e01, e12, e23, e34, e45, e56;
        GHUtility.setSpeed(60, 60, accessEnc, speedEnc,
                e01 = graph.edge(0, 1).setDistance(15),
                e12 = graph.edge(1, 2).setDistance(15),
                e23 = graph.edge(2, 3).setDistance(15),
                e34 = graph.edge(3, 4).setDistance(15),
                e45 = graph.edge(4, 5).setDistance(15),
                e56 = graph.edge(5, 6).setDistance(15));

        e01.set(roadEnvEnc, RoadEnvironment.ROAD);
        e12.set(roadEnvEnc, RoadEnvironment.ROAD);
        e23.set(roadEnvEnc, RoadEnvironment.ROAD);
        e34.set(roadEnvEnc, RoadEnvironment.BRIDGE);
        e45.set(roadEnvEnc, RoadEnvironment.ROAD);
        e56.set(roadEnvEnc, RoadEnvironment.ROAD);

        new BridgeTunnelTowerCorrection(graph, roadEnvEnc).execute();

        assertEquals(100, na.getEle(3), 0.5);
        assertEquals(100, na.getEle(4), 0.5);
    }

    /** Tower with fewer than MIN_ROAD_SAMPLES pure-ground neighbours (deep inside a
     *  combined structure chain) is left at its DEM value — this is the
     *  tunnel→bridge→tunnel boundary case. */
    @Test
    public void leavesTowerAloneWhenNoRoadNeighbourReachable() {
        NodeAccess na = graph.getNodeAccess();
        na.setNode(0, 0, 0, 200); // road
        na.setNode(1, 1, 0, 200); // tunnel outer
        na.setNode(2, 2, 0, 1000); // tunnel inner — touches only tunnel
        na.setNode(3, 3, 0, 1000); // tunnel→bridge boundary — touches only structures
        na.setNode(4, 4, 0, 1000); // bridge→tunnel boundary
        na.setNode(5, 5, 0, 1000); // tunnel inner
        na.setNode(6, 6, 0, 100); // tunnel outer
        na.setNode(7, 7, 0, 100); // road

        EdgeIteratorState e01, e12, e23, e34, e45, e56, e67;
        GHUtility.setSpeed(60, 60, accessEnc, speedEnc,
                e01 = graph.edge(0, 1).setDistance(50),
                e12 = graph.edge(1, 2).setDistance(50),
                e23 = graph.edge(2, 3).setDistance(50),
                e34 = graph.edge(3, 4).setDistance(50), // BRIDGE
                e45 = graph.edge(4, 5).setDistance(50),
                e56 = graph.edge(5, 6).setDistance(50),
                e67 = graph.edge(6, 7).setDistance(50));

        e01.set(roadEnvEnc, RoadEnvironment.ROAD);
        e12.set(roadEnvEnc, RoadEnvironment.TUNNEL);
        e23.set(roadEnvEnc, RoadEnvironment.TUNNEL);
        e34.set(roadEnvEnc, RoadEnvironment.BRIDGE);
        e45.set(roadEnvEnc, RoadEnvironment.TUNNEL);
        e56.set(roadEnvEnc, RoadEnvironment.TUNNEL);
        e67.set(roadEnvEnc, RoadEnvironment.ROAD);

        new BridgeTunnelTowerCorrection(graph, roadEnvEnc).execute();

        // Boundary nodes 3 and 4 have no road neighbours within 50 m via non-structure
        // edges (every path goes through tunnel/bridge). They stay at DEM.
        assertEquals(1000, na.getEle(3), 0.5);
        assertEquals(1000, na.getEle(4), 0.5);
        // Tunnel outer 1 IS reachable from road (one non-structure edge to road tower 0,
        // which is itself pure ground). But MIN_ROAD_SAMPLES=3 — only one road sample,
        // so left at DEM.
        assertEquals(200, na.getEle(1), 0.5);
    }

}
