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
package com.graphhopper.routing.weighting;

import com.graphhopper.json.geo.JsonFeature;
import com.graphhopper.routing.profiles.*;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.CustomModel;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.weighting.custom.CustomWeighting;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.shapes.BBox;
import org.junit.Before;
import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.graphhopper.routing.profiles.RoadClass.*;
import static com.graphhopper.routing.weighting.TurnCostProvider.NO_TURN_COST_PROVIDER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CustomWeightingTest {

    GraphHopperStorage graphHopperStorage;
    DecimalEncodedValue avSpeedEnc;
    BooleanEncodedValue accessEnc;
    EnumEncodedValue<RoadClass> roadClassEnc;
    EncodingManager encodingManager;
    FlagEncoder carFE;

    @Before
    public void setUp() {
        carFE = new CarFlagEncoder();
        encodingManager = new EncodingManager.Builder().add(carFE).build();
        avSpeedEnc = carFE.getAverageSpeedEnc();
        accessEnc = carFE.getAccessEnc();
        roadClassEnc = encodingManager.getEnumEncodedValue(KEY, RoadClass.class);
        graphHopperStorage = new GraphBuilder(encodingManager).create();
    }

    @Test
    public void testBasic() {
        EdgeIteratorState edge1 = graphHopperStorage.edge(0, 1).setDistance(10).
                set(roadClassEnc, PRIMARY).set(avSpeedEnc, 80).set(accessEnc, true).setReverse(accessEnc, true);
        EdgeIteratorState edge2 = graphHopperStorage.edge(1, 2).setDistance(10).
                set(roadClassEnc, SECONDARY).set(avSpeedEnc, 70).set(accessEnc, true).setReverse(accessEnc, true);

        CustomModel vehicleModel = new CustomModel();
        vehicleModel.setBase("car");
        Map map = new HashMap();
        map.put(PRIMARY.toString(), 2.0);
        vehicleModel.getPriority().put(KEY, map);

        Weighting weighting = new CustomWeighting("custom", carFE, encodingManager, new DefaultEncodedValueFactory(), NO_TURN_COST_PROVIDER, vehicleModel);
        assertEquals(2.43, weighting.calcEdgeWeight(edge2, false), 0.01);
        assertEquals(1.15, weighting.calcEdgeWeight(edge1, false), 0.01);

        map.put(PRIMARY.toString(), 1.1);
        weighting = new CustomWeighting("custom", carFE, encodingManager, new DefaultEncodedValueFactory(), NO_TURN_COST_PROVIDER, vehicleModel);
        assertEquals(1.15, weighting.calcEdgeWeight(edge1, false), 0.01);

        // force integer value
        map.put(PRIMARY.toString(), 1);
        weighting = new CustomWeighting("custom", carFE, encodingManager, new DefaultEncodedValueFactory(), NO_TURN_COST_PROVIDER, vehicleModel);
        assertEquals(1.15, weighting.calcEdgeWeight(edge1, false), 0.01);
    }

    @Test
    public void testNoMaxSpeed() {
        EdgeIteratorState edge1 = graphHopperStorage.edge(0, 1).setDistance(10).
                set(roadClassEnc, PRIMARY).set(avSpeedEnc, 80).set(accessEnc, true).setReverse(accessEnc, true);
        CustomModel vehicleModel = new CustomModel();
        vehicleModel.setBase("car");

        Weighting weighting = new CustomWeighting("custom", carFE, encodingManager, new DefaultEncodedValueFactory(), NO_TURN_COST_PROVIDER, vehicleModel);
        assertEquals(1.15, weighting.calcEdgeWeight(edge1, false), 0.01);
    }

    @Test
    public void testMaxSpeedMap() {
        EdgeIteratorState edge1 = graphHopperStorage.edge(0, 1).setDistance(10).
                set(roadClassEnc, PRIMARY).set(avSpeedEnc, 80).set(accessEnc, true).setReverse(accessEnc, true);
        CustomModel vehicleModel = new CustomModel();
        vehicleModel.setBase("car");

        Weighting weighting = new CustomWeighting("custom", carFE, encodingManager, new DefaultEncodedValueFactory(), NO_TURN_COST_PROVIDER, vehicleModel);
        assertEquals(1.15, weighting.calcEdgeWeight(edge1, false), 0.01);

        // reduce speed for road class 'primary'
        Map map = new HashMap();
        map.put(PRIMARY.toString(), 60);
        vehicleModel.getMaxSpeed().put(KEY, map);
        weighting = new CustomWeighting("custom", carFE, encodingManager, new DefaultEncodedValueFactory(), NO_TURN_COST_PROVIDER, vehicleModel);
        assertEquals(1.3, weighting.calcEdgeWeight(edge1, false), 0.01);
    }

    @Test
    public void testSpeedFactorBooleanEV() {
        CustomModel vehicleModel = new CustomModel();
        vehicleModel.setBase("car");

        BooleanEncodedValue rcLinkEnc = encodingManager.getBooleanEncodedValue(RoadClassLink.KEY);
        vehicleModel.getPriority().put(RoadClassLink.KEY, 0.5);
        CustomWeighting weighting = new CustomWeighting("car_based", carFE, encodingManager, new DefaultEncodedValueFactory(), NO_TURN_COST_PROVIDER, vehicleModel);

        assertEquals(3.1, weighting.calcEdgeWeight(graphHopperStorage.edge(0, 1).setDistance(10).
                set(rcLinkEnc, false).set(avSpeedEnc, 15).set(accessEnc, true), false), 0.01);
        assertEquals(6.2, weighting.calcEdgeWeight(graphHopperStorage.edge(0, 1).setDistance(10).
                set(rcLinkEnc, true).set(avSpeedEnc, 15).set(accessEnc, true), false), 0.01);
    }

    @Test
    public void testPriority() {
        CustomModel vehicleModel = new CustomModel();
        vehicleModel.setBase("car");

        Map map = new HashMap();
        map.put(MOTORWAY.toString(), 0.1);
        vehicleModel.getPriority().put(KEY, map);
        CustomWeighting weighting = new CustomWeighting("car_based", carFE, encodingManager, new DefaultEncodedValueFactory(), NO_TURN_COST_PROVIDER, vehicleModel);

        // simple multiplication of the weight...
        assertEquals(31, weighting.calcEdgeWeight(graphHopperStorage.edge(0, 1).setDistance(10).
                set(roadClassEnc, MOTORWAY).set(avSpeedEnc, 15).set(accessEnc, true).setReverse(accessEnc, true), false), 0.01);
        assertEquals(3.1, weighting.calcEdgeWeight(graphHopperStorage.edge(0, 1).setDistance(10).
                set(roadClassEnc, PRIMARY).set(avSpeedEnc, 15).set(accessEnc, true).setReverse(accessEnc, true), false), 0.01);
        assertEquals(155, weighting.calcEdgeWeight(graphHopperStorage.edge(0, 1).setDistance(50).
                set(roadClassEnc, MOTORWAY).set(avSpeedEnc, 15).set(accessEnc, true).setReverse(accessEnc, true), false), 0.01);
        assertEquals(15.5, weighting.calcEdgeWeight(graphHopperStorage.edge(0, 1).setDistance(50).
                set(roadClassEnc, PRIMARY).set(avSpeedEnc, 15).set(accessEnc, true).setReverse(accessEnc, true), false), 0.01);
    }

    @Test
    public void testAvoidArea() {
        CustomModel vehicleModel = new CustomModel();
        vehicleModel.setBase("car");

        Map map = new HashMap();
        map.put(MOTORWAY.toString(), 0.1);
        String areaId = "blup";
        vehicleModel.getPriority().put(KEY, map);
        vehicleModel.getPriority().put("area_" + areaId, 0.5);

        Coordinate[] coordinates = {new Coordinate(13.722, 51.053), new Coordinate(13.722, 51.055),
                new Coordinate(13.731, 51.055), new Coordinate(13.731, 51.053), null};
        coordinates[coordinates.length - 1] = coordinates[0];
        Geometry poly = new GeometryFactory().createPolygon(coordinates);

        JsonFeature area = new JsonFeature(areaId, "Polygon", new BBox(13.713684, 13.719864, 51.036213, 51.036591),
                poly, Collections.<String, Object>emptyMap());
        vehicleModel.getAreas().put(areaId, area);
        CustomWeighting weighting = new CustomWeighting("car_based", carFE, encodingManager, new DefaultEncodedValueFactory(), NO_TURN_COST_PROVIDER, vehicleModel);

        graphHopperStorage.getNodeAccess().setNode(0, 51.036213, 13.713684);
        graphHopperStorage.getNodeAccess().setNode(1, 51.036591, 13.719864);
        // a bit in the north where the blup area is:
        graphHopperStorage.getNodeAccess().setNode(2, 51.054506, 13.723432);
        graphHopperStorage.getNodeAccess().setNode(3, 51.053589, 13.730679);
        EdgeIteratorState edge1 = graphHopperStorage.edge(0, 1).setDistance(500).set(avSpeedEnc, 15).set(accessEnc, true);
        assertEquals(155, weighting.calcEdgeWeight(edge1, false), 0.01);

        // intersect polygon
        EdgeIteratorState edge2 = graphHopperStorage.edge(2, 3).setDistance(500).set(avSpeedEnc, 15).set(accessEnc, true);
        assertTrue(poly.intersects(edge2.fetchWayGeometry(3).toLineString(false)));
        assertEquals(310, weighting.calcEdgeWeight(edge2, false), 0.01);
    }
}