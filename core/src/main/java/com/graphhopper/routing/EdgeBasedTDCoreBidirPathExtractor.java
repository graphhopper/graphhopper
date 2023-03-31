package com.graphhopper.routing;

import com.graphhopper.routing.ch.ShortcutUnpacker;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.RoutingCHEdgeIteratorState;
import com.graphhopper.storage.RoutingCHGraph;
import com.graphhopper.util.*;

public class EdgeBasedTDCoreBidirPathExtractor extends DefaultBidirPathExtractor {
    private final RoutingCHGraph routingGraph;
    private final ShortcutUnpacker shortcutUnpacker;
    private final Weighting weighting;

    public static Path extractPath(RoutingCHGraph graph, Weighting weighting, SPTEntry fwdEntry, SPTEntry bwdEntry, double weight) {
        return (new EdgeBasedTDCoreBidirPathExtractor(graph, weighting)).extract(fwdEntry, bwdEntry, weight);
    }

    protected EdgeBasedTDCoreBidirPathExtractor(RoutingCHGraph routingGraph, Weighting weighting) {
        // TODO ORS: this constructor is currently identical to EdgeBasedCHBidirPathExtractor
        super(routingGraph.getBaseGraph(), weighting);
        this.routingGraph = routingGraph;
        this.shortcutUnpacker = createShortcutUnpacker();
        this.weighting = routingGraph.getBaseGraph().wrapWeighting(routingGraph.getWeighting());//FIXME: just use weighting
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
            path.addTime(GHUtility.calcMillisWithTurnMillis(weighting, edge, reverse, prevOrNextEdgeId));//FIXME: weighting.calcEdgeMillis(edge, reverse) should be enough here
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
        RoutingCHEdgeIteratorState edgeState = routingGraph.getEdgeIteratorState(edgeId, adjNode);

        // Shortcuts do only contain valid weight, so first expand before adding
        // to distance and time
        if (edgeState.isShortcut()) {
            int edge = currEdge.parent.edge;
            onEdge(edgeId, adjNode, bwd, edge);
        } else {
            EdgeIteratorState edge = routingGraph.getBaseGraph().getEdgeIteratorState(edgeState.getOrigEdge(), edgeState.getAdjNode());
            path.addDistance(edge.getDistance());
            path.addTime((bwd ? -1 : 1) * (currEdge.time - currEdge.parent.time));
            path.addEdge(edge.getEdge());
        }
    }

}
