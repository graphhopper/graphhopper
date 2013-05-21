/*
 * Copyright 2013 Thomas Buerli <tbuerli@student.ethz.ch>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.graphhopper.storage.index;

import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.PublicTransitEdgeFilter;
import com.graphhopper.routing.util.PublicTransitFlagEncoder;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.EdgeIterator;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Thomas Buerli <tbuerli@student.ethz.ch>
 */
public class LocationTime2IDIndex {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private Graph graph;
    private Map<Location, Integer> index;
    private Map<Integer, Integer> exitNodeMap;
    private EdgeFilter transitOutFilter;

    public LocationTime2IDIndex(Graph graph) {
        this.graph = graph;
        this.index = new HashMap<Location, Integer>();
        this.exitNodeMap = new HashMap<Integer, Integer>();
        this.transitOutFilter = new PublicTransitEdgeFilter(new PublicTransitFlagEncoder(), false, true, true, false, false);
    }

    /**
     * Finds the node id for a given location and time. At the moment the
     * location must be exact. Returns -1 if no node is found.
     *
     * @param lat
     * @param lon
     * @param startTime time in seconds since midnight
     * @return the node id for the specified geo location (latitude,longitude)
     */
    public int findID(double lat, double lon, double startTime) {
        Integer node = index.get(new Location(lat, lon));
        if (node == null) {
            return -1;
        } else {
            double time = 0;
            while (true) {
                EdgeIterator iter = graph.getEdges(node, transitOutFilter);
                iter.next();
                int tmpNode = iter.adjNode();
                time = time + iter.distance();
                if (iter.next()) {
                    logger.error("Wrong graph structure! Multiple transit edges from a transit node!");
                    return -1;
                }
                if (tmpNode == -1 || time > startTime) {
                    return node;
                }
                node = tmpNode;
            }
        }
    }

    /**
     * Gets the exit node of a station for given location. Returns -1 if no node
     * is found.
     *
     * @param lat
     * @param lon
     * @return node id
     */
    public int getExitNodeID(double lat, double lon) {
        Integer node = index.get(new Location(lat, lon));
        if (node == null) {
            return -1;
        } else {
            return exitNodeMap.get(node);
        }
    }

    /**
     * Add an transit node to the index. Only add the first from a station.
     *
     * @param startId Id of the start node of the station
     * @param exitNodeId Id of the node which marks the exit of the station
     * @param lat
     * @param lon
     */
    public void addStation(int startId, int exitNodeId, double lat, double lon) {
        index.put(new Location(lat, lon), startId);
        exitNodeMap.put(startId, exitNodeId);
    }

    /**
     * Creates this index - to be called once before findID.
     */
    public LocationTime2IDIndex prepareIndex() {
        return this;
    }

    private static class Location {

        private double lat;
        private double lon;

        public Location(double lat, double lon) {
            this.lat = lat;
            this.lon = lon;
        }

        public double getLat() {
            return lat;
        }

        public double getLon() {
            return lon;
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 59 * hash + (int) (Double.doubleToLongBits(this.lat) ^ (Double.doubleToLongBits(this.lat) >>> 32));
            hash = 59 * hash + (int) (Double.doubleToLongBits(this.lon) ^ (Double.doubleToLongBits(this.lon) >>> 32));
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final Location other = (Location) obj;
            if (Double.doubleToLongBits(this.lat) != Double.doubleToLongBits(other.lat)) {
                return false;
            }
            if (Double.doubleToLongBits(this.lon) != Double.doubleToLongBits(other.lon)) {
                return false;
            }
            return true;
        }
    }
}
