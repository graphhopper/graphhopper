package com.graphhopper.reader.gtfs;

import com.conveyal.gtfs.GTFSFeed;
import com.conveyal.gtfs.model.Stop;
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

    static void buildWalkNetwork(List<GTFSFeed> feeds, GraphHopperStorage graph, PtFlagEncoder encoder, DistanceCalc distCalc) {
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
