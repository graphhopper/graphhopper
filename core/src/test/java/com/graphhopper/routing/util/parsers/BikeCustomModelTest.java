package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.OSMParsers;
import com.graphhopper.routing.util.PriorityCode;
import com.graphhopper.routing.weighting.custom.CustomModelParser;
import com.graphhopper.routing.weighting.custom.CustomWeighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.util.CustomModel;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.PMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BikeCustomModelTest {

    private EncodingManager em;
    private OSMParsers parsers;

    @BeforeEach
    public void setup() {
        IntEncodedValue bikeRating = MtbRating.create();
        IntEncodedValue hikeRating = HikeRating.create();
        em = new EncodingManager.Builder().
                add(VehicleAccess.create("bike")).
                add(VehicleSpeed.create("bike", 4, 2, false)).
                add(VehiclePriority.create("bike", 4, PriorityCode.getFactor(1), false)).
                add(VehicleAccess.create("mtb")).
                add(VehicleSpeed.create("mtb", 4, 2, false)).
                add(VehiclePriority.create("mtb", 4, PriorityCode.getFactor(1), false)).
                add(VehicleAccess.create("racingbike")).
                add(VehicleSpeed.create("racingbike", 4, 2, false)).
                add(VehiclePriority.create("racingbike", 4, PriorityCode.getFactor(1), false)).
                add(FerrySpeed.create()).
                add(Country.create()).
                add(RoadClass.create()).
                add(RouteNetwork.create(BikeNetwork.KEY)).
                add(Roundabout.create()).
                add(Smoothness.create()).
                add(RoadAccess.create()).
                add(BikeRoadAccess.create()).
                add(FootRoadAccess.create()).
                add(bikeRating).
                add(hikeRating).build();

        parsers = new OSMParsers().
                addWayTagParser(new OSMMtbRatingParser(bikeRating)).
                addWayTagParser(new OSMHikeRatingParser(hikeRating));

        parsers.addWayTagParser(new BikeAccessParser(em, new PMap()));
        parsers.addWayTagParser(new MountainBikeAccessParser(em, new PMap()));
        parsers.addWayTagParser(new RacingBikeAccessParser(em, new PMap()));
        parsers.addWayTagParser(new BikeAverageSpeedParser(em));
        parsers.addWayTagParser(new MountainBikeAverageSpeedParser(em));
        parsers.addWayTagParser(new RacingBikeAverageSpeedParser(em));
        parsers.addWayTagParser(new BikePriorityParser(em));
        parsers.addWayTagParser(new MountainBikePriorityParser(em));
        parsers.addWayTagParser(new RacingBikePriorityParser(em));
    }

    EdgeIteratorState createEdge(ReaderWay way) {
        BaseGraph graph = new BaseGraph.Builder(em).create();
        EdgeIteratorState edge = graph.edge(0, 1);
        EdgeIntAccess edgeIntAccess = graph.getEdgeAccess();
        parsers.handleWayTags(edge.getEdge(), edgeIntAccess, way, em.createRelationFlags());
        return edge;
    }

    @Test
    public void testCustomBike() {
        CustomModel cm = GHUtility.loadCustomModelFromJar("bike.json");
        ReaderWay way = new ReaderWay(0L);
        way.setTag("highway", "path");
        way.setTag("surface", "ground");
        EdgeIteratorState edge = createEdge(way);
        CustomWeighting.Parameters p = CustomModelParser.createWeightingParameters(cm, em);
        assertEquals(0.9, p.getEdgeToPriorityMapping().get(edge, false), 0.01);

        way.setTag("mtb:scale", "0");
        edge = createEdge(way);
        assertEquals(0.9, p.getEdgeToPriorityMapping().get(edge, false), 0.01);

        way.setTag("mtb:scale", "1+");
        edge = createEdge(way);
        assertEquals(0.9, p.getEdgeToPriorityMapping().get(edge, false), 0.01);

        way.setTag("mtb:scale", "2-");
        edge = createEdge(way);
        assertEquals(0.0, p.getEdgeToPriorityMapping().get(edge, false), 0.01);

        way.removeTag("mtb:scale");
        way.setTag("sac_scale", "hiking");
        edge = createEdge(way);
        assertEquals(0.9, p.getEdgeToPriorityMapping().get(edge, false), 0.01);
        // other scales than hiking are nearly impossible by an ordinary bike, see http://wiki.openstreetmap.org/wiki/Key:sac_scale
        way.setTag("sac_scale", "mountain_hiking");
        edge = createEdge(way);
        assertEquals(0.0, p.getEdgeToPriorityMapping().get(edge, false), 0.01);
        assertEquals(8.0, p.getEdgeToSpeedMapping().get(edge, false), 0.01);
    }

    @Test
    public void testCustomMtbBike() {
        CustomModel cm = GHUtility.loadCustomModelFromJar("mtb.json");
        ReaderWay way = new ReaderWay(0L);
        way.setTag("highway", "path");
        way.setTag("surface", "ground"); // bad surface means slow speed for mtb too
        EdgeIteratorState edge = createEdge(way);
        CustomWeighting.Parameters p = CustomModelParser.createWeightingParameters(cm, em);
        assertEquals(1.2, p.getEdgeToPriorityMapping().get(edge, false), 0.01);
        assertEquals(8.0, p.getEdgeToSpeedMapping().get(edge, false), 0.01);

        way.setTag("mtb:scale", "3");
        edge = createEdge(way);
        assertEquals(0.6, p.getEdgeToPriorityMapping().get(edge, false), 0.01);

        way.setTag("mtb:scale", "5");
        edge = createEdge(way);
        assertEquals(0.6, p.getEdgeToPriorityMapping().get(edge, false), 0.01);
        assertEquals(4.0, p.getEdgeToSpeedMapping().get(edge, false), 0.01);

        way.setTag("mtb:scale", "6");
        edge = createEdge(way);
        assertEquals(0.0, p.getEdgeToPriorityMapping().get(edge, false), 0.01);

        way.removeTag("mtb:scale");
        way.setTag("sac_scale", "hiking");
        edge = createEdge(way);
        assertEquals(1.2, p.getEdgeToPriorityMapping().get(edge, false), 0.01);

        way.setTag("sac_scale", "mountain_hiking");
        edge = createEdge(way);
        assertEquals(1.2, p.getEdgeToPriorityMapping().get(edge, false), 0.01);

        way.setTag("sac_scale", "alpine_hiking");
        edge = createEdge(way);
        assertEquals(1.2, p.getEdgeToPriorityMapping().get(edge, false), 0.01);

        way.setTag("sac_scale", "demanding_alpine_hiking");
        edge = createEdge(way);
        assertEquals(0.0, p.getEdgeToPriorityMapping().get(edge, false), 0.01);
    }

    @Test
    public void testCustomRacingBike() {
        CustomModel cm = GHUtility.loadCustomModelFromJar("racingbike.json");
        ReaderWay way = new ReaderWay(0L);
        way.setTag("highway", "path");
        EdgeIteratorState edge = createEdge(way);
        CustomWeighting.Parameters p = CustomModelParser.createWeightingParameters(cm, em);
        assertEquals(0.9, p.getEdgeToPriorityMapping().get(edge, false), 0.01);
        assertEquals(6.0, p.getEdgeToSpeedMapping().get(edge, false), 0.01);

        way.setTag("mtb:scale", "0");
        edge = createEdge(way);
        assertEquals(0.9, p.getEdgeToPriorityMapping().get(edge, false), 0.01);

        way.setTag("mtb:scale", "1");
        edge = createEdge(way);
        assertEquals(0.45, p.getEdgeToPriorityMapping().get(edge, false), 0.01);

        way.setTag("mtb:scale", "2");
        edge = createEdge(way);
        assertEquals(0.0, p.getEdgeToPriorityMapping().get(edge, false), 0.01);

        way.removeTag("mtb:scale");
        way.setTag("sac_scale", "hiking");
        edge = createEdge(way);
        assertEquals(0.9, p.getEdgeToPriorityMapping().get(edge, false), 0.01);
        way.setTag("sac_scale", "mountain_hiking");
        edge = createEdge(way);
        assertEquals(0.0, p.getEdgeToPriorityMapping().get(edge, false), 0.01);
    }
}
