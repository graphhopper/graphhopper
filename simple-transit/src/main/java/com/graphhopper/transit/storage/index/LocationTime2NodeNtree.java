/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper licenses this file to you under the Apache License, 
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
package com.graphhopper.transit.storage.index;

import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.transit.routing.util.PublicTransitEdgeFilter;
import com.graphhopper.transit.routing.util.PublicTransitFlagEncoder;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.index.Location2NodesNtree;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.PointList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Thomas Buerli <tbuerli@student.ethz.ch>
 */
public class LocationTime2NodeNtree extends Location2NodesNtree implements LocationTime2IDIndex {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private Graph graph;
    private PublicTransitEdgeFilter entryEdgeFilter;
    private PublicTransitEdgeFilter transitOutEdgeFilter;
    private PublicTransitEdgeFilter exitEdgeFilter;

    public LocationTime2NodeNtree(Graph graph, Directory dir) {
        super(graph, dir);
        this.graph = graph;
        entryEdgeFilter = new PublicTransitEdgeFilter(new PublicTransitFlagEncoder(), false, true, true, false, false, false, false);
        transitOutEdgeFilter = new PublicTransitEdgeFilter(new PublicTransitFlagEncoder(), false, true, false, false, true, false, false);
        exitEdgeFilter = new PublicTransitEdgeFilter(new PublicTransitFlagEncoder(), false, true, false, true, false, false, false);
    }

    @Override
    protected AllEdgesIterator getAllEdges() {
        return new AllTransitEdgeIterator(graph.getAllEdges());
    }

    @Override
    protected int pickBestNode(int nodeA, int nodeB) {
        if (nodeA > nodeB) {
            return nodeB;
        } else {
            return nodeA;
        }
    }

    @Override
    protected EdgeIterator getEdges(int node) {
        return graph.getEdges(node, entryEdgeFilter);
    }

    @Override
    public int findID(double lat, double lon, int startTime) {
        int node = findID(lat, lon);
        if (node < 0) {
            return -1;
        } else {
            double time = 0;
            while (time < startTime) {
                EdgeIterator iter = graph.getEdges(node, transitOutEdgeFilter);
                if(!iter.next())
                    return node;
                node = iter.getAdjNode();
                time = time + iter.getDistance();
                if (iter.next()) {
                    logger.error("Wrong graph structure! Multiple transit edges from a transit node!");
                    return -1;
                }
            }
            return node;
        }
    }

    @Override
    public int findExitNode(double lat, double lon) {
        int node = findID(lat, lon);
        EdgeIterator iter = graph.getEdges(node, exitEdgeFilter);
        if (iter.next()) {
            return iter.getAdjNode();
        } else {
            return -1;
        }
    }

    @Override
    public int getTime(int nodeId) {
        double lat = graph.getLatitude(nodeId);
        double lon = graph.getLongitude(nodeId);
        int node = findID(lat, lon);
        if (node < 0) {
            return 0;
        } else {
            int time = 0;
            while (node != nodeId) {
                EdgeIterator iter = graph.getEdges(node, transitOutEdgeFilter);
                if (!iter.next())
                    return 0;
                node = iter.getAdjNode();
                time = time + (int) iter.getDistance();
                if (iter.next()) {
                    logger.error("Wrong graph structure! Multiple transit edges from a transit node!");
                    return 0;
                }
            }
            return time;




        }
    }

    private static class AllTransitEdgeIterator implements AllEdgesIterator {

        private AllEdgesIterator allEdgesIterator;
        private PublicTransitEdgeFilter entryFilter;

        private AllTransitEdgeIterator(AllEdgesIterator allEdgesIterator) {
            this.allEdgesIterator = allEdgesIterator;
            entryFilter = new PublicTransitEdgeFilter(new PublicTransitFlagEncoder(), false, true, true, false, false, false, false);
        }

        @Override
        public int getMaxId() {
            return allEdgesIterator.getMaxId();
        }

        @Override
        public boolean next() {
            while (allEdgesIterator.next()) {
                if (entryFilter.accept(allEdgesIterator)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public int getEdge() {
            return allEdgesIterator.getEdge();
        }

        @Override
        public int getBaseNode() {
            return allEdgesIterator.getBaseNode();
        }

        @Override
        public int getAdjNode() {
            return allEdgesIterator.getAdjNode();
        }

        @Override
        public PointList getWayGeometry() {
            return allEdgesIterator.getWayGeometry();
        }

        @Override
        public void setWayGeometry(PointList list) {
            allEdgesIterator.setWayGeometry(list);
        }

        @Override
        public double getDistance() {
            return allEdgesIterator.getDistance();
        }

        @Override
        public void setDistance(double dist) {
            allEdgesIterator.setDistance(dist);
        }

        @Override
        public int getFlags() {
            return allEdgesIterator.getFlags();
        }

        @Override
        public void setFlags(int flags) {
            allEdgesIterator.setFlags(flags);
        }

        @Override
        public boolean isEmpty() {
            return allEdgesIterator.isEmpty();
        }

        @Override
        public String getName()
        {
            return allEdgesIterator.getName();
        }

        @Override
        public void setName( String name )
        {
            allEdgesIterator.setName(name);
        }
    }
}
