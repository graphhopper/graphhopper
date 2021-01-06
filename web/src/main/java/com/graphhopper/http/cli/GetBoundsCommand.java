/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.graphhopper.http.cli;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.graphhopper.gpx.GpxConversions;
import com.graphhopper.matching.Observation;
import com.graphhopper.jackson.Gpx;
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
                for (Gpx.Trk trk : gpx.trk) {
                    List<Observation> inputGPXEntries = GpxConversions.getEntries(trk);
                    for (Observation entry : inputGPXEntries) {
                        bbox.update(entry.getPoint().getLat(), entry.getPoint().getLon());
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
