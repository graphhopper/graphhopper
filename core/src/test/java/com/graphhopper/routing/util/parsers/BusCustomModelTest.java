package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.OSMParsers;
import com.graphhopper.routing.util.TransportationMode;
import com.graphhopper.routing.weighting.custom.CustomModelParser;
import com.graphhopper.routing.weighting.custom.CustomWeighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.util.CustomModel;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BusCustomModelTest {

    private EncodingManager em;
    private OSMParsers parsers;

    @BeforeEach
    public void setup() {
        BooleanEncodedValue busAccess = BusAccess.create();
        EnumEncodedValue<RoadClass> roadClass = RoadClass.create();
        DecimalEncodedValue maxHeight = MaxHeight.create();
        DecimalEncodedValue maxWidth = MaxWidth.create();
        DecimalEncodedValue maxWeight = MaxWeight.create();
        em = new EncodingManager.Builder().
                add(busAccess).
                add(VehicleSpeed.create("car", 5, 5, false)).
                add(Roundabout.create()).add(RoadAccess.create()).add(roadClass).
                add(maxWeight).add(maxWidth).add(maxHeight).
                build();

        parsers = new OSMParsers().
                addWayTagParser(new OSMRoadClassParser(roadClass)).
                addWayTagParser(new OSMMaxWeightParser(maxHeight)).
                addWayTagParser(new OSMMaxWidthParser(maxWidth)).
                addWayTagParser(new OSMMaxWeightParser(maxWeight)).
                addWayTagParser(new ModeAccessParser(OSMRoadAccessParser.toOSMRestrictions(TransportationMode.BUS),
                        busAccess, true, em.getBooleanEncodedValue(Roundabout.KEY),
                        Set.of(), Set.of()));
    }

    EdgeIteratorState createEdge(ReaderWay way) {
        BaseGraph graph = new BaseGraph.Builder(em).create();
        EdgeIteratorState edge = graph.edge(0, 1);
        parsers.handleWayTags(edge.getEdge(), graph.getEdgeAccess(), way, em.createRelationFlags());
        return edge;
    }

    @Test
    public void testHikePrivate() {
        CustomModel cm = GHUtility.loadCustomModelFromJar("bus.json");
        ReaderWay way = new ReaderWay(0L);
        way.setTag("highway", "steps");
        EdgeIteratorState edge = createEdge(way);
        CustomWeighting.Parameters p = CustomModelParser.createWeightingParameters(cm, em);
        assertEquals(0, p.getEdgeToPriorityMapping().get(edge, false), 0.01);

        way.setTag("highway", "busway");
        edge = createEdge(way);
        assertEquals(1, p.getEdgeToPriorityMapping().get(edge, false), 0.01);
    }
}
