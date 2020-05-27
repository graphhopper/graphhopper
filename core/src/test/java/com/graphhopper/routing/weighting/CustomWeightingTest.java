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
import com.graphhopper.routing.ev.*;
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

import static com.graphhopper.routing.ev.RoadClass.*;
import static com.graphhopper.routing.weighting.TurnCostProvider.NO_TURN_COST_PROVIDER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CustomWeightingTest {

    GraphHopperStorage graphHopperStorage;
    DecimalEncodedValue avSpeedEnc;
    DecimalEncodedValue maxSpeedEnc;
    IntEncodedValue laneEnc;
    EnumEncodedValue<RoadClass> roadClassEnc;
    EncodingManager encodingManager;
    FlagEncoder carFE;

    @BeforeEach
    public void setUp() {
        carFE = new CarFlagEncoder().setSpeedTwoDirections(true);
        laneEnc = new UnsignedIntEncodedValue("lanes", 2, true);
        encodingManager = new EncodingManager.Builder().add(carFE).add(laneEnc).build();
        avSpeedEnc = carFE.getAverageSpeedEnc();
        maxSpeedEnc = encodingManager.getDecimalEncodedValue(MaxSpeed.KEY);
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
        // 25km/h -> 144s per km, 50km/h -> 72s per km, 100km/h -> 36s per km
        EdgeIteratorState slow = graphHopperStorage.edge(0, 1, 1000, true).set(avSpeedEnc, 25).set(roadClassEnc, SECONDARY);
        EdgeIteratorState medium = graphHopperStorage.edge(0, 1, 1000, true).set(avSpeedEnc, 50).set(roadClassEnc, SECONDARY);
        EdgeIteratorState fast = graphHopperStorage.edge(0, 1, 1000, true).set(avSpeedEnc, 100).set(roadClassEnc, SECONDARY);

        // without priority costs fastest weighting is the same as custom weighting
        assertEquals(144, new FastestWeighting(carFE, NO_TURN_COST_PROVIDER).calcEdgeWeight(slow, false), .1);
        assertEquals(72, new FastestWeighting(carFE, NO_TURN_COST_PROVIDER).calcEdgeWeight(medium, false), .1);
        assertEquals(36, new FastestWeighting(carFE, NO_TURN_COST_PROVIDER).calcEdgeWeight(fast, false), .1);

        Map<String, Object> map = new HashMap<>();
        CustomModel model = new CustomModel().setDistanceInfluence(0);
        assertEquals(144, createWeighting(model).calcEdgeWeight(slow, false), .1);
        assertEquals(72, createWeighting(model).calcEdgeWeight(medium, false), .1);
        assertEquals(36, createWeighting(model).calcEdgeWeight(fast, false), .1);

        // if we reduce the priority we get higher edge weights
        map.put(SECONDARY.toString(), 0.5);
        model.getPriority().put(KEY, map);
        // the absolute priority costs depend on the speed, so setting priority=0.5 means a lower absolute weight
        // weight increase for fast edges and a higher absolute increase for slower edges
        assertEquals(2 * 144, createWeighting(model).calcEdgeWeight(slow, false), .1);
        assertEquals(2 * 72, createWeighting(model).calcEdgeWeight(medium, false), .1);
        assertEquals(2 * 36, createWeighting(model).calcEdgeWeight(fast, false), .1);
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
        assertEquals(1.73, createWeighting(vehicleModel).calcEdgeWeight(secondary, false), 0.01);

        // change priority for primary explicitly and change priority for secondary using catch all
        map.put(PRIMARY.toString(), 0.7);
        map.put(CustomWeighting.CATCH_ALL, 0.9);
        assertEquals(1.34, createWeighting(vehicleModel).calcEdgeWeight(primary, false), 0.01);
        assertEquals(1.27, createWeighting(vehicleModel).calcEdgeWeight(secondary, false), 0.01);

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
        assertEquals(5.5, weighting.calcEdgeWeight(edge.set(rcLinkEnc, true), false), 0.01);
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
        edge.set(roadClassEnc, MOTORWAY);
        assertEquals(24.70, weighting.calcEdgeWeight(edge, false), 0.01);
        // the edge weight is proportional to the edge distance
        edge = graphHopperStorage.edge(0, 1, 5 * 10, true).set(avSpeedEnc, 15);
        assertEquals(5 * 3.1, weighting.calcEdgeWeight(edge, false), 0.01);
        assertEquals(5 * 24.70, weighting.calcEdgeWeight(edge.set(roadClassEnc, MOTORWAY), false), 0.01);
    }

    @Test
    public void testAvoidHighSpeed() {
        CustomWeighting weighting = createWeighting(new CustomModel());
        EdgeIteratorState slowEdge = graphHopperStorage.edge(0, 1, 10, true).set(avSpeedEnc, 15).set(maxSpeedEnc, 50);
        EdgeIteratorState fastEdge = graphHopperStorage.edge(1, 2, 10, true).set(avSpeedEnc, 60).set(maxSpeedEnc, 70);
        assertEquals(3.10, weighting.calcEdgeWeight(slowEdge, false), 0.01);
        assertEquals(1.30, weighting.calcEdgeWeight(fastEdge, false), 0.01);

        Map map = new HashMap();
        map.put(">69", 0.2);
        CustomModel vehicleModel = new CustomModel();
        vehicleModel.getPriority().put("max_speed", map);
        weighting = createWeighting(vehicleModel);
        assertEquals(3.10, weighting.calcEdgeWeight(slowEdge, false), 0.01);
        assertEquals(3.70, weighting.calcEdgeWeight(fastEdge, false), 0.01);

        // this is currently a bit hidden feature as only the shared encoded values are suggested in the UI
        map = new HashMap();
        map.put(">50", 0.2);
        vehicleModel = new CustomModel();
        vehicleModel.getPriority().put(EncodingManager.getKey("car", "average_speed"), map);
        weighting = createWeighting(vehicleModel);
        assertEquals(3.10, weighting.calcEdgeWeight(slowEdge, false), 0.01);
        assertEquals(3.70, weighting.calcEdgeWeight(fastEdge, false), 0.01);
    }

    @Test
    public void testIntEncodedValue() {
        // currently we have no inbuilt encoded value that requires int but it is not bad to have for e.g. lanes
        CustomWeighting weighting = createWeighting(new CustomModel());
        EdgeIteratorState slowEdge = graphHopperStorage.edge(0, 1, 10, true).set(avSpeedEnc, 15).set(laneEnc, 0);
        EdgeIteratorState fastEdge = graphHopperStorage.edge(1, 2, 10, true).set(avSpeedEnc, 60).set(laneEnc, 2);
        assertEquals(3.10, weighting.calcEdgeWeight(slowEdge, false), 0.01);
        assertEquals(1.30, weighting.calcEdgeWeight(fastEdge, false), 0.01);

        Map map = new HashMap();
        map.put(" > 1.5", 0.2); // allow decimal values in range even for int encoded value
        CustomModel vehicleModel = new CustomModel();
        vehicleModel.getPriority().put("lanes", map);
        weighting = createWeighting(vehicleModel);
        assertEquals(3.10, weighting.calcEdgeWeight(slowEdge, false), 0.01);
        assertEquals(3.70, weighting.calcEdgeWeight(fastEdge, false), 0.01);
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

        // intersect polygon => increase weight (it doubles, because distance influence is zero)
        EdgeIteratorState edge2 = graphHopperStorage.edge(2, 3, 500, true).set(avSpeedEnc, 15);
        assertTrue(poly.intersects(edge2.fetchWayGeometry(FetchMode.ALL).toLineString(false)));
        assertEquals(240, weighting.calcEdgeWeight(edge2, false), 0.01);
    }

    private CustomWeighting createWeighting(CustomModel vehicleModel) {
        return new CustomWeighting(carFE, encodingManager, NO_TURN_COST_PROVIDER, vehicleModel);
    }
}