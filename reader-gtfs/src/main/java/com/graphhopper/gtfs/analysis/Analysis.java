package com.graphhopper.gtfs.analysis;

import com.carrotsearch.hppc.BitSetIterator;
import com.carrotsearch.hppc.IntArrayList;
import com.carrotsearch.hppc.cursors.IntCursor;
import com.graphhopper.gtfs.GtfsStorage;
import com.graphhopper.gtfs.PtGraph;
import com.graphhopper.routing.subnetwork.TarjanSCC;
import com.graphhopper.routing.util.EdgeFilter;

import java.util.*;

import static com.graphhopper.gtfs.GtfsStorage.EdgeType.ENTER_PT;

public class Analysis {


    public static List<List<GtfsStorage.FeedIdWithStopId>> findStronglyConnectedComponentsOfStopGraph(PtGraph ptGraph) {
        PtGraphAsAdjacencyList ptGraphAsAdjacencyList = new PtGraphAsAdjacencyList(ptGraph);
        TarjanSCC.ConnectedComponents components = TarjanSCC.findComponents(ptGraphAsAdjacencyList, EdgeFilter.ALL_EDGES, false);
        List<List<GtfsStorage.FeedIdWithStopId>> stronglyConnectedComponentsOfStopGraph = new ArrayList<>();
        for (IntArrayList component : components.getComponents()) {
            ArrayList<GtfsStorage.FeedIdWithStopId> stopsOfComponent = new ArrayList<>();
            for (IntCursor intCursor : component) {
                stopsOfComponent.addAll(getStopsForNode(ptGraph, intCursor.value));
            }
            if (!stopsOfComponent.isEmpty()) {
                stronglyConnectedComponentsOfStopGraph.add(stopsOfComponent);
            }
        }
        BitSetIterator iter = components.getSingleNodeComponents().iterator();
        for (int i = iter.nextSetBit(); i >= 0; i = iter.nextSetBit()) {
            List<GtfsStorage.FeedIdWithStopId> stopsForNode = getStopsForNode(ptGraph, i);
            if (!stopsForNode.isEmpty()) {
                stronglyConnectedComponentsOfStopGraph.add(stopsForNode);
            }
        }
        return stronglyConnectedComponentsOfStopGraph;
    }

    public static List<GtfsStorage.FeedIdWithStopId> getStopsForNode(PtGraph ptGraph, int i) {
        EnumSet<GtfsStorage.EdgeType> inEdgeTypes = EnumSet.noneOf(GtfsStorage.EdgeType.class);
        for (PtGraph.PtEdge ptEdge : ptGraph.backEdgesAround(i)) {
            inEdgeTypes.add(ptEdge.getType());
        }
        EnumSet<GtfsStorage.EdgeType> outEdgeTypes = EnumSet.noneOf(GtfsStorage.EdgeType.class);
        for (PtGraph.PtEdge ptEdge : ptGraph.edgesAround(i)) {
            outEdgeTypes.add(ptEdge.getType());
        }
        if (inEdgeTypes.equals(EnumSet.of(GtfsStorage.EdgeType.EXIT_PT)) && outEdgeTypes.equals((EnumSet.of(ENTER_PT)))) {
            Set<GtfsStorage.FeedIdWithStopId> stops = new HashSet<>();
            ptGraph.backEdgesAround(i).forEach(e -> stops.add(new GtfsStorage.FeedIdWithStopId(e.getAttrs().platformDescriptor.feed_id, e.getAttrs().platformDescriptor.stop_id)));
            ptGraph.edgesAround(i).forEach(e -> stops.add(new GtfsStorage.FeedIdWithStopId(e.getAttrs().platformDescriptor.feed_id, e.getAttrs().platformDescriptor.stop_id)));
            return new ArrayList<>(stops);
        } else {
            return Collections.emptyList();
        }
    }
}
