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
        EdgeFilter edgeFilter;
        EdgeIteratorState edge;

        edgeFilter = new NameSimilarityEdgeFilter(new DefaultEdgeFilter(new CarFlagEncoder()), "Wentworth Street, Caringbah South");
        edge = createTestEdgeIterator("Wentworth Street");
        assertTrue(edgeFilter.accept(edge));

        edgeFilter = new NameSimilarityEdgeFilter(new DefaultEdgeFilter(new CarFlagEncoder()), "Zum Toffental, Altdorf bei Nürnnberg");
        edge = createTestEdgeIterator("Zum Toffental");
        assertTrue(edgeFilter.accept(edge));
    }

    @Test
    public void testAcceptFromGoogleMapsGeocoding() {
        EdgeFilter edgeFilter;
        EdgeIteratorState edge;

        edgeFilter = new NameSimilarityEdgeFilter(new DefaultEdgeFilter(new CarFlagEncoder()), "Rue Notre-Dame O Montréal");
        edge = createTestEdgeIterator("Rue Dupré");
        assertFalse(edgeFilter.accept(edge));

        edge = createTestEdgeIterator("Rue Notre-Dame Ouest");
        assertTrue(edgeFilter.accept(edge));

        edgeFilter = new NameSimilarityEdgeFilter(new DefaultEdgeFilter(new CarFlagEncoder()), "227 Rue Saint-Antoine O, Montréal");
        edge = createTestEdgeIterator("Rue Saint-Antoine O");
        assertTrue(edgeFilter.accept(edge));

        edge = createTestEdgeIterator("Rue Saint-Jacques");
        assertFalse(edgeFilter.accept(edge));

        edgeFilter = new NameSimilarityEdgeFilter(new DefaultEdgeFilter(new CarFlagEncoder()), "1025 Rue de Bleury, Montréal, QC H2Z 1M7");
        edge = createTestEdgeIterator("Rue de Bleury");
        assertTrue(edgeFilter.accept(edge));

        edge = createTestEdgeIterator("Rue Balmoral");
        assertFalse(edgeFilter.accept(edge));

        // Modified Test from Below
        edgeFilter = new NameSimilarityEdgeFilter(new DefaultEdgeFilter(new CarFlagEncoder()), "257 Main Road, Claremont, Cape Town, 7708, Afrique du Sud");
        edge = createTestEdgeIterator("Main Road");
        assertTrue(edgeFilter.accept(edge));

        edgeFilter = new NameSimilarityEdgeFilter(new DefaultEdgeFilter(new CarFlagEncoder()), "Cape Point Rd, Cape Peninsula, Cape Town, 8001, Afrique du Sud");
        edge = createTestEdgeIterator("Cape Point / Cape of Good Hope");
        assertTrue(edgeFilter.accept(edge));

        edgeFilter = new NameSimilarityEdgeFilter(new DefaultEdgeFilter(new CarFlagEncoder()), "Viale Puglie, 26, 20137 Milano, Italy");
        edge = createTestEdgeIterator("Viale Puglie");
        assertTrue(edgeFilter.accept(edge));
    }

    @Test
    public void testAcceptMashup() {
        EdgeFilter edgeFilter;
        EdgeIteratorState edge;

        edge = createTestEdgeIterator("Augustine Street");

        // Google Maps
        edgeFilter = new NameSimilarityEdgeFilter(new DefaultEdgeFilter(new CarFlagEncoder()), "Augustine St, Hunters Hill NSW 2110, Australia");
        assertTrue(edgeFilter.accept(edge));

        // Opencagedata
        edgeFilter = new NameSimilarityEdgeFilter(new DefaultEdgeFilter(new CarFlagEncoder()), "Augustine Street, Sydney Neusüdwales 2110, Australien");
        assertTrue(edgeFilter.accept(edge));

        // Nominatim
        edgeFilter = new NameSimilarityEdgeFilter(new DefaultEdgeFilter(new CarFlagEncoder()), "Augustine Street, Sydney, Municipality of Hunters Hill, Neusüdwales, 2111, Australien");
        assertTrue(edgeFilter.accept(edge));

    }

    @Ignore
    public void testThatShouldSucceed(){
        EdgeFilter edgeFilter;
        EdgeIteratorState edge;

        // The Problem is that Rd vs Road is abreviated, if we have Road, it works
        edgeFilter = new NameSimilarityEdgeFilter(new DefaultEdgeFilter(new CarFlagEncoder()), "257 Main Rd, Claremont, Cape Town, 7708, Afrique du Sud");
        edge = createTestEdgeIterator("Main Road");
        assertTrue(edgeFilter.accept(edge));

        // Just too much difference Between Google Maps and OSM @ 32.121435,-110.857969
        edgeFilter = new NameSimilarityEdgeFilter(new DefaultEdgeFilter(new CarFlagEncoder()), "7202 S Wilmot Rd, Tucson, AZ 85701");
        edge = createTestEdgeIterator("South Wilmot Road");
        assertTrue(edgeFilter.accept(edge));

        // @ 37.307774,13.581259
        edgeFilter = new NameSimilarityEdgeFilter(new DefaultEdgeFilter(new CarFlagEncoder()), "Via Manzoni, 50/52, 92100 Agrigento AG, Italy");
        edge = createTestEdgeIterator("Via Alessandro Manzoni");
        assertTrue(edgeFilter.accept(edge));

        edgeFilter = new NameSimilarityEdgeFilter(new DefaultEdgeFilter(new CarFlagEncoder()), "Av. Juan Ramón Ramírez, 12, 02630 La Roda, Albacete, Spain");
        edge = createTestEdgeIterator("Avenida Juan Ramón Ramírez");
        assertTrue(edgeFilter.accept(edge));


    }


    /**
     * We ignore Typos for now, most GeoCoders return pretty good results, we might allow some typos
     */
    @Ignore
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
        edgeFilter = new NameSimilarityEdgeFilter(new DefaultEdgeFilter(new CarFlagEncoder()), "Lauptstrase");
        assertTrue(edgeFilter.accept(edge));

        // We ignore too short Strings for now
        /*
        // Distance - PerfectDistance = 1
        edgeFilter = new NameSimilarityEdgeFilter(new DefaultEdgeFilter(new CarFlagEncoder()), "z");
        assertFalse(edgeFilter.accept(edge));
        // Distance - PerfectDistance = 1
        edgeFilter = new NameSimilarityEdgeFilter(new DefaultEdgeFilter(new CarFlagEncoder()), "az");
        assertFalse(edgeFilter.accept(edge));

        // Distance - PerfectDistance = 2
        edgeFilter = new NameSimilarityEdgeFilter(new DefaultEdgeFilter(new CarFlagEncoder()), "xy");
        assertFalse(edgeFilter.accept(edge));
        */
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
