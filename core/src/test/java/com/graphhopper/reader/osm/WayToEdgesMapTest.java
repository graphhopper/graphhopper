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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WayToEdgesMapTest {

    @Test
    void notReserved() {
        WayToEdgesMap wayToEdgesMap = new WayToEdgesMap();
        wayToEdgesMap.reserve(5);
        wayToEdgesMap.putIfReserved(5, 4);
        wayToEdgesMap.putIfReserved(6, 2);
        checkEdges(wayToEdgesMap, 5, 4);
        // since we did not reserve 6, no edges were added. This is exactly what we want for turn restrictions when
        // reading OSM: first we reserve all the ways that appear in restriction relations and later we only add the
        // edges for these (but not other) ways.
        checkEdges(wayToEdgesMap, 6);
    }

    @Test
    void waysMustBeInOrder() {
        WayToEdgesMap wayToEdgesMap = new WayToEdgesMap();
        wayToEdgesMap.reserve(10);
        wayToEdgesMap.reserve(11);
        wayToEdgesMap.putIfReserved(10, 3);
        wayToEdgesMap.putIfReserved(11, 5);
        assertThrows(IllegalArgumentException.class, () -> wayToEdgesMap.putIfReserved(10, 3));
    }

    @Test
    void basic() {
        WayToEdgesMap wayToEdgesMap = new WayToEdgesMap();
        wayToEdgesMap.reserve(10);
        wayToEdgesMap.reserve(11);
        wayToEdgesMap.reserve(12);
        wayToEdgesMap.reserve(6);
        wayToEdgesMap.reserve(1234);
        wayToEdgesMap.reserve(7);
        wayToEdgesMap.putIfReserved(10, 3);
        wayToEdgesMap.putIfReserved(10, 5);
        wayToEdgesMap.putIfReserved(6, 1);
        wayToEdgesMap.putIfReserved(1234, 12);
        wayToEdgesMap.putIfReserved(1234, 13);
        wayToEdgesMap.putIfReserved(1234, 13);
        wayToEdgesMap.putIfReserved(7, 2);
        checkEdges(wayToEdgesMap, 10, 3, 5);
        checkEdges(wayToEdgesMap, 6, 1);
        checkEdges(wayToEdgesMap, 1234, 12, 13, 13);
        checkEdges(wayToEdgesMap, 7, 2);
        checkEdges(wayToEdgesMap, 42);
    }

    @Test
    void another() {
        WayToEdgesMap wayToEdgesMap = new WayToEdgesMap();
        wayToEdgesMap.reserve(1);
        wayToEdgesMap.reserve(2);
        wayToEdgesMap.reserve(3);
        wayToEdgesMap.putIfReserved(1, 0);
        wayToEdgesMap.putIfReserved(1, 1);
        wayToEdgesMap.putIfReserved(3, 3);
        wayToEdgesMap.putIfReserved(3, 4);
        checkEdges(wayToEdgesMap, 1, 0, 1);
        checkEdges(wayToEdgesMap, 3, 3, 4);
    }

    @Test
    void reserveButDoNotPut() {
        WayToEdgesMap wayToEdgesMap = new WayToEdgesMap();
        wayToEdgesMap.reserve(1);
        wayToEdgesMap.reserve(2);
        wayToEdgesMap.putIfReserved(2, 42);
        checkEdges(wayToEdgesMap, 1);
        checkEdges(wayToEdgesMap, 2, 42);
    }

    private void checkEdges(WayToEdgesMap wayToEdgesMap, long way, int... expected) {
        IntArrayList edges = new IntArrayList();
        wayToEdgesMap.getEdges(way).forEachRemaining(c -> edges.add(c.value));
        assertEquals(IntArrayList.from(expected), edges);
    }

}