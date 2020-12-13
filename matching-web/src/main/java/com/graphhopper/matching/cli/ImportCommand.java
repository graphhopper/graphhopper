package com.graphhopper.matching.cli;

import com.graphhopper.GraphHopper;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.config.Profile;
import com.graphhopper.reader.osm.GraphHopperOSM;
import io.dropwizard.cli.Command;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import java.util.ArrayList;
import java.util.List;

public class ImportCommand extends Command {

    public ImportCommand() {
        super("import", "");
    }

    @Override
    public void configure(Subparser subparser) {
        subparser.addArgument("datasource")
                .type(String.class)
                .required(true);
        subparser.addArgument("--vehicle")
                .type(String.class)
                .required(false)
                .setDefault("car");
    }

    @Override
    public void run(Bootstrap<?> bootstrap, Namespace args) {
        GraphHopperConfig graphHopperConfiguration = new GraphHopperConfig();
        String vehicle = args.getString("vehicle");
        graphHopperConfiguration.putObject("graph.flag_encoders", vehicle);
        graphHopperConfiguration.putObject("datareader.file", args.getString("datasource"));
        graphHopperConfiguration.putObject("graph.location", "graph-cache");
        // always using fastest weighting, see comment in MatchCommand
        String weightingStr = "fastest";
        List<Profile> profiles = new ArrayList<>();
        for (String v : vehicle.split(",")) {
            v = v.trim();
            profiles.add(new Profile(v + "_profile").setVehicle(v).setWeighting(weightingStr));
        }
        graphHopperConfiguration.setProfiles(profiles);
        GraphHopper hopper = new GraphHopperOSM().init(graphHopperConfiguration);
        hopper.importOrLoad();
    }

}
