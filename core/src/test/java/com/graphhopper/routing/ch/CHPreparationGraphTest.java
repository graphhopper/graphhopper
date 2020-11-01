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

package com.graphhopper.routing.ch;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * So far there aren't many tests but the graph should be very easy to test as it has basically no dependencies and
 * if a problem arises it should be easy to write a test here.
 */
class CHPreparationGraphTest {

    @Test
    void basic() {
        // 0->4<-2
        // |
        // 3<-1
        CHPreparationGraph pg = CHPreparationGraph.nodeBased(5, 10);
        double inf = Double.POSITIVE_INFINITY;
        pg.addEdge(0, 4, 3, 10, inf);
        pg.addEdge(4, 2, 0, inf, 5);
        pg.addEdge(0, 3, 1, 6, 6);
        pg.addEdge(1, 3, 2, 9, inf);
        pg.prepareForContraction();

        assertEquals(3, pg.getDegree(0));
        assertEquals(2, pg.getDegree(4));

        pg.addShortcut(3, 4, 1, 3, 1, 3, 16, 2);
        pg.disconnect(0);
        PrepareGraphEdgeIterator iter = pg.createOutEdgeExplorer().setBaseNode(3);
        String res = "";
        while (iter.next()) {
            res += iter.toString() + ",";
        }
        assertEquals("3-4,", res);
    }
}