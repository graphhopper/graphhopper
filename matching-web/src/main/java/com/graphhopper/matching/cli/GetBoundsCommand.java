package com.graphhopper.matching.cli;

import com.graphhopper.matching.GPXFile;
import com.graphhopper.matching.http.MapMatchingServerConfiguration;
import com.graphhopper.util.GPXEntry;
import com.graphhopper.util.shapes.BBox;
import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import java.io.File;
import java.util.List;

public class GetBoundsCommand extends ConfiguredCommand<MapMatchingServerConfiguration> {

    private String gpxLocation;

    public GetBoundsCommand() {
        super("getbounds", "");
    }

    @Override
    public void configure(Subparser subparser) {
        super.configure(subparser);
        subparser.addArgument("gpx")
            .dest("gpxLocation")
            .type(String.class)
            .required(true);
    }


    @Override
    protected void run(Bootstrap<MapMatchingServerConfiguration> bootstrap, Namespace namespace, MapMatchingServerConfiguration mapMatchingServerConfiguration) throws Exception {
        File[] files = MatchCommand.getFiles(gpxLocation);
        BBox bbox = BBox.createInverse(false);
        for (File gpxFile : files) {
            List<GPXEntry> inputGPXEntries = new GPXFile().doImport(gpxFile.getAbsolutePath()).getEntries();
            for (GPXEntry entry : inputGPXEntries) {
                bbox.update(entry.getLat(), entry.getLon());
            }
        }

        System.out.println("max bounds: " + bbox);

        // show download only for small areas
        if (bbox.maxLat - bbox.minLat < 0.1 && bbox.maxLon - bbox.minLon < 0.1) {
            double delta = 0.01;
            System.out.println("Get small areas via\n"
                    + "wget -O extract.osm 'http://overpass-api.de/api/map?bbox="
                    + (bbox.minLon - delta) + "," + (bbox.minLat - delta) + ","
                    + (bbox.maxLon + delta) + "," + (bbox.maxLat + delta) + "'");
        }

    }
}
