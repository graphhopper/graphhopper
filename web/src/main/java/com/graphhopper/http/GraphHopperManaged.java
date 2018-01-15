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

package com.graphhopper.http;

import com.graphhopper.GraphHopper;
import com.graphhopper.reader.osm.GraphHopperOSM;
import com.graphhopper.spatialrules.SpatialRuleLookupHelper;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.Parameters;
import io.dropwizard.lifecycle.Managed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class GraphHopperManaged implements Managed {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final GraphHopper graphHopper;

    @Inject
    public GraphHopperManaged(CmdArgs configuration) {
        graphHopper = new GraphHopperOSM(
                SpatialRuleLookupHelper.createLandmarkSplittingFeatureCollection(configuration.get(Parameters.Landmark.PREPARE + "split_area_location", ""))
        ).forServer();
        SpatialRuleLookupHelper.buildAndInjectSpatialRuleIntoGH(graphHopper, configuration);
        graphHopper.init(configuration);
    }

    @Override
    public void start() {
        graphHopper.importOrLoad();
        logger.info("loaded graph at:" + graphHopper.getGraphHopperLocation()
                + ", data_reader_file:" + graphHopper.getDataReaderFile()
                + ", flag_encoders:" + graphHopper.getEncodingManager()
                + ", " + graphHopper.getGraphHopperStorage().toDetailsString());
    }

    GraphHopper getGraphHopper() {
        return graphHopper;
    }

    @Override
    public void stop() throws Exception {
        graphHopper.close();
    }


}
