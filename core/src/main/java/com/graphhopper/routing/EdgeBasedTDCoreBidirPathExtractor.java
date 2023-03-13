package com.graphhopper.routing;

import com.graphhopper.routing.ch.ShortcutUnpacker;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.RoutingCHGraph;
import com.graphhopper.util.*;

public class EdgeBasedTDCoreBidirPathExtractor extends DefaultBidirPathExtractor {
    private final RoutingCHGraph routingGraph;
    private final ShortcutUnpacker shortcutUnpacker;
    private final Weighting weighting;

    protected EdgeBasedTDCoreBidirPathExtractor(RoutingCHGraph routingGraph, Weighting weighting) {
        // TODO ORS: this constructor is currently identical to EdgeBasedCHBidirPathExtractor
        super(routingGraph.getBaseGraph(), weighting);
        this.routingGraph = routingGraph;
        this.shortcutUnpacker = createShortcutUnpacker();
        this.weighting = routingGraph.getBaseGraph().wrapWeighting(routingGraph.getWeighting());
    }

    @Override
    protected void onEdge(int edge, int adjNode, boolean reverse, int prevOrNextEdge) {
        // TODO ORS: this method is currently identical to EdgeBasedCHBidirPathExtractor
        if (reverse)
            shortcutUnpacker.visitOriginalEdgesBwd(edge, adjNode, true, prevOrNextEdge);
        else
            shortcutUnpacker.visitOriginalEdgesFwd(edge, adjNode, true, prevOrNextEdge);
    }

    @Override
    protected void onMeetingPoint(int inEdge, int viaNode, int outEdge) {
        // no need to process any turns at meeting point
    }

    private ShortcutUnpacker createShortcutUnpacker() {
        return new ShortcutUnpacker(routingGraph, (edge, reverse, prevOrNextEdgeId) -> {
            path.addDistance(edge.getDistance());
            path.addTime(GHUtility.calcMillisWithTurnMillis(weighting, edge, reverse, prevOrNextEdgeId));
            path.addEdge(edge.getEdge());
        }, false); // Turn restrictions are handled in the core, hence, shortcuts
                             // have no turn restrictions and unpacking can be node based.
    }

    @Override
    protected SPTEntry followParentsUntilRoot(SPTEntry sptEntry, boolean reverse) {
        SPTEntry currEntry = sptEntry;
        SPTEntry parentEntry = currEntry.parent;
        while (EdgeIterator.Edge.isValid(currEntry.edge)) {
            onTdEdge(currEntry, reverse); // Here, TD differs from DefaultBidirPathExtractor
            currEntry = parentEntry;
            parentEntry = currEntry.parent;
        }
        return currEntry;
    }

    private void onTdEdge(SPTEntry currEdge, boolean bwd) {
        int edgeId = currEdge.edge;
        int adjNode = currEdge.adjNode;
        CHEdgeIteratorState edgeState = (CHEdgeIteratorState) routingGraph.getEdgeIteratorState(edgeId, adjNode);

        // Shortcuts do only contain valid weight, so first expand before adding
        // to distance and time
        if (edgeState.isShortcut()) {
            int edge = currEdge.parent.edge;
            onEdge(edgeId, adjNode, bwd, edge);
        } else {
            path.addDistance(edgeState.getDistance());
            path.addTime((bwd ? -1 : 1) * (currEdge.time - currEdge.parent.time));
            path.addEdge(edgeId);
        }
    }

}
