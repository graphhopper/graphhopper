package com.graphhopper.application.cli;

import com.graphhopper.application.GraphHopperServerConfiguration;
import com.graphhopper.gtfs.GraphHopperGtfs;
import com.graphhopper.gtfs.analysis.Trips;
import com.graphhopper.http.GraphHopperManaged;
import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import org.mapdb.DB;
import org.mapdb.DBMaker;

import java.io.File;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;

public class AnalyzeTripTransfersCommand extends ConfiguredCommand<GraphHopperServerConfiguration> {
    public AnalyzeTripTransfersCommand() {
        super("triptransfers", "Create trip transfers");
    }

    @Override
    protected void run(Bootstrap<GraphHopperServerConfiguration> bootstrap, Namespace namespace, GraphHopperServerConfiguration configuration) throws Exception {
        GraphHopperGtfs graphHopper = (GraphHopperGtfs) new GraphHopperManaged(configuration.getGraphHopperConfiguration()).getGraphHopper();
        graphHopper.importOrLoad();
        graphHopper.close();

        graphHopper = (GraphHopperGtfs) new GraphHopperManaged(configuration.getGraphHopperConfiguration()).getGraphHopper();
        graphHopper.importOrLoad();

        DB wurst = DBMaker.newFileDB(new File("wurst")).transactionDisable().mmapFileEnable().asyncWriteEnable().make();
        Map<Trips.TripAtStopTime, Collection<Trips.TripAtStopTime>> map = wurst.getTreeMap("pups");
        Trips.findAllTripTransfersInto(graphHopper, map, LocalDate.parse("2023-03-26"));
        System.out.println(map.size());
        wurst.close();
        graphHopper.close();
    }
}
