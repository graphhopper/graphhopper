package com.graphhopper.routing.weighting.custom;

import com.bedatadriven.jackson.datatype.jts.JtsModule;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.json.Statement;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.querygraph.VirtualEdgeIteratorState;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.weighting.DefaultTurnCostProvider;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.graphhopper.json.Statement.*;
import static com.graphhopper.json.Statement.Op.LIMIT;
import static com.graphhopper.json.Statement.Op.MULTIPLY;
import static com.graphhopper.routing.ev.RoadClass.*;
import static com.graphhopper.routing.weighting.TurnCostProvider.NO_TURN_COST_PROVIDER;
import static com.graphhopper.util.GHUtility.getEdge;
import static org.junit.jupiter.api.Assertions.*;

class CustomWeightingTest {
    BaseGraph graph;
    DecimalEncodedValue avSpeedEnc;
    BooleanEncodedValue accessEnc;
    DecimalEncodedValue maxSpeedEnc;
    EnumEncodedValue<RoadClass> roadClassEnc;
    EncodingManager encodingManager;
    BooleanEncodedValue turnRestrictionEnc = TurnRestriction.create("car");

    @BeforeEach
    public void setup() {
        accessEnc = VehicleAccess.create("car");
        avSpeedEnc = VehicleSpeed.create("car", 5, 5, true);
        encodingManager = new EncodingManager.Builder().add(accessEnc).add(avSpeedEnc)
                .add(Toll.create())
                .add(Hazmat.create())
                .add(RouteNetwork.create(BikeNetwork.KEY))
                .add(MaxSpeed.create())
                .add(RoadClass.create())
                .add(RoadClassLink.create())
                .addTurnCostEncodedValue(turnRestrictionEnc)
                .build();
        maxSpeedEnc = encodingManager.getDecimalEncodedValue(MaxSpeed.KEY);
        roadClassEnc = encodingManager.getEnumEncodedValue(KEY, RoadClass.class);
        graph = new BaseGraph.Builder(encodingManager).create();
    }

    private void setTurnRestriction(Graph graph, int from, int via, int to) {
        graph.getTurnCostStorage().set(turnRestrictionEnc, getEdge(graph, from, via).getEdge(), via, getEdge(graph, via, to).getEdge(), true);
    }

    private CustomModel createSpeedCustomModel(DecimalEncodedValue speedEnc) {
        CustomModel customModel = new CustomModel();
        customModel.addToSpeed(If("true", LIMIT, speedEnc.getName()));
        return customModel;
    }

    private Weighting createWeighting(CustomModel vehicleModel) {
        return CustomModelParser.createWeighting(encodingManager, NO_TURN_COST_PROVIDER, vehicleModel);
    }

    @Test
    public void speedOnly() {
        // 50km/h -> 72s per km, 100km/h -> 36s per km
        EdgeIteratorState edge = graph.edge(0, 1).setDistance(1000).set(avSpeedEnc, 50, 100);
        CustomModel customModel = createSpeedCustomModel(avSpeedEnc)
                .setDistanceInfluence(0d);
        Weighting weighting = createWeighting(customModel);

        assertEquals(720, weighting.calcEdgeWeight(edge, false));
        assertEquals(360, weighting.calcEdgeWeight(edge, true));
    }

    @Test
    public void withPriority() {
        // 25km/h -> 144s per km, 50km/h -> 72s per km, 100km/h -> 36s per km
        EdgeIteratorState slow = graph.edge(0, 1).set(avSpeedEnc, 25, 25).setDistance(1000).
                set(roadClassEnc, SECONDARY);
        EdgeIteratorState medium = graph.edge(0, 1).set(avSpeedEnc, 50, 50).setDistance(1000).
                set(roadClassEnc, SECONDARY);
        EdgeIteratorState fast = graph.edge(0, 1).set(avSpeedEnc, 100).setDistance(1000).
                set(roadClassEnc, SECONDARY);

        Weighting weighting = createWeighting(createSpeedCustomModel(avSpeedEnc));
        assertEquals(1440, weighting.calcEdgeWeight(slow, false));
        assertEquals(720, weighting.calcEdgeWeight(medium, false));
        assertEquals(360, weighting.calcEdgeWeight(fast, false));

        // if we reduce the priority we get higher edge weights
        weighting = CustomModelParser.createWeighting(encodingManager, NO_TURN_COST_PROVIDER,
                createSpeedCustomModel(avSpeedEnc)
                        .addToPriority(If("road_class == SECONDARY", MULTIPLY, "0.5"))
        );
        assertEquals(2 * 1440, weighting.calcEdgeWeight(slow, false));
        assertEquals(2 * 720, weighting.calcEdgeWeight(medium, false));
        assertEquals(2 * 360, weighting.calcEdgeWeight(fast, false));
    }

    @Test
    public void withDistanceInfluence() {
        EdgeIteratorState edge1 = graph.edge(0, 1).setDistance(10_000).set(avSpeedEnc, 50);
        EdgeIteratorState edge2 = graph.edge(0, 1).setDistance(5_000).set(avSpeedEnc, 25);
        Weighting weighting = createWeighting(createSpeedCustomModel(avSpeedEnc).setDistanceInfluence(0d));
        assertEquals(7200, weighting.calcEdgeWeight(edge1, false));
        assertEquals(720_000, weighting.calcEdgeMillis(edge1, false));
        // we can also imagine a shorter but slower road that takes the same time
        assertEquals(7200, weighting.calcEdgeWeight(edge2, false));
        assertEquals(720_000, weighting.calcEdgeMillis(edge2, false));

        // distance_influence=30 means that for every kilometer we get additional costs of 30s, so +300s here
        weighting = createWeighting(createSpeedCustomModel(avSpeedEnc).setDistanceInfluence(30d));
        assertEquals(10200, weighting.calcEdgeWeight(edge1, false));
        // for the shorter but slower edge the distance influence also increases the weight, but not as much because it is shorter
        assertEquals(8700, weighting.calcEdgeWeight(edge2, false));
        // ... the travelling times stay the same
        assertEquals(720_000, weighting.calcEdgeMillis(edge1, false));
        assertEquals(720_000, weighting.calcEdgeMillis(edge2, false));
    }

    @Test
    public void testSpeedFactorBooleanEV() {
        EdgeIteratorState edge = graph.edge(0, 1).set(avSpeedEnc, 15, 15).setDistance(10);
        Weighting weighting = createWeighting(createSpeedCustomModel(avSpeedEnc).setDistanceInfluence(70d));
        assertEquals(31, weighting.calcEdgeWeight(edge, false));
        // here we increase weight for edges that are road class links
        weighting = createWeighting(createSpeedCustomModel(avSpeedEnc)
                .setDistanceInfluence(70d)
                .addToPriority(If(RoadClassLink.KEY, MULTIPLY, "0.5")));
        BooleanEncodedValue rcLinkEnc = encodingManager.getBooleanEncodedValue(RoadClassLink.KEY);
        assertEquals(31, weighting.calcEdgeWeight(edge.set(rcLinkEnc, false), false));
        assertEquals(55, weighting.calcEdgeWeight(edge.set(rcLinkEnc, true), false));
    }

    @Test
    public void testBoolean() {
        BooleanEncodedValue specialEnc = new SimpleBooleanEncodedValue("special", true);
        DecimalEncodedValue avSpeedEnc = VehicleSpeed.create("car", 5, 5, false);
        encodingManager = new EncodingManager.Builder().add(specialEnc).add(avSpeedEnc).build();
        graph = new BaseGraph.Builder(encodingManager).create();

        EdgeIteratorState edge = graph.edge(0, 1).set(specialEnc, false, true).set(avSpeedEnc, 15).setDistance(10);

        Weighting weighting = createWeighting(createSpeedCustomModel(avSpeedEnc).setDistanceInfluence(70d));
        assertEquals(31, weighting.calcEdgeWeight(edge, false));
        weighting = createWeighting(createSpeedCustomModel(avSpeedEnc)
                .setDistanceInfluence(70d)
                .addToPriority(If("special == true", MULTIPLY, "0.8"))
                .addToPriority(If("special == false", MULTIPLY, "0.4")));
        assertEquals(67, weighting.calcEdgeWeight(edge, false));
        assertEquals(37, weighting.calcEdgeWeight(edge, true));
    }

    @Test
    public void testSpeedFactorAndPriority() {
        EdgeIteratorState primary = graph.edge(0, 1).setDistance(10).
                set(roadClassEnc, PRIMARY).set(avSpeedEnc, 80);
        EdgeIteratorState secondary = graph.edge(1, 2).setDistance(10).
                set(roadClassEnc, SECONDARY).set(avSpeedEnc, 70);

        CustomModel customModel = createSpeedCustomModel(avSpeedEnc).setDistanceInfluence(70d).
                addToPriority(If("road_class != PRIMARY", MULTIPLY, "0.5")).
                addToSpeed(If("road_class != PRIMARY", MULTIPLY, "0.9"));
        Weighting weighting = createWeighting(customModel);
        assertEquals(12, weighting.calcEdgeWeight(primary, false));
        assertEquals(18, weighting.calcEdgeWeight(secondary, false));

        customModel = createSpeedCustomModel(avSpeedEnc).setDistanceInfluence(70d).
                addToPriority(If("road_class == PRIMARY", MULTIPLY, "1.0")).
                addToPriority(Else(MULTIPLY, "0.5")).
                addToSpeed(If("road_class != PRIMARY", MULTIPLY, "0.9"));
        weighting = createWeighting(customModel);
        assertEquals(12, weighting.calcEdgeWeight(primary, false));
        assertEquals(18, weighting.calcEdgeWeight(secondary, false));
    }

    @Test
    public void testIssueSameKey() {
        EdgeIteratorState withToll = graph.edge(0, 1).setDistance(10).set(avSpeedEnc, 80);
        EdgeIteratorState noToll = graph.edge(1, 2).setDistance(10).set(avSpeedEnc, 80);

        CustomModel customModel = createSpeedCustomModel(avSpeedEnc);
        customModel.setDistanceInfluence(70d).
                addToSpeed(If("toll == HGV || toll == ALL", MULTIPLY, "0.8")).
                addToSpeed(If("hazmat != NO", MULTIPLY, "0.8"));
        Weighting weighting = createWeighting(customModel);
        assertEquals(13, weighting.calcEdgeWeight(withToll, false));
        assertEquals(13, weighting.calcEdgeWeight(noToll, false));

        customModel = createSpeedCustomModel(avSpeedEnc);
        customModel.setDistanceInfluence(70d).
                addToSpeed(If("bike_network != OTHER", MULTIPLY, "0.8"));
        weighting = createWeighting(customModel);
        assertEquals(13, weighting.calcEdgeWeight(withToll, false));
        assertEquals(13, weighting.calcEdgeWeight(noToll, false));
    }

    @Test
    public void testFirstMatch() {
        EdgeIteratorState primary = graph.edge(0, 1).setDistance(10).
                set(roadClassEnc, PRIMARY).set(avSpeedEnc, 80);
        EdgeIteratorState secondary = graph.edge(1, 2).setDistance(10).
                set(roadClassEnc, SECONDARY).set(avSpeedEnc, 70);

        CustomModel customModel = createSpeedCustomModel(avSpeedEnc).setDistanceInfluence(70d).
                addToSpeed(If("road_class == PRIMARY", MULTIPLY, "0.8"));
        Weighting weighting = createWeighting(customModel);
        assertEquals(13, weighting.calcEdgeWeight(primary, false));
        assertEquals(12, weighting.calcEdgeWeight(secondary, false));

        customModel.addToPriority(If("road_class == PRIMARY", MULTIPLY, "0.9"));
        customModel.addToPriority(ElseIf("road_class == SECONDARY", MULTIPLY, "0.8"));

        weighting = createWeighting(customModel);
        assertEquals(13, weighting.calcEdgeWeight(primary, false));
        assertEquals(13, weighting.calcEdgeWeight(secondary, false));
    }

    @Test
    public void testSpeedBiggerThan() {
        EdgeIteratorState edge40 = graph.edge(0, 1).setDistance(10).set(avSpeedEnc, 40);
        EdgeIteratorState edge50 = graph.edge(1, 2).setDistance(10).set(avSpeedEnc, 50);

        CustomModel customModel = createSpeedCustomModel(avSpeedEnc).setDistanceInfluence(70d).
                addToPriority(If("car_average_speed > 40", MULTIPLY, "0.5"));
        Weighting weighting = createWeighting(customModel);

        assertEquals(16, weighting.calcEdgeWeight(edge40, false));
        assertEquals(21, weighting.calcEdgeWeight(edge50, false));
    }

    @Test
    public void testRoadClass() {
        EdgeIteratorState primary = graph.edge(0, 1).setDistance(10).
                set(roadClassEnc, PRIMARY).set(avSpeedEnc, 80);
        EdgeIteratorState secondary = graph.edge(1, 2).setDistance(10).
                set(roadClassEnc, SECONDARY).set(avSpeedEnc, 80);
        CustomModel customModel = createSpeedCustomModel(avSpeedEnc).setDistanceInfluence(70d).
                addToPriority(If("road_class == PRIMARY", MULTIPLY, "0.5"));
        Weighting weighting = createWeighting(customModel);
        assertEquals(16, weighting.calcEdgeWeight(primary, false));
        assertEquals(12, weighting.calcEdgeWeight(secondary, false));
    }

    @Test
    public void testArea() throws Exception {
        EdgeIteratorState edge1 = graph.edge(0, 1).setDistance(10).
                set(roadClassEnc, PRIMARY).set(avSpeedEnc, 80);
        EdgeIteratorState edge2 = graph.edge(2, 3).setDistance(10).
                set(roadClassEnc, PRIMARY).set(avSpeedEnc, 80);
        graph.getNodeAccess().setNode(0, 50.0120, 11.582);
        graph.getNodeAccess().setNode(1, 50.0125, 11.585);
        graph.getNodeAccess().setNode(2, 40.0, 8.0);
        graph.getNodeAccess().setNode(3, 40.1, 8.1);
        CustomModel customModel = createSpeedCustomModel(avSpeedEnc).setDistanceInfluence(70d).
                addToPriority(If("in_custom1", MULTIPLY, "0.5"));

        ObjectMapper om = new ObjectMapper().registerModule(new JtsModule());
        JsonFeature json = om.readValue("{ \"geometry\":{ \"type\": \"Polygon\", \"coordinates\": " +
                "[[[11.5818,50.0126], [11.5818,50.0119], [11.5861,50.0119], [11.5861,50.0126], [11.5818,50.0126]]] }}", JsonFeature.class);
        json.setId("custom1");
        customModel.getAreas().getFeatures().add(json);

        Weighting weighting = createWeighting(customModel);
        // edge1 is located within the area custom1, edge2 is not
        assertEquals(16, weighting.calcEdgeWeight(edge1, false));
        assertEquals(12, weighting.calcEdgeWeight(edge2, false));
    }

    @Test
    public void testMaxSpeed() {
        assertEquals(155, avSpeedEnc.getMaxOrMaxStorableDecimal(), 0.1);

        assertEquals(10d / 72 * 3.6, createWeighting(createSpeedCustomModel(avSpeedEnc).
                addToSpeed(If("true", LIMIT, "72"))).calcMinWeightPerDistance(), .001);

        // ignore too big limit to let custom model compatibility not break when max speed of encoded value later decreases
        assertEquals(10d / 155 * 3.6, createWeighting(createSpeedCustomModel(avSpeedEnc).
                addToSpeed(If("true", LIMIT, "180"))).calcMinWeightPerDistance(), .001);

        // reduce speed only a bit
        assertEquals(10d / 150 * 3.6, createWeighting(createSpeedCustomModel(avSpeedEnc).
                addToSpeed(If("road_class == SERVICE", MULTIPLY, "1.5")).
                addToSpeed(If("true", LIMIT, "150"))).calcMinWeightPerDistance(), .001);
    }

    @Test
    public void testMaxPriority() {
        double maxSpeed = 155;
        assertEquals(maxSpeed, avSpeedEnc.getMaxOrMaxStorableDecimal(), 0.1);
        assertEquals(10d / maxSpeed / 0.5 * 3.6, createWeighting(createSpeedCustomModel(avSpeedEnc).
                addToPriority(If("true", MULTIPLY, "0.5"))).calcMinWeightPerDistance(), 1.e-6);

        // ignore too big limit
        assertEquals(10d / maxSpeed / 1.0 * 3.6, createWeighting(createSpeedCustomModel(avSpeedEnc).
                addToPriority(If("true", LIMIT, "2.0"))).calcMinWeightPerDistance(), 1.e-6);

        // priority bigger 1 is fine (if CustomModel not in query)
        assertEquals(10d / maxSpeed / 2.0 * 3.6, createWeighting(createSpeedCustomModel(avSpeedEnc).
                addToPriority(If("true", MULTIPLY, "3.0")).
                addToPriority(If("true", LIMIT, "2.0"))).calcMinWeightPerDistance(), 1.e-6);
        assertEquals(10d / maxSpeed / 1.5 * 3.6, createWeighting(createSpeedCustomModel(avSpeedEnc).
                addToPriority(If("true", MULTIPLY, "1.5"))).calcMinWeightPerDistance(), 1.e-6);

        // pick maximum priority from value even if this is for a special case
        assertEquals(10d / maxSpeed / 3.0 * 3.6, createWeighting(createSpeedCustomModel(avSpeedEnc).
                addToPriority(If("road_class == SERVICE", MULTIPLY, "3.0"))).calcMinWeightPerDistance(), 1.e-6);

        // do NOT pick maximum priority when it is for a special case
        assertEquals(10d / maxSpeed / 1.0 * 3.6, createWeighting(createSpeedCustomModel(avSpeedEnc).
                addToPriority(If("road_class == SERVICE", MULTIPLY, "0.5"))).calcMinWeightPerDistance(), 1.e-6);
    }

    @Test
    public void maxSpeedViolated_bug_2307() {
        EdgeIteratorState motorway = graph.edge(0, 1).setDistance(10).
                set(roadClassEnc, MOTORWAY).set(avSpeedEnc, 80);
        CustomModel customModel = createSpeedCustomModel(avSpeedEnc)
                .setDistanceInfluence(70d)
                .addToSpeed(If("road_class == MOTORWAY", Statement.Op.MULTIPLY, "0.7"))
                .addToSpeed(Else(LIMIT, "30"));
        Weighting weighting = createWeighting(customModel);
        assertEquals(13, weighting.calcEdgeWeight(motorway, false));
        assertEquals(10 / (80 * 0.7 / 3.6) * 1000, weighting.calcEdgeMillis(motorway, false), 1);
    }

    @Test
    public void bugWithNaNForBarrierEdges() {
        EdgeIteratorState motorway = graph.edge(0, 1).setDistance(0).
                set(roadClassEnc, MOTORWAY).set(avSpeedEnc, 80);
        CustomModel customModel = createSpeedCustomModel(avSpeedEnc)
                .addToPriority(If("road_class == MOTORWAY", Statement.Op.MULTIPLY, "0"));
        Weighting weighting = createWeighting(customModel);
        assertFalse(Double.isNaN(weighting.calcEdgeWeight(motorway, false)));
        assertTrue(Double.isInfinite(weighting.calcEdgeWeight(motorway, false)));
    }

    @Test
    public void testMinWeightHasSameUnitAs_getWeight() {
        EdgeIteratorState edge = graph.edge(0, 1).set(avSpeedEnc, 140, 0).setDistance(1000);
        CustomModel customModel = createSpeedCustomModel(avSpeedEnc);
        Weighting weighting = createWeighting(customModel);
        assertEquals(weighting.calcMinWeightPerDistance() * 1000, weighting.calcEdgeWeight(edge, false));
    }

    @Test
    public void testWeightWrongHeading() {
        CustomModel customModel = createSpeedCustomModel(avSpeedEnc).setHeadingPenalty(100);
        Weighting weighting = createWeighting(customModel);
        EdgeIteratorState edge = graph.edge(1, 2)
                .set(avSpeedEnc, 10, 10)
                .setDistance(10).setWayGeometry(Helper.createPointList(51, 0, 51, 1));
        VirtualEdgeIteratorState virtEdge = new VirtualEdgeIteratorState(edge.getEdgeKey(), 99, 5, 6, edge.getDistance(), edge.getFlags(),
                edge.getKeyValues(), edge.fetchWayGeometry(FetchMode.PILLAR_ONLY), false);
        double time = weighting.calcEdgeWeight(virtEdge, false);

        virtEdge.setUnfavored(true);
        // heading penalty on edge
        assertEquals(time + 1000, weighting.calcEdgeWeight(virtEdge, false));
        // only after setting it
        virtEdge.setUnfavored(true);
        assertEquals(time + 1000, weighting.calcEdgeWeight(virtEdge, true));
        // but not after releasing it
        virtEdge.setUnfavored(false);
        assertEquals(time, weighting.calcEdgeWeight(virtEdge, true));

        // test default penalty
        virtEdge.setUnfavored(true);
        customModel = createSpeedCustomModel(avSpeedEnc);
        weighting = createWeighting(customModel);
        assertEquals(time + 10 * Parameters.Routing.DEFAULT_HEADING_PENALTY, weighting.calcEdgeWeight(virtEdge, false));
    }

    @Test
    public void testSpeed0() {
        EdgeIteratorState edge = graph.edge(0, 1).setDistance(10);
        CustomModel customModel = createSpeedCustomModel(avSpeedEnc);
        Weighting weighting = createWeighting(customModel);
        edge.set(avSpeedEnc, 0);
        assertEquals(1.0 / 0, weighting.calcEdgeWeight(edge, false), 1e-8);

        // 0 / 0 returns NaN but calcWeight should not return NaN!
        edge.setDistance(0);
        assertEquals(1.0 / 0, weighting.calcEdgeWeight(edge, false), 1e-8);
    }

    @Test
    public void testTime() {
        DecimalEncodedValue speedEnc = new DecimalEncodedValueImpl("speed", 4, 2, true);
        EncodingManager em = EncodingManager.start().add(speedEnc).build();
        BaseGraph g = new BaseGraph.Builder(em).create();
        EdgeIteratorState edge = g.edge(0, 1).set(speedEnc, 15, 10).setDistance(100_000);
        CustomModel customModel = createSpeedCustomModel(speedEnc);
        Weighting w = CustomModelParser.createWeighting(em, NO_TURN_COST_PROVIDER, customModel);
        assertEquals(375 * 60 * 1000, w.calcEdgeMillis(edge, false));
        assertEquals(600 * 60 * 1000, w.calcEdgeMillis(edge, true));
    }

    @Test
    public void calcWeightAndTime_withTurnCosts() {
        BaseGraph graph = new BaseGraph.Builder(encodingManager).withTurnCosts(true).create();
        CustomModel customModel = createSpeedCustomModel(avSpeedEnc);
        Weighting weighting = CustomModelParser.createWeighting(encodingManager,
                new DefaultTurnCostProvider(turnRestrictionEnc, graph, new TurnCostsConfig(), null), customModel);
        graph.edge(0, 1).set(avSpeedEnc, 60, 60).setDistance(100);
        EdgeIteratorState edge = graph.edge(1, 2).set(avSpeedEnc, 60, 60).setDistance(100);
        setTurnRestriction(graph, 0, 1, 2);
        assertTrue(Double.isInfinite(GHUtility.calcWeightWithTurnWeight(weighting, edge, false, 0)));
        // the time only reflects the time for the edge, the turn time is 0
        assertEquals(6000, GHUtility.calcMillisWithTurnMillis(weighting, edge, false, 0));
    }

    @Test
    public void calcWeightAndTime_uTurnCosts() {
        BaseGraph graph = new BaseGraph.Builder(encodingManager).withTurnCosts(true).create();
        CustomModel customModel = createSpeedCustomModel(avSpeedEnc);
        Weighting weighting = CustomModelParser.createWeighting(encodingManager,
                new DefaultTurnCostProvider(turnRestrictionEnc, graph, new TurnCostsConfig().setUTurnCosts(40), null), customModel);
        EdgeIteratorState edge = graph.edge(0, 1).set(avSpeedEnc, 60, 60).setDistance(100);
        assertEquals(60 + 400, GHUtility.calcWeightWithTurnWeight(weighting, edge, false, 0), 1.e-6);
        assertEquals(6 * 1000, GHUtility.calcMillisWithTurnMillis(weighting, edge, false, 0), 1.e-6);
    }

    @Test
    public void testDestinationTag() {
        DecimalEncodedValue carSpeedEnc = new DecimalEncodedValueImpl("car_speed", 5, 5, false);
        DecimalEncodedValue bikeSpeedEnc = new DecimalEncodedValueImpl("bike_speed", 4, 2, false);
        EncodingManager em = EncodingManager.start().add(carSpeedEnc).add(bikeSpeedEnc).add(RoadAccess.create()).build();
        BaseGraph graph = new BaseGraph.Builder(em).create();
        EdgeIteratorState edge = graph.edge(0, 1).setDistance(1000);
        edge.set(carSpeedEnc, 60);
        edge.set(bikeSpeedEnc, 18);
        EnumEncodedValue<RoadAccess> roadAccessEnc = em.getEnumEncodedValue(RoadAccess.KEY, RoadAccess.class);

        CustomModel customModel = createSpeedCustomModel(carSpeedEnc)
                .addToPriority(If("road_access == DESTINATION", MULTIPLY, ".1"));
        Weighting weighting = CustomModelParser.createWeighting(em, NO_TURN_COST_PROVIDER, customModel);

        CustomModel bikeCustomModel = createSpeedCustomModel(bikeSpeedEnc);
        Weighting bikeWeighting = CustomModelParser.createWeighting(em, NO_TURN_COST_PROVIDER, bikeCustomModel);

        edge.set(roadAccessEnc, RoadAccess.YES);
        assertEquals(600, weighting.calcEdgeWeight(edge, false));
        assertEquals(2000, bikeWeighting.calcEdgeWeight(edge, false));

        // the destination tag does not change the weight for the bike weighting
        edge.set(roadAccessEnc, RoadAccess.DESTINATION);
        assertEquals(6000, weighting.calcEdgeWeight(edge, false));
        assertEquals(2000, bikeWeighting.calcEdgeWeight(edge, false));
    }

    @Test
    public void testPrivateTag() {
        DecimalEncodedValue carSpeedEnc = new DecimalEncodedValueImpl("car_speed", 5, 5, false);
        DecimalEncodedValue bikeSpeedEnc = new DecimalEncodedValueImpl("bike_speed", 4, 2, false);
        EncodingManager em = EncodingManager.start().add(carSpeedEnc).add(bikeSpeedEnc).add(RoadAccess.create()).build();
        BaseGraph graph = new BaseGraph.Builder(em).create();
        EdgeIteratorState edge = graph.edge(0, 1).setDistance(1000);
        edge.set(carSpeedEnc, 60);
        edge.set(bikeSpeedEnc, 18);
        EnumEncodedValue<RoadAccess> roadAccessEnc = em.getEnumEncodedValue(RoadAccess.KEY, RoadAccess.class);

        CustomModel customModel = createSpeedCustomModel(carSpeedEnc)
                .addToPriority(If("road_access == PRIVATE", MULTIPLY, ".1"));
        Weighting weighting = CustomModelParser.createWeighting(em, NO_TURN_COST_PROVIDER, customModel);

        customModel = createSpeedCustomModel(bikeSpeedEnc)
                .addToPriority(If("road_access == PRIVATE", MULTIPLY, "0.8333"));
        Weighting bikeWeighting = CustomModelParser.createWeighting(em, NO_TURN_COST_PROVIDER, customModel);

        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "secondary");

        edge.set(roadAccessEnc, RoadAccess.YES);
        assertEquals(600, weighting.calcEdgeWeight(edge, false));
        assertEquals(2000, bikeWeighting.calcEdgeWeight(edge, false));

        edge.set(roadAccessEnc, RoadAccess.PRIVATE);
        assertEquals(6000, weighting.calcEdgeWeight(edge, false));
        // private should influence bike only slightly
        assertEquals(2400, bikeWeighting.calcEdgeWeight(edge, false));
    }
}
