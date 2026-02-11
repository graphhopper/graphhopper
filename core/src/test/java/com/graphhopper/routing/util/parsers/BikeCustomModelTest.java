package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderRelation;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.OSMParsers;
import com.graphhopper.routing.util.PriorityCode;
import com.graphhopper.routing.util.TransportationMode;
import com.graphhopper.routing.weighting.custom.CustomModelParser;
import com.graphhopper.routing.weighting.custom.CustomWeighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.IntsRef;
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
        EnumEncodedValue<BikeRoadAccess> bikeRA = BikeRoadAccess.create();
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
                add(RoadEnvironment.create()).
                add(RouteNetwork.create(BikeNetwork.KEY)).
                add(RouteNetwork.create(MtbNetwork.KEY)).
                add(Roundabout.create()).
                add(Smoothness.create()).
                add(RoadAccess.create()).
                add(bikeRA).
                add(FootRoadAccess.create()).
                add(bikeRating).
                add(hikeRating).build();

        parsers = new OSMParsers().
                addWayTagParser(new OSMMtbRatingParser(bikeRating)).
                addWayTagParser(new OSMHikeRatingParser(hikeRating)).
                addWayTagParser(new BikeAccessParser(em, new PMap())).
                addWayTagParser(new MountainBikeAccessParser(em, new PMap())).
                addWayTagParser(new RacingBikeAccessParser(em, new PMap())).
                addWayTagParser(new BikeAverageSpeedParser(em)).
                addWayTagParser(new MountainBikeAverageSpeedParser(em)).
                addWayTagParser(new RacingBikeAverageSpeedParser(em)).
                addWayTagParser(new BikePriorityParser(em)).
                addWayTagParser(new MountainBikePriorityParser(em)).
                addWayTagParser(new RacingBikePriorityParser(em)).
                addWayTagParser(OSMRoadAccessParser.forBike(bikeRA));

        parsers.addRelationTagParser(relConfig -> new OSMBikeNetworkTagParser(em.getEnumEncodedValue(BikeNetwork.KEY, RouteNetwork.class), relConfig, "bicycle")).
                addRelationTagParser(relConfig -> new OSMBikeNetworkTagParser(em.getEnumEncodedValue(MtbNetwork.KEY, RouteNetwork.class), relConfig, "mtb"));
    }

    EdgeIteratorState createEdge(ReaderWay way, ReaderRelation... readerRelation) {
        BaseGraph graph = new BaseGraph.Builder(em).create();
        EdgeIteratorState edge = graph.edge(0, 1);
        EdgeIntAccess edgeIntAccess = graph.getEdgeAccess();
        IntsRef rel = em.createRelationFlags();
        if (readerRelation.length == 1)
            parsers.handleRelationTags(readerRelation[0], rel);
        parsers.handleWayTags(edge.getEdge(), edgeIntAccess, way, rel);
        return edge;
    }

    @Test
    public void testCustomBike() {
        CustomModel baseCM = GHUtility.loadCustomModelFromJar("bike.json");
        CustomModel bikeAvoidPrivate = GHUtility.loadCustomModelFromJar("bike_avoid_private_node.json");
        CustomModel cm = CustomModel.merge(baseCM, bikeAvoidPrivate);
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

        way.clearTags();
        way.setTag("highway", "tertiary");
        way.setTag("vehicle", "private");
        edge = createEdge(way);
        assertEquals(0.1, p.getEdgeToPriorityMapping().get(edge, false), 0.01);
    }

    @Test
    public void testCountryAccessDefault() {
        CustomModel cm = GHUtility.loadCustomModelFromJar("bike.json");
        ReaderWay way = new ReaderWay(0L);
        way.setTag("highway", "bridleway");
        EdgeIteratorState edge = createEdge(way);
        CustomWeighting.Parameters p = CustomModelParser.createWeightingParameters(cm, em);
        assertEquals(0.8, p.getEdgeToPriorityMapping().get(edge, false), 0.01);

        way.setTag("country", Country.DEU);
        edge = createEdge(way);
        p = CustomModelParser.createWeightingParameters(cm, em);
        assertEquals(0, p.getEdgeToPriorityMapping().get(edge, false), 0.01);

        way.setTag("bicycle", "yes");
        edge = createEdge(way);
        p = CustomModelParser.createWeightingParameters(cm, em);
        assertEquals(0.8, p.getEdgeToPriorityMapping().get(edge, false), 0.01);

        way = new ReaderWay(0L);
        way.setTag("highway", "trunk_link");
        way.setTag("country", Country.CHE);
        edge = createEdge(way);
        p = CustomModelParser.createWeightingParameters(cm, em);
        assertEquals(0, p.getEdgeToPriorityMapping().get(edge, false), 0.01);
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
        assertEquals(10.0, p.getEdgeToSpeedMapping().get(edge, false), 0.01);

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

    @Test
    public void testCalcPriority() {
        CustomModel cm = GHUtility.loadCustomModelFromJar("bike.json");
        CustomWeighting.Parameters p = CustomModelParser.createWeightingParameters(cm, em);

        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "tertiary");

        EdgeIteratorState edge = createEdge(way);
        assertEquals(1, p.getEdgeToPriorityMapping().get(edge, false), 0.01);

        ReaderRelation osmRel = new ReaderRelation(1);
        osmRel.setTag("route", "bicycle");
        osmRel.setTag("network", "icn");

        edge = createEdge(way, osmRel);
        assertEquals(1.7, p.getEdgeToPriorityMapping().get(edge, false), 0.01);

        // unknown highway tags will be excluded
        way = new ReaderWay(1);
        way.setTag("highway", "whatever");
        edge = createEdge(way, osmRel);
        assertEquals(0, p.getEdgeToPriorityMapping().get(edge, false), 0.01);
    }

    @Test
    public void testHandleWayTagsInfluencedByRelation() {
        CustomModel cm = GHUtility.loadCustomModelFromJar("bike.json");
        CustomWeighting.Parameters p = CustomModelParser.createWeightingParameters(cm, em);

        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "road");

        EdgeIteratorState edge = createEdge(way);
        assertEquals(1, p.getEdgeToPriorityMapping().get(edge, false), 0.01);

        // "lcn=yes" is in fact no relation, but shall be treated the same like a relation with "network=lcn"
        way.setTag("lcn", "yes");
        edge = createEdge(way);
        assertEquals(1.25, p.getEdgeToPriorityMapping().get(edge, false), 0.01);
        assertEquals(12, p.getEdgeToSpeedMapping().get(edge, false), 0.01);

        // relation code is VERY_NICE
        ReaderRelation rel = new ReaderRelation(1);
        rel.setTag("route", "bicycle");
        way = new ReaderWay(1);
        way.setTag("highway", "road");
        edge = createEdge(way, rel);
        assertEquals(1.25, p.getEdgeToPriorityMapping().get(edge, false), 0.01);
        assertEquals(12, p.getEdgeToSpeedMapping().get(edge, false), 0.01);

        rel.setTag("network", "lcn");
        edge = createEdge(way, rel);
        assertEquals(1.25, p.getEdgeToPriorityMapping().get(edge, false), 0.01);
        assertEquals(12, p.getEdgeToSpeedMapping().get(edge, false), 0.01);

        // relation code is NICE
        rel.setTag("network", "rcn");
        edge = createEdge(way, rel);
        assertEquals(1.25, p.getEdgeToPriorityMapping().get(edge, false), 0.01);
        assertEquals(12, p.getEdgeToSpeedMapping().get(edge, false), 0.01);

        // no "double boosting" due because way lcn=yes is only considered if no route relation
        way.setTag("lcn", "yes");
        edge = createEdge(way, rel);
        assertEquals(1.25, p.getEdgeToPriorityMapping().get(edge, false), 0.01);
        assertEquals(12, p.getEdgeToSpeedMapping().get(edge, false), 0.01);

        // relation code is BEST
        rel.setTag("network", "ncn");
        edge = createEdge(way, rel);
        assertEquals(1.7, p.getEdgeToPriorityMapping().get(edge, false), 0.01);
        assertEquals(12, p.getEdgeToSpeedMapping().get(edge, false), 0.01);

        // PREFER relation, but tertiary road => no get off the bike but road wayTypeCode and faster
        way.clearTags();
        way.setTag("highway", "tertiary");
        rel.setTag("route", "bicycle");
        rel.setTag("network", "lcn");
        edge = createEdge(way, rel);
        assertEquals(1.25, p.getEdgeToPriorityMapping().get(edge, false), 0.01);
        assertEquals(18, p.getEdgeToSpeedMapping().get(edge, false), 0.01);

        rel.clearTags();
        way.clearTags();
        way.setTag("highway", "track");
        edge = createEdge(way, rel);
        assertEquals(1, p.getEdgeToPriorityMapping().get(edge, false), 0.01);
        assertEquals(12, p.getEdgeToSpeedMapping().get(edge, false), 0.01);

        rel.setTag("route", "bicycle");
        rel.setTag("network", "lcn");
        edge = createEdge(way, rel);
        assertEquals(1.25, p.getEdgeToPriorityMapping().get(edge, false), 0.01);
        assertEquals(18, p.getEdgeToSpeedMapping().get(edge, false), 0.01);
    }

    @Test
    public void testHandleWayTagsInfluencedByBikeAndMtbRelation() {
        CustomModel cm = GHUtility.loadCustomModelFromJar("mtb.json");
        CustomWeighting.Parameters p = CustomModelParser.createWeightingParameters(cm, em);

        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "track");

        ReaderRelation rel = new ReaderRelation(1);
        EdgeIteratorState edge = createEdge(way, rel);
        assertEquals(1.2, p.getEdgeToPriorityMapping().get(edge, false), 0.01);
        assertEquals(12, p.getEdgeToSpeedMapping().get(edge, false), 0.01);

        // relation code is PREFER
        rel.setTag("route", "bicycle");
        rel.setTag("network", "lcn");
        edge = createEdge(way, rel);
        assertEquals(1.8, p.getEdgeToPriorityMapping().get(edge, false), 0.01);
        assertEquals(18, p.getEdgeToSpeedMapping().get(edge, false), 0.01);

        rel.setTag("network", "rcn");
        edge = createEdge(way, rel);
        assertEquals(1.8, p.getEdgeToPriorityMapping().get(edge, false), 0.01);
        assertEquals(18, p.getEdgeToSpeedMapping().get(edge, false), 0.01);

        rel.setTag("network", "ncn");
        edge = createEdge(way, rel);
        assertEquals(2.16, p.getEdgeToPriorityMapping().get(edge, false), 0.01);
        assertEquals(18, p.getEdgeToSpeedMapping().get(edge, false), 0.01);

        // no pushing section but road wayTypeCode and faster
        way.clearTags();
        way.setTag("highway", "tertiary");
        rel.setTag("route", "bicycle");
        rel.setTag("network", "lcn");
        edge = createEdge(way, rel);
        assertEquals(1.5, p.getEdgeToPriorityMapping().get(edge, false), 0.01);
        assertEquals(18, p.getEdgeToSpeedMapping().get(edge, false), 0.01);

        way.clearTags();
        rel.clearTags();
        way.setTag("highway", "track");
        edge = createEdge(way, rel);
        assertEquals(1.2, p.getEdgeToPriorityMapping().get(edge, false), 0.01);
        assertEquals(12, p.getEdgeToSpeedMapping().get(edge, false), 0.01);

        rel.setTag("route", "mtb");
        rel.setTag("network", "lcn");
        edge = createEdge(way, rel);
        assertEquals(1.8, p.getEdgeToPriorityMapping().get(edge, false), 0.01);
        assertEquals(12, p.getEdgeToSpeedMapping().get(edge, false), 0.01);

        rel.setTag("network", "rcn");
        edge = createEdge(way, rel);
        assertEquals(1.8, p.getEdgeToPriorityMapping().get(edge, false), 0.01);
        assertEquals(12, p.getEdgeToSpeedMapping().get(edge, false), 0.01);

        rel.setTag("network", "ncn");
        edge = createEdge(way, rel);
        assertEquals(2.16, p.getEdgeToPriorityMapping().get(edge, false), 0.01);
        assertEquals(12, p.getEdgeToSpeedMapping().get(edge, false), 0.01);

        way.clearTags();
        way.setTag("highway", "tertiary");

        rel.setTag("route", "mtb");
        rel.setTag("network", "lcn");
        edge = createEdge(way, rel);
        assertEquals(1.5, p.getEdgeToPriorityMapping().get(edge, false), 0.01);
        assertEquals(18, p.getEdgeToSpeedMapping().get(edge, false), 0.01);
    }

}
