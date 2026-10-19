package com.graphhopper.routing.matrix;

import com.graphhopper.GHRequest;
import com.graphhopper.GraphHopper;
import com.graphhopper.ResponsePath;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.matrix.MatrixRequest;
import com.graphhopper.routing.ev.Subnetwork;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.util.DefaultSnapFilter;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.PMap;
import com.graphhopper.util.shapes.GHPoint;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CHMatrixPairwiseAuditTest {
    @TempDir static Path temp;
    static GraphHopper hopper;
    static final List<GHPoint> POINTS = List.of(new GHPoint(43.730729, 7.42135),
            new GHPoint(43.727697, 7.419199), new GHPoint(43.739213, 7.4277));
    static QueryGraph combined;
    static List<Snap> snaps;
    static Map<String, Number> ordinary, flexible;

    @BeforeAll static void setup() {
        hopper = new GraphHopper();
        hopper.setGraphHopperLocation(temp.resolve("monaco").toString()).setOSMFile("../core/files/monaco.osm.gz")
                .setStoreOnFlush(false)
                .setEncodedValuesString("foot_access,foot_average_speed,foot_priority,foot_road_access,hike_rating,mtb_rating,country,road_class")
                .setProfiles(new Profile("foot").setCustomModel(GHUtility.loadCustomModelFromJar("foot.json"))).setMinNetworkSize(0);
        hopper.getCHPreparationHandler().setCHProfiles(new CHProfile("foot"));
        hopper.importOrLoad();
        Weighting weighting = hopper.createWeighting(hopper.getProfile("foot"), new PMap());
        DefaultSnapFilter filter = new DefaultSnapFilter(weighting,
                hopper.getEncodingManager().getBooleanEncodedValue(Subnetwork.key("foot")));
        snaps = POINTS.stream().map(point -> hopper.getLocationIndex().findClosest(point.lat, point.lon, filter)).toList();
        assertTrue(snaps.stream().allMatch(Snap::isValid));
        combined = QueryGraph.create(hopper.getBaseGraph(), snaps);
        var route = hopper.route(new GHRequest(List.of(POINTS.get(0), POINTS.get(2))).setProfile("foot").setSnapPreventions(List.of())
                .putHint("instructions", false).putHint("calc_points", false));
        assertFalse(route.hasErrors(), route.getErrors().toString());
        ResponsePath path = route.getBest();
        ordinary = Map.of("weights", path.getRouteWeight(), "times_ms", path.getTime(), "distances", path.getDistance());
        var body = new MatrixEngine(hopper).route(new MatrixRequest("foot", POINTS, POINTS,
                List.of("weights", "times_ms", "distances"), false, "flexible"));
        flexible = new LinkedHashMap<>();
        for (String field : List.of("weights", "times_ms", "distances")) {
            @SuppressWarnings("unchecked") List<List<Number>> matrix = (List<List<Number>>) body.get(field);
            flexible.put(field, matrix.get(0).get(2));
        }
    }
    @AfterAll static void close() { if (hopper != null) hopper.close(); }

    @Test void freshPathsBindObservedTriplesAndProveSelectedStorageWeights() {
        CHMatrixPairwiseAudit.Audit proof = audit(ordinary, flexible, snaps.get(2).getClosestNode());
        assertTrue(proof.proved(), proof.summary().toString());
        assertEquals(true, proof.witness().get("actual_observed_scalars_exactly_bound"));
        assertTrue(proof.witness().containsKey("quantization_audit"));
        assertTrue(proof.witness().containsKey("ch_path"));
        assertTrue(proof.witness().containsKey("pair_source_node"));
    }

    @Test void alteredOrdinaryScalarIsRejectedExactly() {
        Map<String, Number> altered = new LinkedHashMap<>(ordinary);
        altered.put("weights", Math.nextUp(ordinary.get("weights").doubleValue()));
        CHMatrixPairwiseAudit.Audit proof = audit(altered, flexible, snaps.get(2).getClosestNode());
        assertFalse(proof.proved());
        assertTrue(proof.summary().get("failures").toString().contains("observed scalars"));
    }

    @Test void alteredSharedScalarIsRejectedExactly() {
        Map<String, Number> altered = new LinkedHashMap<>(flexible);
        altered.put("distances", Math.nextUp(flexible.get("distances").doubleValue()));
        assertFalse(audit(ordinary, altered, snaps.get(2).getClosestNode()).proved());
    }

    @Test void wrongCombinedEndpointCannotBeAccepted() {
        CHMatrixPairwiseAudit.Audit proof = audit(ordinary, flexible, snaps.get(1).getClosestNode());
        assertFalse(proof.proved());
        assertTrue(proof.summary().get("failures").toString().contains("endpoints differ"));
    }

    static CHMatrixPairwiseAudit.Audit audit(Map<String, Number> observedCH, Map<String, Number> observedFlexible, int target) {
        return CHMatrixPairwiseAudit.audit(hopper, "foot", POINTS.get(0), POINTS.get(2), observedCH, observedFlexible,
                combined, snaps.get(0).getClosestNode(), target);
    }
}
