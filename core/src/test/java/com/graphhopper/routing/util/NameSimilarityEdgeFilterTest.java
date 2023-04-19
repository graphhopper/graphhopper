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

import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValueImpl;
import com.graphhopper.routing.ev.SimpleBooleanEncodedValue;
import com.graphhopper.search.KVStorage;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.GHPoint;
import org.junit.jupiter.api.Test;

import static com.graphhopper.search.KVStorage.KeyValue.STREET_NAME;
import static com.graphhopper.search.KVStorage.KeyValue.createKV;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Robin Boldt
 */
public class NameSimilarityEdgeFilterTest {

    private final GHPoint basePoint = new GHPoint(49.4652132, 11.1435159);

    @Test
    public void testAccept() {
        EdgeFilter edgeFilter = createNameSimilarityEdgeFilter("Laufamholzstraße 154 Nürnberg");
        EdgeIteratorState edge = createTestEdgeIterator("Laufamholzstraße, ST1333");
        edge.getName();
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

        edgeFilter = createNameSimilarityEdgeFilter("Johannesstraße, Rastenberg, Deutschland");
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

    @Test
    public void testDistanceFiltering() {
        BaseGraph g = new BaseGraph.Builder(1).create();
        NodeAccess na = g.getNodeAccess();

        GHPoint pointFarAway = new GHPoint(49.458629, 11.146124);
        GHPoint point25mAway = new GHPoint(49.464871, 11.143575);
        GHPoint point200mAway = new GHPoint(49.464598, 11.149039);

        int farAwayId = 0;
        int nodeId50 = 1;
        int nodeID200 = 2;

        na.setNode(farAwayId, pointFarAway.lat, pointFarAway.lon);
        na.setNode(nodeId50, point25mAway.lat, point25mAway.lon);
        na.setNode(nodeID200, point200mAway.lat, point200mAway.lon);

        // Check that it matches a street 50m away
        EdgeIteratorState edge1 = g.edge(nodeId50, farAwayId).setKeyValues(createKV(STREET_NAME, "Wentworth Street"));
        assertTrue(createNameSimilarityEdgeFilter("Wentworth Street").accept(edge1));

        // Check that it doesn't match streets 200m away
        EdgeIteratorState edge2 = g.edge(nodeID200, farAwayId).setKeyValues(createKV(STREET_NAME, "Wentworth Street"));
        assertFalse(createNameSimilarityEdgeFilter("Wentworth Street").accept(edge2));
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
        assertTrue(createNameSimilarityEdgeFilter("Main Rd").accept(createTestEdgeIterator("Main Road, New York")));

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

    @Test
    public void normalization() {
        assertEquals("northderby", createNameSimilarityEdgeFilter("North Derby Lane").getNormalizedPointHint());

        // do not remove the number as it is a significant part of the name, especially in the US
        assertEquals("28north", createNameSimilarityEdgeFilter("I-28 N").getNormalizedPointHint());
        assertEquals("28north", createNameSimilarityEdgeFilter(" I-28    N  ").getNormalizedPointHint());
        assertEquals("south23rd", createNameSimilarityEdgeFilter("S 23rd St").getNormalizedPointHint());
        assertEquals("66", createNameSimilarityEdgeFilter("Route 66").getNormalizedPointHint());
        assertEquals("fayettecounty1", createNameSimilarityEdgeFilter("Fayette County Rd 1").getNormalizedPointHint());

        // too short, except when numbers
        assertEquals("112", createNameSimilarityEdgeFilter("A B C 1 12").getNormalizedPointHint());
    }

    @Test
    public void testServiceMix() {
        assertTrue(createNameSimilarityEdgeFilter("North Derby Lane").accept(createTestEdgeIterator("N Derby Ln")));
        assertTrue(createNameSimilarityEdgeFilter("N Derby Ln").accept(createTestEdgeIterator("North Derby Lane")));

        assertFalse(createNameSimilarityEdgeFilter("North Derby Lane").accept(createTestEdgeIterator("I-29 N")));
        assertFalse(createNameSimilarityEdgeFilter("I-29 N").accept(createTestEdgeIterator("North Derby Lane")));

        assertTrue(createNameSimilarityEdgeFilter("George Street").accept(createTestEdgeIterator("George St")));
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

    /**
     * Create a NameSimilarityEdgeFilter that uses the same coordinates for all nodes
     * so distance is not used when matching
     */
    private NameSimilarityEdgeFilter createNameSimilarityEdgeFilter(String pointHint) {
        return new NameSimilarityEdgeFilter(edgeState -> true, pointHint, basePoint, 100);
    }

    @Test
    public void curvedWayGeometry_issue2319() {
        // 0 - 1
        // |   |
        // |   |
        // -----
        //
        //    2 -- 3
        SimpleBooleanEncodedValue accessEnc = new SimpleBooleanEncodedValue("access", true);
        DecimalEncodedValue speedEnc = new DecimalEncodedValueImpl("speed", 5, 5, true);
        EncodingManager em = EncodingManager.start().add(accessEnc).add(speedEnc).build();
        BaseGraph graph = new BaseGraph.Builder(em).create();
        PointList pointList = new PointList(20, false);
        pointList.add(43.844377, -79.264005);
        pointList.add(43.843771, -79.263824);
        pointList.add(43.843743, -79.2638);
        pointList.add(43.843725, -79.26375);
        pointList.add(43.843724, -79.263676);
        pointList.add(43.843801, -79.263412);
        pointList.add(43.843866, -79.263);
        pointList.add(43.843873, -79.262838);
        pointList.add(43.843863, -79.262801);
        pointList.add(43.843781, -79.262729);
        pointList.add(43.842408, -79.262395);
        pointList.add(43.842363, -79.262397);
        pointList.add(43.842336, -79.262422);
        pointList.add(43.842168, -79.263186);
        pointList.add(43.842152, -79.263348);
        pointList.add(43.842225, -79.263421);
        pointList.add(43.842379, -79.263441);
        pointList.add(43.842668, -79.26352);
        pointList.add(43.842777, -79.263566);
        pointList.add(43.842832, -79.263627);
        pointList.add(43.842833, -79.263739);
        pointList.add(43.842807, -79.263802);
        pointList.add(43.842691, -79.264477);
        pointList.add(43.842711, -79.264588);
        graph.getNodeAccess().setNode(0, 43.844521, -79.263976);
        graph.getNodeAccess().setNode(1, 43.842775, -79.264649);

        EdgeIteratorState doubtfire = graph.edge(0, 1).setWayGeometry(pointList).set(accessEnc, true, true).
                set(speedEnc, 60, 60).setKeyValues(createKV(STREET_NAME, "Doubtfire Crescent"));
        EdgeIteratorState golden = graph.edge(0, 1).set(accessEnc, true, true).set(speedEnc, 60, 60).
                setKeyValues(createKV(STREET_NAME, "Golden Avenue"));

        graph.getNodeAccess().setNode(2, 43.841501560244744, -79.26366394602502);
        graph.getNodeAccess().setNode(3, 43.842247922172724, -79.2605663670726);
        PointList pointList2 = new PointList(1, false);
        pointList2.add(43.84191413615452, -79.261912128223);
        EdgeIteratorState denison = graph.edge(2, 3).setWayGeometry(pointList2).set(accessEnc, true, true).
                set(speedEnc, 60, 60).setKeyValues(createKV(STREET_NAME, "Denison Street"));
        double qlat = 43.842122;
        double qLon = -79.262162;

        // if we use a very large radius we find doubtfire
        NameSimilarityEdgeFilter filter = new NameSimilarityEdgeFilter(EdgeFilter.ALL_EDGES, "doubtfire", new GHPoint(qlat, qLon), 1_000);
        assertFalse(filter.accept(golden));
        assertFalse(filter.accept(denison));
        assertTrue(filter.accept(doubtfire));

        // but also using a smaller radius should work, because the inner way geomerty of Doubtfire Crescent comes very
        // close to the marker even though the tower nodes are rather far away
        filter = new NameSimilarityEdgeFilter(EdgeFilter.ALL_EDGES, "doubtfire", new GHPoint(qlat, qLon), 100);
        assertFalse(filter.accept(golden));
        assertFalse(filter.accept(denison));
        assertTrue(filter.accept(doubtfire));
    }

    private EdgeIteratorState createTestEdgeIterator(String name) {
        PointList pointList = new PointList();
        pointList.add(basePoint);
        EdgeIteratorState edge = new BaseGraph.Builder(1).create().edge(0, 0)
                .setWayGeometry(pointList);
        if (name != null)
            edge.setKeyValues(KVStorage.KeyValue.createKV(KVStorage.KeyValue.STREET_NAME, name));
        return edge;
    }

}
