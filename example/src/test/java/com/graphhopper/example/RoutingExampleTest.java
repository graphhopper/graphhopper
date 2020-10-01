package com.graphhopper.example;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.ResponsePath;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.reader.osm.GraphHopperOSM;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.GHPoint;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Locale;

public class RoutingExampleTest {

    GraphHopper createGraphHopperInstance() {
        GraphHopper hopper = new GraphHopperOSM().forServer();
        hopper.setDataReaderFile("../core/files/andorra.osm.pbf");
        // where to store graphhopper files?
        hopper.setGraphHopperLocation("target/routing-graph-cache");
        hopper.setEncodingManager(EncodingManager.create("car"));

        // see docs/core/profiles.md to learn more about profiles
        hopper.setProfiles(new Profile("car").setVehicle("car").setWeighting("fastest").setTurnCosts(false));

        // this enables speed mode for the profile we called car
        hopper.getCHPreparationHandler().setCHProfiles(new CHProfile("car"));
        // explicitly allow that the calling code can disable this speed mode
        hopper.getRouterConfig().setCHDisablingAllowed(true);

        // now this can take minutes if it imports or a few seconds for loading of course this is dependent on the area you import
        hopper.importOrLoad();
        return hopper;
    }

    @Test
    public void routing() {
        GraphHopper hopper = createGraphHopperInstance();

        // simple configuration of the request object
        GHRequest req = new GHRequest(42.508552, 1.532936, 42.507508, 1.528773).
                // note that we have to specify which profile we are using even when there is only one like here
                        setProfile("car").
                        setLocale(Locale.US);
        GHResponse rsp = hopper.route(req);

        // first check for errors
        if (rsp.hasErrors()) {
            // handle them!
            // rsp.getErrors()
            return;
        }

        // use the best path, see the GHResponse class for more possibilities.
        ResponsePath path = rsp.getBest();

        // points, distance in meters and time in millis of the full path
        PointList pointList = path.getPoints();
        double distance = path.getDistance();
        long timeInMs = path.getTime();

        Translation tr = hopper.getTranslationMap().getWithFallBack(Locale.UK);
        InstructionList il = path.getInstructions();
        // iterate over every turn instruction
        for (Instruction instruction : il) {
            out("distance " + instruction.getDistance() + " for instruction: " + instruction.getTurnDescription(tr));
        }
    }

    @Test
    public void speedModeVersusHybridMode() {
        GraphHopper hopper = createGraphHopperInstance();
        GHRequest req = new GHRequest(42.508552, 1.532936, 42.507508, 1.528773).
                setProfile("car").setAlgorithm(Parameters.Algorithms.ASTAR_BI).putHint(Parameters.CH.DISABLE, true);
        GHResponse res = hopper.route(req);
        out("distance " + res.getBest().getDistance());
    }

    @Test
    public void headingAndAlternativeRoute() {
        GraphHopper hopper = createGraphHopperInstance();
        // define a heading (direction) at start and destination
        GHRequest req = new GHRequest().setProfile("car").
                addPoint(new GHPoint(42.508774, 1.535414)).addPoint(new GHPoint(42.506595, 1.528795)).
                setHeadings(Arrays.asList(180d, 90d)).
                putHint(Parameters.CH.DISABLE, true);
        // also avoid u-turns at via points
        req.getHints().putObject(Parameters.Routing.PASS_THROUGH, true);
        GHResponse res = hopper.route(req);
        out("heading distance: " + res.getBest().getDistance());

        // alternative route is supported for speed mode too
        req = new GHRequest().setProfile("car").
                addPoint(new GHPoint(42.508552, 1.532936)).addPoint(new GHPoint(42.508552, 1.532936));
        req.setAlgorithm(Parameters.Algorithms.ALT_ROUTE);
        req.getHints().putObject(Parameters.Algorithms.AltRoute.MAX_PATHS, 3);
        out("alternative route distance: " + res.getBest().getDistance());
    }

    // TODO NOW should we use assert like in LocationIndexExampleTest or System.out.println?
    static void out(String message) {
        // System.out.println(message);
    }
}
