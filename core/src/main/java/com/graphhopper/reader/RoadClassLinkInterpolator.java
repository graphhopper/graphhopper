package com.graphhopper.reader;

import com.graphhopper.coll.GHBitSet;
import com.graphhopper.coll.GHBitSetImpl;
import com.graphhopper.coll.GHTBitSet;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.BreadthFirstSearch;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIteratorState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.graphhopper.routing.util.EncodingManager.getKey;

/**
 * This class ensures that "xy_link" roads never have higher speeds than their adjacent "xy" part.
 */
public class RoadClassLinkInterpolator {
    private final Graph graph;
    private final List<DecimalEncodedValue> averageSpeeds;
    private final BooleanEncodedValue linkEnc;
    private final EnumEncodedValue<RoadClass> rcEnc;

    public RoadClassLinkInterpolator(Graph graph, EncodedValueLookup lookup, List<DecimalEncodedValue> averageSpeeds) {
        this.graph = graph;
        this.averageSpeeds = averageSpeeds;
        this.linkEnc = lookup.getBooleanEncodedValue(RoadClassLink.KEY);
        this.rcEnc = lookup.getEnumEncodedValue(RoadClass.KEY, RoadClass.class);
    }

    public static List<DecimalEncodedValue> collect(EncodingManager encodingManager) {
        return encodingManager.fetchEdgeEncoders().stream().map(encoder -> encodingManager.getDecimalEncodedValue(getKey(encoder.toString(), "average_speed"))).collect(Collectors.toList());
    }

    public void execute() {
        if (averageSpeeds.isEmpty())
            return;
        EdgeExplorer explorer = graph.createEdgeExplorer();
        AllEdgesIterator allEdgesIterator = graph.getAllEdges();
        final GHBitSet doneEdges = new GHBitSetImpl(graph.getNodes());
        while (allEdgesIterator.next()) {
            if (!allEdgesIterator.get(linkEnc) || doneEdges.contains(allEdgesIterator.getEdge()))
                continue;

            RoadClass currentRC = allEdgesIterator.get(rcEnc);
            // To handle also complex scenarios properly we pick the maximum speed only from neighboring roads with the
            // same road class. For now do not care about the direction.

            Map<DecimalEncodedValue, Double> maxSpeedMap = new HashMap<>();
            for (DecimalEncodedValue speedEnc : averageSpeeds) {
                maxSpeedMap.put(speedEnc, 0.0);
            }

            // 1. explore until the ends are reached - potentially more than two ends and collect the maximum speeds per EncodedValue
            BreadthFirstSearch search = new BreadthFirstSearch() {

                @Override
                protected GHBitSet createBitSet() {
                    return new GHTBitSet(50);
                }

                protected boolean checkAdjacent(EdgeIteratorState bfsEdge) {
                    boolean sameRC = bfsEdge.get(rcEnc) == currentRC;
                    boolean isLink = bfsEdge.get(linkEnc);
                    if (sameRC && !isLink) {
                        doneEdges.add(bfsEdge.getEdge());
                        for (DecimalEncodedValue speedEnc : averageSpeeds) {
                            double maxSpeed = maxSpeedMap.get(speedEnc);
                            maxSpeed = Math.max(Math.max(maxSpeed, bfsEdge.get(speedEnc)), bfsEdge.getReverse(speedEnc));
                            maxSpeedMap.put(speedEnc, maxSpeed);
                        }
                    }
                    return sameRC && isLink;
                }
            };
            search.start(explorer, allEdgesIterator.getAdjNode());

            maxSpeedMap.values().removeIf(tmp -> tmp <= 0);
            if(maxSpeedMap.isEmpty())
                continue;

            // 2. store max speed for different encoder (ensure encoders without access are excluded from this interpolation)
            search = new BreadthFirstSearch() {

                @Override
                protected GHBitSet createBitSet() {
                    return new GHTBitSet(50);
                }

                protected boolean checkAdjacent(EdgeIteratorState bfsEdge) {
                    boolean sameRC = bfsEdge.get(rcEnc) == currentRC;
                    boolean isLink = bfsEdge.get(linkEnc);
                    if (sameRC && isLink) {
                        boolean reverse = bfsEdge.get(EdgeIteratorState.REVERSE_STATE);
                        for (Map.Entry<DecimalEncodedValue, Double> entry : maxSpeedMap.entrySet()) {
                            if (!reverse || entry.getKey().isStoreTwoDirections()) {
                                double tmp = bfsEdge.get(entry.getKey());
                                if (tmp > 0)
                                    bfsEdge.set(entry.getKey(), Math.min(tmp, entry.getValue()));
                            }

                            if (reverse || entry.getKey().isStoreTwoDirections()) {
                                double tmp = bfsEdge.getReverse(entry.getKey());
                                if (tmp > 0)
                                    bfsEdge.setReverse(entry.getKey(), Math.min(tmp, entry.getValue()));
                            }
                        }
                    }
                    return sameRC && isLink;
                }
            };
            search.start(explorer, allEdgesIterator.getAdjNode());
        }
    }
}
