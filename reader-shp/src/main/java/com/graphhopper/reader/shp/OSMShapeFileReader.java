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
package com.graphhopper.reader.shp;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;

import org.geotools.data.DataStore;
import org.geotools.feature.FeatureIterator;
import org.opengis.feature.simple.SimpleFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.graphhopper.reader.DataReader;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.reader.dem.ElevationProvider;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.GHPoint;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiLineString;

import gnu.trove.impl.Constants;
import gnu.trove.map.hash.TObjectIntHashMap;

/**
 * OSMShapeFileReader for files present at : http://download.geofabrik.de/ It
 * extracts the data as per the structure of shape files
 *
 * @author Vikas Veshishth
 * @author Philip Welch
 */
public class OSMShapeFileReader extends ShapeFileReader {
    private static final int COORD_STATE_UNKNOWN = -1;
    private static final int COORD_STATE_PILLAR = -2;
    private static final int FIRST_NODE_ID = 1;
    private static final String[] DIRECT_COPY_TAGS = new String[]{"name"};
    private File roadsFile;
    private final TObjectIntHashMap<Coordinate> coordState = new TObjectIntHashMap<>(
            Constants.DEFAULT_CAPACITY, Constants.DEFAULT_LOAD_FACTOR, COORD_STATE_UNKNOWN);
    private final DistanceCalc distCalc = Helper.DIST_EARTH;
    private static final Logger LOGGER = LoggerFactory.getLogger(OSMShapeFileReader.class);
    private final HashSet<EdgeAddedListener> edgeAddedListeners = new HashSet<>();
    private int nextNodeId = FIRST_NODE_ID;

    public OSMShapeFileReader(GraphHopperStorage ghStorage) {
        super(ghStorage);
    }

    private List<Coordinate[]> getCoords(Object o) {
        ArrayList<Coordinate[]> ret = new ArrayList<>();
        if (o == null) {
            return ret;
        }

        if (o instanceof LineString) {
            ret.add(((LineString) o).getCoordinates());
        } else if (o instanceof MultiLineString) {
            MultiLineString mls = (MultiLineString) o;
            int n = mls.getNumGeometries();
            for (int i = 0; i < n; i++) {
                ret.add(mls.getGeometryN(i).getCoordinates());
            }
        }

        return ret;
    }

    @Override
    void processJunctions() {
        DataStore dataStore = null;
        FeatureIterator<SimpleFeature> roads = null;

        try {
            dataStore = openShapefileDataStore(roadsFile);
            roads = getFeatureIterator(dataStore);

            HashSet<Coordinate> tmpSet = new HashSet<>();
            while (roads.hasNext()) {
                SimpleFeature road = roads.next();

                for (Coordinate[] points : getCoords(road.getDefaultGeometry())) {
                    tmpSet.clear();
                    for (int i = 0; i < points.length; i++) {
                        Coordinate c = points[i];

                        // don't add the same coord twice for the same edge
                        // - happens with bad geometry, i.e.
                        // duplicate coords or a road which forms a circle
                        // (e.g. roundabout)
                        if (tmpSet.contains(c)) {
                            continue;
                        }
                        tmpSet.add(c);

                        // skip if its already a node
                        int state = coordState.get(c);
                        if (state >= FIRST_NODE_ID) {
                            continue;
                        }

                        if (i == 0 || i == points.length - 1 || state == COORD_STATE_PILLAR) {
                            // turn into a node if its the first or last
                            // point, or already appeared in another edge
                            int nodeId = nextNodeId++;
                            coordState.put(c, nodeId);
                            saveTowerPosition(nodeId, c);
                        } else if (state == COORD_STATE_UNKNOWN) {
                            // mark it as a pillar (which may get upgraded
                            // to an edge later)
                            coordState.put(c, COORD_STATE_PILLAR);
                        }
                    }
                }

            }
        } finally {
            if (roads != null) {
                roads.close();
            }
            if (dataStore != null) {
                dataStore.dispose();
            }
        }

        if (nextNodeId == FIRST_NODE_ID)
            throw new IllegalArgumentException("No data found for roads file " + roadsFile);

        LOGGER.info("Number of junction points : " + (nextNodeId - FIRST_NODE_ID));
    }

    @Override
    void processRoads() {

        DataStore dataStore = null;
        FeatureIterator<SimpleFeature> roads = null;

        try {
            dataStore = openShapefileDataStore(roadsFile);
            roads = getFeatureIterator(dataStore);

            while (roads.hasNext()) {
                SimpleFeature road = roads.next();

                for (Coordinate[] points : getCoords(road.getDefaultGeometry())) {

                    // Parse all points in the geometry, splitting into
                    // individual graphhopper edges
                    // whenever we find a node in the list of points
                    Coordinate startTowerPnt = null;
                    List<Coordinate> pillars = new ArrayList<Coordinate>();
                    for (Coordinate point : points) {
                        if (startTowerPnt == null) {
                            startTowerPnt = point;
                        } else {
                            int state = coordState.get(point);
                            if (state >= FIRST_NODE_ID) {
                                int fromTowerNodeId = coordState.get(startTowerPnt);
                                int toTowerNodeId = state;

                                // get distance and estimated centres
                                double distance = getWayLength(startTowerPnt, pillars, point);
                                GHPoint estmCentre = new GHPoint(
                                        0.5 * (lat(startTowerPnt) + lat(point)),
                                        0.5 * (lng(startTowerPnt) + lng(point)));
                                PointList pillarNodes = new PointList(pillars.size(), false);

                                for (Coordinate pillar : pillars) {
                                    pillarNodes.add(lat(pillar), lng(pillar));
                                }

                                addEdge(fromTowerNodeId, toTowerNodeId, road, distance, estmCentre,
                                        pillarNodes);
                                startTowerPnt = point;
                                pillars.clear();
                            } else {
                                pillars.add(point);
                            }
                        }
                    }
                }

            }
        } finally {
            if (roads != null) {
                roads.close();
            }

            if (dataStore != null) {
                dataStore.dispose();
            }
        }
    }

    private double getWayLength(Coordinate start, List<Coordinate> pillars, Coordinate end) {
        double distance = 0;

        Coordinate previous = start;
        for (Coordinate point : pillars) {
            distance += distCalc.calcDist(lat(previous), lng(previous), lat(point), lng(point));
            previous = point;
        }
        distance += distCalc.calcDist(lat(previous), lng(previous), lat(end), lng(end));

        return distance;
    }

    @Override
    public DataReader setFile(File file) {
        this.roadsFile = file;
        return this;
    }

    @Override
    public DataReader setElevationProvider(ElevationProvider ep) {
        // Elevation not supported
        return this;
    }

    @Override
    public DataReader setWorkerThreads(int workerThreads) {
        // Its only single-threaded
        return this;
    }

    @Override
    public DataReader setWayPointMaxDistance(double wayPointMaxDistance) {
        // TODO Auto-generated method stub
        return this;
    }

    @Override
    public Date getDataDate() {
        return null;
    }

    public static interface EdgeAddedListener {
        void edgeAdded(ReaderWay way, EdgeIteratorState edge);
    }

    private void addEdge(int fromTower, int toTower, SimpleFeature road, double distance,
                         GHPoint estmCentre, PointList pillarNodes) {
        EdgeIteratorState edge = graph.edge(fromTower, toTower);

        // read the OSM id, should never be null
        long id = getOSMId(road);

        // Make a temporary ReaderWay object with the properties we need so we
        // can use the enocding manager
        // We (hopefully don't need the node structure on here as we're only
        // calling the flag
        // encoders, which don't use this...
        ReaderWay way = new ReaderWay(id);

        way.setTag("estimated_distance", distance);
        way.setTag("estimated_center", estmCentre);

        // read the highway type
        Object type = road.getAttribute("fclass");
        if (type != null) {
            way.setTag("highway", type.toString());
        }

        // read maxspeed filtering for 0 which for Geofabrik shapefiles appears
        // to correspond to no tag
        Object maxSpeed = road.getAttribute("maxspeed");
        if (maxSpeed != null && !maxSpeed.toString().trim().equals("0")) {
            way.setTag("maxspeed", maxSpeed.toString());
        }

        for (String tag : DIRECT_COPY_TAGS) {
            Object val = road.getAttribute(tag);
            if (val != null) {
                way.setTag(tag, val.toString());
            }
        }

        // read oneway
        Object oneway = road.getAttribute("oneway");
        if (oneway != null) {
            // Geofabrik is using an odd convention for oneway field in
            // shapefile.
            // We map back to the standard convention so that tag can be dealt
            // with correctly by the flag encoder.
            String val = oneway.toString().trim().toLowerCase();
            if (val.equals("b")) {
                // both ways
                val = "no";
            } else if (val.equals("t")) {
                // one way against the direction of digitisation
                val = "-1";
            } else if (val.equals("f")) {
                // one way Forward in the direction of digitisation
                val = "yes";
            } else {
                throw new RuntimeException("Unrecognised value of oneway field \"" + val
                        + "\" found in road with OSM id " + id);
            }

            way.setTag("oneway", val);
        }

        // Process the flags using the encoders
        long includeWay = encodingManager.acceptWay(way);
        if (includeWay == 0) {
            return;
        }

        // TODO we're not using the relation flags
        long relationFlags = 0;

        long wayFlags = encodingManager.handleWayTags(way, includeWay, relationFlags);
        if (wayFlags == 0)
            return;

        edge.setDistance(distance);
        edge.setFlags(wayFlags);
        edge.setWayGeometry(pillarNodes);

        if (edgeAddedListeners.size() > 0) {
            // check size first so we only allocate the iterator if we have
            // listeners
            for (EdgeAddedListener l : edgeAddedListeners) {
                l.edgeAdded(way, edge);
            }
        }
    }

    private long getOSMId(SimpleFeature road) {
        long id = Long.parseLong(road.getAttribute("osm_id").toString());
        return id;
    }

    public void addListener(EdgeAddedListener l) {
        edgeAddedListeners.add(l);
    }
}
