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
import org.junit.Ignore;
import org.junit.Test;

import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Robin Boldt
 */
public class NameSimilarityEdgeFilterTest {

    @Test
    public void testAccept() {
        EdgeFilter edgeFilter = createNameSimilarityEdgeFilter("Laufamholzstraße 154 Nürnberg");
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

        edgeFilter = createNameSimilarityEdgeFilter(null);
        edge = createTestEdgeIterator("Laufamholzstraße, ST1333");
        assertTrue(edgeFilter.accept(edge));

        edgeFilter = createNameSimilarityEdgeFilter("");
        edge = createTestEdgeIterator("Laufamholzstraße, ST1333");
        assertTrue(edgeFilter.accept(edge));

        edgeFilter = createNameSimilarityEdgeFilter("Johannesstraße, 99636, Rastenberg, Deutschland");
        edge = createTestEdgeIterator("Laufamholzstraße, ST1333");
        assertFalse(edgeFilter.accept(edge));

        edge = createTestEdgeIterator("Johannesstraße");
        assertTrue(edgeFilter.accept(edge));

        edgeFilter = createNameSimilarityEdgeFilter("Hauptstraße");
        edge = createTestEdgeIterator("Teststraße");
        assertFalse(edgeFilter.accept(edge));

        edge = createTestEdgeIterator("Hauptstraße");
        assertTrue(edgeFilter.accept(edge));

        edge = createTestEdgeIterator("Hauptstrasse");
        assertTrue(edgeFilter.accept(edge));

        edge = createTestEdgeIterator("Hauptstr.");
        assertTrue(edgeFilter.accept(edge));
    }

    /**
     * With Nominatim you should not use the "placename" for best results, otherwise the length difference becomes too big
     */
    @Test
    public void testAcceptFromNominatim() {
        assertTrue(createNameSimilarityEdgeFilter("Wentworth Street, Caringbah South").
                accept(createTestEdgeIterator("Wentworth Street")));
        assertTrue(createNameSimilarityEdgeFilter("Zum Toffental, Altdorf bei Nürnnberg").
                accept(createTestEdgeIterator("Zum Toffental")));
    }

    @Test
    public void testAcceptFromGoogleMapsGeocoding() {
        EdgeFilter edgeFilter = createNameSimilarityEdgeFilter("Rue Notre-Dame O Montréal");
        assertFalse(edgeFilter.accept(createTestEdgeIterator("Rue Dupré")));
        assertTrue(edgeFilter.accept(createTestEdgeIterator("Rue Notre-Dame Ouest")));

        edgeFilter = createNameSimilarityEdgeFilter("Rue Saint-Antoine O, Montréal");
        assertTrue(edgeFilter.accept(createTestEdgeIterator("Rue Saint-Antoine O")));
        assertFalse(edgeFilter.accept(createTestEdgeIterator("Rue Saint-Jacques")));

        edgeFilter = createNameSimilarityEdgeFilter("Rue de Bleury");
        assertTrue(edgeFilter.accept(createTestEdgeIterator("Rue de Bleury")));
        assertFalse(edgeFilter.accept(createTestEdgeIterator("Rue Balmoral")));

        assertTrue(createNameSimilarityEdgeFilter("Main Rd").accept(createTestEdgeIterator("Main Road")));
        assertTrue(createNameSimilarityEdgeFilter("Main Road").accept(createTestEdgeIterator("Main Rd")));

        assertTrue(createNameSimilarityEdgeFilter("Cape Point Rd").accept(createTestEdgeIterator("Cape Point")));
        assertTrue(createNameSimilarityEdgeFilter("Cape Point Rd").accept(createTestEdgeIterator("Cape Point Road")));

        assertTrue(createNameSimilarityEdgeFilter("Av. Juan Ramón Ramírez").accept(createTestEdgeIterator("Avenida Juan Ramón Ramírez")));
    }

    @Test
    public void testAcceptStForStreet() {
        EdgeIteratorState edge = createTestEdgeIterator("Augustine Street");
        assertTrue(createNameSimilarityEdgeFilter("Augustine St").accept(edge));
        assertTrue(createNameSimilarityEdgeFilter("Augustine Street").accept(edge));

        edge = createTestEdgeIterator("Augustine St");
        assertTrue(createNameSimilarityEdgeFilter("Augustine St").accept(edge));
        assertTrue(createNameSimilarityEdgeFilter("Augustine Street").accept(edge));
    }

    @Test
    public void testWithDash() {
        EdgeIteratorState edge = createTestEdgeIterator("Ben-Gurion-Straße");
        assertTrue(createNameSimilarityEdgeFilter("Ben-Gurion").accept(edge));
        assertTrue(createNameSimilarityEdgeFilter("Ben Gurion").accept(edge));
        assertTrue(createNameSimilarityEdgeFilter("Ben Gurion Strasse").accept(edge));
        assertFalse(createNameSimilarityEdgeFilter("Potsdamer Str.").accept(edge));
    }

    @Ignore
    public void testThatShouldSucceed() {
        // Google Maps vs OSM @ 32.121435,-110.857969
        assertTrue(createNameSimilarityEdgeFilter("S Wilmot Rd").accept(createTestEdgeIterator("South Wilmot Road")));

        // @ 37.307774,13.581259
        assertTrue(createNameSimilarityEdgeFilter("Via Manzoni").accept(createTestEdgeIterator("Via Alessandro Manzoni")));
    }

    /**
     * We ignore Typos for now, most GeoCoders return pretty good results, we might allow some typos
     */
    @Test
    public void testAcceptWithTypos() {
        EdgeFilter edgeFilter = createNameSimilarityEdgeFilter("Laufamholzstraße 154 Nürnberg");
        EdgeIteratorState edge = createTestEdgeIterator("Laufamholzstraße, ST1333");
        assertTrue(edgeFilter.accept(edge));

        // Single Typo
        edgeFilter = createNameSimilarityEdgeFilter("Kaufamholzstraße");
        assertTrue(edgeFilter.accept(edge));

        // Two Typos
        edgeFilter = createNameSimilarityEdgeFilter("Kaufamholystraße");
        assertTrue(edgeFilter.accept(edge));

        // Three Typos
        edgeFilter = createNameSimilarityEdgeFilter("Kaufmholystraße");
        assertFalse(edgeFilter.accept(edge));

        edgeFilter = createNameSimilarityEdgeFilter("Hauptstraße");
        edge = createTestEdgeIterator("Hauptstraße");
        assertTrue(edgeFilter.accept(edge));

        // Single Typo
        edgeFilter = createNameSimilarityEdgeFilter("Hauptstrase");
        assertTrue(edgeFilter.accept(edge));

        // Two Typos
        edgeFilter = createNameSimilarityEdgeFilter("Lauptstrase");
//        assertTrue(edgeFilter.accept(edge));
    }

    private NameSimilarityEdgeFilter createNameSimilarityEdgeFilter(String s) {
        return new NameSimilarityEdgeFilter(new EdgeFilter() {
            @Override
            public boolean accept(EdgeIteratorState edgeState) {
                return true;
            }
        }, s);
    }

    private EdgeIteratorState createTestEdgeIterator(final String name) {
        return new GHUtility.DisabledEdgeIterator() {

            @Override
            public String getName() {
                return name;
            }
        };
    }
}
