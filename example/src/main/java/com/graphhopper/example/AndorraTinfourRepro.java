package com.graphhopper.example;

import com.graphhopper.GraphHopper;
import com.graphhopper.config.Profile;
import com.graphhopper.isochrone.algorithm.ShortestPathTree;
import com.graphhopper.routing.ev.Subnetwork;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.util.DefaultSnapFilter;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.*;
import org.tinfour.common.Vertex;
import org.tinfour.contour.ContourBuilderForTin;
import org.tinfour.contour.ContourRegion;
import org.tinfour.standard.IncrementalTin;
import org.tinfour.utils.HilbertSort;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

public class AndorraTinfourRepro {
    public static void main(String[] args) {
        GraphHopper hopper = new GraphHopper();
        hopper.setOSMFile("core/files/andorra.osm.pbf");
        hopper.setGraphHopperLocation("target/andorra-tinfour-cache");
        hopper.setEncodedValuesString("car_access, road_environment, ferry_speed, car_average_speed, max_speed");
        hopper.setProfiles(new Profile("fast_car").setCustomModel(GHUtility.loadCustomModelFromJar("car.json"))
                .setTurnCostsConfig(TurnCostsConfig.car()));
        hopper.importOrLoad();

        Weighting weighting = hopper.createWeighting(hopper.getProfile("fast_car"), new PMap());
        Snap snap = hopper.getLocationIndex().findClosest(42.531073, 1.573792,
                new DefaultSnapFilter(weighting, hopper.getEncodingManager().getBooleanEncodedValue(Subnetwork.key("fast_car"))));
        QueryGraph queryGraph = QueryGraph.create(hopper.getBaseGraph(), snap);
        NodeAccess na = queryGraph.getNodeAccess();

        ShortestPathTree spt = new ShortestPathTree(queryGraph, queryGraph.wrapWeighting(weighting), false, TraversalMode.EDGE_BASED);
        double limit = 5 * 60 * 1000d;
        spt.setTimeLimit(limit + Math.max(limit * 0.14, 200_000));
        ToDoubleFunction<ShortestPathTree.IsoLabel> fz = l -> l.time;
        List<Double> zs = List.of(limit / 2, limit);

        final List<Vertex> verts = new ArrayList<>();
        StopWatch sw = new StopWatch().start();
        spt.search(snap.getClosestNode(), label -> {
            double ev = fz.applyAsDouble(label);
            verts.add(new Vertex(na.getLon(label.node), na.getLat(label.node), ev));
            if (label.parent != null) {
                EdgeIteratorState edge = queryGraph.getEdgeIteratorState(label.edge, label.node);
                PointList inner = edge.fetchWayGeometry(FetchMode.PILLAR_ONLY);
                if (inner.size() > 0) {
                    int mid = inner.size() / 2;
                    if (inner.size() % 2 == 0 && edge.get(EdgeIteratorState.REVERSE_STATE)) mid -= 1;
                    verts.add(new Vertex(inner.getLon(mid), inner.getLat(mid), ev));
                }
            }
        });
        System.out.println("search+collect: " + sw.stop().getMillis() + "ms, verts=" + verts.size());

        sw = new StopWatch().start();
        new HilbertSort().sort(verts);
        // nominal point spacing must match the coordinate scale (degrees), else Tinfour treats
        // all points as coincident and fails to bootstrap. Estimate from bbox + count.
        double xMin = Double.MAX_VALUE, xMax = -Double.MAX_VALUE, yMin = Double.MAX_VALUE, yMax = -Double.MAX_VALUE;
        for (Vertex v : verts) {
            xMin = Math.min(xMin, v.getX()); xMax = Math.max(xMax, v.getX());
            yMin = Math.min(yMin, v.getY()); yMax = Math.max(yMax, v.getY());
        }
        double spacing = Math.sqrt(Math.max(1e-12, (xMax - xMin) * (yMax - yMin)) / Math.max(1, verts.size()));
        System.out.println("bbox=" + (xMax - xMin) + "x" + (yMax - yMin) + " spacing=" + spacing);
        IncrementalTin tin = new IncrementalTin(spacing);
        tin.add(verts, null);
        System.out.println("tin build: " + sw.stop().getMillis() + "ms, bootstrapped=" + tin.isBootstrapped());

        for (double z : zs) {
            sw = new StopWatch().start();
            ContourBuilderForTin cbt = new ContourBuilderForTin(tin, null, new double[]{z}, true);
            List<ContourRegion> regions = cbt.getRegions();
            int idx0 = 0;
            double area = 0;
            for (ContourRegion r : regions) {
                if (r.getRegionIndex() == 0) { idx0++; area += Math.abs(r.getArea()); }
            }
            System.out.println("z=" + z + " contour: " + sw.stop().getMillis() + "ms, regions=" + regions.size() + " idx0=" + idx0 + " area=" + area);
        }
        System.out.println("DONE");
    }
}
