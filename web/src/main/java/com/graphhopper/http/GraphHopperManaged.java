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
import com.graphhopper.routing.util.DefaultFlagEncoderFactory;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.FlagEncoderFactory;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.spatialrules.SpatialRuleLookupHelper;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PMap;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.details.AbstractPathDetailsBuilder;
import com.graphhopper.util.details.PathDetailsBuilder;
import com.graphhopper.util.details.PathDetailsBuilderFactory;
import io.dropwizard.lifecycle.Managed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.michaz.OriginalDirectionFlagEncoder;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

@Singleton
public class GraphHopperManaged implements Managed {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final GraphHopper graphHopper;

    @Inject
    public GraphHopperManaged(CmdArgs configuration) {
        graphHopper = new GraphHopperOSM(
                SpatialRuleLookupHelper.createLandmarkSplittingFeatureCollection(configuration.get(Parameters.Landmark.PREPARE + "split_area_location", ""))
        ).forServer();
        graphHopper.setFlagEncoderFactory(new FlagEncoderFactory() {
            private FlagEncoderFactory delegate = new DefaultFlagEncoderFactory();
            @Override
            public FlagEncoder createFlagEncoder(String name, PMap configuration) {
                if (name.equals("original-direction")) {
                    return new OriginalDirectionFlagEncoder();
                }
                return delegate.createFlagEncoder(name, configuration);
            }
        });
        SpatialRuleLookupHelper.buildAndInjectSpatialRuleIntoGH(graphHopper, configuration);
        graphHopper.init(configuration);
        graphHopper.setPathDetailsBuilderFactory(new PathDetailsBuilderFactory() {
            @Override
            public List<PathDetailsBuilder> createPathDetailsBuilders(List<String> requestedPathDetails, FlagEncoder encoder, Weighting weighting) {
                // request-scoped
                OriginalDirectionFlagEncoder originalDirectionFlagEncoder = (OriginalDirectionFlagEncoder) graphHopper.getGraphHopperStorage().getEncodingManager().getEncoder("original-direction");
                List<PathDetailsBuilder> pathDetailsBuilders = super.createPathDetailsBuilders(requestedPathDetails, encoder, weighting);
                pathDetailsBuilders.add(new AbstractPathDetailsBuilder("r5_edge_id") {
                    private int edgeId = -1;

                    @Override
                    public boolean isEdgeDifferentToLastEdge(EdgeIteratorState edge) {
                        if (edge.getEdge() != edgeId) {
                            edgeId = edge.getEdge() * 2 + (originalDirectionFlagEncoder.isOriginalDirection(edge.getFlags()) ? 0 : 1);
                            return true;
                        }
                        return false;
                    }

                    @Override
                    public Object getCurrentValue() {
                        return this.edgeId;
                    }
                });
                return pathDetailsBuilders;
            }
        });
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
