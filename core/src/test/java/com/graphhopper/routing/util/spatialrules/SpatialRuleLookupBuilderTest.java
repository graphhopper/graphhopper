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
import com.graphhopper.json.geo.JsonFeatureCollection;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.profiles.*;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.parsers.SpatialRuleParser;
import com.graphhopper.routing.util.spatialrules.SpatialRuleLookupBuilder.SpatialRuleFactory;
import com.graphhopper.routing.util.spatialrules.countries.GermanySpatialRule;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.EdgeIteratorState;
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
import java.util.concurrent.TimeUnit;

import static com.graphhopper.util.GHUtility.updateDistancesFor;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;

/**
 * @author Robin Boldt
 */
public class SpatialRuleLookupBuilderTest {

    private static final String COUNTRIES_FILE = "../core/files/spatialrules/countries.geo.json";

    @Test
    public void testIndex() throws IOException {
        final FileReader reader = new FileReader(COUNTRIES_FILE);
        SpatialRuleLookup spatialRuleLookup = SpatialRuleLookupBuilder.buildIndex(Collections.singletonList(
                Jackson.newObjectMapper().readValue(reader, JsonFeatureCollection.class)), "ISO_A3", new CountriesSpatialRuleFactory());

        // Berlin
        assertEquals(RoadAccess.DESTINATION, spatialRuleLookup.lookupRule(52.5243700, 13.4105300).
                getAccess("track", TransportationMode.MOTOR_VEHICLE, RoadAccess.YES));
        assertEquals(RoadAccess.YES, spatialRuleLookup.lookupRule(52.5243700, 13.4105300).
                getAccess("primary", TransportationMode.MOTOR_VEHICLE, RoadAccess.YES));

        // Paris -> empty rule
        assertEquals(RoadAccess.YES, spatialRuleLookup.lookupRule(48.864716, 2.349014).
                getAccess("track", TransportationMode.MOTOR_VEHICLE, RoadAccess.YES));
        assertEquals(RoadAccess.YES, spatialRuleLookup.lookupRule(48.864716, 2.349014).
                getAccess("primary", TransportationMode.MOTOR_VEHICLE, RoadAccess.YES));

        // Austria
        assertEquals(RoadAccess.FORESTRY, spatialRuleLookup.lookupRule(48.204484, 16.107888).
                getAccess("track", TransportationMode.MOTOR_VEHICLE, RoadAccess.YES));
        assertEquals(RoadAccess.YES, spatialRuleLookup.lookupRule(48.210033, 16.363449).
                getAccess("primary", TransportationMode.MOTOR_VEHICLE, RoadAccess.YES));
        assertEquals(RoadAccess.DESTINATION, spatialRuleLookup.lookupRule(48.210033, 16.363449).
                getAccess("living_street", TransportationMode.MOTOR_VEHICLE, RoadAccess.YES));
    }

    @Test
    public void testBounds() throws IOException {
        final FileReader reader = new FileReader(COUNTRIES_FILE);
        SpatialRuleLookup spatialRuleLookup = SpatialRuleLookupBuilder.buildIndex(Collections.singletonList(
                Jackson.newObjectMapper().readValue(reader, JsonFeatureCollection.class)), "ISO_A3", new CountriesSpatialRuleFactory());
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
                Jackson.newObjectMapper().readValue(reader, JsonFeatureCollection.class)), "ISO_A3",
                new CountriesSpatialRuleFactory(), new Envelope(9, 10, 51, 52));
        assertFalse("BBox seems to be incorrectly contracted", spatialRuleLookup.getBounds().contains(49.9, 8.9));
    }

    @Test
    public void testNoIntersection() throws IOException {
        final FileReader reader = new FileReader(COUNTRIES_FILE);
        SpatialRuleLookup spatialRuleLookup = SpatialRuleLookupBuilder.buildIndex(Collections.singletonList(
                Jackson.newObjectMapper().readValue(reader, JsonFeatureCollection.class)), "ISO_A3",
                new CountriesSpatialRuleFactory(), new Envelope(-180, -179, -90, -89));
        assertEquals(SpatialRuleLookup.EMPTY, spatialRuleLookup);
    }


    @Test
    public void testSpatialId() {
        final GeometryFactory fac = new GeometryFactory();
        org.locationtech.jts.geom.Polygon polygon = fac.createPolygon(new Coordinate[]{
                new Coordinate(0, 0), new Coordinate(0, 1), new Coordinate(1, 1), new Coordinate(1, 0), new Coordinate(0, 0)});
        final GermanySpatialRule germany = new GermanySpatialRule();
        germany.setBorders(Collections.singletonList(polygon));

        SpatialRuleLookup index = new SpatialRuleLookup() {
            @Override
            public SpatialRule lookupRule(double lat, double lon) {
                for (Polygon polygon : germany.getBorders()) {
                    if (polygon.covers(fac.createPoint(new Coordinate(lon, lat)))) {
                        return germany;
                    }
                }
                return SpatialRule.EMPTY;
            }

            @Override
            public SpatialRule lookupRule(GHPoint point) {
                return lookupRule(point.lat, point.lon);
            }

            @Override
            public int getSpatialId(SpatialRule rule) {
                if (germany.equals(rule)) {
                    return 1;
                } else {
                    return 0;
                }
            }

            @Override
            public SpatialRule getSpatialRule(int spatialId) {
                return SpatialRule.EMPTY;
            }

            @Override
            public int size() {
                return 2;
            }

            @Override
            public Envelope getBounds() {
                return new Envelope(-180, 180, -90, 90);
            }
        };

        EncodingManager em = new EncodingManager.Builder().add(new SpatialRuleParser(index)).add(new CarFlagEncoder(new PMap())).build();
        IntEncodedValue countrySpatialIdEnc = em.getIntEncodedValue(Country.KEY);
        EnumEncodedValue<RoadAccess> tmpRoadAccessEnc = em.getEnumEncodedValue(RoadAccess.KEY, RoadAccess.class);
        DecimalEncodedValue tmpCarMaxSpeedEnc = em.getDecimalEncodedValue(MaxSpeed.KEY);

        Graph graph = new GraphBuilder(em).create();
        EdgeIteratorState e1 = graph.edge(0, 1, 1, true);
        EdgeIteratorState e2 = graph.edge(0, 2, 1, true);
        EdgeIteratorState e3 = graph.edge(0, 3, 1, true);
        EdgeIteratorState e4 = graph.edge(0, 4, 1, true);
        updateDistancesFor(graph, 0, 0.00, 0.00);
        updateDistancesFor(graph, 1, 0.01, 0.01);
        updateDistancesFor(graph, 2, -0.01, -0.01);
        updateDistancesFor(graph, 3, 0.01, 0.01);
        updateDistancesFor(graph, 4, -0.01, -0.01);

        IntsRef relFlags = em.createRelationFlags();
        EncodingManager.AcceptWay map = new EncodingManager.AcceptWay().put("car", EncodingManager.Access.WAY);
        ReaderWay way = new ReaderWay(27l);
        way.setTag("highway", "track");
        way.setTag("estimated_center", new GHPoint(0.005, 0.005));
        e1.setFlags(em.handleWayTags(way, map, relFlags));
        assertEquals(RoadAccess.DESTINATION, e1.get(tmpRoadAccessEnc));

        ReaderWay way2 = new ReaderWay(28l);
        way2.setTag("highway", "track");
        way2.setTag("estimated_center", new GHPoint(-0.005, -0.005));
        e2.setFlags(em.handleWayTags(way2, map, relFlags));
        assertEquals(RoadAccess.YES, e2.get(tmpRoadAccessEnc));

        assertEquals(index.getSpatialId(new GermanySpatialRule()), e1.get(countrySpatialIdEnc));
        assertEquals(index.getSpatialId(SpatialRule.EMPTY), e2.get(countrySpatialIdEnc));

        ReaderWay livingStreet = new ReaderWay(29l);
        livingStreet.setTag("highway", "living_street");
        livingStreet.setTag("estimated_center", new GHPoint(0.005, 0.005));
        e3.setFlags(em.handleWayTags(livingStreet, map, relFlags));
        assertEquals(5, e3.get(tmpCarMaxSpeedEnc), .1);

        ReaderWay livingStreet2 = new ReaderWay(30l);
        livingStreet2.setTag("highway", "living_street");
        livingStreet2.setTag("estimated_center", new GHPoint(-0.005, -0.005));
        e4.setFlags(em.handleWayTags(livingStreet2, map, relFlags));
        assertEquals(MaxSpeed.UNSET_SPEED, e4.get(tmpCarMaxSpeedEnc), .1);
    }

    @Test
    public void testSpeed() throws IOException {
        final FileReader reader = new FileReader(COUNTRIES_FILE);
        SpatialRuleFactory rulePerCountryFactory = new SpatialRuleFactory() {

            @Override
            public SpatialRule createSpatialRule(final String id, final List<Polygon> borders) {
                return new SpatialRule() {

                    @Override
                    public double getMaxSpeed(String highway, double _default) {
                        return 100;
                    }

                    @Override
                    public String getId() {
                        return id;
                    }

                    @Override
                    public List<Polygon> getBorders() {
                        return borders;
                    }

                    @Override
                    public RoadAccess getAccess(String highwayTag, TransportationMode transportationMode, RoadAccess _default) {
                        return RoadAccess.YES;
                    }

                    @Override
                    public String toString() {
                        return getId();
                    }
                };
            }
        };
        SpatialRuleLookup spatialRuleLookup = SpatialRuleLookupBuilder.buildIndex(Collections.singletonList(
                Jackson.newObjectMapper().readValue(reader, JsonFeatureCollection.class)), "ISO_A3", rulePerCountryFactory);

        // generate random points in central Europe
        int randomPoints = 250_000;
        long start = System.nanoTime();
        for (int i = 0; i < randomPoints; i++) {
            double lat = 46d + Math.random() * 7d;
            double lon = 6d + Math.random() * 21d;
            spatialRuleLookup.lookupRule(new GHPoint(lat, lon));
        }

        long duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        // System.out.println("Lookup of " + randomPoints + " points took " + duration + "ms");
        // System.out.println("Average lookup duration: " + ((double) duration) / randomPoints + "ms");
        long maxBenchmarkRuntimeMs = 5_000L;
        assertTrue("Benchmark must be finished in less than " + maxBenchmarkRuntimeMs + "ms",
                duration < maxBenchmarkRuntimeMs);
    }
}
