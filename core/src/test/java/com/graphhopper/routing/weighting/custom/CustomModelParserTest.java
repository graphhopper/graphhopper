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

package com.graphhopper.routing.weighting.custom;

import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.util.CustomModel;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.JsonFeature;
import com.graphhopper.util.JsonFeatureCollection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

import static com.graphhopper.json.Statement.*;
import static com.graphhopper.json.Statement.Op.LIMIT;
import static com.graphhopper.json.Statement.Op.MULTIPLY;
import static com.graphhopper.routing.ev.RoadClass.*;
import static com.graphhopper.routing.weighting.custom.CustomModelParser.parseExpressions;
import static org.junit.jupiter.api.Assertions.*;

class CustomModelParserTest {
    BaseGraph graph;
    EncodingManager encodingManager;
    EnumEncodedValue<RoadClass> roadClassEnc;
    BooleanEncodedValue accessEnc;
    DecimalEncodedValue avgSpeedEnc;
    EnumEncodedValue<Country> countryEnc;
    EnumEncodedValue<State> stateEnc;
    double maxSpeed;

    @BeforeEach
    void setup() {
        accessEnc = VehicleAccess.create("car");
        avgSpeedEnc = VehicleSpeed.create("car", 5, 5, false);
        countryEnc = Country.create();
        stateEnc = State.create();
        encodingManager = new EncodingManager.Builder().add(accessEnc).add(avgSpeedEnc)
                .add(stateEnc).add(countryEnc).add(MaxSpeed.create()).add(Surface.create()).build();
        graph = new BaseGraph.Builder(encodingManager).create();
        roadClassEnc = encodingManager.getEnumEncodedValue(RoadClass.KEY, RoadClass.class);
        maxSpeed = 140;
    }

    @Test
    void setPriorityForRoadClass() {
        CustomModel customModel = new CustomModel();
        customModel.addToPriority(If("road_class == PRIMARY", MULTIPLY, "0.5"));
        customModel.addToSpeed(If("true", LIMIT, "100"));
        CustomWeighting.EdgeToDoubleMapping priorityMapping = CustomModelParser.createWeightingParameters(customModel, encodingManager).getEdgeToPriorityMapping();

        BaseGraph graph = new BaseGraph.Builder(encodingManager).create();
        EdgeIteratorState edge1 = graph.edge(0, 1).setDistance(100).set(roadClassEnc, RoadClass.PRIMARY);
        EdgeIteratorState edge2 = graph.edge(1, 2).setDistance(100).set(roadClassEnc, RoadClass.SECONDARY);

        assertEquals(0.5, priorityMapping.get(edge1, false), 1.e-6);
        assertEquals(1.0, priorityMapping.get(edge2, false), 1.e-6);
    }

    @Test
    void testPriority() {
        EdgeIteratorState primary = graph.edge(0, 1).setDistance(10).
                set(roadClassEnc, PRIMARY).set(avgSpeedEnc, 80).set(accessEnc, true, true);
        EdgeIteratorState secondary = graph.edge(1, 2).setDistance(10).
                set(roadClassEnc, SECONDARY).set(avgSpeedEnc, 70).set(accessEnc, true, true);
        EdgeIteratorState tertiary = graph.edge(1, 2).setDistance(10).
                set(roadClassEnc, TERTIARY).set(avgSpeedEnc, 70).set(accessEnc, true, true);

        CustomModel customModel = new CustomModel();
        customModel.addToPriority(If("road_class == PRIMARY", MULTIPLY, "0.5"));
        customModel.addToPriority(ElseIf("road_class == SECONDARY", MULTIPLY, "0.7"));
        customModel.addToPriority(Else(MULTIPLY, "0.9"));
        customModel.addToPriority(If("road_environment != FERRY", MULTIPLY, "0.8"));
        customModel.addToSpeed(If("true", LIMIT, "100"));
        CustomWeighting.EdgeToDoubleMapping priorityMapping = CustomModelParser.createWeightingParameters(customModel, encodingManager).getEdgeToPriorityMapping();

        assertEquals(0.5 * 0.8, priorityMapping.get(primary, false), 0.01);
        assertEquals(0.7 * 0.8, priorityMapping.get(secondary, false), 0.01);
        assertEquals(0.9 * 0.8, priorityMapping.get(tertiary, false), 0.01);

        // force integer value
        customModel = new CustomModel();
        customModel.addToPriority(If("road_class == PRIMARY", MULTIPLY, "1"));
        customModel.addToPriority(If("road_class == SECONDARY", MULTIPLY, "0.9"));
        customModel.addToSpeed(If("true", LIMIT, "100"));
        priorityMapping = CustomModelParser.createWeightingParameters(customModel, encodingManager).getEdgeToPriorityMapping();
        assertEquals(1, priorityMapping.get(primary, false), 0.01);
        assertEquals(0.9, priorityMapping.get(secondary, false), 0.01);
    }

    @Test
    public void testCountry() {
        EdgeIteratorState usRoad = graph.edge(0, 1).setDistance(10).
                set(roadClassEnc, PRIMARY).set(avgSpeedEnc, 80).set(accessEnc, true, true).
                set(countryEnc, Country.USA).set(stateEnc, State.US_AK);
        EdgeIteratorState us2Road = graph.edge(0, 1).setDistance(10).
                set(roadClassEnc, PRIMARY).set(avgSpeedEnc, 80).set(accessEnc, true, true).
                set(countryEnc, Country.USA);
        EdgeIteratorState deRoad = graph.edge(1, 2).setDistance(10).
                set(roadClassEnc, PRIMARY).set(avgSpeedEnc, 70).set(accessEnc, true, true).
                set(countryEnc, Country.DEU);

        CustomModel customModel = new CustomModel();
        customModel.addToPriority(If("country == USA", MULTIPLY, "0.5"));
        customModel.addToPriority(If("country == USA && state == US_AK", MULTIPLY, "0.6"));
        customModel.addToPriority(If("country == DEU", MULTIPLY, "0.8"));
        customModel.addToSpeed(If("true", LIMIT, "100"));
        CustomWeighting.EdgeToDoubleMapping priorityMapping = CustomModelParser.createWeightingParameters(customModel, encodingManager).getEdgeToPriorityMapping();

        assertEquals(0.6 * 0.5, priorityMapping.get(usRoad, false), 0.01);
        assertEquals(0.5, priorityMapping.get(us2Road, false), 0.01);
        assertEquals(0.8, priorityMapping.get(deRoad, false), 0.01);
    }

    @Test
    public void testBrackets() {
        EdgeIteratorState primary = graph.edge(0, 1).setDistance(10).set(accessEnc, true, true).
                set(roadClassEnc, PRIMARY).set(avgSpeedEnc, 80);
        EdgeIteratorState secondary = graph.edge(0, 1).setDistance(10).set(accessEnc, true, true).
                set(roadClassEnc, SECONDARY).set(avgSpeedEnc, 40);

        CustomModel customModel = new CustomModel();
        customModel.addToPriority(If("(road_class == PRIMARY || car_access == true) && car_average_speed > 50", MULTIPLY, "0.9"));
        customModel.addToSpeed(If("true", LIMIT, "100"));
        CustomWeighting.Parameters parameters = CustomModelParser.createWeightingParameters(customModel, encodingManager);
        assertEquals(0.9, parameters.getEdgeToPriorityMapping().get(primary, false), 0.01);
        assertEquals(1, parameters.getEdgeToPriorityMapping().get(secondary, false), 0.01);
    }

    @Test
    public void testSpeedFactorAndPriorityAndMaxSpeed() {
        EdgeIteratorState primary = graph.edge(0, 1).setDistance(10).
                set(roadClassEnc, PRIMARY).set(avgSpeedEnc, 80).set(accessEnc, true, true);
        EdgeIteratorState secondary = graph.edge(1, 2).setDistance(10).
                set(roadClassEnc, SECONDARY).set(avgSpeedEnc, 70).set(accessEnc, true, true);

        CustomModel customModel = new CustomModel();
        customModel.addToPriority(If("road_class == PRIMARY", MULTIPLY, "0.9"));
        customModel.addToSpeed(If("true", LIMIT, avgSpeedEnc.getName()));
        customModel.addToSpeed(If("road_class == PRIMARY", MULTIPLY, "0.8"));
        CustomWeighting.Parameters parameters = CustomModelParser.createWeightingParameters(customModel, encodingManager);
        assertEquals(0.9, parameters.getEdgeToPriorityMapping().get(primary, false), 0.01);
        assertEquals(64, parameters.getEdgeToSpeedMapping().get(primary, false), 0.01);

        assertEquals(1, parameters.getEdgeToPriorityMapping().get(secondary, false), 0.01);
        assertEquals(70, parameters.getEdgeToSpeedMapping().get(secondary, false), 0.01);

        customModel.addToSpeed(If("road_class != PRIMARY", LIMIT, "50"));
        CustomWeighting.EdgeToDoubleMapping speedMapping = CustomModelParser.createWeightingParameters(customModel, encodingManager).getEdgeToSpeedMapping();
        assertEquals(64, speedMapping.get(primary, false), 0.01);
        assertEquals(50, speedMapping.get(secondary, false), 0.01);
    }

    @Test
    void testIllegalOrder() {
        CustomModel customModel = new CustomModel();
        customModel.addToPriority(Else(MULTIPLY, "0.9"));
        customModel.addToPriority(If("road_environment != FERRY", MULTIPLY, "0.8"));
        assertThrows(IllegalArgumentException.class, () -> CustomModelParser.createWeightingParameters(customModel, encodingManager));

        CustomModel customModel2 = new CustomModel();
        customModel2.addToPriority(ElseIf("road_environment != FERRY", MULTIPLY, "0.9"));
        customModel2.addToPriority(If("road_class != PRIMARY", MULTIPLY, "0.8"));
        assertThrows(IllegalArgumentException.class, () -> CustomModelParser.createWeightingParameters(customModel2, encodingManager));
    }

    @Test
    public void multipleAreas() {
        CustomModel customModel = new CustomModel();
        JsonFeatureCollection areas = new JsonFeatureCollection();
        Coordinate[] area_1_coordinates = new Coordinate[]{
                new Coordinate(48.019324184801185, 11.28021240234375),
                new Coordinate(48.019324184801185, 11.53564453125),
                new Coordinate(48.11843396091691, 11.53564453125),
                new Coordinate(48.11843396091691, 11.28021240234375),
                new Coordinate(48.019324184801185, 11.28021240234375),
        };
        Coordinate[] area_2_coordinates = new Coordinate[]{
                new Coordinate(48.15509285476017, 11.53289794921875),
                new Coordinate(48.15509285476017, 11.8212890625),
                new Coordinate(48.281365151571755, 11.8212890625),
                new Coordinate(48.281365151571755, 11.53289794921875),
                new Coordinate(48.15509285476017, 11.53289794921875),
        };
        areas.getFeatures().add(new JsonFeature("area_1",
                "Feature",
                null,
                new GeometryFactory().createPolygon(area_1_coordinates),
                new HashMap<>()));
        areas.getFeatures().add(new JsonFeature("area_2",
                "Feature",
                null,
                new GeometryFactory().createPolygon(area_2_coordinates),
                new HashMap<>()));
        customModel.setAreas(areas);

        customModel.addToSpeed(If("in_area_1", LIMIT, "100"));
        customModel.addToSpeed(If("!in_area_2", LIMIT, "25"));
        customModel.addToSpeed(Else(LIMIT, "15"));

        // No exception is thrown during createWeightingParameters
        assertAll(() -> CustomModelParser.createWeightingParameters(customModel, encodingManager));

        CustomModel customModel2 = new CustomModel();
        customModel2.setAreas(areas);

        customModel2.addToSpeed(If("in_area_1", LIMIT, "100"));
        customModel2.addToSpeed(If("in_area_2", LIMIT, "25"));
        customModel2.addToSpeed(If("in_area_3", LIMIT, "150"));
        customModel2.addToSpeed(Else(LIMIT, "15"));

        assertThrows(IllegalArgumentException.class, () -> CustomModelParser.createWeightingParameters(customModel2, encodingManager));
    }

    @Test
    public void parseValue() {
        DecimalEncodedValue maxSpeedEnc = encodingManager.getDecimalEncodedValue(MaxSpeed.KEY);
        EdgeIteratorState maxLower = graph.edge(0, 1).setDistance(10).
                set(maxSpeedEnc, 60).set(avgSpeedEnc, 70).set(accessEnc, true, true);
        EdgeIteratorState maxSame = graph.edge(1, 2).setDistance(10).
                set(maxSpeedEnc, 70).set(avgSpeedEnc, 70).set(accessEnc, true, true);

        CustomModel customModel = new CustomModel();
        customModel.addToSpeed(If("true", LIMIT, avgSpeedEnc.getName()));
        customModel.addToSpeed(If("true", LIMIT, "max_speed * 1.1"));
        CustomWeighting.EdgeToDoubleMapping speedMapping = CustomModelParser.createWeightingParameters(customModel, encodingManager).
                getEdgeToSpeedMapping();
        assertEquals(70.0, speedMapping.get(maxSame, false), 0.01);
        assertEquals(66.0, speedMapping.get(maxLower, false), 0.01);
    }

    @Test
    public void parseValueWithError() {
        CustomModel customModel1 = new CustomModel();
        customModel1.addToSpeed(If("true", LIMIT, "unknown"));

        IllegalArgumentException ret = assertThrows(IllegalArgumentException.class,
                () -> CustomModelParser.createWeightingParameters(customModel1, encodingManager));
        assertEquals("Cannot compile expression: 'unknown' not available", ret.getMessage());

        CustomModel customModel3 = new CustomModel();
        customModel3.addToSpeed(If("road_class == PRIMARY", MULTIPLY, "0.5"));
        customModel3.addToSpeed(Else(MULTIPLY, "road_class"));
        ret = assertThrows(IllegalArgumentException.class,
                () -> CustomModelParser.createWeightingParameters(customModel3, encodingManager));
        assertTrue(ret.getMessage().contains("Binary numeric promotion not possible on types \"double\" and \"com.graphhopper.routing.ev.RoadClass\""), ret.getMessage());
    }

    @Test
    public void parseConditionWithError() {
        NameValidator validVariable = s -> encodingManager.hasEncodedValue(s);

        // existing encoded value but not added
        IllegalArgumentException ret = assertThrows(IllegalArgumentException.class,
                () -> parseExpressions(new StringBuilder(),
                        validVariable, "[HERE]", new HashSet<>(),
                        Arrays.asList(If("max_weight > 10", MULTIPLY, "0"))));
        assertTrue(ret.getMessage().startsWith("[HERE] invalid condition \"max_weight > 10\": 'max_weight' not available"), ret.getMessage());

        // invalid variable or constant (NameValidator returns false)
        ret = assertThrows(IllegalArgumentException.class,
                () -> parseExpressions(new StringBuilder(),
                        validVariable, "[HERE]", new HashSet<>(),
                        Arrays.asList(If("country == GERMANY", MULTIPLY, "0"))));
        assertTrue(ret.getMessage().startsWith("[HERE] invalid condition \"country == GERMANY\": 'GERMANY' not available"), ret.getMessage());

        // not whitelisted method
        ret = assertThrows(IllegalArgumentException.class,
                () -> parseExpressions(new StringBuilder(),
                        validVariable, "[HERE]", new HashSet<>(),
                        Arrays.asList(If("edge.fetchWayGeometry().size() > 2", MULTIPLY, "0"))));
        assertTrue(ret.getMessage().startsWith("[HERE] invalid condition \"edge.fetchWayGeometry().size() > 2\": size is an illegal method"), ret.getMessage());
    }

    @Test
    void testBackwardFunction() {
        CustomModel customModel = new CustomModel();
        customModel.addToPriority(If("backward_car_access != car_access", MULTIPLY, "0.5"));
        customModel.addToSpeed(If("true", LIMIT, "100"));
        CustomWeighting.EdgeToDoubleMapping priorityMapping = CustomModelParser.createWeightingParameters(customModel, encodingManager).
                getEdgeToPriorityMapping();

        BaseGraph graph = new BaseGraph.Builder(encodingManager).create();
        EdgeIteratorState edge1 = graph.edge(0, 1).setDistance(100).set(accessEnc, true, false);
        EdgeIteratorState edge2 = graph.edge(1, 2).setDistance(100).set(accessEnc, true, true);

        assertEquals(0.5, priorityMapping.get(edge1, false), 1.e-6);
        assertEquals(1.0, priorityMapping.get(edge2, false), 1.e-6);
    }

}
