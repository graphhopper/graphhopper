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
package com.graphhopper.matching;

import com.graphhopper.GraphHopper;
import com.graphhopper.PathWrapper;
import com.graphhopper.reader.osm.GraphHopperOSM;
import com.graphhopper.routing.AlgorithmOptions;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.BBox;
import java.io.File;
import java.io.FilenameFilter;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Peter Karich
 */
public class MapMatchingMain {

    public static void main(String[] args) {
        new MapMatchingMain().start(CmdArgs.read(args));
    }

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private void start(CmdArgs args) {
        String action = args.get("action", "").toLowerCase();
        args.put("graph.location", "./graph-cache");
        if (action.equals("import")) {
            String flagEncoders = args.get("vehicle", "").toLowerCase();
            if (flagEncoders.isEmpty()) {
                flagEncoders = args.get("vehicles", "car").toLowerCase();
            }

            args.put("graph.flag_encoders", flagEncoders);
            args.put("datareader.file", args.get("datasource", ""));

            // standard should be to remove disconnected islands            
            if (!args.has("prepare.min_one_way_network_size")) {
                args.put("prepare.min_one_way_network_size", 200);
            }
            logger.info("Configuration: " + args);
            GraphHopper hopper = new GraphHopperOSM().init(args);
            hopper.getCHFactoryDecorator().setEnabled(false);
            hopper.importOrLoad();

        } else if (action.equals("match")) {
            GraphHopper hopper = new GraphHopperOSM().init(args);
            hopper.getCHFactoryDecorator().setEnabled(false);
            logger.info("loading graph from cache");
            hopper.load("./graph-cache");
            FlagEncoder firstEncoder = hopper.getEncodingManager().fetchEdgeEncoders().get(0);

            int gpsAccuracy = args.getInt("gps_accuracy", -1);
            if (gpsAccuracy < 0) {
                // backward compatibility since 0.8
                gpsAccuracy = args.getInt("gpx_accuracy", 40);
            }

            String instructions = args.get("instructions", "");
            logger.info("Setup lookup index. Accuracy filter is at " + gpsAccuracy + "m");
            AlgorithmOptions opts = AlgorithmOptions.start().
                    algorithm(Parameters.Algorithms.DIJKSTRA_BI).traversalMode(hopper.getTraversalMode()).
                    weighting(new FastestWeighting(firstEncoder)).
                    maxVisitedNodes(args.getInt("max_visited_nodes", 1000)).
                    // Penalizing inner-link U-turns only works with fastest weighting, since
                    // shortest weighting does not apply penalties to unfavored virtual edges.
                    hints(new HintsMap().put("weighting", "fastest").put("vehicle", firstEncoder.toString())).
                    build();
            MapMatching mapMatching = new MapMatching(hopper, opts);
            mapMatching.setTransitionProbabilityBeta(args.getDouble
                    ("transition_probability_beta", 2.0));
            mapMatching.setMeasurementErrorSigma(gpsAccuracy);

            // do the actual matching, get the GPX entries from a file or via stream
            String gpxLocation = args.get("gpx", "");
            File[] files = getFiles(gpxLocation);

            logger.info("Now processing " + files.length + " files");
            StopWatch importSW = new StopWatch();
            StopWatch matchSW = new StopWatch();

            Translation tr = new TranslationMap().doImport().get(instructions);

            for (File gpxFile : files) {
                try {
                    importSW.start();
                    List<GPXEntry> inputGPXEntries = new GPXFile().doImport(gpxFile.getAbsolutePath()).getEntries();
                    importSW.stop();
                    matchSW.start();
                    MatchResult mr = mapMatching.doWork(inputGPXEntries);
                    matchSW.stop();
                    System.out.println(gpxFile);
                    System.out.println("\tmatches:\t" + mr.getEdgeMatches().size() + ", gps entries:" + inputGPXEntries.size());
                    System.out.println("\tgpx length:\t" + (float) mr.getGpxEntriesLength() + " vs " + (float) mr.getMatchLength());
                    System.out.println("\tgpx time:\t" + mr.getGpxEntriesMillis() / 1000f + " vs " + mr.getMatchMillis() / 1000f);

                    String outFile = gpxFile.getAbsolutePath() + ".res.gpx";
                    System.out.println("\texport results to:" + outFile);

                    InstructionList il;
                    if (instructions.isEmpty()) {
                        il = new InstructionList(null);
                    } else {
                        PathWrapper matchGHRsp = new PathWrapper();
                        Path path = mapMatching.calcPath(mr);
                        new PathMerger().doWork(matchGHRsp, Collections.singletonList(path), tr);
                        il = matchGHRsp.getInstructions();
                    }

                    new GPXFile(mr, il).doExport(outFile);
                } catch (Exception ex) {
                    importSW.stop();
                    matchSW.stop();
                    logger.error("Problem with file " + gpxFile + " Error: " + ex.getMessage(), ex);
                }
            }
            System.out.println("gps import took:" + importSW.getSeconds() + "s, match took: " + matchSW.getSeconds());

        } else if (action.equals("getbounds")) {
            String gpxLocation = args.get("gpx", "");
            File[] files = getFiles(gpxLocation);
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
        } else {
            System.out.println("Usage: Do an import once, then do the matching\n"
                    + "./map-matching action=import datasource=your.pbf\n"
                    + "./map-matching action=match gpx=your.gpx\n"
                    + "./map-matching action=match gpx=.*gpx\n\n"
                    + "Or start in-built matching web service\n"
                    + "./map-matching action=start-server\n\n");
        }
    }

    File[] getFiles(String gpxLocation) {
        if (gpxLocation.contains("*")) {
            int lastIndex = gpxLocation.lastIndexOf(File.separator);
            final String pattern;
            File dir = new File(".");
            if (lastIndex >= 0) {
                dir = new File(gpxLocation.substring(0, lastIndex));
                pattern = gpxLocation.substring(lastIndex + 1);
            } else {
                pattern = gpxLocation;
            }

            return dir.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.matches(pattern);
                }
            });
        } else {
            return new File[]{
                new File(gpxLocation)
            };
        }
    }
}
