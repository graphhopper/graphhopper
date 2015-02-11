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
package com.graphhopper.matching;

import com.graphhopper.routing.Dijkstra;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.QueryGraph;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.FastestWeighting;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.util.Weighting;
import com.graphhopper.storage.EdgeEntry;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.*;
import gnu.trove.map.hash.TIntDoubleHashMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.procedure.TIntObjectProcedure;
import gnu.trove.set.hash.TIntHashSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * This class matches real world GPX entries to the digital road network stored
 * in GraphHopper. The algorithm is a simple 4 phase process:
 * <p>
 * <ol>
 * <li>Lookup Phase: Find some closest edges for every GPX entry</li>
 * <li>Custom Weighting Phase: Create a weighting object where those edges will
 * be preferred</li>
 * <li>Search Phase: Calculate the path and its list of edges from the best
 * start to the best end edge</li>
 * <li>Match Phase: Associate all GPX entries for every edge</li>
 * </ol>
 * <p>
 *
 * Note: currently tested with very close GPX points only. Will fail if best
 * match for start or end node is incorrect. Performance improvements possible
 * if not the full but only partial routes are calculated this will also improve
 * accuracy as currently all loops in a GPX trail are automatically removed.
 * <p>
 * @see http://en.wikipedia.org/wiki/Map_matching
 * @author Peter Karich
 */
public class MapMatching {

    private final Graph graph;
    private final LocationIndexMatch locationIndex;
    private final FlagEncoder encoder;
    private TraversalMode traversalMode;
    // we split the incoming list into smaller parts (hopefully) without loops
    // later we'll detect loops and insert the correctly detected road recursivly
    // see #1
    private double separatedSearchDistance = 300;
    // e.g. 5 as multiplier is not sufficient as we guess the weight via distance weighting.getMinWeight(distance)
    // should we use the 'visited nodes' as more generic limit?
    private double maxSearchMultiplier = 50;
    private final int nodeCount;
    private DistanceCalc distanceCalc = new DistancePlaneProjection();
    private static final Comparator<QueryResult> CLOSEST_MATCH = new Comparator<QueryResult>() {
        @Override
        public int compare(QueryResult o1, QueryResult o2) {
            return Double.compare(o1.getQueryDistance(), o2.getQueryDistance());
        }
    };

    public MapMatching(Graph graph, LocationIndexMatch locationIndex, FlagEncoder encoder) {
        this.graph = graph;
        this.nodeCount = graph.getNodes();
        this.locationIndex = locationIndex;

        // TODO initialization of start values for the algorithm is currently done explicitely via node IDs! 
        // To fix this use instead: traversalMode.createTraversalId(iter, false);
//        this.traversalMode = graph.getExtension() instanceof TurnCostExtension
//                ? TraversalMode.EDGE_BASED_2DIR : TraversalMode.NODE_BASED;                
        this.traversalMode = TraversalMode.NODE_BASED;
        this.encoder = encoder;
    }

    public void setDistanceCalc(DistanceCalc distanceCalc) {
        this.distanceCalc = distanceCalc;
    }

    /**
     * Specify the length of the route parts to improve map matching in case of
     * loops in meter. Use -1 if no route splitting should happen. Default is
     * 500m
     */
    public MapMatching setSeparatedSearchDistance(int separatedSearchDistance) {
        this.separatedSearchDistance = separatedSearchDistance;
        return this;
    }

    public void setMaxSearchMultiplier(int maxSearchMultiplier) {
        this.maxSearchMultiplier = maxSearchMultiplier;
    }

    /**
     * This method does the actual map matchting.
     * <p>
     * @param gpxList the input list with GPX points which should match to edges
     * of the graph specified in the constructor
     */
    public MatchResult doWork(List<GPXEntry> gpxList) {
        int currentIndex = 0;
        if (gpxList.size() < 2) {
            throw new IllegalStateException("gpx list needs at least 2 points!");
        }

        List<QueryResult> firstQueryResults = new ArrayList<QueryResult>();
        List<EdgeMatch> edgeMatches = new ArrayList<EdgeMatch>();
        MatchResult matchResult = new MatchResult(edgeMatches);
        while (true) {
            int separatedListStartIndex = currentIndex;
            int separatedListEndIndex = separatedListStartIndex + 1;
            GPXEntry prevEntry = gpxList.get(separatedListStartIndex);
            double gpxLength = 0;
            while (separatedListEndIndex < gpxList.size()) {
                GPXEntry entry = gpxList.get(separatedListEndIndex);
                gpxLength += distanceCalc.calcDist(prevEntry.lat, prevEntry.lon, entry.lat, entry.lon);
                prevEntry = entry;
                separatedListEndIndex++;
                if (separatedSearchDistance > 0 && gpxLength > separatedSearchDistance) {
                    // avoid that last sublist is only 1 point and include it in current list
                    if (gpxList.size() - separatedListEndIndex == 1) {
                        continue;
                    }

                    break;
                }
            }

            currentIndex = separatedListEndIndex;
            List<GPXEntry> gpxSublist = gpxList.subList(separatedListStartIndex, separatedListEndIndex);

            if (gpxSublist.size() < 2) {
                throw new IllegalStateException("GPX sublist is too short: "
                        + gpxSublist + " taken from [" + separatedListStartIndex + "," + separatedListEndIndex + ") " + gpxList.size());
            }

            boolean doEnd = currentIndex >= gpxList.size();
            MatchResult subMatch = doWork(firstQueryResults, gpxSublist, gpxLength, doEnd);
            List<EdgeMatch> result = subMatch.getEdgeMatches();
            matchResult.setMatchLength(matchResult.getMatchLength() + subMatch.getMatchLength());
            matchResult.setMatchMillis(matchResult.getMatchMillis() + subMatch.getMatchMillis());

            // remove later
            check(result);

            // no merging necessary as end of old and new start GPXExtension & edge should be identical
            for (int i = 0; i < result.size(); i++) {
                EdgeMatch currEM = result.get(i);

                if (i == 0 && !edgeMatches.isEmpty()) {
                    // skip edge if we would introduce a u-turn, see testAvoidOffRoadUTurns
                    EdgeMatch lastEdgeMatch = edgeMatches.get(edgeMatches.size() - 1);
                    if (lastEdgeMatch.getEdgeState().getAdjNode() == currEM.getEdgeState().getAdjNode()) {
                        continue;
                    }
                }

                edgeMatches.add(currEM);
            }

            if (doEnd) {
                break;
            }
        }

        //////// Calculate stats to determine quality of matching //////// 
        double gpxLength = 0;
        GPXEntry prevEntry = gpxList.get(0);
        for (int i = 1; i < gpxList.size(); i++) {
            GPXEntry entry = gpxList.get(i);
            gpxLength += distanceCalc.calcDist(prevEntry.lat, prevEntry.lon, entry.lat, entry.lon);
            prevEntry = entry;
        }

        long gpxMillis = gpxList.get(gpxList.size() - 1).getMillis() - gpxList.get(0).getMillis();
        matchResult.setGPXEntriesMillis(gpxMillis);
        matchResult.setGPXEntriesLength(gpxLength);

        // remove later
        check(matchResult.getEdgeMatches());

        return matchResult;
    }

    /**
     * This method creates a matching for the specified sublist, it uses the
     * firstQueryResults to do the initialization for the start nodes, or just a
     * locationIndex lookup if none.
     *
     * @param doEnd the very last virtual edges is always removed, except if
     * doEnd is true, then the original edge is added
     */
    MatchResult doWork(List<QueryResult> firstQueryResults,
            List<GPXEntry> gpxList, double gpxLength, boolean doEnd) {
        int guessedEdgesPerPoint = 4;
        List<EdgeMatch> edgeMatches = new ArrayList<EdgeMatch>();
        final TIntObjectHashMap<List<GPXExtension>> extensionMap
                = new TIntObjectHashMap<List<GPXExtension>>(gpxList.size() * guessedEdgesPerPoint, 0.5f, -1);
        final TIntDoubleHashMap minFactorMap = new TIntDoubleHashMap(gpxList.size() * guessedEdgesPerPoint, 0.5f, -1, -1);
        EdgeFilter edgeFilter = new DefaultEdgeFilter(encoder);
        int startIndex = -1, endIndex = -1;
        List<QueryResult> startQRList = null, endQRList = null;

        //////// Lookup Phase (1) ////////
        for (int gpxIndex = 0; gpxIndex < gpxList.size(); gpxIndex++) {
            GPXEntry entry = gpxList.get(gpxIndex);

            List<QueryResult> qResults = gpxIndex == 0 && !firstQueryResults.isEmpty()
                    ? firstQueryResults
                    : locationIndex.findNClosest(entry.lat, entry.lon, edgeFilter);

            if (qResults.isEmpty()) {
                // throw new IllegalStateException("no match found for " + entry);
                continue;
            }

            if (startIndex < 0) {
                startIndex = gpxIndex;
                startQRList = qResults;
            } else {
                endIndex = gpxIndex;
                endQRList = qResults;
            }

            for (int matchIndex = 0; matchIndex < qResults.size(); matchIndex++) {
                QueryResult qr = qResults.get(matchIndex);
                int edge = qr.getClosestEdge().getEdge();
                List<GPXExtension> extensionList = extensionMap.get(edge);
                if (extensionList == null) {
                    extensionList = new ArrayList(5);
                    extensionMap.put(edge, extensionList);
                }

                extensionList.add(new GPXExtension(entry, qr, gpxIndex));
            }
        }

        if (startQRList == null || endQRList == null) {
            throw new IllegalArgumentException("Input GPX list does not contain valid points "
                    + "or outside of imported area!? " + gpxList.size() + ", " + gpxList);
        }

        // sort by distance to closest edge
        Collections.sort(startQRList, CLOSEST_MATCH);
        Collections.sort(endQRList, CLOSEST_MATCH);

        //////// Custom Weighting Phase (2) ////////
        final DoubleRef maxWeight = new DoubleRef(0);
        FastestWeighting customWeighting = new FastestWeighting(encoder) {
            @Override
            public double calcWeight(EdgeIteratorState edge, boolean reverse, int prevOrNextEdgeId) {
                double matchFactor = minFactorMap.get(edge.getEdge());
                double weight = super.calcWeight(edge, reverse, prevOrNextEdgeId);
                if (matchFactor < 0) {
                    return maxWeight.value * weight;
                }

                return matchFactor * weight;
            }
        };

        QueryGraph queryGraph = new QueryGraph(graph);
        List<QueryResult> allQRs = new ArrayList<QueryResult>();
        allQRs.addAll(startQRList);
        allQRs.addAll(endQRList);
        queryGraph.lookup(allQRs);
        EdgeExplorer explorer = queryGraph.createEdgeExplorer(edgeFilter);

        // every virtual edge maps to its real edge where the orientation is already correct!
        TIntObjectHashMap<EdgeIteratorState> virtualEdgesMap = new TIntObjectHashMap<EdgeIteratorState>();

        // make new virtual edges from QueryGraph also available in minDistanceMap and prefer them
        for (QueryResult qr : startQRList) {
            fillVirtualEdges(minFactorMap, virtualEdgesMap, explorer, qr);
        }
        for (QueryResult qr : endQRList) {
            fillVirtualEdges(minFactorMap, virtualEdgesMap, explorer, qr);
        }

        extensionMap.forEachEntry(new TIntObjectProcedure<List<GPXExtension>>() {
            @Override
            public boolean execute(int edge, List<GPXExtension> list) {
                double minimumDist = Double.MAX_VALUE;
                for (GPXExtension ext : list) {
                    if (ext.queryResult.getQueryDistance() < minimumDist) {
                        minimumDist = ext.queryResult.getQueryDistance();
                    }
                }

                // Prefer close match, prefer direct match (small minimumMatchIndex) and many GPX points.
                // And '+0.5' to avoid extreme decrease in case of a match close to a tower node
                double weight = minimumDist + .5;
                if (weight > maxWeight.value) {
                    maxWeight.value = weight;
                }
                minFactorMap.put(edge, weight);
                return true;
            }
        });

        TIntHashSet goalSet = new TIntHashSet(endQRList.size());
        for (QueryResult qr : endQRList) {
            goalSet.add(qr.getClosestNode());
        }

        //////// Search Phase (3) ////////
        CustomDijkstra algo = new CustomDijkstra(goalSet, queryGraph, encoder, customWeighting, traversalMode);
        algo.setWeightLimit(customWeighting.getMinWeight(gpxLength * maxSearchMultiplier));

        // Set an approximative weight for start nodes.
        // The method initFrom uses minimum weight if two QueryResult edges share same node        
        for (QueryResult qr : startQRList) {
            double distance = distanceCalc.calcDist(qr.getQueryPoint().getLat(), qr.getQueryPoint().getLon(),
                    qr.getSnappedPoint().getLat(), qr.getSnappedPoint().getLon());

            // TODO take speed from edge instead of taking default speed and reducing it via maxSearchMultiplier        
            // encoder.getSpeed(qr.getClosestEdge().getFlags())
            algo.initFrom(qr.getClosestNode(), customWeighting.getMinWeight(distance * maxSearchMultiplier));
        }

        algo.runAlgo();
        if (!algo.oneNodeWasReached()) {
            throw new RuntimeException("Cannot find matching path! Missing or old OpenStreetMap data? "
                    + gpxList.size() + ", " + startQRList + ", " + endQRList);
        }

        // choose a good end point i.e. close to query point but also close to the start points
        Path path = algo.extractPath(endQRList);
        List<EdgeIteratorState> pathEdgeList = path.calcEdges();

        if (pathEdgeList.isEmpty()) {
            throw new RuntimeException("Cannot extract path - no edges returned? "
                    + gpxList.size() + ", " + startQRList + ", " + endQRList);
        }

        // only in the first run of doWork firstQueryResults.clear() won't clear 'startQRList' too:
        firstQueryResults.clear();
        int lastMatchNode = pathEdgeList.get(pathEdgeList.size() - 1).getAdjNode();
        for (QueryResult qr : endQRList) {
            if (qr.getClosestNode() == lastMatchNode) {
                firstQueryResults.add(qr);
            }
        }

        if (firstQueryResults.isEmpty()) {
            throw new RuntimeException("No start query results for next iteration specified! "
                    + ", edges:" + pathEdgeList.size() + ", entries:" + gpxList.size()
                    // startQRs is empty as we called firstQueryResults.clear()
                    + ", all results:" + allQRs + ", end results:" + endQRList);
        }

        //
        // replace virtual edges with original *full edge* at start and end!
        List<EdgeIteratorState> list = new ArrayList<EdgeIteratorState>(pathEdgeList.size());
        for (EdgeIteratorState es : pathEdgeList) {
            // skip edges with virtual adjacent node => which are either incoming edges from end-QueryResult
            // or ignorable bridge edges from start-QueryResult with two virtual nodes                        
            // good: outgoding edges from end-QueryResults are adding => no problem if path includes end-QueryResult
            if (!isVirtualNode(es.getAdjNode())) {
                EdgeIteratorState realEdge = virtualEdgesMap.get(es.getEdge());
                if (realEdge == null) {
                    list.add(es);
                } else {
                    if (list.isEmpty() || list.get(0).getEdge() != realEdge.getEdge()) {
                        list.add(realEdge);
                    }
                }
            }
        }
        if (doEnd) {
            // add very last edge
            EdgeIteratorState es = pathEdgeList.get(pathEdgeList.size() - 1);
            if (isVirtualNode(es.getAdjNode())) {
                EdgeIteratorState realEdge = virtualEdgesMap.get(es.getEdge());
                if (list.isEmpty() || list.get(0).getEdge() != realEdge.getEdge()) {
                    list.add(realEdge.detach(true));
                }
            }
        }
        pathEdgeList = list;

        //////// Match Phase (4) ////////
        int minGPXIndex = startIndex;
        for (EdgeIteratorState edge : pathEdgeList) {
            List<GPXExtension> gpxExtensionList = extensionMap.get(edge.getEdge());
            if (gpxExtensionList == null) {
                edgeMatches.add(new EdgeMatch(edge, Collections.<GPXExtension>emptyList()));
                continue;
            }

            List<GPXExtension> clonedList = new ArrayList<GPXExtension>(gpxExtensionList.size());
            // skip GPXExtensions with too small index otherwise EdgeMatch could go into the past
            int newMinGPXIndex = minGPXIndex;
            for (GPXExtension ext : gpxExtensionList) {
                if (ext.gpxListIndex > minGPXIndex) {
                    clonedList.add(ext);
                    if (newMinGPXIndex < ext.gpxListIndex) {
                        newMinGPXIndex = ext.gpxListIndex;
                    }
                }
            }
            minGPXIndex = newMinGPXIndex;
            EdgeMatch edgeMatch = new EdgeMatch(edge, clonedList);
            edgeMatches.add(edgeMatch);
        }

        MatchResult res = new MatchResult(edgeMatches);
        res.setMatchLength(path.getDistance());
        res.setMatchMillis(path.getMillis());

        return res;
    }

    private boolean isVirtualNode(int node) {
        return node >= nodeCount;
    }

    private static class DoubleRef {

        double value;

        public DoubleRef(double value) {
            this.value = value;
        }
    }

    // make some methods public
    private class CustomDijkstra extends Dijkstra {

        private final TIntHashSet goalNodeSet;
        private boolean oneNodeWasReached = false;

        public CustomDijkstra(TIntHashSet goalNodeSet, Graph g, FlagEncoder encoder, Weighting weighting, TraversalMode tMode) {
            super(g, encoder, weighting, tMode);
            this.goalNodeSet = goalNodeSet;
        }

        public void initFrom(int node, double weight) {
            EdgeEntry entry = createEdgeEntry(node, weight);
            if (currEdge == null || currEdge.weight > weight) {
                currEdge = entry;
            }

            EdgeEntry old = fromMap.get(node);
            if (old == null || old.weight > weight) {
                fromHeap.add(entry);
                fromMap.put(node, entry);
            }
        }

        @Override
        public void runAlgo() {
            checkAlreadyRun();
            super.runAlgo();
        }

        boolean oneNodeWasReached() {
            return oneNodeWasReached;
        }

        @Override
        protected boolean finished() {
            if (goalNodeSet.remove(currEdge.adjNode)) {
                oneNodeWasReached = true;
                if (goalNodeSet.isEmpty()) {
                    return true;
                }
            }
            return false;
        }

        public Path extractPath(Collection<QueryResult> endQRs) {
            // pick QueryResult closest to last GPX entry
            // => prefer QueryResults close to the edge
            double bestWeight = Double.MAX_VALUE;
            for (QueryResult qr : endQRs) {
                int node = qr.getClosestNode();
                EdgeEntry tmp1 = fromMap.get(node);
                double w = weighting.getMinWeight(qr.getQueryDistance() * maxSearchMultiplier);
                if (tmp1 != null && bestWeight > tmp1.weight + w) {
                    currEdge = tmp1;
                    bestWeight = tmp1.weight + w;
                }
            }

            return new Path(graph, flagEncoder).setWeight(currEdge.weight).setEdgeEntry(currEdge).extract();
        }
    }

    /**
     * Fills the minFactorMap with weights for the virtual edges.
     */
    private void fillVirtualEdges(TIntDoubleHashMap minFactorMap,
            TIntObjectHashMap<EdgeIteratorState> virtualEdgesMap,
            EdgeExplorer explorer, QueryResult qr) {
        EdgeIterator iter = explorer.setBaseNode(qr.getClosestNode());
        while (iter.next()) {
            if (isVirtualNode(qr.getClosestNode())) {
                if (traverseToClosestRealAdj(explorer, iter) == qr.getClosestEdge().getAdjNode()) {
                    virtualEdgesMap.put(iter.getEdge(), qr.getClosestEdge());
                } else {
                    virtualEdgesMap.put(iter.getEdge(), qr.getClosestEdge().detach(true));
                }
            }

            double dist = minFactorMap.get(iter.getEdge());
            if (dist < 0 || dist > qr.getQueryDistance()) {
                minFactorMap.put(iter.getEdge(), qr.getQueryDistance() + 0.5);
            }
        }
    }

    private int traverseToClosestRealAdj(EdgeExplorer explorer, EdgeIteratorState edge) {
        if (!isVirtualNode(edge.getAdjNode())) {
            return edge.getAdjNode();
        }

        EdgeIterator iter = explorer.setBaseNode(edge.getAdjNode());
        while (iter.next()) {
            if (iter.getAdjNode() != edge.getBaseNode()) {
                return traverseToClosestRealAdj(explorer, iter);
            }
        }
        throw new IllegalStateException("Cannot find adjacent edge " + edge);
    }

    private void check(List<EdgeMatch> emList) {
        int prevNode = -1;
        int prevEdge = -1;
        List<String> errors = new ArrayList<String>();
        for (EdgeMatch em : emList) {
            EdgeIteratorState es = em.getEdgeState();
            if (prevNode >= 0) {
                if (es.getBaseNode() != prevNode) {
                    errors.add("wrong orientation:" + es.getName() + ":" + es.getBaseNode() + "->" + es.getAdjNode() /*+ ", " + es.fetchWayGeometry(3)*/);
                }
            }
            if (prevEdge >= 0) {
                if (es.getEdge() == prevEdge) {
                    errors.add("duplicate edge:" + es.getName() + ":" + es.getBaseNode() + "->" + es.getAdjNode() /*+ ", " + es.fetchWayGeometry(3)*/);
                }
            }
            prevEdge = es.getEdge();
            prevNode = es.getAdjNode();
        }

        if (!errors.isEmpty()) {
            throw new IllegalStateException("Result contains illegal edges:" + errors);
        }
    }
}
