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
package com.graphhopper.routing.util.spatialrules;

import com.graphhopper.jackson.Jackson;
import com.graphhopper.util.JsonFeatureCollection;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.TransportationMode;
import com.graphhopper.routing.util.parsers.SpatialRuleParser;
import com.graphhopper.routing.util.spatialrules.countries.GermanySpatialRule;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.PMap;
import com.graphhopper.util.shapes.GHPoint;
import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;

import java.io.FileReader;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static com.graphhopper.util.GHUtility.updateDistancesFor;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;

/**
 * @author Robin Boldt
 */
public class SpatialRuleLookupBuilderTest {

    private static final String COUNTRIES_FILE = "../core/files/spatialrules/countries.geojson";

    private static SpatialRuleLookup createLookup() throws IOException {
        return createLookup(new Envelope(-180, 180, -90, 90));
    }

    private static SpatialRuleLookup createLookup(Envelope maxBounds) throws IOException {
        final FileReader reader = new FileReader(COUNTRIES_FILE);
        List<JsonFeatureCollection> feats = Collections.singletonList(
                Jackson.newObjectMapper().readValue(reader, JsonFeatureCollection.class));
        return SpatialRuleLookupBuilder.buildIndex(feats, "ISO3166-1:alpha3", new CountriesSpatialRuleFactory());
    }

    @Test
    public void testIndex() throws IOException {
        SpatialRuleLookup spatialRuleLookup = createLookup();

        // Berlin
        assertEquals(RoadAccess.DESTINATION, spatialRuleLookup.lookupRules(52.5243700, 13.4105300).
                getAccess(RoadClass.TRACK, TransportationMode.CAR, RoadAccess.YES));
        assertEquals(RoadAccess.YES, spatialRuleLookup.lookupRules(52.5243700, 13.4105300).
                getAccess(RoadClass.PRIMARY, TransportationMode.CAR, RoadAccess.YES));

        // Paris -> empty rule
        assertEquals(RoadAccess.YES, spatialRuleLookup.lookupRules(48.864716, 2.349014).
                getAccess(RoadClass.TRACK, TransportationMode.CAR, RoadAccess.YES));
        assertEquals(RoadAccess.YES, spatialRuleLookup.lookupRules(48.864716, 2.349014).
                getAccess(RoadClass.PRIMARY, TransportationMode.CAR, RoadAccess.YES));

        // Austria
        assertEquals(RoadAccess.FORESTRY, spatialRuleLookup.lookupRules(48.204484, 16.107888).
                getAccess(RoadClass.TRACK, TransportationMode.CAR, RoadAccess.YES));
        assertEquals(RoadAccess.YES, spatialRuleLookup.lookupRules(48.210033, 16.363449).
                getAccess(RoadClass.PRIMARY, TransportationMode.CAR, RoadAccess.YES));
        assertEquals(RoadAccess.DESTINATION, spatialRuleLookup.lookupRules(48.210033, 16.363449).
                getAccess(RoadClass.LIVING_STREET, TransportationMode.CAR, RoadAccess.YES));
    }

    @Test
    public void testBounds() throws IOException {
        final FileReader reader = new FileReader(COUNTRIES_FILE);
        SpatialRuleLookup spatialRuleLookup = SpatialRuleLookupBuilder.buildIndex(Collections.singletonList(
                Jackson.newObjectMapper().readValue(reader, JsonFeatureCollection.class)), "ISO3166-1:alpha3", new CountriesSpatialRuleFactory());
        Envelope almostWorldWide = new Envelope(-179, 179, -89, 89);

        // Might fail if a polygon is defined outside the above coordinates
        assertTrue("BBox seems to be not contracted", almostWorldWide.contains(spatialRuleLookup.getBounds()));
    }

    @Test
    public void testIntersection() throws IOException {
        /*
         We are creating a BBox smaller than Germany. We have the German Spatial rule activated by default.
         So the BBox should not contain a Point lying somewhere close in Germany.
        */
        final FileReader reader = new FileReader(COUNTRIES_FILE);
        SpatialRuleLookup spatialRuleLookup = SpatialRuleLookupBuilder.buildIndex(Collections.singletonList(
                Jackson.newObjectMapper().readValue(reader, JsonFeatureCollection.class)), "ISO3166-1:alpha3",
                new CountriesSpatialRuleFactory(), new Envelope(9, 10, 51, 52));
        assertFalse("BBox seems to be incorrectly contracted", spatialRuleLookup.getBounds().contains(49.9, 8.9));
    }

    @Test
    public void testNoIntersection() throws IOException {
        final FileReader reader = new FileReader(COUNTRIES_FILE);
        SpatialRuleLookup spatialRuleLookup = SpatialRuleLookupBuilder.buildIndex(Collections.singletonList(
                Jackson.newObjectMapper().readValue(reader, JsonFeatureCollection.class)), "ISO3166-1:alpha3",
                new CountriesSpatialRuleFactory(), new Envelope(-180, -179, -90, -89));
        assertEquals(SpatialRuleLookup.EMPTY, spatialRuleLookup);
    }


    @Test
    public void testSpatialId() {
        final GeometryFactory fac = new GeometryFactory();
        org.locationtech.jts.geom.Polygon polygon = fac.createPolygon(new Coordinate[]{
                new Coordinate(0, 0), new Coordinate(0, 1), new Coordinate(1, 1), new Coordinate(1, 0), new Coordinate(0, 0)});
        final SpatialRule germany = new GermanySpatialRule(Collections.singletonList(polygon));

        SpatialRuleLookup index = new SpatialRuleLookup() {
            @Override
            public SpatialRuleSet lookupRules(double lat, double lon) {
                for (Polygon polygon : germany.getBorders()) {
                    if (polygon.covers(fac.createPoint(new Coordinate(lon, lat)))) {
                        return new SpatialRuleSet(Collections.singletonList(germany), 1);
                    }
                }
                return SpatialRuleSet.EMPTY;
            }

            @Override
            public List<SpatialRule> getRules() {
                return Collections.singletonList(germany);
            }

            @Override
            public Envelope getBounds() {
                return new Envelope(-180, 180, -90, 90);
            }
        };

        EncodingManager em = new EncodingManager.Builder().add(new SpatialRuleParser(index, Country.create())).add(new CarFlagEncoder(new PMap())).build();
        IntEncodedValue countrySpatialIdEnc = em.getIntEncodedValue(Country.KEY);
        EnumEncodedValue<RoadAccess> tmpRoadAccessEnc = em.getEnumEncodedValue(RoadAccess.KEY, RoadAccess.class);
        DecimalEncodedValue tmpCarMaxSpeedEnc = em.getDecimalEncodedValue(MaxSpeed.KEY);

        Graph graph = new GraphBuilder(em).create();
        FlagEncoder encoder = em.getEncoder("car");
        EdgeIteratorState e1 = GHUtility.setSpeed(60, true, true, encoder, graph.edge(0, 1).setDistance(1));
        EdgeIteratorState e2 = GHUtility.setSpeed(60, true, true, encoder, graph.edge(0, 2).setDistance(1));
        EdgeIteratorState e3 = GHUtility.setSpeed(60, true, true, encoder, graph.edge(0, 3).setDistance(1));
        EdgeIteratorState e4 = GHUtility.setSpeed(60, true, true, encoder, graph.edge(0, 4).setDistance(1));
        updateDistancesFor(graph, 0, 0.00, 0.00);
        updateDistancesFor(graph, 1, 0.01, 0.01);
        updateDistancesFor(graph, 2, -0.01, -0.01);
        updateDistancesFor(graph, 3, 0.01, 0.01);
        updateDistancesFor(graph, 4, -0.01, -0.01);

        IntsRef relFlags = em.createRelationFlags();
        EncodingManager.AcceptWay map = new EncodingManager.AcceptWay().put("car", EncodingManager.Access.WAY);
        ReaderWay way = new ReaderWay(27L);
        way.setTag("highway", "track");
        way.setTag("estimated_center", new GHPoint(0.005, 0.005));
        e1.setFlags(em.handleWayTags(way, map, relFlags));
        assertEquals(RoadAccess.DESTINATION, e1.get(tmpRoadAccessEnc));

        ReaderWay way2 = new ReaderWay(28L);
        way2.setTag("highway", "track");
        way2.setTag("estimated_center", new GHPoint(-0.005, -0.005));
        e2.setFlags(em.handleWayTags(way2, map, relFlags));
        assertEquals(RoadAccess.YES, e2.get(tmpRoadAccessEnc));

        assertEquals(index.getRules().indexOf(germany), e1.get(countrySpatialIdEnc) - 1);
        assertEquals(0, e2.get(countrySpatialIdEnc));

        ReaderWay livingStreet = new ReaderWay(29L);
        livingStreet.setTag("highway", "living_street");
        livingStreet.setTag("estimated_center", new GHPoint(0.005, 0.005));
        e3.setFlags(em.handleWayTags(livingStreet, map, relFlags));
        assertEquals(5, e3.get(tmpCarMaxSpeedEnc), .1);

        ReaderWay livingStreet2 = new ReaderWay(30L);
        livingStreet2.setTag("highway", "living_street");
        livingStreet2.setTag("estimated_center", new GHPoint(-0.005, -0.005));
        e4.setFlags(em.handleWayTags(livingStreet2, map, relFlags));
        assertEquals(MaxSpeed.UNSET_SPEED, e4.get(tmpCarMaxSpeedEnc), .1);
    }
}
