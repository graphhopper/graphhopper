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
package com.graphhopper.routing.util;

import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import org.junit.Test;

import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Robin Boldt
 */
public class NameSimilarityEdgeFilterTest {

    @Test
    public void testAccept() {

        EdgeFilter edgeFilter = new NameSimilarityEdgeFilter(new DefaultEdgeFilter(new CarFlagEncoder()), "Laufamholzstraße 154 Nürnberg");
        EdgeIteratorState edge = createTestEdgeIterator("Laufamholzstraße, ST1333");
        assertTrue(edgeFilter.accept(edge));

        edge = createTestEdgeIterator("Hauptstraße");
        assertFalse(edgeFilter.accept(edge));

        edge = createTestEdgeIterator("Lorem Ipsum");
        assertFalse(edgeFilter.accept(edge));

        edge = createTestEdgeIterator("");
        assertFalse(edgeFilter.accept(edge));

        edge = createTestEdgeIterator(null);
        assertFalse(edgeFilter.accept(edge));

        edgeFilter = new NameSimilarityEdgeFilter(new DefaultEdgeFilter(new CarFlagEncoder()), null);
        edge = createTestEdgeIterator("Laufamholzstraße, ST1333");
        assertTrue(edgeFilter.accept(edge));

        edgeFilter = new NameSimilarityEdgeFilter(new DefaultEdgeFilter(new CarFlagEncoder()), "");
        edge = createTestEdgeIterator("Laufamholzstraße, ST1333");
        assertTrue(edgeFilter.accept(edge));

        edgeFilter = new NameSimilarityEdgeFilter(new DefaultEdgeFilter(new CarFlagEncoder()), "Johannesstraße, 99636, Rastenberg, Deutschland");
        edge = createTestEdgeIterator("Laufamholzstraße, ST1333");
        assertFalse(edgeFilter.accept(edge));

        edge = createTestEdgeIterator("Johannesstraße");
        assertTrue(edgeFilter.accept(edge));

        edgeFilter = new NameSimilarityEdgeFilter(new DefaultEdgeFilter(new CarFlagEncoder()), "Hauptstraße, 39025, Naturns, Italien");
        edge = createTestEdgeIterator("Teststraße");
        assertFalse(edgeFilter.accept(edge));

        edge = createTestEdgeIterator("Hauptstraße");
        assertTrue(edgeFilter.accept(edge));
    }

    @Test
    public void testAcceptWithTypos() {
        EdgeFilter edgeFilter = new NameSimilarityEdgeFilter(new DefaultEdgeFilter(new CarFlagEncoder()), "Laufamholzstraße 154 Nürnberg");
        EdgeIteratorState edge = createTestEdgeIterator("Laufamholzstraße, ST1333");
        assertTrue(edgeFilter.accept(edge));

        // Single Typo
        edgeFilter = new NameSimilarityEdgeFilter(new DefaultEdgeFilter(new CarFlagEncoder()), "Kaufamholzstraße 154 Nürnberg");
        assertTrue(edgeFilter.accept(edge));

        // Two Typos
        edgeFilter = new NameSimilarityEdgeFilter(new DefaultEdgeFilter(new CarFlagEncoder()), "Kaufamholystraße 154 Nürnberg");
        assertTrue(edgeFilter.accept(edge));

        // Three Typos
        edgeFilter = new NameSimilarityEdgeFilter(new DefaultEdgeFilter(new CarFlagEncoder()), "Kaufmholystraße 154 Nürnberg");
        assertFalse(edgeFilter.accept(edge));

        edgeFilter = new NameSimilarityEdgeFilter(new DefaultEdgeFilter(new CarFlagEncoder()), "Hauptstraße");
        edge = createTestEdgeIterator("Hauptstraße");
        assertTrue(edgeFilter.accept(edge));

        // Single Typo
        edgeFilter = new NameSimilarityEdgeFilter(new DefaultEdgeFilter(new CarFlagEncoder()), "Hauptstrase");
        assertTrue(edgeFilter.accept(edge));

        // Two Typos
        edgeFilter = new NameSimilarityEdgeFilter(new DefaultEdgeFilter(new CarFlagEncoder()), "Hauptstrasi");
        assertFalse(edgeFilter.accept(edge));

        //TODO Maybe we should not allow matching too short strings here?
        // Distance - PerfectDistance = 1
        edgeFilter = new NameSimilarityEdgeFilter(new DefaultEdgeFilter(new CarFlagEncoder()), "z");
        assertTrue(edgeFilter.accept(edge));
        // Distance - PerfectDistance = 1
        edgeFilter = new NameSimilarityEdgeFilter(new DefaultEdgeFilter(new CarFlagEncoder()), "az");
        assertTrue(edgeFilter.accept(edge));

        // Distance - PerfectDistance = 2
        edgeFilter = new NameSimilarityEdgeFilter(new DefaultEdgeFilter(new CarFlagEncoder()), "xy");
        assertFalse(edgeFilter.accept(edge));
    }

    private EdgeIteratorState createTestEdgeIterator(final String name) {
        return new GHUtility.DisabledEdgeIterator() {

            @Override
            public String getName() {
                return name;
            }

            @Override
            public boolean isForward(FlagEncoder encoder) {
                return true;
            }

            @Override
            public boolean isBackward(FlagEncoder encoder) {
                return true;
            }
        };
    }
}
