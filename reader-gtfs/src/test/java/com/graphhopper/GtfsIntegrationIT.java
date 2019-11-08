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

package com.graphhopper;

import com.graphhopper.reader.gtfs.GraphHopperGtfs;
import com.graphhopper.reader.gtfs.GtfsStorage;
import com.graphhopper.reader.gtfs.PtEncodedValues;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FootFlagEncoder;
import com.graphhopper.storage.DAType;
import com.graphhopper.storage.GHDirectory;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.Helper;
import com.graphhopper.util.TranslationMap;
import org.junit.Test;

import java.io.File;

public class GtfsIntegrationIT {

    @Test
    public void testConstructStorage() {
        CmdArgs cmdArgs = new CmdArgs();
        cmdArgs.put("datareader.file", "files/beatty.osm");
        cmdArgs.put("gtfs.file", "files/sample-feed.zip");
        Helper.removeDir(new File("target/GtfsIntegrationIT"));
        EncodingManager encodingManager = PtEncodedValues.createAndAddEncodedValues(EncodingManager.start()).add(new CarFlagEncoder()).add(new FootFlagEncoder()).build();
        GHDirectory directory = new GHDirectory("target/GtfsIntegrationIT", DAType.RAM_STORE);
        GtfsStorage gtfsStorage = GtfsStorage.createOrLoad(directory);
        GraphHopper graphHopperStorage = GraphHopperGtfs.createOrLoad(directory, encodingManager, gtfsStorage, cmdArgs);
        GraphHopperGtfs graphHopper = GraphHopperGtfs.createFactory(new TranslationMap().doImport(), graphHopperStorage, graphHopperStorage.getLocationIndex(), gtfsStorage)
                .createWithoutRealtimeFeed();

    }

}
