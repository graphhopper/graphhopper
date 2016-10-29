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
import com.graphhopper.util.PointList;
import org.junit.Test;

import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Robin Boldt
 */
public class NameSimilarityEdgeFilterTest {

    @Test
    public void testAccept() {

        EdgeFilter edgeFilter = new NameSimilarityEdgeFilter(new DefaultEdgeFilter(new CarFlagEncoder()), "Laufamholzstraße 154 Nürnberg");
        EdgeIteratorState edge = new TestEdgeIterator("Laufamholzstraße, ST1333");
        assertTrue(edgeFilter.accept(edge));

        // Single Typo
        edge = new TestEdgeIterator("Kaufamholzstraße, ST1333");
        assertTrue(edgeFilter.accept(edge));

        // Two Typos
        edge = new TestEdgeIterator("Kaufamholystraße, ST1333");
        assertFalse(edgeFilter.accept(edge));

        edge = new TestEdgeIterator("Hauptstraße");
        assertFalse(edgeFilter.accept(edge));

        edge = new TestEdgeIterator("Lorem Ipsum");
        assertFalse(edgeFilter.accept(edge));

        edge = new TestEdgeIterator("");
        assertFalse(edgeFilter.accept(edge));

        edge = new TestEdgeIterator(null);
        assertFalse(edgeFilter.accept(edge));

        edgeFilter = new NameSimilarityEdgeFilter(new DefaultEdgeFilter(new CarFlagEncoder()), null);
        edge = new TestEdgeIterator("Laufamholzstraße, ST1333");
        assertTrue(edgeFilter.accept(edge));

        edgeFilter = new NameSimilarityEdgeFilter(new DefaultEdgeFilter(new CarFlagEncoder()), "");
        edge = new TestEdgeIterator("Laufamholzstraße, ST1333");
        assertTrue(edgeFilter.accept(edge));

        edgeFilter = new NameSimilarityEdgeFilter(new DefaultEdgeFilter(new CarFlagEncoder()), "Johannesstraße, 99636, Rastenberg, Deutschland");
        edge = new TestEdgeIterator("Laufamholzstraße, ST1333");
        assertFalse(edgeFilter.accept(edge));

        edge = new TestEdgeIterator("Johannesstraße");
        assertTrue(edgeFilter.accept(edge));

        edgeFilter = new NameSimilarityEdgeFilter(new DefaultEdgeFilter(new CarFlagEncoder()), "Hauptstraße, 39025, Naturns, Italien");
        edge = new TestEdgeIterator("Teststraße");
        assertFalse(edgeFilter.accept(edge));

        edge = new TestEdgeIterator("Hauptstraße");
        assertTrue(edgeFilter.accept(edge));

        // Single Typoe
        edge = new TestEdgeIterator("Hauptstrase");
        assertTrue(edgeFilter.accept(edge));
    }

    static class TestEdgeIterator implements EdgeIteratorState {

        String name;

        public TestEdgeIterator(String name) {
            this.name = name;
        }

        @Override
        public final int getBaseNode() {
            return 0;
        }

        @Override
        public final int getAdjNode() {
            return 0;
        }

        @Override
        public final double getDistance() {
            return 1;
        }

        @Override
        public final EdgeIteratorState setDistance(double dist) {
            return this;
        }

        @Override
        public long getFlags() {
            return 1l;
        }

        @Override
        public final EdgeIteratorState setFlags(long fl) {
            return this;
        }

        @Override
        public final int getAdditionalField() {
            return 0;
        }

        @Override
        public final EdgeIteratorState setAdditionalField(int value) {
            return this;
        }

        @Override
        public final EdgeIteratorState copyPropertiesTo(EdgeIteratorState edge) {
            return this;
        }

        /**
         * Reports whether the edge is available in forward direction for the specified encoder.
         */
        @Override
        public boolean isForward(FlagEncoder encoder) {
            return true;
        }

        /**
         * Reports whether the edge is available in backward direction for the specified encoder.
         */
        @Override
        public boolean isBackward(FlagEncoder encoder) {
            return true;
        }

        @Override
        public EdgeIteratorState setWayGeometry(PointList pillarNodes) {
            return this;
        }

        @Override
        public PointList fetchWayGeometry(int mode) {
            return new PointList();
        }

        @Override
        public int getEdge() {
            return 0;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public EdgeIteratorState setName(String name) {
            this.name = name;
            return this;
        }

        @Override
        public EdgeIteratorState detach(boolean reverse) {
            return null;
        }

        @Override
        public final boolean getBool(int key, boolean _default) {
            return true;
        }

        @Override
        public final String toString() {
            return getEdge() + " " + getBaseNode() + "-" + getAdjNode();
        }
    }
}
