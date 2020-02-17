package com.graphhopper.matching.cli;

import com.graphhopper.GraphHopper;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.reader.osm.GraphHopperOSM;
import io.dropwizard.cli.Command;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

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
        graphHopperConfiguration.put("graph.flag_encoders", args.getString("vehicle"));
        graphHopperConfiguration.put("datareader.file", args.getString("datasource"));
        graphHopperConfiguration.put("graph.location", "graph-cache");

        GraphHopper hopper = new GraphHopperOSM().init(graphHopperConfiguration);
        hopper.importOrLoad();
    }

}
