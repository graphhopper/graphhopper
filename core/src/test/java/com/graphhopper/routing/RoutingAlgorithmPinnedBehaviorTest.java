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

import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValueImpl;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.SpeedWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.util.PMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins subtle behavior of the routing algorithm layer that is not covered by the regular tests,
 * discovered during the Java-to-Kotlin conversion (see docs/pinned-behavior.md).
 */
public class RoutingAlgorithmPinnedBehaviorTest {

    @Test
    public void algorithmOptionsCopyConstructorDoesNotCopyTimeout() {
        AlgorithmOptions opts = new AlgorithmOptions()
                .setAlgorithm("astar")
                .setTraversalMode(TraversalMode.EDGE_BASED)
                .setMaxVisitedNodes(42)
                .setTimeoutMillis(1234)
                .setHints(new PMap().putObject("some_key", "some_value"));

        AlgorithmOptions copy = new AlgorithmOptions(opts);
        assertEquals("astar", copy.getAlgorithm());
        assertEquals(TraversalMode.EDGE_BASED, copy.getTraversalMode());
        assertEquals(42, copy.getMaxVisitedNodes());
        assertEquals("some_value", copy.getHints().getString("some_key", ""));
        // the timeout is deliberately NOT copied - this was the behavior of the Java implementation
        assertEquals(Long.MAX_VALUE, copy.getTimeoutMillis());
        // the hints are deep-copied: changing the copy must not write through to the original
        copy.getHints().putObject("some_key", "changed");
        assertEquals("some_value", opts.getHints().getString("some_key", ""));
    }

    @Test
    public void sptEntryCompareToTreatsEqualWeightsAsEqual() {
        SPTEntry a = new SPTEntry(1, 1.5);
        SPTEntry b = new SPTEntry(2, 1.5);
        // no tie-breaking on any other field, equal weights compare as 0 (heap order left to PriorityQueue)
        assertEquals(0, a.compareTo(b));
        assertEquals(-1, new SPTEntry(1, 1.0).compareTo(b));
        assertEquals(1, new SPTEntry(1, 2.0).compareTo(b));
        // exact toString format (adjNode (edge) weight: w)
        assertEquals("3 (7) weight: 1.5", new SPTEntry(7, 3, 1.5, null).toString());
    }

    @Test
    public void pathGetFromNodeThrowsOnEmptyPath() {
        BaseGraph graph = new BaseGraph.Builder(EncodingManager.start().build()).create();
        Path path = new Path(graph);
        IllegalStateException e = assertThrows(IllegalStateException.class, path::getFromNode);
        assertEquals("fromNode < 0 should not happen", e.getMessage());
    }

    @Test
    public void algorithmsAreSingleUseButDijkstraOneToManyIsReusable() {
        DecimalEncodedValue speedEnc = new DecimalEncodedValueImpl("speed", 5, 5, false);
        EncodingManager em = EncodingManager.start().add(speedEnc).build();
        BaseGraph graph = new BaseGraph.Builder(em).create();
        graph.edge(0, 1).setDistance(100).set(speedEnc, 10);
        graph.edge(1, 2).setDistance(100).set(speedEnc, 10);
        Weighting weighting = new SpeedWeighting(speedEnc);

        Dijkstra dijkstra = new Dijkstra(graph, weighting, TraversalMode.NODE_BASED);
        assertTrue(dijkstra.calcPath(0, 2).isFound());
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> dijkstra.calcPath(0, 2));
        assertEquals("Create a new instance per call", e.getMessage());

        // DijkstraOneToMany deliberately has no such check - it caches data structures between runs
        DijkstraOneToMany oneToMany = new DijkstraOneToMany(graph, weighting, TraversalMode.NODE_BASED);
        assertTrue(oneToMany.calcPath(0, 2).isFound());
        assertTrue(oneToMany.calcPath(0, 1).isFound());
    }
}
