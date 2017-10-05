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
package com.graphhopper.tools;

import com.graphhopper.GraphHopper;
import com.graphhopper.json.GHJsonFactory;
import com.graphhopper.json.geo.JsonFeatureCollection;
import com.graphhopper.reader.osm.GraphHopperOSM;
import com.graphhopper.routing.lm.LandmarkStorage;
import com.graphhopper.spatialrules.SpatialRuleLookupHelper;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;

/**
 * @author Peter Karich
 */
public class Import {
    private static final Logger logger = LoggerFactory.getLogger(Import.class);

    public static void main(String[] strs) throws Exception {
        CmdArgs args = CmdArgs.read(strs);
        args = CmdArgs.readFromConfigAndMerge(args, "config", "graphhopper.config");
        GraphHopper hopper = new GraphHopperOSM(
                SpatialRuleLookupHelper.createLandmarkSplittingFeatureCollection(args.get(Parameters.Landmark.PREPARE + "split_area_location", ""))
        );
        SpatialRuleLookupHelper.buildAndInjectSpatialRuleIntoGH(hopper, args);
        hopper.init(args);
        hopper.importOrLoad();
        hopper.close();
    }
}
