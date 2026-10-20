package com.graphhopper.routing.matrix;

import com.graphhopper.GraphHopper;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.matrix.MatrixException;
import com.graphhopper.matrix.MatrixRequest;
import com.graphhopper.storage.RoutingCHGraph;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.TurnCostsConfig;
import com.graphhopper.util.shapes.GHPoint;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Public backend selection, certificate, limits and request-isolation checks, separate from bucket tests. */
class MatrixCHIntegrationTest {
    @TempDir static Path temp;
    static MaskableHopper hopper;
    static MatrixEngine engine;
    static final List<String> METRICS = List.of("times", "times_ms", "distances", "weights");
    static final List<GHPoint> POINTS = List.of(new GHPoint(43.730729, 7.42135),
            new GHPoint(43.727697, 7.419199), new GHPoint(43.739213, 7.4277));

    static class MaskableHopper extends GraphHopper {
        boolean hideCH;
        @Override public Map<String, RoutingCHGraph> getCHGraphs() {
            return hideCH ? Map.of() : super.getCHGraphs();
        }
    }

    @BeforeAll static void setup() {
        hopper = new MaskableHopper();
        hopper.setGraphHopperLocation(temp.resolve("monaco").toString())
                .setOSMFile("../core/files/monaco.osm.gz").setStoreOnFlush(false)
                .setEncodedValuesString("car_access,car_average_speed,road_access,foot_access,foot_average_speed,foot_priority,foot_road_access,hike_rating,mtb_rating,country,road_class")
                .setProfiles(new Profile("foot").setCustomModel(GHUtility.loadCustomModelFromJar("foot.json")),
                        new Profile("car").setCustomModel(GHUtility.loadCustomModelFromJar("car.json"))
                                .setTurnCostsConfig(TurnCostsConfig.car()))
                .setMinNetworkSize(0);
        hopper.getCHPreparationHandler().setCHProfiles(new CHProfile("foot"), new CHProfile("car"));
        hopper.importOrLoad();
        engine = new MatrixEngine(hopper, new MatrixEngine.Limits(1000, 100000, 4, 120000, 10000000));
    }

    @AfterAll static void close() { if (hopper != null) hopper.close(); }

    static MatrixRequest request(String profile, List<GHPoint> from, List<GHPoint> to, String algorithm) {
        return new MatrixRequest(profile, from, to, METRICS, false, algorithm);
    }

    @SuppressWarnings("unchecked") static Map<String, Object> info(Map<String, Object> response) {
        return (Map<String, Object>) ((Map<String, Object>) response.get("info")).get("local_matrix");
    }

    @SuppressWarnings("unchecked") static Map<String, Object> ch(Map<String, Object> response) {
        return (Map<String, Object>) info(response).get("ch_matrix");
    }

    static void assertMetricsEqual(Map<String, Object> expected, Map<String, Object> actual) {
        for (String metric : METRICS) assertEquals(expected.get(metric), actual.get(metric), metric);
        assertEquals(expected.get("hints"), actual.get("hints"));
    }

    @Test void preparedNodeProfileHasGenuineBucketWorkAndExactCertifiedMetrics() {
        List<GHPoint> from = List.of(POINTS.get(0), POINTS.get(1), POINTS.get(0));
        List<GHPoint> to = List.of(POINTS.get(2), POINTS.get(0), POINTS.get(2));
        Map<String, Object> flexible = engine.route(request("foot", from, to, "flexible"));
        Map<String, Object> accelerated = engine.route(request("foot", from, to, "ch_experimental"));
        assertMetricsEqual(flexible, accelerated);
        assertEquals("ch_experimental", info(accelerated).get("algorithm"));
        assertEquals("flexible_each_origin", info(accelerated).get("optimality_verification"));
        assertEquals(Boolean.TRUE, info(accelerated).get("experimental"));
        assertEquals(2L, ch(accelerated).get("backward_searches"));
        assertEquals(2L, ch(accelerated).get("forward_searches"));
        assertEquals(2L, ch(accelerated).get("certificate_searches"));
        assertEquals(6L, info(accelerated).get("searches"));
        assertTrue(((Number) ch(accelerated).get("bucket_entries")).longValue() > 0);
        assertTrue(((Number) ch(accelerated).get("metric_cache_entries")).longValue() > 0);
        assertEquals(((Number) ch(accelerated).get("candidate_visited_states")).longValue()
                        + ((Number) ch(accelerated).get("certificate_visited_states")).longValue(),
                ((Number) info(accelerated).get("visited_states")).longValue());
        assertFalse(info(accelerated).containsKey("fallback_reason"));
        assertEquals("flexible", info(engine.route(request("foot", from, to, "auto"))).get("algorithm"));
    }

    @Test void edgeProfileAndUnavailablePreparationHaveExplicitFlexibleFallbacks() {
        Map<String, Object> car = engine.route(request("car", POINTS, POINTS, "ch_experimental"));
        assertMetricsEqual(engine.route(request("car", POINTS, POINTS, "flexible")), car);
        assertEquals("flexible", info(car).get("algorithm"));
        assertEquals("ch_experimental", info(car).get("requested_algorithm"));
        assertEquals("TURN_COST_PROFILE_UNSUPPORTED", info(car).get("fallback_reason"));
        hopper.hideCH = true;
        try {
            Map<String, Object> unavailable = engine.route(request("foot", POINTS, POINTS, "ch_experimental"));
            assertMetricsEqual(engine.route(request("foot", POINTS, POINTS, "flexible")), unavailable);
            assertEquals("flexible", info(unavailable).get("algorithm"));
            assertEquals("NO_PREPARED_CH", info(unavailable).get("fallback_reason"));
        } finally { hopper.hideCH = false; }
    }

    @Test void invalidAxesPreserveNullsAndDoNoCHWork() {
        List<GHPoint> invalid = List.of(new GHPoint(0, 0));
        Map<String, Object> result = engine.route(request("foot", invalid, invalid, "ch_experimental"));
        assertMetricsEqual(engine.route(request("foot", invalid, invalid, "flexible")), result);
        assertEquals(0L, info(result).get("searches"));
        assertEquals(0L, ch(result).get("backward_searches"));
        assertEquals(0L, ch(result).get("certificate_searches"));
        MatrixException error = assertThrows(MatrixException.class,
                () -> engine.route(new MatrixRequest("foot", invalid, invalid, METRICS, true, "ch_experimental")));
        assertEquals("INVALID_SNAP", error.getCode());
    }

    @Test void wholeRequestLimitsAndCancellationRemainFailures() {
        MatrixEngine limited = new MatrixEngine(hopper, new MatrixEngine.Limits(1000, 100000, 1, 30000, 1));
        assertEquals("SEARCH_LIMIT_EXCEEDED", assertThrows(MatrixException.class,
                () -> limited.route(request("foot", POINTS, POINTS, "ch_experimental"))).getCode());
        Thread.currentThread().interrupt();
        try {
            assertEquals("CANCELLED", assertThrows(MatrixException.class,
                    () -> engine.route(request("foot", POINTS, POINTS, "ch_experimental"))).getCode());
        } finally { Thread.interrupted(); }
        assertNotNull(engine.route(request("foot", POINTS, POINTS, "ch_experimental")));
    }

    @Test void concurrentCandidateAndCertificateStateDoesNotMutatePreparedGraphs() throws Exception {
        String before = new MatrixProvenance(hopper).graphFingerprint;
        Map<String, Object> expected = engine.route(request("foot", POINTS, POINTS, "flexible"));
        var executor = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Map<String, Object>>> tasks = new ArrayList<>();
            for (int i = 0; i < 8; i++)
                tasks.add(() -> engine.route(request("foot", POINTS, POINTS, "ch_experimental")));
            for (var result : executor.invokeAll(tasks)) assertMetricsEqual(expected, result.get());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
        assertEquals(before, new MatrixProvenance(hopper).graphFingerprint);
    }
}
