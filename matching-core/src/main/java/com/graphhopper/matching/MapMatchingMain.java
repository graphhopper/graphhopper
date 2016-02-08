/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper licenses this file to you under the Apache License, 
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
import com.graphhopper.routing.Path;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.index.LocationIndexTree;
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
            String vehicle = args.get("vehicle", "car").toLowerCase();
            args.put("graph.flagEncoders", vehicle);
            args.put("osmreader.osm", args.get("datasource", ""));

            // standard should be to remove disconnected islands
            args.put("prepare.minNetworkSize", 200);
            args.put("prepare.minOneWayNetworkSize", 200);
            GraphHopper hopper = new GraphHopper().init(args);
            hopper.setCHEnable(false);
            hopper.importOrLoad();

        } else if (action.equals("match")) {
            GraphHopper hopper = new GraphHopper().init(args);
            hopper.setCHEnable(false);
            logger.info("loading graph from cache");
            hopper.load("./graph-cache");
            FlagEncoder firstEncoder = hopper.getEncodingManager().fetchEdgeEncoders().get(0);
            GraphHopperStorage graph = hopper.getGraphHopperStorage();

            int gpxAccuracy = args.getInt("gpxAccuracy", 15);
            String instructions = args.get("instructions", "");
            logger.info("Setup lookup index. Accuracy filter is at " + gpxAccuracy + "m");
            LocationIndexMatch locationIndex = new LocationIndexMatch(graph,
                    (LocationIndexTree) hopper.getLocationIndex(), gpxAccuracy);
            MapMatching mapMatching = new MapMatching(graph, locationIndex, firstEncoder);
            mapMatching.setSeparatedSearchDistance(args.getInt("separatedSearchDistance", 500));
            mapMatching.setMaxNodesToVisit(args.getInt("maxNodesToVisit", 1000));
            mapMatching.setForceRepair(args.getBool("forceRepair", false));

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
                    logger.error("Problem with file " + gpxFile + " Error: " + ex.getMessage());
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
                    if (entry.getLat() < bbox.minLat) {
                        bbox.minLat = entry.getLat();
                    }
                    if (entry.getLat() > bbox.maxLat) {
                        bbox.maxLat = entry.getLat();
                    }
                    if (entry.getLon() < bbox.minLon) {
                        bbox.minLon = entry.getLon();
                    }
                    if (entry.getLon() > bbox.maxLon) {
                        bbox.maxLon = entry.getLon();
                    }
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
