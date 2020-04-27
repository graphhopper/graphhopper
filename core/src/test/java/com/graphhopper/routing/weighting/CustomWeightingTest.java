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
import com.graphhopper.util.FetchMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.graphhopper.routing.profiles.RoadClass.*;
import static com.graphhopper.routing.weighting.TurnCostProvider.NO_TURN_COST_PROVIDER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CustomWeightingTest {

    GraphHopperStorage graphHopperStorage;
    DecimalEncodedValue avSpeedEnc;
    EnumEncodedValue<RoadClass> roadClassEnc;
    EncodingManager encodingManager;
    FlagEncoder carFE;

    @BeforeEach
    public void setUp() {
        carFE = new CarFlagEncoder().setSpeedTwoDirections(true);
        encodingManager = new EncodingManager.Builder().add(carFE).build();
        avSpeedEnc = carFE.getAverageSpeedEnc();
        roadClassEnc = encodingManager.getEnumEncodedValue(KEY, RoadClass.class);
        graphHopperStorage = new GraphBuilder(encodingManager).create();
    }

    @Test
    public void speedOnly() {
        // 50km/h -> 72s per km, 100km/h -> 36s per km
        EdgeIteratorState edge = graphHopperStorage.edge(0, 1, 1000, true)
                .set(avSpeedEnc, 50).setReverse(avSpeedEnc, 100);
        assertEquals(72, createWeighting(new CustomModel().setDistanceInfluence(0)).calcEdgeWeight(edge, false), 1.e-6);
        assertEquals(36, createWeighting(new CustomModel().setDistanceInfluence(0)).calcEdgeWeight(edge, true), 1.e-6);
    }

    @Test
    public void withPriority() {
        // 50km/h -> 72s per km, 100km/h -> 36s per km
        EdgeIteratorState edge = graphHopperStorage.edge(0, 1, 1000, true)
                .set(avSpeedEnc, 50).setReverse(avSpeedEnc, 100)
                .set(roadClassEnc, SECONDARY);
        // if we reduce the priority we get higher edge weights
        Map<String, Object> map = new HashMap<>();
        map.put(SECONDARY.toString(), 0.5);
        CustomModel model = new CustomModel().setDistanceInfluence(0);
        model.getPriority().put(KEY, map);
        // regardless of the speed we get the same priority costs and they are roughly of the same order as the speed
        // costs we get if the speed is 50km/h
        double expectedPriorityCosts = 85.7;
        assertEquals(expectedPriorityCosts + 72, createWeighting(model).calcEdgeWeight(edge, false), .1);
        assertEquals(expectedPriorityCosts + 36, createWeighting(model).calcEdgeWeight(edge, true), .1);
    }

    @Test
    public void withDistanceInfluence() {
        EdgeIteratorState edge = graphHopperStorage.edge(0, 1, 10_000, true).set(avSpeedEnc, 50);
        assertEquals(720, createWeighting(new CustomModel().setDistanceInfluence(0)).calcEdgeWeight(edge, false), .1);
        assertEquals(720_000, createWeighting(new CustomModel().setDistanceInfluence(0)).calcEdgeMillis(edge, false), .1);
        // distance_influence=30 means that for every kilometer we get additional costs of 30s, so +300s here
        assertEquals(1020, createWeighting(new CustomModel().setDistanceInfluence(30)).calcEdgeWeight(edge, false), .1);
        // ... but the travelling time stays the same
        assertEquals(720_000, createWeighting(new CustomModel().setDistanceInfluence(30)).calcEdgeMillis(edge, false), .1);

        // we can also imagine a shorter but slower road that takes the same time
        edge = graphHopperStorage.edge(0, 1, 5_000, true).set(avSpeedEnc, 25);
        assertEquals(720, createWeighting(new CustomModel().setDistanceInfluence(0)).calcEdgeWeight(edge, false), .1);
        assertEquals(720_000, createWeighting(new CustomModel().setDistanceInfluence(0)).calcEdgeMillis(edge, false), .1);
        // and if we include the distance influence the weight will be bigger but still smaller than what we got for
        // the longer and faster edge
        assertEquals(870, createWeighting(new CustomModel().setDistanceInfluence(30)).calcEdgeWeight(edge, false), .1);
    }

    @Test
    public void testBasic() {
        EdgeIteratorState primary = graphHopperStorage.edge(0, 1, 10, true).
                set(roadClassEnc, PRIMARY).set(avSpeedEnc, 80);
        EdgeIteratorState secondary = graphHopperStorage.edge(1, 2, 10, true).
                set(roadClassEnc, SECONDARY).set(avSpeedEnc, 70);

        CustomModel vehicleModel = new CustomModel();
        Map map = new HashMap();

        map.put(PRIMARY.toString(), 1.0);
        map.put(CustomWeighting.CATCH_ALL, 0.5);
        vehicleModel.getPriority().put(KEY, map);

        assertEquals(1.15, createWeighting(vehicleModel).calcEdgeWeight(primary, false), 0.01);
        assertEquals(2.07, createWeighting(vehicleModel).calcEdgeWeight(secondary, false), 0.01);

        // change priority for primary explicitly and change priority for secondary using catch all
        map.put(PRIMARY.toString(), 0.7);
        map.put(CustomWeighting.CATCH_ALL, 0.9);
        assertEquals(1.51, createWeighting(vehicleModel).calcEdgeWeight(primary, false), 0.01);
        assertEquals(1.31, createWeighting(vehicleModel).calcEdgeWeight(secondary, false), 0.01);

        // force integer value
        map.put(PRIMARY.toString(), 1);
        assertEquals(1.15, createWeighting(vehicleModel).calcEdgeWeight(primary, false), 0.01);
    }

    @Test
    public void testMaxSpeedMap() {
        EdgeIteratorState primary = graphHopperStorage.edge(0, 1, 10, true).
                set(roadClassEnc, PRIMARY).set(avSpeedEnc, 80);
        CustomModel vehicleModel = new CustomModel();

        // without setting a max speed
        assertEquals(1.15, createWeighting(vehicleModel).calcEdgeWeight(primary, false), 0.01);

        // now reduce speed for road class 'primary' -> the weight increases
        Map map = new HashMap();
        map.put(PRIMARY.toString(), 60);
        vehicleModel.getMaxSpeed().put(KEY, map);
        assertEquals(1.3, createWeighting(vehicleModel).calcEdgeWeight(primary, false), 0.01);
    }

    @Test
    public void testSpeedFactorBooleanEV() {
        EdgeIteratorState edge = graphHopperStorage.edge(0, 1, 10, true).set(avSpeedEnc, 15);
        CustomModel vehicleModel = new CustomModel();
        assertEquals(3.1, createWeighting(vehicleModel).calcEdgeWeight(edge, false), 0.01);
        // here we increase weight for edges that are road class links
        Map map = new HashMap<>();
        map.put("true", 0.5);
        vehicleModel.getPriority().put(RoadClassLink.KEY, map);
        CustomWeighting weighting = createWeighting(vehicleModel);
        BooleanEncodedValue rcLinkEnc = encodingManager.getBooleanEncodedValue(RoadClassLink.KEY);
        assertEquals(3.1, weighting.calcEdgeWeight(edge.set(rcLinkEnc, false), false), 0.01);
        // todonow: here we are dealing with rather low speeds, so setting priority=0.5 does not have such a strong
        // effect...
        assertEquals(3.96, weighting.calcEdgeWeight(edge.set(rcLinkEnc, true), false), 0.01);
    }

    @Test
    public void testPriority() {
        CustomModel vehicleModel = new CustomModel();

        Map map = new HashMap();
        map.put(MOTORWAY.toString(), 0.1);
        vehicleModel.getPriority().put(KEY, map);
        CustomWeighting weighting = createWeighting(vehicleModel);

        EdgeIteratorState edge = graphHopperStorage.edge(0, 1, 10, true).set(avSpeedEnc, 15);
        assertEquals(3.1, weighting.calcEdgeWeight(edge, false), 0.01);
        // we increase weight for motorways
        assertEquals(10.8142, weighting.calcEdgeWeight(edge.set(roadClassEnc, MOTORWAY), false), 0.01);
        // the edge weight is proportional to the edge distance
        edge = graphHopperStorage.edge(0, 1, 5 * 10, true).set(avSpeedEnc, 15);
        assertEquals(5 * 3.1, weighting.calcEdgeWeight(edge, false), 0.01);
        assertEquals(5 * 10.8142, weighting.calcEdgeWeight(edge.set(roadClassEnc, MOTORWAY), false), 0.01);
    }

    @Test
    public void testAvoidArea() {
        CustomModel vehicleModel = new CustomModel();

        String areaId = "my_area";
        vehicleModel.getPriority().put("area_" + areaId, 0.5);

        Coordinate[] coordinates = {new Coordinate(13.722, 51.053), new Coordinate(13.722, 51.055),
                new Coordinate(13.731, 51.055), new Coordinate(13.731, 51.053), null};
        coordinates[coordinates.length - 1] = coordinates[0];
        Geometry poly = new GeometryFactory().createPolygon(coordinates);

        JsonFeature area = new JsonFeature(areaId, "Polygon", null, poly, Collections.<String, Object>emptyMap());
        vehicleModel.getAreas().put(areaId, area);
        vehicleModel.setDistanceInfluence(0);
        CustomWeighting weighting = createWeighting(vehicleModel);

        graphHopperStorage.getNodeAccess().setNode(0, 51.036213, 13.713684);
        graphHopperStorage.getNodeAccess().setNode(1, 51.036591, 13.719864);
        // a bit in the north where my_area is:
        graphHopperStorage.getNodeAccess().setNode(2, 51.054506, 13.723432);
        graphHopperStorage.getNodeAccess().setNode(3, 51.053589, 13.730679);
        EdgeIteratorState edge1 = graphHopperStorage.edge(0, 1, 500, true).set(avSpeedEnc, 15);
        assertEquals(120, weighting.calcEdgeWeight(edge1, false), 0.01);

        // intersect polygon => increase weight
        EdgeIteratorState edge2 = graphHopperStorage.edge(2, 3, 500, true).set(avSpeedEnc, 15);
        assertTrue(poly.intersects(edge2.fetchWayGeometry(FetchMode.ALL).toLineString(false)));
        assertEquals(162.86, weighting.calcEdgeWeight(edge2, false), 0.01);
    }

    private CustomWeighting createWeighting(CustomModel vehicleModel) {
        return new CustomWeighting(carFE, encodingManager, NO_TURN_COST_PROVIDER, vehicleModel);
    }
}