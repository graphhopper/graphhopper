package com.graphhopper.routing.matrix;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.GraphHopper;
import com.graphhopper.config.Profile;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.TurnCostsConfig;
import com.graphhopper.util.shapes.GHPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MatrixSplitDiagnosticsTest {
    @TempDir Path temp;

    @Test
    void retainsPathEvidenceForIndependentlySnappedVersusCombinedGraphs() throws Exception {
        GraphHopper hopper = new GraphHopper();
        try {
            hopper.setGraphHopperLocation(temp.resolve("monaco").toString())
                    .setOSMFile("../core/files/monaco.osm.gz").setStoreOnFlush(false)
                    .setEncodedValuesString("car_access,car_average_speed,road_access,foot_access,foot_average_speed,foot_priority,foot_road_access,hike_rating,mtb_rating,country,road_class")
                    .setProfiles(new Profile("car").setCustomModel(GHUtility.loadCustomModelFromJar("car.json")).setTurnCostsConfig(TurnCostsConfig.car()),
                            new Profile("foot").setCustomModel(GHUtility.loadCustomModelFromJar("foot.json")))
                    .setMinNetworkSize(0);
            hopper.importOrLoad();
            List<GHPoint> points = new ArrayList<>();
            Random random = new Random(20261019L);
            for (int i = 0; i < 34; i++) {
                int node = random.nextInt(hopper.getBaseGraph().getNodes());
                points.add(new GHPoint(hopper.getBaseGraph().getNodeAccess().getLat(node), hopper.getBaseGraph().getNodeAccess().getLon(node)));
            }
            List<Map<String, Object>> evidence = new ArrayList<>();
            MatrixPrecisionDiagnostics diagnostics = new MatrixPrecisionDiagnostics(hopper, "car", points);
            MatrixPrecisionDiagnostics.Evidence first = diagnostics.diagnose(0, 1);
            evidence.add(first.details());
            Map<String, Object> report = new LinkedHashMap<>();
            report.put("fixture", "core/files/monaco.osm.gz");
            report.put("seed", 20261019L);
            report.put("point_count", points.size());
            report.put("status", first.explained() ? "EXPLAINED" : "UNEXPLAINED");
            report.put("cases", evidence);
            Path destination = Path.of(System.getProperty("matrix.diagnostic.output", "../docs/matrix/precision-diagnostics.json"));
            Files.createDirectories(destination.toAbsolutePath().getParent());
            new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(destination.toFile(), report);
            assertTrue(first.explained(), first.details().toString());
        } finally {
            hopper.close();
        }
    }
}
