package com.graphhopper.routing.weighting.custom;

import com.bedatadriven.jackson.datatype.jts.JtsModule;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.json.Statement;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.graphhopper.json.Statement.*;
import static com.graphhopper.json.Statement.Op.LIMIT;
import static com.graphhopper.json.Statement.Op.MULTIPLY;
import static com.graphhopper.routing.ev.RoadClass.*;
import static com.graphhopper.routing.weighting.TurnCostProvider.NO_TURN_COST_PROVIDER;
import static org.junit.jupiter.api.Assertions.*;

class CustomWeightingTest {
    BaseGraph graph;
    DecimalEncodedValue avSpeedEnc;
    BooleanEncodedValue accessEnc;
    DecimalEncodedValue maxSpeedEnc;
    EnumEncodedValue<RoadClass> roadClassEnc;
    EncodingManager encodingManager;
    CarFlagEncoder carFE;

    @BeforeEach
    public void setup() {
        carFE = new CarFlagEncoder(new PMap().putObject("speed_two_directions", true));
        encodingManager = new EncodingManager.Builder().add(carFE)
                .add(new EnumEncodedValue<>(Toll.KEY, Toll.class))
                .add(new EnumEncodedValue<>(Hazmat.KEY, Hazmat.class))
                .add(new EnumEncodedValue<>(BikeNetwork.KEY, RouteNetwork.class))
                .build();
        avSpeedEnc = carFE.getAverageSpeedEnc();
        accessEnc = carFE.getAccessEnc();
        maxSpeedEnc = encodingManager.getDecimalEncodedValue(MaxSpeed.KEY);
        roadClassEnc = encodingManager.getEnumEncodedValue(KEY, RoadClass.class);
        graph = new BaseGraph.Builder(encodingManager).create();
    }

    @Test
    public void speedOnly() {
        // 50km/h -> 72s per km, 100km/h -> 36s per km
        EdgeIteratorState edge;
        GHUtility.setSpeed(50, 100, carFE, edge = graph.edge(0, 1).setDistance(1000));
        assertEquals(72, createWeighting(new CustomModel().setDistanceInfluence(0)).calcEdgeWeight(edge, false), 1.e-6);
        assertEquals(36, createWeighting(new CustomModel().setDistanceInfluence(0)).calcEdgeWeight(edge, true), 1.e-6);
    }

    @Test
    public void withPriority() {
        // 25km/h -> 144s per km, 50km/h -> 72s per km, 100km/h -> 36s per km
        EdgeIteratorState slow = GHUtility.setSpeed(25, true, true, carFE, graph.edge(0, 1).setDistance(1000)).
                set(roadClassEnc, SECONDARY);
        EdgeIteratorState medium = GHUtility.setSpeed(50, true, true, carFE, graph.edge(0, 1).setDistance(1000)).
                set(roadClassEnc, SECONDARY);
        EdgeIteratorState fast = GHUtility.setSpeed(100, true, true, carFE, graph.edge(0, 1).setDistance(1000)).
                set(roadClassEnc, SECONDARY);

        // without priority costs fastest weighting is the same as custom weighting
        assertEquals(144, new FastestWeighting(carFE, NO_TURN_COST_PROVIDER).calcEdgeWeight(slow, false), .1);
        assertEquals(72, new FastestWeighting(carFE, NO_TURN_COST_PROVIDER).calcEdgeWeight(medium, false), .1);
        assertEquals(36, new FastestWeighting(carFE, NO_TURN_COST_PROVIDER).calcEdgeWeight(fast, false), .1);

        CustomModel model = new CustomModel().setDistanceInfluence(0);
        assertEquals(144, createWeighting(model).calcEdgeWeight(slow, false), .1);
        assertEquals(72, createWeighting(model).calcEdgeWeight(medium, false), .1);
        assertEquals(36, createWeighting(model).calcEdgeWeight(fast, false), .1);

        // if we reduce the priority we get higher edge weights
        model.addToPriority(If("road_class == SECONDARY", MULTIPLY, 0.5));
        // the absolute priority costs depend on the speed, so setting priority=0.5 means a lower absolute weight
        // weight increase for fast edges and a higher absolute increase for slower edges
        assertEquals(2 * 144, createWeighting(model).calcEdgeWeight(slow, false), .1);
        assertEquals(2 * 72, createWeighting(model).calcEdgeWeight(medium, false), .1);
        assertEquals(2 * 36, createWeighting(model).calcEdgeWeight(fast, false), .1);
    }

    @Test
    public void withDistanceInfluence() {
        BooleanEncodedValue accessEnc = carFE.getAccessEnc();
        EdgeIteratorState edge = graph.edge(0, 1).setDistance(10_000).set(avSpeedEnc, 50).set(accessEnc, true, true);
        assertEquals(720, createWeighting(new CustomModel().setDistanceInfluence(0)).calcEdgeWeight(edge, false), .1);
        assertEquals(720_000, createWeighting(new CustomModel().setDistanceInfluence(0)).calcEdgeMillis(edge, false), .1);
        // distance_influence=30 means that for every kilometer we get additional costs of 30s, so +300s here
        assertEquals(1020, createWeighting(new CustomModel().setDistanceInfluence(30)).calcEdgeWeight(edge, false), .1);
        // ... but the travelling time stays the same
        assertEquals(720_000, createWeighting(new CustomModel().setDistanceInfluence(30)).calcEdgeMillis(edge, false), .1);

        // we can also imagine a shorter but slower road that takes the same time
        edge = graph.edge(0, 1).setDistance(5_000).set(avSpeedEnc, 25).set(accessEnc, true, true);
        assertEquals(720, createWeighting(new CustomModel().setDistanceInfluence(0)).calcEdgeWeight(edge, false), .1);
        assertEquals(720_000, createWeighting(new CustomModel().setDistanceInfluence(0)).calcEdgeMillis(edge, false), .1);
        // and if we include the distance influence the weight will be bigger but still smaller than what we got for
        // the longer and faster edge
        assertEquals(870, createWeighting(new CustomModel().setDistanceInfluence(30)).calcEdgeWeight(edge, false), .1);
    }

    @Test
    public void testSpeedFactorBooleanEV() {
        EdgeIteratorState edge = GHUtility.setSpeed(15, true, true, carFE, graph.edge(0, 1).setDistance(10));
        CustomModel vehicleModel = new CustomModel();
        assertEquals(3.1, createWeighting(vehicleModel).calcEdgeWeight(edge, false), 0.01);
        // here we increase weight for edges that are road class links
        vehicleModel.addToPriority(If(RoadClassLink.KEY, MULTIPLY, 0.5));
        Weighting weighting = createWeighting(vehicleModel);
        BooleanEncodedValue rcLinkEnc = encodingManager.getBooleanEncodedValue(RoadClassLink.KEY);
        assertEquals(3.1, weighting.calcEdgeWeight(edge.set(rcLinkEnc, false), false), 0.01);
        assertEquals(5.5, weighting.calcEdgeWeight(edge.set(rcLinkEnc, true), false), 0.01);
    }

    @Test
    public void testBoolean() {
        carFE = new CarFlagEncoder();
        BooleanEncodedValue specialEnc = new SimpleBooleanEncodedValue("special", true);
        encodingManager = new EncodingManager.Builder().add(carFE).add(specialEnc).build();
        avSpeedEnc = carFE.getAverageSpeedEnc();
        graph = new BaseGraph.Builder(encodingManager).create();

        BooleanEncodedValue accessEnc = carFE.getAccessEnc();
        EdgeIteratorState edge = graph.edge(0, 1).set(accessEnc, true).setReverse(accessEnc, true).
                set(avSpeedEnc, 15).set(specialEnc, false).setReverse(specialEnc, true).setDistance(10);

        CustomModel vehicleModel = new CustomModel();
        assertEquals(3.1, createWeighting(vehicleModel).calcEdgeWeight(edge, false), 0.01);
        vehicleModel.addToPriority(If("special == true", MULTIPLY, 0.8));
        vehicleModel.addToPriority(If("special == false", MULTIPLY, 0.4));
        Weighting weighting = createWeighting(vehicleModel);
        assertEquals(6.7, weighting.calcEdgeWeight(edge, false), 0.01);
        assertEquals(3.7, weighting.calcEdgeWeight(edge, true), 0.01);
    }

    @Test
    public void testSpeedFactorAndPriority() {
        EdgeIteratorState primary = graph.edge(0, 1).setDistance(10).
                set(roadClassEnc, PRIMARY).set(avSpeedEnc, 80).set(accessEnc, true, true);
        EdgeIteratorState secondary = graph.edge(1, 2).setDistance(10).
                set(roadClassEnc, SECONDARY).set(avSpeedEnc, 70).set(accessEnc, true, true);

        CustomModel vehicleModel = new CustomModel();
        vehicleModel.addToPriority(If("road_class != PRIMARY", MULTIPLY, 0.5));
        vehicleModel.addToSpeed(If("road_class != PRIMARY", MULTIPLY, 0.9));
        assertEquals(1.15, createWeighting(vehicleModel).calcEdgeWeight(primary, false), 0.01);
        assertEquals(1.84, createWeighting(vehicleModel).calcEdgeWeight(secondary, false), 0.01);

        vehicleModel = new CustomModel();
        vehicleModel.addToPriority(If("road_class == PRIMARY", MULTIPLY, 1.0));
        vehicleModel.addToPriority(Else(MULTIPLY, 0.5));
        vehicleModel.addToSpeed(If("road_class != PRIMARY", MULTIPLY, 0.9));
        assertEquals(1.15, createWeighting(vehicleModel).calcEdgeWeight(primary, false), 0.01);
        assertEquals(1.84, createWeighting(vehicleModel).calcEdgeWeight(secondary, false), 0.01);
    }

    @Test
    public void testIssueSameKey() {
        EdgeIteratorState withToll = graph.edge(0, 1).setDistance(10).
                set(avSpeedEnc, 80).set(accessEnc, true, true);
        EdgeIteratorState noToll = graph.edge(1, 2).setDistance(10).
                set(avSpeedEnc, 80).set(accessEnc, true, true);

        CustomModel vehicleModel = new CustomModel();
        vehicleModel.addToSpeed(If("toll == HGV || toll == ALL", MULTIPLY, 0.8));
        vehicleModel.addToSpeed(If("hazmat != NO", MULTIPLY, 0.8));
        assertEquals(1.26, createWeighting(vehicleModel).calcEdgeWeight(withToll, false), 0.01);
        assertEquals(1.26, createWeighting(vehicleModel).calcEdgeWeight(noToll, false), 0.01);

        vehicleModel = new CustomModel();
        vehicleModel.addToSpeed(If("bike_network != OTHER", MULTIPLY, 0.8));
        assertEquals(1.26, createWeighting(vehicleModel).calcEdgeWeight(withToll, false), 0.01);
        assertEquals(1.26, createWeighting(vehicleModel).calcEdgeWeight(noToll, false), 0.01);
    }

    @Test
    public void testFirstMatch() {
        EdgeIteratorState primary = graph.edge(0, 1).setDistance(10).
                set(roadClassEnc, PRIMARY).set(avSpeedEnc, 80).set(accessEnc, true, true);
        EdgeIteratorState secondary = graph.edge(1, 2).setDistance(10).
                set(roadClassEnc, SECONDARY).set(avSpeedEnc, 70).set(accessEnc, true, true);

        CustomModel vehicleModel = new CustomModel();
        vehicleModel.addToSpeed(If("road_class == PRIMARY", MULTIPLY, 0.8));
        assertEquals(1.26, createWeighting(vehicleModel).calcEdgeWeight(primary, false), 0.01);
        assertEquals(1.21, createWeighting(vehicleModel).calcEdgeWeight(secondary, false), 0.01);

        vehicleModel.addToPriority(If("road_class == PRIMARY", MULTIPLY, 0.9));
        vehicleModel.addToPriority(ElseIf("road_class == SECONDARY", MULTIPLY, 0.8));

        assertEquals(1.33, createWeighting(vehicleModel).calcEdgeWeight(primary, false), 0.01);
        assertEquals(1.34, createWeighting(vehicleModel).calcEdgeWeight(secondary, false), 0.01);
    }

    @Test
    public void testCarAccess() {
        EdgeIteratorState edge40 = graph.edge(0, 1).setDistance(10).set(avSpeedEnc, 40).set(accessEnc, true, true);
        EdgeIteratorState edge50 = graph.edge(1, 2).setDistance(10).set(avSpeedEnc, 50).set(accessEnc, true, true);

        CustomModel vehicleModel = new CustomModel();
        vehicleModel.addToPriority(If("car$average_speed > 40", MULTIPLY, 0.5));

        assertEquals(1.60, createWeighting(vehicleModel).calcEdgeWeight(edge40, false), 0.01);
        assertEquals(2.14, createWeighting(vehicleModel).calcEdgeWeight(edge50, false), 0.01);
    }

    @Test
    public void testRoadClass() throws Exception {
        EdgeIteratorState primary = graph.edge(0, 1).setDistance(10).
                set(roadClassEnc, PRIMARY).set(avSpeedEnc, 80).set(accessEnc, true, true);
        EdgeIteratorState secondary = graph.edge(1, 2).setDistance(10).
                set(roadClassEnc, SECONDARY).set(avSpeedEnc, 80).set(accessEnc, true, true);
        CustomModel vehicleModel = new CustomModel();
        vehicleModel.addToPriority(If("road_class == PRIMARY", MULTIPLY, 0.5));
        assertEquals(1.6, createWeighting(vehicleModel).calcEdgeWeight(primary, false), 0.01);
        assertEquals(1.15, createWeighting(vehicleModel).calcEdgeWeight(secondary, false), 0.01);
    }

    @Test
    public void testArea() throws Exception {
        EdgeIteratorState edge1 = graph.edge(0, 1).setDistance(10).
                set(roadClassEnc, PRIMARY).set(avSpeedEnc, 80).set(accessEnc, true, true);
        EdgeIteratorState edge2 = graph.edge(2, 3).setDistance(10).
                set(roadClassEnc, PRIMARY).set(avSpeedEnc, 80).set(accessEnc, true, true);
        graph.getNodeAccess().setNode(0, 50.0120, 11.582);
        graph.getNodeAccess().setNode(1, 50.0125, 11.585);
        graph.getNodeAccess().setNode(2, 40.0, 8.0);
        graph.getNodeAccess().setNode(3, 40.1, 8.1);
        CustomModel vehicleModel = new CustomModel();
        vehicleModel.addToPriority(If("in_custom1", MULTIPLY, 0.5));

        ObjectMapper om = new ObjectMapper().registerModule(new JtsModule());
        JsonFeature json = om.readValue("{ \"geometry\":{ \"type\": \"Polygon\", \"coordinates\": " +
                "[[[11.5818,50.0126], [11.5818,50.0119], [11.5861,50.0119], [11.5861,50.0126], [11.5818,50.0126]]] }}", JsonFeature.class);
        vehicleModel.getAreas().put("custom1", json);

        // edge1 is located within the area custom1, edge2 is not
        assertEquals(1.6, createWeighting(vehicleModel).calcEdgeWeight(edge1, false), 0.01);
        assertEquals(1.15, createWeighting(vehicleModel).calcEdgeWeight(edge2, false), 0.01);
    }

    @Test
    public void testMaxSpeed() {
        assertEquals(140, carFE.getMaxSpeed(), 0.1);

        assertEquals(1000.0 / 72 * 3.6, createWeighting(new CustomModel().
                addToSpeed(If("true", LIMIT, 72)).setDistanceInfluence(0)).getMinWeight(1000));

        // ignore too big limit to let custom model compatibility not break when max speed of encoder later decreases
        assertEquals(1000.0 / 140 * 3.6, createWeighting(new CustomModel().
                addToSpeed(If("true", LIMIT, 150)).setDistanceInfluence(0)).getMinWeight(1000));

        // a speed bigger than the allowed stored speed is fine, see discussion in #2335
        assertEquals(1000.0 / 150 * 3.6, createWeighting(new CustomModel().
                addToSpeed(If("road_class == SERVICE", MULTIPLY, 1.5)).
                addToSpeed(If("true", LIMIT, 150)).setDistanceInfluence(0)).getMinWeight(1000));
    }

    @Test
    public void testMaxPriority() {
        assertEquals(1000.0 / 140 / 0.5 * 3.6, createWeighting(new CustomModel().
                addToPriority(If("true", MULTIPLY, 0.5)).setDistanceInfluence(0)).getMinWeight(1000));

        // ignore too big limit
        assertEquals(1000.0 / 140 / 1.0 * 3.6, createWeighting(new CustomModel().
                addToPriority(If("true", LIMIT, 2.0)).setDistanceInfluence(0)).getMinWeight(1000));

        // priority bigger 1 is fine (if CustomModel not in query)
        assertEquals(1000.0 / 140 / 2.0 * 3.6, createWeighting(new CustomModel().
                addToPriority(If("true", MULTIPLY, 3.0)).
                addToPriority(If("true", LIMIT, 2.0)).setDistanceInfluence(0)).getMinWeight(1000));
        assertEquals(1000.0 / 140 / 1.5 * 3.6, createWeighting(new CustomModel().
                addToPriority(If("true", MULTIPLY, 1.5)).setDistanceInfluence(0)).getMinWeight(1000));
        // pick maximum priority from value even if this is for a very special case
        assertEquals(1000.0 / 140 / 3.0 * 3.6, createWeighting(new CustomModel().
                addToPriority(If("road_class == SERVICE", MULTIPLY, 3.0)).setDistanceInfluence(0)).getMinWeight(1000));
    }

    @Test
    public void tooManyStatements() {
        CustomModel customModel = new CustomModel();
        for (int i = 0; i < 1050; i++) {
            customModel.addToPriority(If("road_class == MOTORWAY || road_class == SECONDARY || road_class == PRIMARY", MULTIPLY, 0.1));
        }
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> createWeighting(customModel));
        assertTrue(ex.getMessage().startsWith("Custom Model too big"), ex.getMessage());
    }

    @Test
    public void maxSpeedViolated_bug_2307() {
        EdgeIteratorState motorway = graph.edge(0, 1).setDistance(10).
                set(roadClassEnc, MOTORWAY).set(avSpeedEnc, 80).set(accessEnc, true, true);
        CustomModel customModel = new CustomModel()
                .addToSpeed(Statement.If("road_class == MOTORWAY", Statement.Op.MULTIPLY, 0.7))
                .addToSpeed(Statement.Else(LIMIT, 30));
        Weighting weighting = createWeighting(customModel);
        assertEquals(1.3429, weighting.calcEdgeWeight(motorway, false), 1e-4);
        assertEquals(10 / (80 * 0.7 / 3.6) * 1000, weighting.calcEdgeMillis(motorway, false), 1);
    }

    private Weighting createWeighting(CustomModel vehicleModel) {
        return CustomModelParser.createWeighting(carFE, encodingManager, NO_TURN_COST_PROVIDER, vehicleModel);
    }
}