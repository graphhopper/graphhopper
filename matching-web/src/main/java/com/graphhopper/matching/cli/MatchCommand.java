package com.graphhopper.matching.cli;

import com.graphhopper.GraphHopper;
import com.graphhopper.PathWrapper;
import com.graphhopper.matching.GPXFile;
import com.graphhopper.matching.MapMatching;
import com.graphhopper.matching.MatchResult;
import com.graphhopper.matching.http.MapMatchingServerConfiguration;
import com.graphhopper.reader.osm.GraphHopperOSM;
import com.graphhopper.routing.AlgorithmOptions;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.util.*;
import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Collections;
import java.util.List;

public class MatchCommand extends ConfiguredCommand<MapMatchingServerConfiguration> {

    public MatchCommand() {
        super("match", "map-match one or more gpx files");
    }

    private String gpxLocation;
    private String instructions;
    private int gpsAccuracy = 40;
    private int maxVisitedNodes = 1000;
    private double transitionProbabilityBeta = 2.0;

    @Override
    public void configure(Subparser subparser) {
        super.configure(subparser);
        subparser.addArgument("gpx")
                .dest("gpxLocation")
                .type(String.class)
                .required(true);
        subparser.addArgument("--instructions")
                .dest("instructions")
                .type(String.class)
                .required(false)
                .help("Locale for instructions");
        subparser.addArgument("--max_visited_nodes")
                .dest("maxVisitedNodes")
                .type(Integer.class)
                .required(false);
        subparser.addArgument("--transition_probability_beta")
                .dest("transitionProbabilityBeta")
                .type(Double.class)
                .required(false);
    }

    @Override
    protected void run(Bootstrap<MapMatchingServerConfiguration> bootstrap, Namespace namespace, MapMatchingServerConfiguration mapMatchingServerConfiguration) {
        GraphHopper hopper = new GraphHopperOSM().init(mapMatchingServerConfiguration.getGraphHopperConfiguration());
        hopper.getCHFactoryDecorator().setEnabled(false);
        System.out.println("loading graph from cache");
        hopper.load(mapMatchingServerConfiguration.getGraphHopperConfiguration().get("graph.location", "./graph-cache"));
        FlagEncoder firstEncoder = hopper.getEncodingManager().fetchEdgeEncoders().get(0);


        System.out.println("Accuracy filter is at " + gpsAccuracy + "m");
        AlgorithmOptions opts = AlgorithmOptions.start().
                algorithm(Parameters.Algorithms.DIJKSTRA_BI).traversalMode(hopper.getTraversalMode()).
                weighting(new FastestWeighting(firstEncoder)).
                maxVisitedNodes(maxVisitedNodes).
                // Penalizing inner-link U-turns only works with fastest weighting, since
                // shortest weighting does not apply penalties to unfavored virtual edges.
                        hints(new HintsMap().put("weighting", "fastest").put("vehicle", firstEncoder.toString())).
                        build();
        MapMatching mapMatching = new MapMatching(hopper, opts);
        mapMatching.setTransitionProbabilityBeta(transitionProbabilityBeta);
        mapMatching.setMeasurementErrorSigma(gpsAccuracy);

        File[] files = getFiles(gpxLocation);

        System.out.println("Now processing " + files.length + " files");
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
                System.err.println("Problem with file " + gpxFile);
                ex.printStackTrace(System.err);
            }
        }
        System.out.println("gps import took:" + importSW.getSeconds() + "s, match took: " + matchSW.getSeconds());
    }

    public static File[] getFiles(String gpxLocation) {
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
