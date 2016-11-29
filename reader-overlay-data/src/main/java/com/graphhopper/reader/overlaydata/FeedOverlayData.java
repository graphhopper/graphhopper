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
package com.graphhopper.reader.overlaydata;

import com.carrotsearch.hppc.cursors.IntCursor;
import com.graphhopper.coll.GHIntHashSet;
import com.graphhopper.json.GHson;
import com.graphhopper.json.geo.Geometry;
import com.graphhopper.json.geo.JsonFeature;
import com.graphhopper.json.geo.JsonFeatureCollection;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphBrowser;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.GHPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * @author Peter Karich
 */
public class FeedOverlayData {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Graph graph;
    private final LocationIndex locationIndex;
    private final GHson ghson;
    private final EncodingManager em;
    private final GraphBrowser graphBrowser;
    private boolean enableLogging = false;

    public FeedOverlayData(Graph graph, EncodingManager em, LocationIndex locationIndex, GHson ghson) {
        this.ghson = ghson;
        this.graph = graph;
        this.em = em;
        this.locationIndex = locationIndex;
        this.graphBrowser = new GraphBrowser(graph, locationIndex);
    }

    public void setLogging(boolean log) {
        enableLogging = log;
    }

    public long applyChanges(String fileOrFolderStr) {
        File fileOrFolder = new File(fileOrFolderStr);
        try {
            if (fileOrFolder.isFile()) {
                return applyChanges(new FileReader(fileOrFolder));
            }

            long sum = 0;
            File[] fList = new File(fileOrFolderStr).listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.endsWith(".json");
                }
            });
            for (File f : fList) {
                sum += applyChanges(new FileReader(f));
            }
            return sum;

        } catch (FileNotFoundException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * This method applies changes to the graph, specified by the reader.
     *
     * @return number of successfully applied edge changes
     */
    public long applyChanges(Reader reader) {
        // read full file, later support one json feature or collection per line to avoid high mem consumption
        JsonFeatureCollection data = ghson.fromJson(reader, JsonFeatureCollection.class);
        long updates = 0;
        for (JsonFeature jsonFeature : data.getFeatures()) {
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
        long updates = 0;
        EdgeFilter filter = new DefaultEdgeFilter(encoder);
        GHIntHashSet edges = new GHIntHashSet();
        if (jsonFeature.hasGeometry()) {
            fillEdgeIDs(edges, jsonFeature.getGeometry(), filter);
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
                edge.setFlags(encoder.setAccess(edge.getFlags(), value, value));

            } else if (props.containsKey("speed")) {
                // TODO use different speed for the different directions (see e.g. Bike2WeightFlagEncoder)
                double value = ((Number) props.get("speed")).doubleValue();
                double oldSpeed = encoder.getSpeed(edge.getFlags());
                if (oldSpeed != value) {
                    updates++;
                    if (enableLogging)
                        logger.info(encoder.toString() + " - speed change via feature " + jsonFeature.getId() + ". Old: " + oldSpeed + ", new:" + value);
                    edge.setFlags(encoder.setSpeed(edge.getFlags(), value));
                }
            }
        }
        return updates;
    }

    public void fillEdgeIDs(GHIntHashSet edgeIds, Geometry geometry, EdgeFilter filter) {
        if (geometry.isPoint()) {
            GHPoint point = geometry.asPoint();
            graphBrowser.findClosestEdgeToPoint(edgeIds, point, filter);
        } else if (geometry.isPointList()) {
            PointList pl = geometry.asPointList();
            if (geometry.getType().equals("LineString")) {
                // TODO do map matching or routing
                int lastIdx = pl.size() - 1;
                if (pl.size() >= 2) {
                    double meanLat = (pl.getLatitude(0) + pl.getLatitude(lastIdx)) / 2;
                    double meanLon = (pl.getLongitude(0) + pl.getLongitude(lastIdx)) / 2;
                    graphBrowser.findClosestEdge(edgeIds, meanLat, meanLon, filter);
                }
            } else {
                for (int i = 0; i < pl.size(); i++) {
                    graphBrowser.findClosestEdge(edgeIds, pl.getLatitude(i), pl.getLongitude(i), filter);
                }
            }
        }
    }
}
