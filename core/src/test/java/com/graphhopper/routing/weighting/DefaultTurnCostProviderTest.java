package com.graphhopper.routing.weighting;

import com.graphhopper.config.Profile;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.DefaultWeightingFactory;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.parsers.OrientationCalculator;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.util.*;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static com.graphhopper.json.Statement.If;
import static com.graphhopper.json.Statement.Op.LIMIT;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class DefaultTurnCostProviderTest {

    @Test
    public void testRawTurnWeight() {
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
        EdgeIteratorState edge12 = handleWayTags(edgeIntAccess, calc, graph.edge(1, 2));
        EdgeIteratorState edge24 = handleWayTags(edgeIntAccess, calc, graph.edge(2, 4));
        EdgeIteratorState edge23 = handleWayTags(edgeIntAccess, calc, graph.edge(2, 3), Arrays.asList(0.020, 0.002));
        EdgeIteratorState edge23down = handleWayTags(edgeIntAccess, calc, graph.edge(2, 3), Arrays.asList(0.010, 0.005));

        TurnCostsConfig tcConfig = new TurnCostsConfig();
        DefaultTurnCostProvider tcp = new DefaultTurnCostProvider(null, orientationEnc, graph, tcConfig);
        assertEquals(-12, tcp.calcChangeAngle(edge12.getEdge(), 2, edge24.getEdge()), 1);
        assertEquals(-12, tcp.calcChangeAngle(edge23down.getEdge(), 2, edge12.getEdge()), 1);

        // left
        assertEquals(-84, tcp.calcChangeAngle(edge24.getEdge(), 2, edge23.getEdge()), 1);
        assertEquals(-84, tcp.calcChangeAngle(edge23.getEdge(), 2, edge12.getEdge()), 1);

        // right
        assertEquals(96, tcp.calcChangeAngle(edge23down.getEdge(), 3, edge23.getEdge()), 1);
        assertEquals(84, tcp.calcChangeAngle(edge12.getEdge(), 2, edge23.getEdge()), 1);
    }

    @Test
    public void testCalcTurnWeight() {
        BooleanEncodedValue tcAccessEnc = VehicleAccess.create("car");
        DecimalEncodedValue tcAvgSpeedEnc = VehicleSpeed.create("car", 5, 5, true);
        DecimalEncodedValue orientEnc = Orientation.create();
        EncodingManager em = new EncodingManager.Builder().add(tcAccessEnc).add(tcAvgSpeedEnc).
                add(orientEnc).addTurnCostEncodedValue(TurnRestriction.create("car")).build();
        BaseGraph turnGraph = new BaseGraph.Builder(em).withTurnCosts(true).create();

        //       4   5
        //   0 - 1 - 2
        //        3  6

        turnGraph.getNodeAccess().setNode(0, 51.0362, 13.714);
        turnGraph.getNodeAccess().setNode(1, 51.0362, 13.720);
        turnGraph.getNodeAccess().setNode(2, 51.0362, 13.726);
        turnGraph.getNodeAccess().setNode(3, 51.0358, 13.7205);
        turnGraph.getNodeAccess().setNode(4, 51.0366, 13.720);
        turnGraph.getNodeAccess().setNode(5, 51.0366, 13.726);
        turnGraph.getNodeAccess().setNode(6, 51.0358, 13.726);

        Profile profile = new Profile("car");
        TurnCostsConfig config = new TurnCostsConfig().
                setRightTurnCosts(0.5).setSharpRightTurnCosts(1).
                setLeftTurnCosts(6).setSharpLeftTurnCosts(12);
        profile.setCustomModel(new CustomModel().addToSpeed(If("true", LIMIT, tcAvgSpeedEnc.getName())));
        profile.setTurnCostsConfig(config);
        Weighting weighting = new DefaultWeightingFactory(turnGraph, em).createWeighting(profile, new PMap(), false);
        OrientationCalculator calc = new OrientationCalculator(orientEnc);
        EdgeIntAccess edgeIntAccess = turnGraph.getEdgeAccess();
        EdgeIteratorState edge01 = handleWayTags(edgeIntAccess, calc, turnGraph.edge(0, 1).setDistance(500).set(tcAvgSpeedEnc, 15).set(tcAccessEnc, true, true));
        EdgeIteratorState edge13 = handleWayTags(edgeIntAccess, calc, turnGraph.edge(1, 3).setDistance(500).set(tcAvgSpeedEnc, 15).set(tcAccessEnc, true, true));
        EdgeIteratorState edge14 = handleWayTags(edgeIntAccess, calc, turnGraph.edge(1, 4).setDistance(500).set(tcAvgSpeedEnc, 15).set(tcAccessEnc, true, true));
        EdgeIteratorState edge26 = handleWayTags(edgeIntAccess, calc, turnGraph.edge(2, 6).setDistance(500).set(tcAvgSpeedEnc, 15).set(tcAccessEnc, true, true));
        EdgeIteratorState edge25 = handleWayTags(edgeIntAccess, calc, turnGraph.edge(2, 5).setDistance(500).set(tcAvgSpeedEnc, 15).set(tcAccessEnc, true, true));
        EdgeIteratorState edge12 = handleWayTags(edgeIntAccess, calc, turnGraph.edge(1, 2).setDistance(500).set(tcAvgSpeedEnc, 15).set(tcAccessEnc, true, true));

        // from top to left => sharp right turn
        assertEquals(1, weighting.calcTurnWeight(edge14.getEdge(), 1, edge01.getEdge()), 0.01);
        // left to right => straight
        assertEquals(0.0, weighting.calcTurnWeight(edge01.getEdge(), 1, edge12.getEdge()), 0.01);
        // top to right => sharp left turn
        assertEquals(12, weighting.calcTurnWeight(edge14.getEdge(), 1, edge12.getEdge()), 0.01);
        // left to down => right turn
        assertEquals(0.5, weighting.calcTurnWeight(edge01.getEdge(), 1, edge13.getEdge()), 0.01);
        // bottom to left => left turn
        assertEquals(6, weighting.calcTurnWeight(edge13.getEdge(), 1, edge01.getEdge()), 0.01);

        // left to top => sharp left turn => here like 'straight'
        assertEquals(12, weighting.calcTurnWeight(edge12.getEdge(), 2, edge25.getEdge()), 0.01);
        // down to left => sharp left turn => here again like 'straight'
        assertEquals(12, weighting.calcTurnWeight(edge26.getEdge(), 2, edge12.getEdge()), 0.01);
        // top to left => sharp right turn
        assertEquals(1, weighting.calcTurnWeight(edge25.getEdge(), 2, edge12.getEdge()), 0.01);
    }

    EdgeIteratorState handleWayTags(EdgeIntAccess edgeIntAccess, OrientationCalculator calc, EdgeIteratorState edge) {
        return handleWayTags(edgeIntAccess, calc, edge, List.of());
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
