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

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TruckCustomModelTest {

    private EncodingManager em;
    private OSMParsers parsers;
    private CustomWeighting.Config params;

    @BeforeEach
    public void setup() {
        BooleanEncodedValue hgvAccess = HgvAccess.create();
        EnumEncodedValue<Hgv> hgv = Hgv.create();
        DecimalEncodedValue maxHeight = MaxHeight.create();
        DecimalEncodedValue maxWidth = MaxWidth.create();
        DecimalEncodedValue maxWeight = MaxWeight.create();
        EnumEncodedValue<MaxWeightExcept> maxWeightExcept = MaxWeightExcept.create();
        em = new EncodingManager.Builder().
                add(hgvAccess).add(hgv).
                add(VehicleSpeed.create("car", 5, 5, false)).
                add(RoadEnvironment.create()).
                add(Roundabout.create()).add(FerrySpeed.create()).
                add(maxWeight).add(maxWeightExcept).add(maxWidth).add(maxHeight).add(MaxSpeed.create()).
                build();

        parsers = new OSMParsers().
                addWayTagParser(new OSMHgvParser(hgv)).
                addWayTagParser(new OSMMaxHeightParser(maxHeight)).
                addWayTagParser(new OSMMaxWidthParser(maxWidth)).
                addWayTagParser(new OSMMaxWeightParser(maxWeight)).
                addWayTagParser(new MaxWeightExceptParser(maxWeightExcept)).
                addWayTagParser(new ModeAccessParser(OSMRoadAccessParser.toOSMRestrictions(TransportationMode.HGV),
                        hgvAccess, true, em.getBooleanEncodedValue(Roundabout.KEY),
                        Set.of("delivery", "private"), Set.of()));

        CustomModel cm = GHUtility.loadCustomModelFromJar("truck.json");
        params = CustomModelParser.createWeightingConfig(cm, em);
    }

    double priority(ReaderWay way) {
        BaseGraph graph = new BaseGraph.Builder(em).create();
        EdgeIteratorState edge = graph.edge(0, 1);
        parsers.handleWayTags(edge.getEdge(), graph.getEdgeAccess(), way, em.createRelationFlags());
        return params.getEdgeToPriorityMapping().get(edge, false);
    }

    ReaderWay createWay(String highway, String... tags) {
        ReaderWay way = new ReaderWay(0L);
        way.setTag("highway", highway);
        for (int i = 0; i < tags.length; i += 2)
            way.setTag(tags[i], tags[i + 1]);
        return way;
    }

    @Test
    public void testHighwayTypes() {
        for (String highway : List.of("primary", "secondary", "tertiary", "residential", "unclassified", "trunk", "motorway"))
            assertEquals(1, priority(createWay(highway)), 0.01, highway + " should be accessible");

        for (String highway : List.of("steps", "footway", "cycleway", "pedestrian", "path", "bridleway"))
            assertEquals(0, priority(createWay(highway)), 0.01, highway + " should be blocked");
    }

    @Test
    public void testAccessRestrictions() {
        assertEquals(0, priority(createWay("primary", "hgv", "no")), 0.01);
        assertEquals(0, priority(createWay("residential", "motor_vehicle", "no")), 0.01);
        // hgv=yes overrules the more generic motor_vehicle=no
        assertEquals(1, priority(createWay("residential", "motor_vehicle", "no", "hgv", "yes")), 0.01);
    }

    @Test
    public void testDestinationAndPrivate() {
        assertEquals(0.1, priority(createWay("residential", "hgv", "destination")), 0.01);
        assertEquals(0.1, priority(createWay("residential", "hgv", "delivery")), 0.01);
        // deliveries to private premises must remain possible, see the allow=private option
        assertEquals(1, priority(createWay("residential", "access", "private")), 0.01);
    }

    @Test
    public void testDimensionAndWeightLimits() {
        assertEquals(0, priority(createWay("primary", "maxheight", "3.5")), 0.01);
        assertEquals(0, priority(createWay("primary", "maxweight", "10")), 0.01);
        // but not if there is an exception for delivery
        assertEquals(1, priority(createWay("primary", "maxweight", "10", "maxweight:conditional", "none @ delivery")), 0.01);
    }

    @Test
    public void testBarriers() {
        ReaderWay way = createWay("residential");
        way.setTag("gh:barrier_edge", true);
        way.setTag("node_tags", List.of(Map.of("barrier", "bollard"), Map.of()));
        assertEquals(0, priority(way), 0.01);
    }
}
