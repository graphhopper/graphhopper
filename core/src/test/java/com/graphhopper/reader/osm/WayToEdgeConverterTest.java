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

package com.graphhopper.reader.osm;

import com.carrotsearch.hppc.IntArrayList;
import com.carrotsearch.hppc.LongArrayList;
import com.carrotsearch.hppc.cursors.IntCursor;
import com.graphhopper.storage.BaseGraph;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.function.LongFunction;

import static org.junit.jupiter.api.Assertions.*;

class WayToEdgeConverterTest {

    @Test
    void convertForViaWays() throws OSMRestrictionException {
        BaseGraph graph = new BaseGraph.Builder(1).create();
        for (int i = 0; i < 10; i++)
            graph.edge(i, i + 1);
        WayToEdgeConverter.EdgeResult edgeResult = new WayToEdgeConverter(graph, way -> IntArrayList.from(Math.toIntExact(way)).iterator())
                .convertForViaWays(ways(0), ways(2, 6, 4, 1, 7, 3, 5, 8), ways(9));
        assertEquals(IntArrayList.from(1, 2, 3, 4, 5, 6, 7, 8), edgeResult.getViaEdges());
        assertEquals(IntArrayList.from(1, 2, 3, 4, 5, 6, 7, 8, 9), edgeResult.getNodes());
    }

    @Test
    void convertForViaWays_multipleEdgesForViaWay() throws OSMRestrictionException {
        BaseGraph graph = new BaseGraph.Builder(1).create();
        graph.edge(0, 1);
        graph.edge(1, 2);
        graph.edge(2, 3);
        graph.edge(3, 4);
        LongFunction<Iterator<IntCursor>> edgesByWay = way -> {
            if (way == 0) return IntArrayList.from(0).iterator();
                // way 1 is split into the two edges 1 and 2
            else if (way == 1) return IntArrayList.from(1, 2).iterator();
            else if (way == 2) return IntArrayList.from(3).iterator();
            else throw new IllegalArgumentException();
        };
        WayToEdgeConverter.EdgeResult edgeResult = new WayToEdgeConverter(graph, edgesByWay).convertForViaWays(ways(0), ways(1), ways(2));
        assertEquals(IntArrayList.from(1, 2), edgeResult.getViaEdges());
        assertEquals(IntArrayList.from(1, 2, 3), edgeResult.getNodes());
    }

    @Test
    void convertForViaWays_multipleEdgesForViaWay_oppositeDirection() throws OSMRestrictionException {
        BaseGraph graph = new BaseGraph.Builder(1).create();
        graph.edge(0, 1);
        graph.edge(1, 2);
        graph.edge(2, 3);
        graph.edge(3, 4);
        LongFunction<Iterator<IntCursor>> edgesByWay = way -> {
            if (way == 0) return IntArrayList.from(0).iterator();
                // way 1 is split into the two edges 2, 1 (the wrong order)
                // Accepting an arbitrary order is important, because OSM ways are generally split into multiple edges
                // and a via-way might be pointing in the 'wrong' direction.
            else if (way == 1) return IntArrayList.from(2, 1).iterator();
            else if (way == 2) return IntArrayList.from(3).iterator();
            else throw new IllegalArgumentException();
        };
        WayToEdgeConverter.EdgeResult edgeResult = new WayToEdgeConverter(graph, edgesByWay).convertForViaWays(ways(0), ways(1), ways(2));
        assertEquals(IntArrayList.from(1, 2), edgeResult.getViaEdges());
        assertEquals(IntArrayList.from(1, 2, 3), edgeResult.getNodes());
    }

    @Test
    void convertForViaWays_reorderEdges() throws OSMRestrictionException {
        BaseGraph graph = new BaseGraph.Builder(1).create();
        graph.edge(0, 1);
        graph.edge(1, 2);
        // the next two edges are given in the 'wrong' order
        graph.edge(3, 4);
        graph.edge(2, 3);
        graph.edge(4, 5);
        graph.edge(5, 6);
        LongFunction<Iterator<IntCursor>> edgesByWay = way -> {
            // way 1 is split into the four edges 1-4
            if (way == 1) return IntArrayList.from(1, 2, 3, 4).iterator();
            else if (way == 0) return IntArrayList.from(0).iterator();
            else if (way == 2) return IntArrayList.from(5).iterator();
            else throw new IllegalArgumentException();
        };
        WayToEdgeConverter.EdgeResult edgeResult = new WayToEdgeConverter(graph, edgesByWay).convertForViaWays(ways(0), ways(1), ways(2));
        assertEquals(IntArrayList.from(1, 3, 2, 4), edgeResult.getViaEdges());
        assertEquals(IntArrayList.from(1, 2, 3, 4, 5), edgeResult.getNodes());
    }

    @Test
    void convertForViaWays_loop() {
        BaseGraph graph = new BaseGraph.Builder(1).create();
        //   4
        //   |
        // 0-1-2
        //   |/
        //   3
        graph.edge(0, 1);
        graph.edge(1, 2);
        graph.edge(2, 3);
        graph.edge(3, 1);
        graph.edge(1, 4);
        LongFunction<Iterator<IntCursor>> edgesByWay = way -> IntArrayList.from(Math.toIntExact(way)).iterator();
        OSMRestrictionException e = assertThrows(OSMRestrictionException.class, () ->
                new WayToEdgeConverter(graph, edgesByWay).convertForViaWays(ways(0), ways(1, 2, 3), ways(4)));
        // So far we allow the via ways/edges to be in an arbitrary order, but do not allow multiple solutions.
        assertTrue(e.getMessage().contains("has member ways that do not form a unique path"), e.getMessage());
    }

    @Test
    void convertForViaNode_multipleFrom() throws OSMRestrictionException {
        BaseGraph graph = new BaseGraph.Builder(1).create();
        graph.edge(1, 0);
        graph.edge(2, 0);
        graph.edge(3, 0);
        graph.edge(0, 4);
        WayToEdgeConverter.NodeResult nodeResult = new WayToEdgeConverter(graph, way -> IntArrayList.from(Math.toIntExact(way)).iterator())
                .convertForViaNode(ways(0, 1, 2), 0, ways(3));
        assertEquals(IntArrayList.from(0, 1, 2), nodeResult.getFromEdges());
        assertEquals(IntArrayList.from(3), nodeResult.getToEdges());
    }

    @Test
    void convertForViaNode_multipleTo() throws OSMRestrictionException {
        BaseGraph graph = new BaseGraph.Builder(1).create();
        graph.edge(1, 0);
        graph.edge(2, 0);
        graph.edge(3, 0);
        graph.edge(0, 4);
        WayToEdgeConverter.NodeResult nodeResult = new WayToEdgeConverter(graph, way -> IntArrayList.from(Math.toIntExact(way)).iterator())
                .convertForViaNode(ways(3), 0, ways(0, 1, 2));
        assertEquals(IntArrayList.from(3), nodeResult.getFromEdges());
        assertEquals(IntArrayList.from(0, 1, 2), nodeResult.getToEdges());
    }

    @Test
    void convertForViaWay_multipleFrom() throws OSMRestrictionException {
        BaseGraph graph = new BaseGraph.Builder(1).create();
        graph.edge(1, 0);
        graph.edge(2, 0);
        graph.edge(3, 0);
        graph.edge(0, 4);
        graph.edge(4, 5);
        WayToEdgeConverter.EdgeResult edgeResult = new WayToEdgeConverter(graph, way -> IntArrayList.from(Math.toIntExact(way)).iterator())
                .convertForViaWays(ways(0, 1, 2), ways(3), ways(4));
        assertEquals(IntArrayList.from(0, 1, 2), edgeResult.getFromEdges());
        assertEquals(IntArrayList.from(3), edgeResult.getViaEdges());
        assertEquals(IntArrayList.from(4), edgeResult.getToEdges());
        assertEquals(IntArrayList.from(0, 4), edgeResult.getNodes());
    }

    @Test
    void convertForViaWay_multipleTo() throws OSMRestrictionException {
        BaseGraph graph = new BaseGraph.Builder(1).create();
        graph.edge(1, 0);
        graph.edge(2, 0);
        graph.edge(3, 0);
        graph.edge(0, 4);
        graph.edge(4, 5);
        WayToEdgeConverter.EdgeResult edgeResult = new WayToEdgeConverter(graph, way -> IntArrayList.from(Math.toIntExact(way)).iterator())
                .convertForViaWays(ways(4), ways(3), ways(0, 1, 2));
        assertEquals(IntArrayList.from(0, 1, 2), edgeResult.getToEdges());
        assertEquals(IntArrayList.from(3), edgeResult.getViaEdges());
        assertEquals(IntArrayList.from(4), edgeResult.getFromEdges());
        assertEquals(IntArrayList.from(4, 0), edgeResult.getNodes());
    }

    private LongArrayList ways(long... ways) {
        return LongArrayList.from(ways);
    }

}
