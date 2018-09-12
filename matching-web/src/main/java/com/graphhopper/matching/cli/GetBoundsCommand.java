package com.graphhopper.matching.cli;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.graphhopper.matching.gpx.Gpx;
import com.graphhopper.matching.gpx.Trk;
import com.graphhopper.util.GPXEntry;
import com.graphhopper.util.shapes.BBox;
import io.dropwizard.cli.Command;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class GetBoundsCommand extends Command {

    public GetBoundsCommand() {
        super("getbounds", "");
    }

    @Override
    public void configure(Subparser subparser) {
        subparser.addArgument("gpx")
            .type(File.class)
            .required(true)
            .nargs("+")
            .help("GPX file");
    }

    @Override
    public void run(Bootstrap bootstrap, Namespace args) {
        XmlMapper xmlMapper = new XmlMapper();
        BBox bbox = BBox.createInverse(false);
        for (File gpxFile : args.<File>getList("gpx")) {
            try {
                Gpx gpx = xmlMapper.readValue(gpxFile, Gpx.class);
                for (Trk trk : gpx.trk) {
                    List<GPXEntry> inputGPXEntries = trk.getEntries();
                    for (GPXEntry entry : inputGPXEntries) {
                        bbox.update(entry.getLat(), entry.getLon());
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        System.out.println("bounds: " + bbox);

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
