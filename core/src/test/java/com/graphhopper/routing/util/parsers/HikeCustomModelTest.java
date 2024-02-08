package com.graphhopper.routing.util.parsers;

import com.graphhopper.jackson.Jackson;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.reader.osm.conditional.DateRangeParser;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.OSMParsers;
import com.graphhopper.routing.util.PriorityCode;
import com.graphhopper.routing.weighting.custom.CustomModelParser;
import com.graphhopper.routing.weighting.custom.CustomWeighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class HikeCustomModelTest {

    private EncodingManager em;
    private DecimalEncodedValue roadsSpeedEnc;
    private OSMParsers parsers;

    @BeforeEach
    public void setup() {
        IntEncodedValue hikeRating = HikeRating.create();
        em = new EncodingManager.Builder().
                add(VehicleAccess.create("roads")).
                add(VehicleSpeed.create("roads", 7, 2, true)).
                add(VehicleAccess.create("foot")).
                add(VehicleSpeed.create("foot", 4, 1, false)).
                add(VehiclePriority.create("foot", 4, PriorityCode.getFactor(1), false)).
                add(FerrySpeed.create()).
                add(RouteNetwork.create(FootNetwork.KEY)).
                add(RoadAccess.create()).
                add(hikeRating).build();

        roadsSpeedEnc = em.getDecimalEncodedValue(VehicleSpeed.key("roads"));
        parsers = new OSMParsers().
                addWayTagParser(new OSMHikeRatingParser(hikeRating));

        parsers.addWayTagParser(new FootAccessParser(em, new PMap()).init(new DateRangeParser()));
        parsers.addWayTagParser(new FootAverageSpeedParser(em));
        parsers.addWayTagParser(new FootPriorityParser(em));

        parsers.addWayTagParser(new RoadsAccessParser(em));
        parsers.addWayTagParser(new RoadsAverageSpeedParser(em));
    }

    EdgeIteratorState createEdge(ReaderWay way) {
        BaseGraph graph = new BaseGraph.Builder(em).create();
        EdgeIteratorState edge = graph.edge(0, 1);
        EdgeIntAccess edgeIntAccess = graph.createEdgeIntAccess();
        parsers.handleWayTags(edge.getEdge(), edgeIntAccess, way, em.createRelationFlags());
        return edge;
    }

    static CustomModel getCustomModel(String file) {
        try {
            String string = Helper.readJSONFileWithoutComments(new InputStreamReader(GHUtility.class.getResourceAsStream("/com/graphhopper/custom_models/" + file)));
            return Jackson.newObjectMapper().readValue(string, CustomModel.class);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Test
    public void testHikePrivate() {
        ReaderWay way = new ReaderWay(0L);
        way.setTag("highway", "track");
        EdgeIteratorState edge = createEdge(way);
        CustomWeighting.Parameters p = CustomModelParser.createWeightingParameters(getCustomModel("hike.json"), em, roadsSpeedEnc, null);
        assertEquals(1.2, p.getEdgeToPriorityMapping().get(edge, false), 0.01);

        way.setTag("motor_vehicle", "private");
        edge = createEdge(way);
        p = CustomModelParser.createWeightingParameters(getCustomModel("hike.json"), em, roadsSpeedEnc, null);
        assertEquals(1.2, p.getEdgeToPriorityMapping().get(edge, false), 0.01);

        way.setTag("sac_scale", "alpine_hiking");
        edge = createEdge(way);
        p = CustomModelParser.createWeightingParameters(getCustomModel("hike.json"), em, roadsSpeedEnc, null);
        assertEquals(1.2, p.getEdgeToPriorityMapping().get(edge, false), 0.01);
        assertEquals(2, p.getEdgeToSpeedMapping().get(edge, false), 0.01);

        way = new ReaderWay(0L);
        way.setTag("highway", "track");
        way.setTag("access", "private");
        edge = createEdge(way);
        p = CustomModelParser.createWeightingParameters(getCustomModel("hike.json"), em, roadsSpeedEnc, null);
        assertEquals(0, p.getEdgeToPriorityMapping().get(edge, false), 0.01);

        way.setTag("sac_scale", "alpine_hiking");
        edge = createEdge(way);
        p = CustomModelParser.createWeightingParameters(getCustomModel("hike.json"), em, roadsSpeedEnc, null);
        // TODO this would be wrong tagging but still we should exclude the way - will be fixed with #2819
        // assertEquals(0, p.getEdgeToPriorityMapping().get(edge, false), 0.01);
    }
}
