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
    void convertForViaWays_throwsIfViaWayIsSplitIntoMultipleEdges() {
        BaseGraph graph = new BaseGraph.Builder(1).create();
        graph.edge(0, 1);
        graph.edge(1, 2);
        graph.edge(2, 3);
        graph.edge(3, 4);
        LongFunction<Iterator<IntCursor>> edgesByWay = way -> {
            // way 0 and 2 simply correspond to edges 0 and 3, but way 1 is split into the two edges 1 and 2
            if (way == 1) return IntArrayList.from(1, 2).iterator();
            else return IntArrayList.from(Math.toIntExact(way)).iterator();
        };
        OSMRestrictionException e = assertThrows(OSMRestrictionException.class,
                () -> new WayToEdgeConverter(graph, edgesByWay).convertForViaWays(ways(0), ways(1), ways(2)));
        assertTrue(e.getMessage().contains("has via member way that isn't split at adjacent ways"), e.getMessage());
    }

    private LongArrayList ways(long... ways) {
        return LongArrayList.from(ways);
    }

}