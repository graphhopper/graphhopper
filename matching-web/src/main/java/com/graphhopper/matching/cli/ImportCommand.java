package com.graphhopper.matching.cli;

import com.graphhopper.GraphHopper;
import com.graphhopper.reader.osm.GraphHopperOSM;
import com.graphhopper.util.CmdArgs;
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
        subparser.addArgument("--prepare.min_one_way_network_size")
                .type(Integer.class)
                .required(false)
                .setDefault(200);
    }

    @Override
    public void run(Bootstrap<?> bootstrap, Namespace args) {
        CmdArgs graphHopperConfiguration = new CmdArgs();
        graphHopperConfiguration.put("graph.flag_encoders", args.getString("vehicle"));
        graphHopperConfiguration.put("datareader.file", args.getString("datasource"));
        graphHopperConfiguration.put("prepare.min_one_way_network_size", args.getInt("prepare.min_one_way_network_size"));
        graphHopperConfiguration.put("graph.location", "graph-cache");

        GraphHopper hopper = new GraphHopperOSM().init(graphHopperConfiguration);
        hopper.getCHFactoryDecorator().setEnabled(false);
        hopper.importOrLoad();
    }

}
