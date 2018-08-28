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
package com.graphhopper.storage.change;

import com.carrotsearch.hppc.cursors.IntCursor;
import com.graphhopper.coll.GHIntHashSet;
import com.graphhopper.json.geo.JsonFeature;
import com.graphhopper.routing.profiles.BooleanEncodedValue;
import com.graphhopper.routing.profiles.DecimalEncodedValue;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphEdgeIdFinder;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.util.EdgeIteratorState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * This graph applies permanent changes passed as JsonFeature to the specified graph.
 * <p>
 * This class is not thread-safe. It is currently only safe to use it via GraphHopper.changeGraph
 *
 * @author Peter Karich
 */
public class ChangeGraphHelper {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Graph graph;
    private final GraphEdgeIdFinder graphBrowser;
    private boolean enableLogging = false;

    public ChangeGraphHelper(Graph graph, LocationIndex locationIndex) {
        this.graph = graph;
        this.graphBrowser = new GraphEdgeIdFinder(graph, locationIndex);
    }

    public void setLogging(boolean log) {
        enableLogging = log;
    }

    /**
     * This method applies changes to the graph, specified by the json features.
     *
     * @return number of successfully applied edge changes
     */
    public long applyChanges(EncodingManager em, Collection<JsonFeature> features) {
        if (em == null)
            throw new NullPointerException("EncodingManager cannot be null to change existing graph");

        long updates = 0;
        for (JsonFeature jsonFeature : features) {
            if (!jsonFeature.hasProperties())
                throw new IllegalArgumentException("One feature has no properties, please specify properties e.g. speed or access");

            List<String> encodersAsStr = (List) jsonFeature.getProperty("vehicles");
            if (encodersAsStr == null) {
                for (FlagEncoder encoder : em.fetchEdgeEncoders()) {
                    updates += applyChange(jsonFeature, encoder);
                }
            } else {
                for (String encoderStr : encodersAsStr) {
                    updates += applyChange(jsonFeature, em.getEncoder(encoderStr));
                }
            }
        }

        return updates;
    }

    private long applyChange(JsonFeature jsonFeature, FlagEncoder encoder) {
        BooleanEncodedValue accessEnc = encoder.getAccessEnc();
        DecimalEncodedValue avSpeedEnc = encoder.getAverageSpeedEnc();
        long updates = 0;
        EdgeFilter filter = DefaultEdgeFilter.allEdges(encoder);
        GHIntHashSet edges = new GHIntHashSet();
        if (jsonFeature.hasGeometry()) {
            graphBrowser.fillEdgeIDs(edges, jsonFeature.getGeometry(), filter);
        } else if (jsonFeature.getBBox() != null) {
            graphBrowser.findEdgesInShape(edges, jsonFeature.getBBox(), filter);
        } else
            throw new IllegalArgumentException("Feature " + jsonFeature.getId() + " has no geometry and no bbox");

        Iterator<IntCursor> iter = edges.iterator();
        Map<String, Object> props = jsonFeature.getProperties();
        while (iter.hasNext()) {
            int edgeId = iter.next().value;
            EdgeIteratorState edge = graph.getEdgeIteratorState(edgeId, Integer.MIN_VALUE);
            if (props.containsKey("access")) {
                boolean value = (boolean) props.get("access");
                updates++;
                if (enableLogging)
                    logger.info(encoder.toString() + " - access change via feature " + jsonFeature.getId());
                edge.set(accessEnc, value).setReverse(accessEnc, value);

            } else if (props.containsKey("speed")) {
                // TODO use different speed for the different directions (see e.g. Bike2WeightFlagEncoder)
                double value = ((Number) props.get("speed")).doubleValue();
                double oldSpeed = edge.get(avSpeedEnc);
                if (oldSpeed != value) {
                    updates++;
                    if (enableLogging)
                        logger.info(encoder.toString() + " - speed change via feature " + jsonFeature.getId() + ". Old: " + oldSpeed + ", new:" + value);
                    edge.set(avSpeedEnc, value);
                }
            }
        }
        return updates;
    }
}
