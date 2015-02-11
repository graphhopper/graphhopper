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
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.util.*;
import java.io.File;
import java.io.FilenameFilter;
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
            args.put("osmreader.osm", args.get("datasource", ""));
            GraphHopper hopper = new GraphHopper().init(args);
            hopper.setCHEnable(false);
            hopper.importOrLoad();

        } else if (action.equals("match")) {
            GraphHopper hopper = new GraphHopper().init(args);
            hopper.setCHEnable(false);
            logger.info("loading graph from cache");
            hopper.load("./graph-cache");
            GraphStorage graph = hopper.getGraph();

            int gpxAccuracy = args.getInt("gpxAccuracy", 15);
            logger.info("Setup lookup index. Accuracy filter is at " + gpxAccuracy + "m");
            LocationIndexMatch locationIndex = new LocationIndexMatch(graph,
                    (LocationIndexTree) hopper.getLocationIndex(), gpxAccuracy);
            MapMatching mapMatching = new MapMatching(graph, locationIndex, hopper.getEncodingManager().getSingle());
            mapMatching.setSeparatedSearchDistance(args.getInt("separatedSearchDistance", 500));
            mapMatching.setMaxSearchMultiplier(args.getInt("maxSearchMultiplier", 50));

            // do the actual matching, get the GPX entries from a file or via stream
            String gpxLocation = args.get("gpx", "");
            File[] files;
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

                files = dir.listFiles(new FilenameFilter() {
                    @Override
                    public boolean accept(File dir, String name) {
                        return name.matches(pattern);
                    }
                });
            } else {
                files = new File[]{
                    new File(gpxLocation)
                };
            }

            logger.info("Now processing " + files.length + " files");
            StopWatch importSW = new StopWatch();
            StopWatch matchSW = new StopWatch();
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
                    new GPXFile(mr).doExport(outFile);
                } catch (Exception ex) {
                    importSW.stop();
                    matchSW.stop();
                    logger.error("Problem with file " + gpxFile + " Error: " + ex.getMessage());
                }
            }
            System.out.println("gps import took:" + importSW.getSeconds() + "s, match took: " + matchSW.getSeconds());

        } else {
            System.out.println("Usage: Do an import once, then do the matching\n"
                    + "./map-matching action=import datasource=your.pbf\n"
                    + "./map-matching action=match gpx=your.gpx\n"
                    + "./map-matching action=match gpx=.*gpx\n\n");
        }
    }
}
