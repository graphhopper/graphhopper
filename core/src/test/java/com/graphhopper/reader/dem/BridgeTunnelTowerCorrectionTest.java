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

import static com.graphhopper.routing.ev.RoadEnvironment.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

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

        GHUtility.setSpeed(60, 60, accessEnc, speedEnc,
                graph.edge(0, 1).setDistance(15).set(roadEnvEnc, ROAD),
                graph.edge(1, 2).setDistance(15).set(roadEnvEnc, ROAD),
                graph.edge(2, 3).setDistance(15).set(roadEnvEnc, ROAD),
                graph.edge(3, 4).setDistance(15).set(roadEnvEnc, BRIDGE),
                graph.edge(4, 5).setDistance(15).set(roadEnvEnc, ROAD),
                graph.edge(5, 6).setDistance(15).set(roadEnvEnc, ROAD));

        new BridgeTunnelTowerCorrection(graph, roadEnvEnc).execute();

        // Tower 3 should now be close to the inverse-distance-weighted mean (IDW) of nearby road nodes — 100.
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
        na.setNode(4, 4, 0, 80);   // bridge outer with bad DEM — gets lifted to 100
        na.setNode(5, 5, 0, 100);
        na.setNode(6, 6, 0, 100);

        EdgeIteratorState e34;
        GHUtility.setSpeed(60, 60, accessEnc, speedEnc,
                graph.edge(0, 1).setDistance(15).set(roadEnvEnc, ROAD),
                graph.edge(1, 2).setDistance(15).set(roadEnvEnc, ROAD),
                graph.edge(2, 3).setDistance(15).set(roadEnvEnc, ROAD),
                e34 = graph.edge(3, 4).setDistance(15).set(roadEnvEnc, ROAD), // ramp edge with bad pillar
                graph.edge(4, 5).setDistance(15).set(roadEnvEnc, BRIDGE),
                graph.edge(5, 6).setDistance(15).set(roadEnvEnc, ROAD));

        // Pillar mid-ramp at bad DEM 85.
        e34.setWayGeometry(Helper.createPointList3D(3.5, 0, 85));

        new BridgeTunnelTowerCorrection(graph, roadEnvEnc).execute();

        // Tower 4 was corrected from 80 to ~100 via IDW, so the ramp edge (3-4) gets
        // its pillar re-interpolated linearly between 100 and ~100 → pillar at ~100
        // instead of its original bad DEM value of 85.
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

        GHUtility.setSpeed(60, 60, accessEnc, speedEnc,
                graph.edge(0, 1).setDistance(15).set(roadEnvEnc, ROAD),
                graph.edge(1, 2).setDistance(15).set(roadEnvEnc, ROAD),
                graph.edge(2, 3).setDistance(15).set(roadEnvEnc, ROAD),
                graph.edge(3, 4).setDistance(15).set(roadEnvEnc, TUNNEL),
                graph.edge(4, 5).setDistance(15).set(roadEnvEnc, ROAD),
                graph.edge(5, 6).setDistance(15).set(roadEnvEnc, ROAD));

        new BridgeTunnelTowerCorrection(graph, roadEnvEnc).execute();

        assertEquals(100, na.getEle(3), 0.5);
    }

    /** A bridge tower whose DEM is already consistent with nearby road must not be moved
     *  (IDW mean ≈ tower's own elevation). Real terrain at the bridge level. */
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

        GHUtility.setSpeed(60, 60, accessEnc, speedEnc,
                graph.edge(0, 1).setDistance(15).set(roadEnvEnc, ROAD),
                graph.edge(1, 2).setDistance(15).set(roadEnvEnc, ROAD),
                graph.edge(2, 3).setDistance(15).set(roadEnvEnc, ROAD),
                graph.edge(3, 4).setDistance(15).set(roadEnvEnc, BRIDGE),
                graph.edge(4, 5).setDistance(15).set(roadEnvEnc, ROAD),
                graph.edge(5, 6).setDistance(15).set(roadEnvEnc, ROAD));

        new BridgeTunnelTowerCorrection(graph, roadEnvEnc).execute();

        assertEquals(100, na.getEle(3), 0.5);
        assertEquals(100, na.getEle(4), 0.5);
    }

    /** A short bridge at the bottom of a real road gradient (B115 Vordernberger Straße,
     *  Eisenerz: the road descends to a 26 m bridge over a stream). The DEM is self-consistent,
     *  but all Dijkstra samples lie uphill on one side, so the raw IDW would lift the towers by
     *  several metres and turn the gentle deck into a >15% spike (which made racingbike avoid the
     *  bridge). Such a correction increases the deck gradient and must be rejected. */
    @Test
    public void doesNotLiftTowerAtTheBottomOfARealRoadGradient() {
        NodeAccess na = graph.getNodeAccess();
        // real-world elevations of the B115 case
        na.setNode(0, 0, 0, 801.16); // ground, road keeps climbing
        na.setNode(1, 1, 0, 799.81); // ground
        na.setNode(2, 2, 0, 796.05); // bridge tower at the bottom of the descent
        na.setNode(3, 3, 0, 795.44); // bridge tower
        na.setNode(4, 4, 0, 796.58); // ground
        na.setNode(5, 5, 0, 796.60); // ground

        GHUtility.setSpeed(60, 60, accessEnc, speedEnc,
                graph.edge(0, 1).setDistance(15).set(roadEnvEnc, ROAD),
                graph.edge(1, 2).setDistance(35).set(roadEnvEnc, ROAD),
                graph.edge(2, 3).setDistance(26).set(roadEnvEnc, BRIDGE),
                graph.edge(3, 4).setDistance(35).set(roadEnvEnc, ROAD),
                graph.edge(4, 5).setDistance(15).set(roadEnvEnc, ROAD));

        new BridgeTunnelTowerCorrection(graph, roadEnvEnc).execute();

        // Without the steepening guard the IDW of the one-sided uphill samples (~800) would
        // replace tower 2 and create a ~16% deck where the DEM says only ~-2.3%.
        assertEquals(796.05, na.getEle(2), 0.1);
        // The deck must stay gentle.
        assertEquals(0, Math.abs(na.getEle(2) - na.getEle(3)) / 26, 0.05);
        // Ground untouched.
        assertEquals(799.81, na.getEle(1), 1e-3);
        assertEquals(796.58, na.getEle(4), 1e-3);
    }

    /** A viaduct over a valley where BOTH towers sit in the valley DEM (Klausenbachviadukt,
     *  Lenzkirch: 47 m deck, towers ~8/11 m too low). Each tower's correction alone would steepen
     *  the deck against the other tower's old elevation, but lifted together the deck flattens and
     *  both ramps drop to ~0%. The guard must evaluate the towers' proposed elevations jointly. */
    @Test
    public void liftsBothViaductTowersTogether() {
        NodeAccess na = graph.getNodeAccess();
        // real-world elevations of the Klausenbachviadukt case
        na.setNode(0, 0, 0, 807.50); // ground
        na.setNode(1, 1, 0, 807.28); // ground
        na.setNode(2, 2, 0, 799.39); // viaduct tower, valley DEM (too low)
        na.setNode(3, 3, 0, 794.50); // viaduct tower, valley DEM (too low)
        na.setNode(4, 4, 0, 805.87); // ground
        na.setNode(5, 5, 0, 806.00); // ground

        GHUtility.setSpeed(60, 60, accessEnc, speedEnc,
                graph.edge(0, 1).setDistance(15).set(roadEnvEnc, ROAD),
                graph.edge(1, 2).setDistance(42).set(roadEnvEnc, ROAD),
                graph.edge(2, 3).setDistance(47).set(roadEnvEnc, BRIDGE),
                graph.edge(3, 4).setDistance(35).set(roadEnvEnc, ROAD),
                graph.edge(4, 5).setDistance(15).set(roadEnvEnc, ROAD));

        new BridgeTunnelTowerCorrection(graph, roadEnvEnc).execute();

        // Both towers lifted to ~the surrounding road level, the deck nearly level instead of
        // dropping ~5 m into the valley.
        assertEquals(807.3, na.getEle(2), 0.6);
        assertEquals(805.9, na.getEle(3), 0.6);
        assertEquals(0, Math.abs(na.getEle(2) - na.getEle(3)) / 47, 0.05);
    }

    /** Like {@link #liftsBothViaductTowersTogether()}, but the deck consists of THREE edges (a long
     *  bridge split into several OSM ways), so there are two inner towers. Inner towers have no road
     *  samples and are re-interpolated between the outer towers by EdgeElevationInterpolator
     *  afterwards — so their stale valley DEM must not make the guard reject the outer towers' lift. */
    @Test
    public void liftsOuterTowersOfMultiEdgeViaduct() {
        NodeAccess na = graph.getNodeAccess();
        na.setNode(0, 0, 0, 807.50); // ground
        na.setNode(1, 1, 0, 807.28); // ground
        na.setNode(2, 2, 0, 799.39); // outer viaduct tower, valley DEM (too low)
        na.setNode(3, 3, 0, 785.00); // inner viaduct tower, deep valley DEM
        na.setNode(4, 4, 0, 786.00); // inner viaduct tower, deep valley DEM
        na.setNode(5, 5, 0, 794.50); // outer viaduct tower, valley DEM (too low)
        na.setNode(6, 6, 0, 805.87); // ground
        na.setNode(7, 7, 0, 806.00); // ground

        GHUtility.setSpeed(60, 60, accessEnc, speedEnc,
                graph.edge(0, 1).setDistance(15).set(roadEnvEnc, ROAD),
                graph.edge(1, 2).setDistance(42).set(roadEnvEnc, ROAD),
                graph.edge(2, 3).setDistance(30).set(roadEnvEnc, BRIDGE),
                graph.edge(3, 4).setDistance(30).set(roadEnvEnc, BRIDGE),
                graph.edge(4, 5).setDistance(30).set(roadEnvEnc, BRIDGE),
                graph.edge(5, 6).setDistance(35).set(roadEnvEnc, ROAD),
                graph.edge(6, 7).setDistance(15).set(roadEnvEnc, ROAD));

        new BridgeTunnelTowerCorrection(graph, roadEnvEnc).execute();

        // Outer towers lifted to ~the surrounding road level, inner towers untouched here
        // (EdgeElevationInterpolator interpolates them from the outers afterwards).
        assertEquals(807.3, na.getEle(2), 0.6);
        assertEquals(805.9, na.getEle(5), 0.6);
        assertEquals(785.0, na.getEle(3), 0.5);
        assertEquals(786.0, na.getEle(4), 0.5);
    }

    /** The steepening guard must judge the DECK (structure edges) only, not the ramps. A bridge
     *  tower sits at the bottom of a STEEP approach ramp but has a flat, self-consistent DEM deck.
     *  Lifting the tower (one-sided IDW ~112) would flatten the steep ramp while turning the flat
     *  deck into a ~20% spike — a net the old "max over all incident edges" guard wrongly accepted,
     *  because the ramp improvement masked the deck getting steeper. Judging the deck alone keeps it
     *  flat. */
    @Test
    public void doesNotTradeASteepRampForASteepDeck() {
        NodeAccess na = graph.getNodeAccess();
        na.setNode(0, 0, 0, 120); // ground, steep ramp up
        na.setNode(1, 1, 0, 110); // ground, steep ramp up
        na.setNode(2, 2, 0, 100); // bridge tower at the bottom, flat DEM deck (correct)
        na.setNode(3, 3, 0, 100); // bridge tower, flat DEM deck (correct)
        na.setNode(4, 4, 0, 100); // ground, flat
        na.setNode(5, 5, 0, 100); // ground, flat

        GHUtility.setSpeed(60, 60, accessEnc, speedEnc,
                graph.edge(0, 1).setDistance(20).set(roadEnvEnc, ROAD),
                graph.edge(1, 2).setDistance(20).set(roadEnvEnc, ROAD),
                graph.edge(2, 3).setDistance(60).set(roadEnvEnc, BRIDGE),
                graph.edge(3, 4).setDistance(20).set(roadEnvEnc, ROAD),
                graph.edge(4, 5).setDistance(20).set(roadEnvEnc, ROAD));

        new BridgeTunnelTowerCorrection(graph, roadEnvEnc).execute();

        // The flat deck must be kept flat, not steepened by the one-sided uphill IDW.
        assertEquals(100, na.getEle(2), 0.5);
        assertEquals(100, na.getEle(3), 0.5);
        assertEquals(0, Math.abs(na.getEle(2) - na.getEle(3)) / 60, 0.02);
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

        GHUtility.setSpeed(60, 60, accessEnc, speedEnc,
                graph.edge(0, 1).setDistance(50).set(roadEnvEnc, ROAD),
                graph.edge(1, 2).setDistance(50).set(roadEnvEnc, TUNNEL),
                graph.edge(2, 3).setDistance(50).set(roadEnvEnc, TUNNEL),
                graph.edge(3, 4).setDistance(50).set(roadEnvEnc, BRIDGE),
                graph.edge(4, 5).setDistance(50).set(roadEnvEnc, TUNNEL),
                graph.edge(5, 6).setDistance(50).set(roadEnvEnc, TUNNEL),
                graph.edge(6, 7).setDistance(50).set(roadEnvEnc, ROAD));

        new BridgeTunnelTowerCorrection(graph, roadEnvEnc).execute();

        // Boundary nodes 3 and 4 have no road neighbours within 50 m via non-structure
        // edges (every path goes through tunnel/bridge). They stay at DEM.
        assertEquals(1000, na.getEle(3), 0.5);
        assertEquals(1000, na.getEle(4), 0.5);
        // Tunnel outer 1 IS reachable from road (one non-structure edge to road tower 0,
        // which is itself pure ground). With MIN_ROAD_SAMPLES=1 there is enough data, but
        // the inferred elevation equals the current value, so it remains unchanged.
        assertEquals(200, na.getEle(1), 0.5);
    }

}
