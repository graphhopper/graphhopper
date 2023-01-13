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

package com.graphhopper.application.cli;

import com.graphhopper.core.util.TranslationMap;
import com.graphhopper.core.util.Constants;
import com.graphhopper.core.util.PathMerger;
import com.graphhopper.core.util.StopWatch;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.graphhopper.core.GraphHopper;
import com.graphhopper.core.GraphHopperConfig;
import com.graphhopper.ResponsePath;
import com.graphhopper.application.GraphHopperServerConfiguration;
import com.graphhopper.gpx.GpxConversions;
import com.graphhopper.jackson.Gpx;
import com.graphhopper.matching.MapMatching;
import com.graphhopper.matching.MatchResult;
import com.graphhopper.matching.Observation;
import com.graphhopper.util.*;
import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Argument;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class MatchCommand extends ConfiguredCommand<GraphHopperServerConfiguration> {

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
        subparser.addArgument("--file")
                .required(true)
                .help("application configuration file");
        subparser.addArgument("--profile")
                .type(String.class)
                .required(true)
                .help("profile to use for map-matching (must be configured in configuration file)");
        subparser.addArgument("--instructions")
                .type(String.class)
                .required(false)
                .setDefault("")
                .help("Locale for instructions");
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
    protected Argument addFileArgument(Subparser subparser) {
        // Never called, but overridden for clarity:
        // In this command, we want the configuration file parameter to be a named argument,
        // not a positional argument, because the positional arguments are the gpx files,
        // and we configure it up there ^^.
        // Must be called "file" because superclass gets it by name.
        throw new RuntimeException();
    }

    @Override
    protected void run(Bootstrap<GraphHopperServerConfiguration> bootstrap, Namespace args, GraphHopperServerConfiguration configuration) {
        GraphHopperConfig graphHopperConfiguration = configuration.getGraphHopperConfiguration();

        GraphHopper hopper = new GraphHopper().init(graphHopperConfiguration);
        hopper.importOrLoad();

        PMap hints = new PMap();
        hints.putObject("profile", args.get("profile"));
        MapMatching mapMatching = MapMatching.fromGraphHopper(hopper, hints);
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
                List<Observation> measurements = GpxConversions.getEntries(gpx.trk.get(0));
                importSW.stop();
                matchSW.start();
                MatchResult mr = mapMatching.match(measurements);
                matchSW.stop();
                System.out.println(gpxFile);
                System.out.println("\tmatches:\t" + mr.getEdgeMatches().size() + ", gps entries:" + measurements.size());
                System.out.println("\tgpx length:\t" + (float) mr.getGpxEntriesLength() + " vs " + (float) mr.getMatchLength());

                String outFile = gpxFile.getAbsolutePath() + ".res.gpx";
                System.out.println("\texport results to:" + outFile);

                ResponsePath responsePath = new PathMerger(mr.getGraph(), mr.getWeighting()).
                        doWork(PointList.EMPTY, Collections.singletonList(mr.getMergedPath()), hopper.getEncodingManager(), tr);
                if (responsePath.hasErrors()) {
                    System.err.println("Problem with file " + gpxFile + ", " + responsePath.getErrors());
                    continue;
                }

                try (BufferedWriter writer = new BufferedWriter(new FileWriter(outFile))) {
                    long time = gpx.trk.get(0).getStartTime()
                            .map(Date::getTime)
                            .orElse(System.currentTimeMillis());
                    writer.append(GpxConversions.createGPX(responsePath.getInstructions(), gpx.trk.get(0).name != null ? gpx.trk.get(0).name : "", time, hopper.hasElevation(), withRoute, true, false, Constants.VERSION, tr));
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
