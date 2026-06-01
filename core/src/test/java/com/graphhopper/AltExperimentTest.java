package com.graphhopper;

import com.graphhopper.routing.TestProfiles;
import com.graphhopper.util.CustomModel;
import com.graphhopper.util.Parameters;
import org.junit.jupiter.api.Test;

public class AltExperimentTest {
    private static final String DIR = "../core/files";

    private GraphHopper hopper(String loc) {
        GraphHopper h = new GraphHopper().
                setGraphHopperLocation(loc).
                setOSMFile(DIR + "/north-bayreuth.osm.gz").
                setEncodedValuesString("bike_access, bike_priority, bike_average_speed").
                setProfiles(TestProfiles.accessSpeedAndPriority("bike", "bike"));
        h.getRouterConfig().setMaxVisitedNodes(Integer.MAX_VALUE);
        h.importOrLoad();
        return h;
    }

    private void run(GraphHopper hopper, double distInf, int maxNodes) {
        GHRequest req = new GHRequest(50.028917, 11.496506, 49.982829, 11.593139).
                setAlgorithm(Parameters.Algorithms.ALT_ROUTE).setProfile("bike");
        req.putHint("alternative_route.max_paths", 3);
        req.putHint(Parameters.CH.DISABLE, true);
        if (maxNodes > 0) req.putHint(Parameters.Routing.MAX_VISITED_NODES, maxNodes);
        CustomModel cm = new CustomModel();
        cm.setDistanceInfluence(distInf);
        req.setCustomModel(cm);
        try {
            GHResponse rsp = hopper.route(req);
            System.out.println("distInf=" + distInf + " maxNodes=" + maxNodes
                    + " errors=" + rsp.getErrors()
                    + " alts=" + (rsp.hasErrors() ? "-" : rsp.getAll().size()));
        } catch (Exception e) {
            System.out.println("distInf=" + distInf + " maxNodes=" + maxNodes
                    + " EXCEPTION " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    @Test
    public void experiment() {
        GraphHopper hopper = hopper("target/alt-exp-gh");
        run(hopper, 15, 50);
        run(hopper, 15, 200);
        run(hopper, 0, 50);
    }
}
