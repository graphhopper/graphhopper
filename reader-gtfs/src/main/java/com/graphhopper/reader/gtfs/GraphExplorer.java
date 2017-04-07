package com.graphhopper.reader.gtfs;

import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;

import java.util.Iterator;

final class GraphExplorer {

    private final EdgeExplorer edgeExplorer;
    private final PtFlagEncoder flagEncoder;
    private final GtfsStorage gtfsStorage;
    private final RealtimeFeed realtimeFeed;
    private final boolean reverse;

    GraphExplorer(Graph graph, PtFlagEncoder flagEncoder, GtfsStorage gtfsStorage, RealtimeFeed realtimeFeed, boolean reverse) {
        this.edgeExplorer = graph.createEdgeExplorer(new DefaultEdgeFilter(flagEncoder, reverse, !reverse));
        this.flagEncoder = flagEncoder;
        this.gtfsStorage = gtfsStorage;
        this.realtimeFeed = realtimeFeed;
        this.reverse = reverse;
    }

    Iterable<EdgeIteratorState> exploreEdgesAround(Label label) {
        return new Iterable<EdgeIteratorState>() {
            EdgeIterator edgeIterator = edgeExplorer.setBaseNode(label.adjNode);

            @Override
            public Iterator<EdgeIteratorState> iterator() {
                return new Iterator<EdgeIteratorState>() {
                    boolean foundEnteredTimeExpandedNetworkEdge = false;

                    @Override
                    public boolean hasNext() {
                        while(edgeIterator.next()) {
                            final GtfsStorage.EdgeType edgeType = flagEncoder.getEdgeType(edgeIterator.getFlags());
                            final int trafficDay = (int) (label.currentTime / (24 * 60 * 60));
                            if (!isValidOn(edgeIterator, trafficDay)) {
                                continue;
                            }
                            if (realtimeFeed.isBlocked(edgeIterator.getEdge())) {
                                continue;
                            }
                            if (edgeType == GtfsStorage.EdgeType.ENTER_TIME_EXPANDED_NETWORK && !reverse) {
                                if ((int) (label.currentTime) % (24 * 60 * 60) > flagEncoder.getTime(edgeIterator.getFlags())) {
                                    continue;
                                } else {
                                    if (foundEnteredTimeExpandedNetworkEdge) {
                                        continue;
                                    } else {
                                        foundEnteredTimeExpandedNetworkEdge = true;
                                    }
                                }
                            } else if (edgeType == GtfsStorage.EdgeType.LEAVE_TIME_EXPANDED_NETWORK && reverse) {
                                if ((int) (label.currentTime) % (24 * 60 * 60) < flagEncoder.getTime(edgeIterator.getFlags())) {
                                    continue;
                                }
                            }
                            return true;
                        }
                        return false;
                    }

                    @Override
                    public EdgeIteratorState next() {
                        return edgeIterator;
                    }
                };
            }
        };
    }

    private boolean isValidOn(EdgeIteratorState edge, int trafficDay) {
        return gtfsStorage.getValidities().get(flagEncoder.getValidityId(edge.getFlags())).get(trafficDay);
    }

}
