package com.graphhopper.routing.weighting.custom;

import com.graphhopper.config.Profile;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.DefaultWeightingFactory;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.parsers.OrientationCalculator;
import com.graphhopper.routing.weighting.BikeClimbSpeedTable;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.GHPoint3D;
import com.graphhopper.util.shapes.Polygon;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static com.graphhopper.json.Statement.*;
import static com.graphhopper.json.Statement.Op.*;
import static org.junit.jupiter.api.Assertions.*;

class CustomWeightingHelperTest {

    @Test
    public void testInRectangle() {
        Polygon square = new Polygon(new double[]{0, 0, 20, 20}, new double[]{0, 20, 20, 0});
        assertTrue(square.isRectangle());

        BaseGraph g = new BaseGraph.Builder(1).create();

        // (1,1) (2,2) (3,3)
        // Polygon fully contains the edge and its BBox
        g.getNodeAccess().setNode(0, 1, 1);
        g.getNodeAccess().setNode(1, 3, 3);
        EdgeIteratorState edge = g.edge(0, 1).setWayGeometry(Helper.createPointList(2, 2));
        assertTrue(CustomWeightingHelper.in(square, edge));

        // (0,0) (20,0) (20,20)
        // Polygon contains the edge; BBoxes overlap
        g.getNodeAccess().setNode(2, 0, 0);
        g.getNodeAccess().setNode(3, 20, 20);
        edge = g.edge(2, 3).setWayGeometry(Helper.createPointList(20, 0));
        assertTrue(CustomWeightingHelper.in(square, edge));

        // (0,30) (10,40) (20,50)
        // Edge is outside the polygon; BBoxes are not intersecting
        g.getNodeAccess().setNode(4, 0, 30);
        g.getNodeAccess().setNode(5, 20, 50);
        edge = g.edge(4, 5).setWayGeometry(Helper.createPointList(10, 40));
        assertFalse(CustomWeightingHelper.in(square, edge));

        // (0,30) (30,30) (30,0)
        // Edge is outside the polygon; BBoxes are intersecting
        g.getNodeAccess().setNode(6, 0, 30);
        g.getNodeAccess().setNode(7, 30, 0);
        edge = g.edge(6, 7).setWayGeometry(Helper.createPointList(30, 30));
        assertFalse(CustomWeightingHelper.in(square, edge));
    }

    @Test
    public void testBikeClimbFactor() {
        // 120W, 95kg and a drag area of 0.6m^2: the flat speed is 22.14km/h and the static variant returns the
        // factor relative to it, i.e. the speed at 12% is 22.14 * factor = 3.67km/h
        double flat = new BikeClimbSpeedTable(120, 95, 0.6, 0.006).getFlatSpeed();
        assertEquals(22.14, flat, 0.01);
        assertEquals(3.67, flat * CustomWeightingHelper.bike_climb_factor(12, 120, 95, 0.6, 0.006), 0.01);
        // where riding gets slower than walking the cyclist pushes the bike instead
        // (0.8 times Tobler's hiking speed)
        assertEquals(2.38, flat * CustomWeightingHelper.bike_climb_factor(15, 120, 95, 0.6, 0.006), 0.01);
        assertEquals(1.36, flat * CustomWeightingHelper.bike_climb_factor(31, 120, 95, 0.6, 0.006), 0.01);
        assertEquals(1, CustomWeightingHelper.bike_climb_factor(0, 120, 95, 0.6, 0.006));
        // faster for downhill slopes: 38.8km/h at -4%
        assertEquals(1.75, CustomWeightingHelper.bike_climb_factor(-4, 120, 95, 0.6, 0.006), 0.01);
        assertEquals(2.61, CustomWeightingHelper.bike_climb_factor(-10, 120, 95, 0.6, 0.006), 0.01);

        assertThrows(IllegalArgumentException.class, () -> CustomWeightingHelper.bike_climb_factor(12, 0, 95, 0.6, 0.006));
        assertThrows(IllegalArgumentException.class, () -> CustomWeightingHelper.bike_climb_factor(12, 120, 0, 0.6, 0.006));
        assertThrows(IllegalArgumentException.class, () -> CustomWeightingHelper.bike_climb_factor(12, 120, 95, 0, 0.006));
    }

    @Test
    public void testBikeClimbFactorCurrentSpeed() {
        // the generated code calls the table method with the injected current speed: for a current
        // speed reduced from the flat speed 22.14 (e.g. from a rough surface) the rolling resistance is
        // increased (2.5 times for 9km/h) but the power-limited climbing speed is not scaled down
        // proportionally, i.e. 9*factor is 3.32km/h and not 9/22.14*3.67=1.49km/h
        BikeClimbSpeedTable table = new BikeClimbSpeedTable(120, 95, 0.6, 0.006);
        assertEquals(3.32, 9 * table.getClimbFactor(12, 9), 0.01);
        // the factor never increases the speed above the current speed
        assertEquals(1, table.getClimbFactor(2, 3));
        // a current speed above the flat speed results in the same absolute climbing speed
        assertEquals(3.67, 25 * table.getClimbFactor(12, 25), 0.01);
        // the static variant used for the bounds is the factor at the flat speed
        assertEquals(table.getClimbFactor(12, table.getFlatSpeed()), CustomWeightingHelper.bike_climb_factor(12, 120, 95, 0.6, 0.006), 1.e-6);
    }

    @Test
    public void testNegativeMax() {
        CustomModel customModel = new CustomModel();
        customModel.addToSpeed(If("true", LIMIT, VehicleSpeed.key("car")));
        customModel.addToSpeed(If("road_class == PRIMARY", MULTIPLY, "0.5"));
        customModel.addToSpeed(Else(MULTIPLY, "-0.5"));

        CustomWeightingHelper helper = new CustomWeightingHelper();
        EncodingManager lookup = new EncodingManager.Builder().add(VehicleSpeed.create("car", 5, 5, true)).build();
        helper.init(customModel, lookup, null);
        IllegalArgumentException ret = assertThrows(IllegalArgumentException.class, helper::calcMaxSpeed);
        assertTrue(ret.getMessage().startsWith("statement resulted in negative value"));
    }

    @Test
    public void testCalcChangeAngle() {
        EncodingManager encodingManager = new EncodingManager.Builder().add(Orientation.create()).build();
        DecimalEncodedValue orientationEnc = encodingManager.getDecimalEncodedValue(Orientation.KEY);
        OrientationCalculator calc = new OrientationCalculator(orientationEnc);
        BaseGraph graph = new BaseGraph.Builder(encodingManager).withTurnCosts(true).create();
        graph.getNodeAccess().setNode(1, 0.030, 0.011);
        graph.getNodeAccess().setNode(2, 0.020, 0.009);
        graph.getNodeAccess().setNode(3, 0.010, 0.000);
        graph.getNodeAccess().setNode(4, 0.000, 0.008);

        EdgeIntAccess edgeIntAccess = graph.getEdgeAccess();
        //      1
        //      |
        //   /--2
        //   3-/|
        //      4
        EdgeIteratorState edge12 = handleWayTags(edgeIntAccess, calc, graph.edge(1, 2), List.of());
        EdgeIteratorState edge24 = handleWayTags(edgeIntAccess, calc, graph.edge(2, 4), List.of());
        EdgeIteratorState edge23 = handleWayTags(edgeIntAccess, calc, graph.edge(2, 3), Arrays.asList(0.020, 0.002));
        EdgeIteratorState edge23down = handleWayTags(edgeIntAccess, calc, graph.edge(2, 3), Arrays.asList(0.010, 0.005));

        assertEquals(-12, calcChangeAngle(graph, edgeIntAccess, orientationEnc, edge12.getEdge(), 2, edge24.getEdge()), 1);
        assertEquals(-12, calcChangeAngle(graph, edgeIntAccess, orientationEnc, edge23down.getEdge(), 2, edge12.getEdge()), 1);

        // left
        assertEquals(-84, calcChangeAngle(graph, edgeIntAccess, orientationEnc, edge24.getEdge(), 2, edge23.getEdge()), 1);
        assertEquals(-84, calcChangeAngle(graph, edgeIntAccess, orientationEnc, edge23.getEdge(), 2, edge12.getEdge()), 1);

        // right
        assertEquals(96, calcChangeAngle(graph, edgeIntAccess, orientationEnc, edge23down.getEdge(), 3, edge23.getEdge()), 1);
        assertEquals(84, calcChangeAngle(graph, edgeIntAccess, orientationEnc, edge12.getEdge(), 2, edge23.getEdge()), 1);
    }

    public static double calcChangeAngle(BaseGraph graph, EdgeIntAccess edgeIntAccess, DecimalEncodedValue orientationEnc,
                                         int inEdge, int viaNode, int outEdge) {
        boolean inEdgeReverse = !graph.isAdjNode(inEdge, viaNode);
        boolean outEdgeReverse = graph.isAdjNode(outEdge, viaNode);
        return CustomWeightingHelper.calcChangeAngle(edgeIntAccess, orientationEnc, inEdge, inEdgeReverse, outEdge, !outEdgeReverse);
    }

    @Test
    public void testCalcTurnWeight() {
        BooleanEncodedValue accessEnc = VehicleAccess.create("car");
        DecimalEncodedValue avgSpeedEnc = VehicleSpeed.create("car", 5, 5, true);
        DecimalEncodedValue orientEnc = Orientation.create();
        EncodingManager em = new EncodingManager.Builder().add(accessEnc).add(avgSpeedEnc).
                add(orientEnc).addTurnCostEncodedValue(TurnRestriction.create("car")).build();
        BaseGraph graph = new BaseGraph.Builder(em).withTurnCosts(true).create();

        //       4   5
        //   0 - 1 - 2
        //        3  6

        graph.getNodeAccess().setNode(0, 51.0362, 13.714);
        graph.getNodeAccess().setNode(1, 51.0362, 13.720);
        graph.getNodeAccess().setNode(2, 51.0362, 13.726);
        graph.getNodeAccess().setNode(3, 51.0358, 13.7205);
        graph.getNodeAccess().setNode(4, 51.0366, 13.720);
        graph.getNodeAccess().setNode(5, 51.0366, 13.726);
        graph.getNodeAccess().setNode(6, 51.0358, 13.726);

        CustomModel customModel = new CustomModel();
        customModel.addToSpeed(If("true", LIMIT, "100"));
        customModel.addToTurnPenalty(If("change_angle > -25 && change_angle < 25", ADD, "0")); // straight
        customModel.addToTurnPenalty(ElseIf("change_angle >= 25 && change_angle < 80", ADD, "0.5")); // right
        customModel.addToTurnPenalty(ElseIf("change_angle >= 80 && change_angle <= 180", ADD, "1")); // sharp right
        customModel.addToTurnPenalty(ElseIf("change_angle <= -25 && change_angle > -80", ADD, "6")); // left
        customModel.addToTurnPenalty(ElseIf("change_angle <= -80 && change_angle >= -180", ADD, "12")); // sharp left
        customModel.addToTurnPenalty(Else(ADD, "Infinity")); // uTurn

        Profile profile = new Profile("car");
        profile.setTurnCostsConfig(new TurnCostsConfig());
        profile.setCustomModel(customModel);

        Weighting weighting = new DefaultWeightingFactory(graph, em).createWeighting(profile, new PMap(), false);
        OrientationCalculator calc = new OrientationCalculator(orientEnc);
        EdgeIntAccess edgeIntAccess = graph.getEdgeAccess();
        EdgeIteratorState edge01 = handleWayTags(edgeIntAccess, calc, graph.edge(0, 1).setDistance(500).set(avgSpeedEnc, 15).set(accessEnc, true, true), List.of());
        EdgeIteratorState edge13 = handleWayTags(edgeIntAccess, calc, graph.edge(1, 3).setDistance(500).set(avgSpeedEnc, 15).set(accessEnc, true, true), List.of());
        EdgeIteratorState edge14 = handleWayTags(edgeIntAccess, calc, graph.edge(1, 4).setDistance(500).set(avgSpeedEnc, 15).set(accessEnc, true, true), List.of());
        EdgeIteratorState edge26 = handleWayTags(edgeIntAccess, calc, graph.edge(2, 6).setDistance(500).set(avgSpeedEnc, 15).set(accessEnc, true, true), List.of());
        EdgeIteratorState edge25 = handleWayTags(edgeIntAccess, calc, graph.edge(2, 5).setDistance(500).set(avgSpeedEnc, 15).set(accessEnc, true, true), List.of());
        EdgeIteratorState edge12 = handleWayTags(edgeIntAccess, calc, graph.edge(1, 2).setDistance(500).set(avgSpeedEnc, 15).set(accessEnc, true, true), List.of());

        // from top to left => sharp right turn
        assertEquals(10, weighting.calcTurnWeight(edge14.getEdge(), 1, edge01.getEdge()));
        // left to right => straight
        assertEquals(0.0, weighting.calcTurnWeight(edge01.getEdge(), 1, edge12.getEdge()));
        // top to right => sharp left turn
        assertEquals(120, weighting.calcTurnWeight(edge14.getEdge(), 1, edge12.getEdge()));
        // left to down => right turn
        assertEquals(5, weighting.calcTurnWeight(edge01.getEdge(), 1, edge13.getEdge()));
        // bottom to left => left turn
        assertEquals(60, weighting.calcTurnWeight(edge13.getEdge(), 1, edge01.getEdge()));

        // left to top => sharp left turn => here like 'straight'
        assertEquals(120, weighting.calcTurnWeight(edge12.getEdge(), 2, edge25.getEdge()));
        // down to left => sharp left turn => here again like 'straight'
        assertEquals(120, weighting.calcTurnWeight(edge26.getEdge(), 2, edge12.getEdge()));
        // top to left => sharp right turn
        assertEquals(10, weighting.calcTurnWeight(edge25.getEdge(), 2, edge12.getEdge()));
    }

    @Test
    public void testCalcChangeAngleBarrierEdge() {
        // a "barrier" edge has two endpoints at exactly the same lat/lon (see
        // WaySegmentParser#splitSegmentAtSplitNodes). The change angle when entering or leaving
        // such an edge on an otherwise straight road must still be ~0, not a U-turn.
        EncodingManager encodingManager = new EncodingManager.Builder().add(Orientation.create()).build();
        DecimalEncodedValue orientationEnc = encodingManager.getDecimalEncodedValue(Orientation.KEY);
        OrientationCalculator calc = new OrientationCalculator(orientationEnc);
        BaseGraph graph = new BaseGraph.Builder(encodingManager).withTurnCosts(true).create();

        // straight road with a barrier node in the middle that is duplicated (nodes 2 and 3
        // share the same coordinates) — matches the example reported at
        // https://graphhopper.com/maps/?point=52.527237%2C5.432981&point=52.527133%2C5.435938
        double prevLat = 52.527237, prevLon = 5.432981;
        double barrierLat = 52.527185, barrierLon = 5.434460;
        double nextLat = 52.527133, nextLon = 5.435938;
        graph.getNodeAccess().setNode(1, prevLat, prevLon);
        graph.getNodeAccess().setNode(2, barrierLat, barrierLon);
        graph.getNodeAccess().setNode(3, barrierLat, barrierLon);
        graph.getNodeAccess().setNode(4, nextLat, nextLon);

        EdgeIntAccess edgeIntAccess = graph.getEdgeAccess();
        EdgeIteratorState edge12 = handleWayTags(edgeIntAccess, calc, graph.edge(1, 2), List.of());
        // simulate the transient tags that WaySegmentParser attaches for barrier edges
        ReaderWay barrierWay = new ReaderWay(2);
        barrierWay.setTag("gh:barrier_prev_point", new GHPoint3D(prevLat, prevLon, Double.NaN));
        barrierWay.setTag("gh:barrier_next_point", new GHPoint3D(nextLat, nextLon, Double.NaN));
        EdgeIteratorState edge23 = graph.edge(2, 3);
        barrierWay.setTag("point_list", edge23.fetchWayGeometry(FetchMode.ALL));
        calc.handleWayTags(edge23.getEdge(), edgeIntAccess, barrierWay, null);
        EdgeIteratorState edge34 = handleWayTags(edgeIntAccess, calc, graph.edge(3, 4), List.of());

        // entering the barrier from the west: change angle should be ~0 (straight)
        assertEquals(0, calcChangeAngle(graph, edgeIntAccess, orientationEnc, edge12.getEdge(), 2, edge23.getEdge()), 1);
        // leaving the barrier to the east: change angle should be ~0 (straight)
        assertEquals(0, calcChangeAngle(graph, edgeIntAccess, orientationEnc, edge23.getEdge(), 3, edge34.getEdge()), 1);

        // same the other way round
        assertEquals(0, calcChangeAngle(graph, edgeIntAccess, orientationEnc, edge34.getEdge(), 3, edge23.getEdge()), 1);
        assertEquals(0, calcChangeAngle(graph, edgeIntAccess, orientationEnc, edge23.getEdge(), 2, edge12.getEdge()), 1);
    }

    EdgeIteratorState handleWayTags(EdgeIntAccess edgeIntAccess, OrientationCalculator calc, EdgeIteratorState edge, List<Double> rawPointList) {
        if (rawPointList.size() % 2 != 0) throw new IllegalArgumentException();
        if (!rawPointList.isEmpty()) {
            PointList list = new PointList();
            for (int i = 0; i < rawPointList.size(); i += 2) {
                list.add(rawPointList.get(0), rawPointList.get(1));
            }
            edge.setWayGeometry(list);
        }

        ReaderWay way = new ReaderWay(1);
        way.setTag("point_list", edge.fetchWayGeometry(FetchMode.ALL));
        calc.handleWayTags(edge.getEdge(), edgeIntAccess, way, null);
        return edge;
    }
}
