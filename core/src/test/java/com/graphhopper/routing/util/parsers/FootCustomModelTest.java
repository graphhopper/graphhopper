package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.OSMParsers;
import com.graphhopper.routing.util.PriorityCode;
import com.graphhopper.routing.weighting.custom.CustomModelParser;
import com.graphhopper.routing.weighting.custom.CustomWeighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.PMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FootCustomModelTest {

    @Test
    public void testSidewalk() {
        EncodingManager em = new EncodingManager.Builder().
                add(VehicleAccess.create("foot")).
                add(VehicleSpeed.create("foot", 4, 1, false)).
                add(VehiclePriority.create("foot", 4, PriorityCode.getFactor(1), false)).
                add(FootRoadAccess.create()).add(Sidewalk.create()).add(HikeRating.create()).add(MtbRating.create()).
                add(RoadEnvironment.create()).add(FerrySpeed.create()).build();
        OSMParsers parsers = new OSMParsers().
                addWayTagParser(new FootAccessParser(em, new PMap())).
                addWayTagParser(new FootPriorityParser(em)).
                addWayTagParser(new OSMSidewalkParser(em.getEnumEncodedValue(Sidewalk.KEY, Sidewalk.class)));
        CustomWeighting.Config p = CustomModelParser.createWeightingConfig(GHUtility.loadCustomModelFromJar("foot.json"), em);
        BaseGraph graph = new BaseGraph.Builder(em).create();

        ReaderWay way = new ReaderWay(0L);
        way.setTag("highway", "primary");
        EdgeIteratorState edge = graph.edge(0, 1);
        parsers.handleWayTags(edge.getEdge(), graph.getEdgeAccess(), way, em.createRelationFlags());
        assertEquals(0.8, p.getEdgeToPriorityMapping().get(edge, false), 0.01);

        // a sidewalk on one side counts for both directions
        way.setTag("sidewalk", "left");
        edge = graph.edge(1, 2);
        parsers.handleWayTags(edge.getEdge(), graph.getEdgeAccess(), way, em.createRelationFlags());
        assertEquals(0.88, p.getEdgeToPriorityMapping().get(edge, false), 0.01);
        assertEquals(0.88, p.getEdgeToPriorityMapping().get(edge, true), 0.01);
    }
}
