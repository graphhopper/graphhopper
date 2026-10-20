package com.graphhopper.routing.matrix;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.config.Profile;
import com.graphhopper.matrix.MatrixException;
import com.graphhopper.matrix.MatrixRequest;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.TurnCostsConfig;
import com.graphhopper.util.shapes.GHPoint;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class MatrixEngineTest {
    @TempDir static Path temp;
    static BlockingHopper hopper;
    static MatrixEngine engine;
    static final List<String> METRICS = List.of("times", "times_ms", "distances", "weights");
    static final List<GHPoint> POINTS = List.of(new GHPoint(43.730729, 7.42135),
            new GHPoint(43.727697, 7.419199), new GHPoint(43.739213, 7.4277));

    static class BlockingHopper extends GraphHopper {
        volatile CountDownLatch entered;
        volatile CountDownLatch release;
        volatile boolean omitCounters;
        @Override public GHResponse route(GHRequest request) {
            CountDownLatch latch = release;
            if (latch != null) {
                entered.countDown();
                try {
                    if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Test latch timed out");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }
            GHResponse response = super.route(request);
            if (omitCounters) response.getHints().remove("visited_nodes.sum");
            return response;
        }
    }

    @BeforeAll static void setup() {
        hopper = new BlockingHopper();
        hopper.setGraphHopperLocation(temp.resolve("monaco").toString())
                .setOSMFile("../core/files/monaco.osm.gz").setStoreOnFlush(false)
                .setEncodedValuesString("car_access,car_average_speed,road_access,foot_access,foot_average_speed,foot_priority,foot_road_access,hike_rating,mtb_rating,country,road_class")
                .setProfiles(new Profile("car").setCustomModel(GHUtility.loadCustomModelFromJar("car.json")).setTurnCostsConfig(TurnCostsConfig.car()),
                        new Profile("foot").setCustomModel(GHUtility.loadCustomModelFromJar("foot.json")))
                .setMinNetworkSize(0);
        hopper.importOrLoad();
        engine = new MatrixEngine(hopper, new MatrixEngine.Limits(1000, 100000, 4, 120000, 10000000));
    }

    @AfterAll static void close() { if (hopper != null) hopper.close(); }

    static MatrixRequest request(String profile, List<GHPoint> from, List<GHPoint> to, String algorithm) {
        return new MatrixRequest(profile, from, to, METRICS, false, algorithm);
    }

    @SuppressWarnings("unchecked") static Number cell(Map<String, Object> result, String metric, int i, int j) {
        return ((List<List<Number>>) result.get(metric)).get(i).get(j);
    }

    @SuppressWarnings("unchecked") static Map<String, Object> metadata(Map<String, Object> result) {
        return (Map<String, Object>) ((Map<String, Object>) result.get("info")).get("local_matrix");
    }

    @Test void duplicateRowsOrderSelectiveMetricsAndSearchCounters() {
        List<GHPoint> from = List.of(POINTS.get(1), POINTS.get(0), POINTS.get(1));
        List<GHPoint> to = List.of(POINTS.get(2), POINTS.get(0), POINTS.get(2), POINTS.get(1));
        Map<String, Object> flexible = engine.route(request("car", from, to, "flexible"));
        Map<String, Object> reference = engine.route(request("car", from, to, "reference"));
        assertEquals(2L, metadata(flexible).get("searches"));
        assertEquals(12L, metadata(reference).get("searches"));
        for (String metric : METRICS) {
            assertEquals(cell(flexible, metric, 0, 0), cell(flexible, metric, 2, 0));
            assertEquals(cell(flexible, metric, 0, 0), cell(flexible, metric, 0, 2));
            assertEquals(0, cell(flexible, metric, 0, 3).doubleValue());
        }
        Map<String, Object> only = engine.route(new MatrixRequest("car", from, to, List.of("distances"), false, "flexible"));
        assertFalse(only.containsKey("times"));
        assertEquals(flexible.get("distances"), only.get("distances"));
    }

    @Test void invalidDiagonalAndAllInvalidPreserveShapeAndDiagnostics() {
        List<GHPoint> invalid = List.of(new GHPoint(0, 0), new GHPoint(1, 1));
        for (String backend : List.of("reference", "flexible")) {
            Map<String, Object> result = engine.route(request("foot", invalid, invalid, backend));
            assertNull(cell(result, "weights", 0, 0));
            assertNull(cell(result, "times_ms", 1, 1));
            assertEquals(0L, metadata(result).get("searches"));
            assertEquals("INVALID_SNAP", ((List<Map<String, Object>>) result.get("hints")).get(0).get("details"));
            MatrixException error = assertThrows(MatrixException.class, () -> engine.route(new MatrixRequest("foot", invalid, invalid, METRICS, true, backend)));
            assertEquals("INVALID_SNAP", error.getCode());
        }
    }

    @Test void missingReferenceCountersFailTheRequestSoTheStateBudgetCannotBeBypassed() {
        hopper.omitCounters = true;
        try {
            assertEquals("REFERENCE_ROUTING_FAILED", assertThrows(MatrixException.class,
                    () -> engine.route(request("car", POINTS.subList(0, 1), POINTS.subList(1, 2), "reference"))).getCode());
        } finally { hopper.omitCounters = false; }
        assertNotNull(engine.route(request("car", POINTS.subList(0, 1), POINTS.subList(1, 2), "reference")));
    }

    @Test void nodeAndEdgeProfilesCompareEveryCellOf1156PairFixtureMatrices() throws Exception {
        List<GHPoint> points = new ArrayList<>();
        Random random = new Random(20261019L);
        for (int i = 0; i < 34; i++) {
            int node = random.nextInt(hopper.getBaseGraph().getNodes());
            points.add(new GHPoint(hopper.getBaseGraph().getNodeAccess().getLat(node), hopper.getBaseGraph().getNodeAccess().getLon(node)));
        }
        List<Map<String, Object>> diagnosedCases = new ArrayList<>();
        List<String> unexplained = new ArrayList<>();
        Map<String, Object> counts = new LinkedHashMap<>();
        for (String profile : List.of("foot", "car")) {
            Map<String, Object> matrix = engine.route(request(profile, points, points, "flexible"));
            Map<String, Object> reference = engine.route(request(profile, points, points, "reference"));
            MatrixPrecisionDiagnostics diagnostics = null;
            long reachable = 0, disconnected = 0, tight = 0, explained = 0;
            for (int i = 0; i < points.size(); i++) {
                for (int j = 0; j < points.size(); j++) {
                    String detail = profile + " " + i + "->" + j + " " + points.get(i) + "->" + points.get(j);
                    Number weight = cell(matrix, "weights", i, j), expected = cell(reference, "weights", i, j);
                    if (expected == null) { assertNull(weight, detail); disconnected++; continue; }
                    assertNotNull(weight, detail);
                    reachable++;
                    double referenceDistance = cell(reference, "distances", i, j).doubleValue();
                    double matrixDistance = cell(matrix, "distances", i, j).doubleValue();
                    long referenceTime = cell(reference, "times_ms", i, j).longValue();
                    long matrixTime = cell(matrix, "times_ms", i, j).longValue();
                    boolean ordinaryPrecision = Math.abs(expected.doubleValue() - weight.doubleValue())
                            <= 1e-7 + Math.abs(expected.doubleValue()) * 1e-10
                            && Math.abs(referenceDistance - matrixDistance) <= .002
                            && Math.abs(referenceTime - matrixTime) <= 1;
                    if (ordinaryPrecision) {
                        tight++;
                    } else {
                        if (diagnostics == null) diagnostics = new MatrixPrecisionDiagnostics(hopper, profile, points);
                        MatrixPrecisionDiagnostics.Evidence evidence = diagnostics.diagnose(i, j);
                        Map<String, Object> saved = new LinkedHashMap<>(evidence.details());
                        saved.put("observed_reference", Map.of("weight", expected.doubleValue(), "time_ms", referenceTime, "distance_m", referenceDistance));
                        saved.put("observed_shared", Map.of("weight", weight.doubleValue(), "time_ms", matrixTime, "distance_m", matrixDistance));
                        boolean referenceMatches = matchesDiagnostic(evidence.details().get("upstream_route_metrics"), expected.doubleValue(), referenceTime, referenceDistance);
                        boolean sharedMatches = matchesDiagnostic(evidence.details().get("reported_shared_metrics"), weight.doubleValue(), matrixTime, matrixDistance);
                        saved.put("observed_reference_matches_diagnostic", referenceMatches);
                        saved.put("observed_shared_matches_diagnostic", sharedMatches);
                        boolean classified = evidence.explained() && referenceMatches && sharedMatches;
                        saved.put("accepted_with_independent_path_evidence", classified);
                        diagnosedCases.add(saved);
                        if (classified) explained++;
                        else unexplained.add(detail);
                    }
                    assertEquals(cell(matrix, "times_ms", i, j).longValue() / 1000, cell(matrix, "times", i, j).longValue(), detail);
                }
            }
            counts.put(profile, Map.of("compared_cells", (long) points.size() * points.size(), "reachable", reachable,
                    "disconnected", disconnected, "within_original_tolerances", tight, "explained_split_cases", explained,
                    "profile_fingerprint", metadata(matrix).get("profile_fingerprint")));
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("status", unexplained.isEmpty() ? "PASS_WITH_CLASSIFIED_SPLIT_PRECISION" : "FAIL");
        report.put("fixture", "core/files/monaco.osm.gz");
        report.put("seed", 20261019L);
        report.put("graph_fingerprint", new MatrixProvenance(hopper).graphFingerprint);
        report.put("points_lon_lat", points.stream().map(point -> List.of(point.lon, point.lat)).toList());
        report.put("profile_counts", counts);
        report.put("diagnosed_case_count", diagnosedCases.size());
        report.put("unexplained_cases", unexplained);
        report.put("diagnosed_cases", diagnosedCases);
        report.put("original_tolerances", Map.of("weight_absolute", 1e-7, "weight_relative", 1e-10,
                "distance_m_absolute", .002, "time_ms_absolute", 1));
        report.put("classification_rule", "Only an independently reconstructed identical canonical directed path, identical snaps, "
                + "unchanged turn costs and edge weight per metre, matching observed output, and exact same-QueryGraph Dijkstra/path metrics "
                + "can classify an out-of-tolerance pair. No global tolerance is enlarged.");
        Path reportPath = Path.of(System.getProperty("matrix.fixture.report", "target/matrix-fixture-validation.json"));
        Files.createDirectories(reportPath.toAbsolutePath().getParent());
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(reportPath.toFile(), report);
        assertTrue(unexplained.isEmpty(), "Unexplained independent-snap discrepancies: " + unexplained);
    }

    @SuppressWarnings("unchecked") private static boolean matchesDiagnostic(Object raw, double weight, long time, double distance) {
        if (!(raw instanceof Map<?, ?>)) return false;
        Map<String, Object> metrics = (Map<String, Object>) raw;
        double diagnosticWeight = ((Number) metrics.get("weight")).doubleValue();
        return Math.abs(diagnosticWeight - weight) <= 1e-7 + Math.abs(weight) * 1e-10
                && ((Number) metrics.get("time_ms")).longValue() == time
                && Math.abs(((Number) metrics.get("distance_m")).doubleValue() - distance) <= 1e-8 + Math.abs(distance) * 1e-12;
    }

    @Test void boundedAdmissionAndCancellationReleaseSlots() throws Exception {
        MatrixEngine limited = new MatrixEngine(hopper, new MatrixEngine.Limits(1000, 100000, 1, 30000, 1000000));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        hopper.entered = new CountDownLatch(1);
        hopper.release = new CountDownLatch(1);
        try {
            Future<?> first = executor.submit(() -> limited.route(request("car", POINTS.subList(0, 1), POINTS.subList(1, 2), "reference")));
            assertTrue(hopper.entered.await(5, TimeUnit.SECONDS));
            assertEquals("SERVICE_BUSY", assertThrows(MatrixException.class,
                    () -> limited.route(request("foot", POINTS, POINTS, "flexible"))).getCode());
            hopper.release.countDown();
            first.get(10, TimeUnit.SECONDS);
        } finally {
            hopper.release.countDown();
            hopper.release = null;
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
        Thread.currentThread().interrupt();
        try {
            assertEquals("CANCELLED", assertThrows(MatrixException.class,
                    () -> limited.route(request("car", POINTS, POINTS, "flexible"))).getCode());
        } finally { Thread.interrupted(); }
        assertNotNull(limited.route(request("car", POINTS, POINTS, "flexible")));
    }

    @Test void searchLimitsAreWholeRequestFailuresAndGraphDoesNotChangeUnderConcurrentProfiles() throws Exception {
        MatrixEngine limited = new MatrixEngine(hopper, new MatrixEngine.Limits(1000, 100000, 2, 30000, 1));
        for (String backend : List.of("reference", "flexible"))
            assertEquals("SEARCH_LIMIT_EXCEEDED", assertThrows(MatrixException.class,
                    () -> limited.route(request("car", POINTS, POINTS, backend))).getCode());
        String before = new MatrixProvenance(hopper).graphFingerprint;
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Map<String, Object>>> tasks = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                String profile = i % 2 == 0 ? "foot" : "car";
                tasks.add(() -> engine.route(request(profile, POINTS, POINTS, "flexible")));
            }
            for (Future<Map<String, Object>> future : executor.invokeAll(tasks)) assertNotNull(future.get());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
        assertEquals(before, new MatrixProvenance(hopper).graphFingerprint);
    }
}
