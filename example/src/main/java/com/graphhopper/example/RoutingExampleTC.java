package com.graphhopper.example;


import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.ResponsePath;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.reader.osm.GraphHopperOSM;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PMap;

import java.util.Arrays;

import static com.graphhopper.util.Parameters.Curbsides.CURBSIDE_ANY;
import static com.graphhopper.util.Parameters.Curbsides.CURBSIDE_RIGHT;

/**
 * Routing with turn costs
 */
public class RoutingExampleTC {
    public static void main(String[] args) {
        String relDir = args.length == 1 ? args[0] : "";
        GraphHopper hopper = createGraphHopperInstance(relDir + "core/files/andorra.osm.pbf");
        routing(hopper);
    }

    // see RoutingExample for more details
    public static void routing(GraphHopper hopper) {
        GHRequest req = new GHRequest(42.50822, 1.533966, 42.506899,1.525372).
                setCurbsides(Arrays.asList(CURBSIDE_ANY, CURBSIDE_RIGHT)).
                setProfile("car");
        GHResponse rsp = hopper.route(req);

        // handle errors
        if (rsp.hasErrors())
            // if you get: Impossible curbside constraint: 'curbside=right'
            // you either specify 'curbside=any' or Parameters.Routing.FORCE_CURBSIDE=false to ignore this situation
            throw new RuntimeException(rsp.getErrors().toString());

        ResponsePath path = rsp.getBest();
        assert Helper.round(path.getDistance(), -2) == 1700;
    }

    // see RoutingExample for more details
    static GraphHopper createGraphHopperInstance(String ghLoc) {
        GraphHopper hopper = new GraphHopperOSM().forServer();
        hopper.setDataReaderFile(ghLoc);
        hopper.setGraphHopperLocation("target/routing-tc-graph-cache");
        // to enable turn restriction and curbside support ensure that FlagEncoder and profile supports turn costs
        FlagEncoder car = new CarFlagEncoder(new PMap().
                putObject("u_turn_costs", 3).putObject("max_turn_costs", 1));
        hopper.setEncodingManager(EncodingManager.create(car));
        hopper.setProfiles(new Profile("car").setVehicle("car").setWeighting("fastest").setTurnCosts(true));
        hopper.getCHPreparationHandler().setCHProfiles(new CHProfile("car"));
        hopper.importOrLoad();
        return hopper;
    }
}
