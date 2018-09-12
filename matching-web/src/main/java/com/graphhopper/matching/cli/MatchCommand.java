package com.graphhopper.matching.cli;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.graphhopper.GraphHopper;
import com.graphhopper.PathWrapper;
import com.graphhopper.matching.gpx.Gpx;
import com.graphhopper.matching.MapMatching;
import com.graphhopper.matching.MatchResult;
import com.graphhopper.reader.osm.GraphHopperOSM;
import com.graphhopper.routing.AlgorithmOptions;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.util.*;
import io.dropwizard.cli.Command;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.Collections;
import java.util.List;

public class MatchCommand extends Command {

    public MatchCommand() {
        super("match", "map-match one or more gpx files");
    }

    @Override
    public void configure(Subparser subparser) {
        subparser.addArgument("gpx")
                .type(File.class)
                .required(true)
                .nargs("+")
                .help("GPX file");
        subparser.addArgument("--instructions")
                .type(String.class)
                .required(false)
                .setDefault("")
                .help("Locale for instructions");
        subparser.addArgument("--max_visited_nodes")
                .type(Integer.class)
                .required(false)
                .setDefault(1000);
        subparser.addArgument("--gps_accuracy")
                .type(Integer.class)
                .required(false)
                .setDefault(40);
        subparser.addArgument("--transition_probability_beta")
                .type(Double.class)
                .required(false)
                .setDefault(2.0);
    }

    @Override
    public void run(Bootstrap bootstrap, Namespace args) {
        CmdArgs graphHopperConfiguration = new CmdArgs();
        graphHopperConfiguration.put("graph.location", "graph-cache");
        GraphHopper hopper = new GraphHopperOSM().init(graphHopperConfiguration);
        hopper.getCHFactoryDecorator().setEnabled(false);
        System.out.println("loading graph from cache");
        hopper.load(graphHopperConfiguration.get("graph.location", "graph-cache"));

        FlagEncoder firstEncoder = hopper.getEncodingManager().fetchEdgeEncoders().get(0);
        AlgorithmOptions opts = AlgorithmOptions.start().
                algorithm(Parameters.Algorithms.DIJKSTRA_BI).traversalMode(hopper.getTraversalMode()).
                weighting(new FastestWeighting(firstEncoder)).
                maxVisitedNodes(args.getInt("max_visited_nodes")).
                // Penalizing inner-link U-turns only works with fastest weighting, since
                // shortest weighting does not apply penalties to unfavored virtual edges.
                hints(new HintsMap().put("weighting", "fastest").put("vehicle", firstEncoder.toString())).
                build();
        MapMatching mapMatching = new MapMatching(hopper, opts);
        mapMatching.setTransitionProbabilityBeta(args.getDouble("transition_probability_beta"));
        mapMatching.setMeasurementErrorSigma(args.getInt("gps_accuracy"));

        StopWatch importSW = new StopWatch();
        StopWatch matchSW = new StopWatch();

        Translation tr = new TranslationMap().doImport().getWithFallBack(Helper.getLocale(args.getString("instructions")));
        final boolean withRoute = !args.getString("instructions").isEmpty();
        XmlMapper xmlMapper = new XmlMapper();

        for (File gpxFile : args.<File>getList("gpx")) {
            try {
                importSW.start();
                Gpx gpx = xmlMapper.readValue(gpxFile, Gpx.class);
                if (gpx.trk == null) {
                    throw new IllegalArgumentException("No tracks found in GPX document. Are you using waypoints or routes instead?");
                }
                if (gpx.trk.size() > 1) {
                    throw new IllegalArgumentException("GPX documents with multiple tracks not supported yet.");
                }
                List<GPXEntry> measurements = gpx.trk.get(0).getEntries();
                importSW.stop();
                matchSW.start();
                MatchResult mr = mapMatching.doWork(measurements);
                matchSW.stop();
                System.out.println(gpxFile);
                System.out.println("\tmatches:\t" + mr.getEdgeMatches().size() + ", gps entries:" + measurements.size());
                System.out.println("\tgpx length:\t" + (float) mr.getGpxEntriesLength() + " vs " + (float) mr.getMatchLength());
                System.out.println("\tgpx time:\t" + mr.getGpxEntriesMillis() / 1000f + " vs " + mr.getMatchMillis() / 1000f);

                String outFile = gpxFile.getAbsolutePath() + ".res.gpx";
                System.out.println("\texport results to:" + outFile);

                PathWrapper pathWrapper = new PathWrapper();
                new PathMerger().doWork(pathWrapper, Collections.singletonList(mr.getMergedPath()), tr);
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(outFile))) {
                    long time = System.currentTimeMillis();
                    if (!measurements.isEmpty()) {
                        time = measurements.get(0).getTime();
                    }
                    writer.append(pathWrapper.getInstructions().createGPX(gpx.trk.get(0).name != null ? gpx.trk.get(0).name : "", time, hopper.hasElevation(), withRoute, true, false, Constants.VERSION));
                }
            } catch (Exception ex) {
                importSW.stop();
                matchSW.stop();
                System.err.println("Problem with file " + gpxFile);
                ex.printStackTrace(System.err);
            }
        }
        System.out.println("gps import took:" + importSW.getSeconds() + "s, match took: " + matchSW.getSeconds());
    }

}
