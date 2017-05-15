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

package com.graphhopper.reader.gtfs;

import com.conveyal.gtfs.GTFSFeed;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.EdgeIteratorState;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.triangulate.ConformingDelaunayTriangulator;
import com.vividsolutions.jts.triangulate.ConstraintVertex;
import com.vividsolutions.jts.triangulate.quadedge.QuadEdge;
import com.vividsolutions.jts.triangulate.quadedge.QuadEdgeSubdivision;
import com.vividsolutions.jts.triangulate.quadedge.Vertex;

import java.util.*;

class FakeWalkNetworkBuilder {

    static void buildWalkNetwork(Collection<GTFSFeed> feeds, GraphHopperStorage graph, PtFlagEncoder encoder, DistanceCalc distCalc) {
        Collection<ConstraintVertex> sites = new ArrayList<>();
        Map<Vertex, Integer> vertex2nodeId = new HashMap<>();
        feeds.stream().flatMap(feed -> feed.stops.values().stream()).forEach( stop -> {
            int i = graph.getNodes();
            graph.getNodeAccess().setNode(i++, stop.stop_lat, stop.stop_lon);
            ConstraintVertex site = new ConstraintVertex(new Coordinate(stop.stop_lon,stop.stop_lat));
            sites.add(site);
            vertex2nodeId.put(site, i-1);
        });
        ConformingDelaunayTriangulator conformingDelaunayTriangulator = new ConformingDelaunayTriangulator(sites, 0.0);
        conformingDelaunayTriangulator.setConstraints(new ArrayList(), new ArrayList());
        conformingDelaunayTriangulator.formInitialDelaunay();
        QuadEdgeSubdivision tin = conformingDelaunayTriangulator.getSubdivision();
        List<QuadEdge> edges = tin.getPrimaryEdges(false);
        for (QuadEdge edge : edges) {
            EdgeIteratorState ghEdge = graph.edge(vertex2nodeId.get(edge.orig()), vertex2nodeId.get(edge.dest()));
            double distance = distCalc.calcDist(
                    edge.orig().getY(),
                    edge.orig().getX(),
                    edge.dest().getY(),
                    edge.dest().getX());
            ghEdge.setDistance(distance);
            ghEdge.setFlags(encoder.setSpeed(ghEdge.getFlags(), 5.0));
            ghEdge.setFlags(encoder.setAccess(ghEdge.getFlags(), true, true));
        }
    }


}
