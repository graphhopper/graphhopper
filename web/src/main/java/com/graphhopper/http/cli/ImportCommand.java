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

import com.google.transit.realtime.GtfsRealtime;
import com.graphhopper.http.GraphHopperManaged;
import com.graphhopper.http.GraphHopperServerConfiguration;
import com.graphhopper.reader.gtfs.GraphHopperGtfs;
import com.graphhopper.reader.gtfs.GtfsStorage;
import com.graphhopper.reader.gtfs.PtFlagEncoder;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FootFlagEncoder;
import com.graphhopper.storage.GHDirectory;
import com.graphhopper.storage.GraphHopperStorage;
import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;

import java.util.Arrays;
import java.util.Collections;

public class ImportCommand extends ConfiguredCommand<GraphHopperServerConfiguration> {

    public ImportCommand() {
        super("import", "creates the graphhopper files used for later (faster) starts");
    }

    @Override
    protected void run(Bootstrap<GraphHopperServerConfiguration> bootstrap, Namespace namespace, GraphHopperServerConfiguration configuration) throws Exception {
        if (configuration.getGraphHopperConfiguration().has("gtfs.file")) {
            final PtFlagEncoder ptFlagEncoder = new PtFlagEncoder();
            final GHDirectory ghDirectory = GraphHopperGtfs.createGHDirectory(configuration.getGraphHopperConfiguration().get("graph.location", "target/tmp"));
            final GtfsStorage gtfsStorage = GraphHopperGtfs.createGtfsStorage();
            final EncodingManager encodingManager = EncodingManager.create(Arrays.asList(ptFlagEncoder, new FootFlagEncoder(), new CarFlagEncoder()), 12);
            final GraphHopperStorage graphHopperStorage = GraphHopperGtfs.createOrLoad(ghDirectory, encodingManager, ptFlagEncoder, gtfsStorage,
                    configuration.getGraphHopperConfiguration().has("gtfs.file") ? Arrays.asList(configuration.getGraphHopperConfiguration().get("gtfs.file", "").split(",")) : Collections.emptyList(),
                    configuration.getGraphHopperConfiguration().has("datareader.file") ? Arrays.asList(configuration.getGraphHopperConfiguration().get("datareader.file", "").split(",")) : Collections.emptyList());
            graphHopperStorage.close();
        } else {
            final GraphHopperManaged graphHopper = new GraphHopperManaged(configuration.getGraphHopperConfiguration(), bootstrap.getObjectMapper());
            graphHopper.start();
            graphHopper.stop();
        }

    }

}
