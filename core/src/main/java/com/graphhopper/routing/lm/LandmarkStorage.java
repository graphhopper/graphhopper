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
package com.graphhopper.routing.lm;

import com.carrotsearch.hppc.IntArrayList;
import com.carrotsearch.hppc.IntHashSet;
import com.carrotsearch.hppc.IntObjectMap;
import com.carrotsearch.hppc.predicates.IntObjectPredicate;
import com.carrotsearch.hppc.procedures.IntObjectProcedure;
import com.graphhopper.coll.MapEntry;
import com.graphhopper.routing.DijkstraBidirectionRef;
import com.graphhopper.routing.subnetwork.SubnetworkStorage;
import com.graphhopper.routing.subnetwork.TarjansSCCAlgorithm;
import com.graphhopper.routing.util.*;
import com.graphhopper.routing.util.spatialrules.SpatialRule;
import com.graphhopper.routing.util.spatialrules.SpatialRuleLookup;
import com.graphhopper.routing.weighting.AbstractWeighting;
import com.graphhopper.routing.weighting.ShortestWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.*;
import com.graphhopper.util.*;
import com.graphhopper.util.exceptions.ConnectionNotFoundException;
import com.graphhopper.util.shapes.BBox;
import com.graphhopper.util.shapes.GHPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This class stores the landmark nodes and the weights from and to all other nodes in every
 * subnetwork. This data is created to apply a speed-up for path calculation but at the same times
 * stays flexible to per-request changes. The class is safe for usage from multiple reading threads
 * across algorithms.
 *
 * @author Peter Karich
 */
public class LandmarkStorage implements Storable<LandmarkStorage> {
    private static final Logger LOGGER = LoggerFactory.getLogger(LandmarkStorage.class);
    // This value is used to identify nodes where no subnetwork is associated
    private static final int UNSET_SUBNETWORK = -1;
    // This value should only be used if subnetwork is too small to be explicitely stored
    private static final int UNCLEAR_SUBNETWORK = 0;
    // one node has an associated landmark information ('one landmark row'): the forward and backward weight
    private long LM_ROW_LENGTH;
    private int landmarks;
    private final int FROM_OFFSET;
    private final int TO_OFFSET;
    private final DataAccess landmarkWeightDA;
    /* every subnetwork has its own landmark mapping but the count of landmarks is always the same */
    private final List<int[]> landmarkIDs;
    private double factor = -1;
    private final static double DOUBLE_MLTPL = 1e6;
    private final GraphHopperStorage graph;
    private final FlagEncoder encoder;
    private final Weighting weighting;
    private Weighting lmSelectionWeighting;
    private final TraversalMode traversalMode;
    private boolean initialized;
    private int minimumNodes = 500_000;
    private final SubnetworkStorage subnetworkStorage;
    private List<LandmarkSuggestion> landmarkSuggestions = Collections.emptyList();
    private SpatialRuleLookup ruleLookup;
    /**
     * 'to' and 'from' fit into 32 bit => 16 bit for each of them => 65536
     */
    static final long PRECISION = 1 << 16;

    public LandmarkStorage(GraphHopperStorage graph, Directory dir, int landmarks, final Weighting weighting, TraversalMode traversalMode) {
        this.graph = graph;
        this.minimumNodes = Math.min(graph.getNodes() / 2, 500_000);
        this.encoder = weighting.getFlagEncoder();
        this.weighting = weighting;
        // allowing arbitrary weighting is too dangerous
        this.lmSelectionWeighting = new ShortestWeighting(encoder) {
            @Override
            public double calcWeight(EdgeIteratorState edge, boolean reverse, int prevOrNextEdgeId) {
                // make accessibility of shortest identical to the provided weighting to avoid problems like shown in testWeightingConsistence
                double res = weighting.calcWeight(edge, reverse, prevOrNextEdgeId);
                if (res >= Double.MAX_VALUE)
                    return Double.POSITIVE_INFINITY;

                // returning the time or distance leads to strange landmark positions (ferries -> slow&very long) and BFS is more what we want
                return 1;
            }

            @Override
            public String toString() {
                return "LM_BFS|" + encoder;
            }
        };

        // later make edge base working! Not really necessary as when adding turn costs while routing we can still
        // use the node based traversal as this is a smaller weight approximation
        this.traversalMode = TraversalMode.NODE_BASED;
        final String name = AbstractWeighting.weightingToFileName(weighting);
        this.landmarkWeightDA = dir.find("landmarks_" + name);

        this.landmarks = landmarks;
        // one short per landmark and two directions => 2*2 byte
        this.LM_ROW_LENGTH = landmarks * 4;
        this.FROM_OFFSET = 0;
        this.TO_OFFSET = 2;
        this.landmarkIDs = new ArrayList<>();
        this.subnetworkStorage = new SubnetworkStorage(dir, "landmarks_" + name);
    }

    /**
     * Specify the maximum possible value for your used area. With this maximum weight value you can influence the storage
     * precision for your weights that help A* finding its way to the goal. The same value is used for all subnetworks.
     * Note, if you pick this value too big then too similar weights are stored
     * (some bits of the storage capability will be left unused) which could lead to suboptimal routes.
     * If too low then far away values will have the same maximum value associated ("maxed out") leading to bad performance.
     *
     * @param maxWeight use a negative value to automatically determine this value.
     */
    public LandmarkStorage setMaximumWeight(double maxWeight) {
        if (maxWeight > 0) {
            this.factor = maxWeight / PRECISION;
            if (Double.isInfinite(factor) || Double.isNaN(factor))
                throw new IllegalStateException("Illegal factor " + factor + " calculated from maximum weight " + maxWeight);
        }
        return this;
    }

    /**
     * This method forces the landmark preparation to skip the landmark search and uses the specified landmark list instead.
     * Useful for manual tuning of larger areas to safe import time or improve quality.
     */
    public LandmarkStorage setLandmarkSuggestions(List<LandmarkSuggestion> landmarkSuggestions) {
        if (landmarkSuggestions == null)
            throw new IllegalArgumentException("landmark suggestions cannot be null");

        this.landmarkSuggestions = landmarkSuggestions;
        return this;
    }

    /**
     * This method sets the required number of nodes of a subnetwork for which landmarks should be calculated. Every
     * subnetwork below this count will be ignored.
     */
    public void setMinimumNodes(int minimumNodes) {
        this.minimumNodes = minimumNodes;
    }

    /**
     * @see #setMinimumNodes(int)
     */
    public int getMinimumNodes() {
        return minimumNodes;
    }

    SubnetworkStorage getSubnetworkStorage() {
        return subnetworkStorage;
    }

    /**
     * This weighting is used for the selection heuristic and is per default not the weighting specified in the contructor.
     * The special weighting leads to a much better distribution of the landmarks and results in better response times.
     */
    public void setLMSelectionWeighting(Weighting lmSelectionWeighting) {
        this.lmSelectionWeighting = lmSelectionWeighting;
    }

    public Weighting getLmSelectionWeighting() {
        return lmSelectionWeighting;
    }

    /**
     * This method returns the weighting for which the landmarks are originally created
     */
    public Weighting getWeighting() {
        return weighting;
    }

    boolean isInitialized() {
        return initialized;
    }

    /**
     * This method calculates the landmarks and initial weightings to & from them.
     */
    public void createLandmarks() {
        if (isInitialized())
            throw new IllegalStateException("Initialize the landmark storage only once!");

        // fill 'from' and 'to' weights with maximum value
        long maxBytes = (long) graph.getNodes() * LM_ROW_LENGTH;
        this.landmarkWeightDA.create(2000);
        this.landmarkWeightDA.ensureCapacity(maxBytes);

        for (long pointer = 0; pointer < maxBytes; pointer += 2) {
            landmarkWeightDA.setShort(pointer, (short) SHORT_INFINITY);
        }

        String additionalInfo = "";
        // guess the factor
        if (factor <= 0) {
            // A 'factor' is necessary to store the weight in just a short value but without loosing too much precision.
            // This factor is rather delicate to pick, we estimate it through the graph boundaries its maximum distance.
            // For small areas we use max_bounds_dist*X and otherwise we use a big fixed value for this distance.
            // If we would pick the distance too big for small areas this could lead to (slightly) suboptimal routes as there
            // will be too big rounding errors. But picking it too small is dangerous regarding performance
            // e.g. for Germany at least 1500km is very important otherwise speed is at least twice as slow e.g. for just 1000km
            BBox bounds = graph.getBounds();
            double distanceInMeter = Helper.DIST_EARTH.calcDist(bounds.maxLat, bounds.maxLon, bounds.minLat, bounds.minLon) * 7;
            if (distanceInMeter > 50_000 * 7 || /* for tests and convenience we do for now: */ !bounds.isValid())
                distanceInMeter = 30_000_000;

            double maxWeight = weighting.getMinWeight(distanceInMeter);
            setMaximumWeight(maxWeight);
            additionalInfo = ", maxWeight:" + maxWeight + ", from max distance:" + distanceInMeter / 1000f + "km";
        }

        LOGGER.info("init landmarks for subnetworks with node count greater than " + minimumNodes + " with factor:" + factor + additionalInfo);

        int[] empty = new int[landmarks];
        Arrays.fill(empty, UNSET_SUBNETWORK);
        landmarkIDs.add(empty);

        byte[] subnetworks = new byte[graph.getNodes()];
        Arrays.fill(subnetworks, (byte) UNSET_SUBNETWORK);
        EdgeFilter tarjanFilter = new DefaultEdgeFilter(encoder, false, true);
        IntHashSet blockedEdges = new IntHashSet();

        // the ruleLookup splits certain areas from each other but avoids making this a permanent change so that other algorithms still can route through these regions.
        if (ruleLookup != null && ruleLookup.size() > 0) {
            StopWatch sw = new StopWatch().start();
            blockedEdges = findBorderEdgeIds(ruleLookup);
            tarjanFilter = new BlockedEdgesFilter(encoder, false, true, blockedEdges);
            LOGGER.info("Made " + blockedEdges.size() + " edges inaccessible. Calculated country cut in " + sw.stop().getSeconds() + "s, " + Helper.getMemInfo());
        }

        StopWatch sw = new StopWatch().start();

        // we cannot reuse the components calculated in PrepareRoutingSubnetworks as the edgeIds changed in between (called graph.optimize)
        // also calculating subnetworks from scratch makes bigger problems when working with many oneways
        TarjansSCCAlgorithm tarjanAlgo = new TarjansSCCAlgorithm(graph, tarjanFilter, true);
        List<IntArrayList> graphComponents = tarjanAlgo.findComponents();
        LOGGER.info("Calculated tarjan subnetworks in " + sw.stop().getSeconds() + "s, " + Helper.getMemInfo());

        EdgeExplorer tmpExplorer = graph.createEdgeExplorer(new RequireBothDirectionsEdgeFilter(encoder));

        int nodes = 0;
        for (IntArrayList subnetworkIds : graphComponents) {
            nodes += subnetworkIds.size();
            if (subnetworkIds.size() < minimumNodes)
                continue;

            int index = subnetworkIds.size() - 1;
            // ensure start node is reachable from both sides and no subnetwork is associated
            for (; index >= 0; index--) {
                int nextStartNode = subnetworkIds.get(index);
                if (subnetworks[nextStartNode] == UNSET_SUBNETWORK
                        && GHUtility.count(tmpExplorer.setBaseNode(nextStartNode)) > 0) {

                    GHPoint p = createPoint(graph, nextStartNode);
                    LOGGER.info("start node: " + nextStartNode + " (" + p + ") subnetwork size: " + subnetworkIds.size()
                            + ", " + Helper.getMemInfo() + ((ruleLookup == null) ? "" : " area:" + ruleLookup.lookupRule(p).getId()));

                    if (createLandmarksForSubnetwork(nextStartNode, subnetworks, blockedEdges))
                        break;
                }
            }
            if (index < 0)
                LOGGER.warn("next start node not found in big enough network of size " + subnetworkIds.size() + ", first element is " + subnetworkIds.get(0) + ", " + createPoint(graph, subnetworkIds.get(0)));
        }

        int subnetworkCount = landmarkIDs.size();
        // store all landmark node IDs and one int for the factor itself.
        this.landmarkWeightDA.ensureCapacity(maxBytes /* landmark weights */ + subnetworkCount * landmarks /* landmark mapping per subnetwork */);

        // calculate offset to point into landmark mapping
        long bytePos = maxBytes;
        for (int[] landmarks : landmarkIDs) {
            for (int lmNodeId : landmarks) {
                landmarkWeightDA.setInt(bytePos, lmNodeId);
                bytePos += 4L;
            }
        }

        landmarkWeightDA.setHeader(0 * 4, graph.getNodes());
        landmarkWeightDA.setHeader(1 * 4, landmarks);
        landmarkWeightDA.setHeader(2 * 4, subnetworkCount);
        if (factor * DOUBLE_MLTPL > Integer.MAX_VALUE)
            throw new UnsupportedOperationException("landmark weight factor cannot be bigger than Integer.MAX_VALUE " + factor * DOUBLE_MLTPL);
        landmarkWeightDA.setHeader(3 * 4, (int) Math.round(factor * DOUBLE_MLTPL));

        // serialize fast byte[] into DataAccess
        subnetworkStorage.create(graph.getNodes());
        for (int nodeId = 0; nodeId < subnetworks.length; nodeId++) {
            subnetworkStorage.setSubnetwork(nodeId, subnetworks[nodeId]);
        }

        LOGGER.info("Finished landmark creation. Subnetwork node count sum " + nodes + " vs. nodes " + graph.getNodes());
        initialized = true;
    }

    /**
     * This method creates landmarks for the specified subnetwork (integer list)
     *
     * @return landmark mapping
     */
    private boolean createLandmarksForSubnetwork(final int startNode, final byte[] subnetworks, IntHashSet blockedEdges) {
        final int subnetworkId = landmarkIDs.size();
        boolean random = false;
        int[] tmpLandmarkNodeIds = new int[landmarks];
        int logOffset = Math.max(1, tmpLandmarkNodeIds.length / 2);
        boolean pickedPrecalculatedLandmarks = false;

        if (!landmarkSuggestions.isEmpty()) {
            NodeAccess na = graph.getNodeAccess();
            double lat = na.getLatitude(startNode), lon = na.getLongitude(startNode);
            LandmarkSuggestion selectedSuggestion = null;
            for (LandmarkSuggestion lmsugg : landmarkSuggestions) {
                if (lmsugg.getBox().contains(lat, lon)) {
                    selectedSuggestion = lmsugg;
                    break;
                }
            }

            if (selectedSuggestion != null) {
                if (selectedSuggestion.getNodeIds().size() < tmpLandmarkNodeIds.length)
                    throw new IllegalArgumentException("landmark suggestions are too few " + selectedSuggestion.getNodeIds().size() + " for requested landmarks " + landmarks);

                pickedPrecalculatedLandmarks = true;
                for (int i = 0; i < tmpLandmarkNodeIds.length; i++) {
                    int lmNodeId = selectedSuggestion.getNodeIds().get(i);
                    tmpLandmarkNodeIds[i] = lmNodeId;
                }
            }
//            Random randomInst = new Random();
//            for (int i = 0; i < tmpLandmarkNodeIds.length; i++) {
//                int index = randomInst.nextInt(subnetworkIds.size());
//                tmpLandmarkNodeIds[i] = subnetworkIds.get(index);
//            }
        }

        if (pickedPrecalculatedLandmarks) {
            LOGGER.info("Picked " + tmpLandmarkNodeIds.length + " landmark suggestions, skipped expensive landmark determination");
        } else {
            // 1a) pick landmarks via special weighting for a better geographical spreading
            Weighting initWeighting = lmSelectionWeighting;
            LandmarkExplorer explorer = new LandmarkExplorer(graph, this, initWeighting, traversalMode);
            explorer.initFrom(startNode, 0);
            explorer.setFilter(blockedEdges, true, true);
            explorer.runAlgo(true);

            if (explorer.getFromCount() < minimumNodes) {
                // too small subnetworks are initialized with special id==0
                explorer.setSubnetworks(subnetworks, UNCLEAR_SUBNETWORK);
                return false;
            }

            // 1b) we have one landmark, now determine the other landmarks
            tmpLandmarkNodeIds[0] = explorer.getLastNode();
            for (int lmIdx = 0; lmIdx < tmpLandmarkNodeIds.length - 1; lmIdx++) {
                if(Thread.currentThread().isInterrupted()){
                    throw new RuntimeException("Thread was interrupted");
                }
                explorer = new LandmarkExplorer(graph, this, initWeighting, traversalMode);
                explorer.setFilter(blockedEdges, true, true);
                // set all current landmarks as start so that the next getLastNode is hopefully a "far away" node
                for (int j = 0; j < lmIdx + 1; j++) {
                    explorer.initFrom(tmpLandmarkNodeIds[j], 0);
                }
                explorer.runAlgo(true);
                tmpLandmarkNodeIds[lmIdx + 1] = explorer.getLastNode();
                if (lmIdx % logOffset == 0)
                    LOGGER.info("Finding landmarks [" + weighting + "] in network [" + explorer.getVisitedNodes() + "]. "
                            + "Progress " + (int) (100.0 * lmIdx / tmpLandmarkNodeIds.length) + "%, " + Helper.getMemInfo());
            }
            LOGGER.info("Finished searching landmarks for subnetwork " + subnetworkId + " of size " + explorer.getVisitedNodes());
        }

        // 2) calculate weights for all landmarks -> 'from' and 'to' weight
        for (int lmIdx = 0; lmIdx < tmpLandmarkNodeIds.length; lmIdx++) {
            if(Thread.currentThread().isInterrupted()){
                throw new RuntimeException("Thread was interrupted");
            }
            int lmNodeId = tmpLandmarkNodeIds[lmIdx];
            LandmarkExplorer explorer = new LandmarkExplorer(graph, this, weighting, traversalMode);
            explorer.initFrom(lmNodeId, 0);
            explorer.setFilter(blockedEdges, false, true);
            explorer.runAlgo(true);
            explorer.initLandmarkWeights(lmIdx, lmNodeId, LM_ROW_LENGTH, FROM_OFFSET);

            // set subnetwork id to all explored nodes, but do this only for the first landmark
            if (lmIdx == 0) {
                if (explorer.setSubnetworks(subnetworks, subnetworkId))
                    return false;
            }

            explorer = new LandmarkExplorer(graph, this, weighting, traversalMode);
            explorer.initTo(lmNodeId, 0);
            explorer.setFilter(blockedEdges, true, false);
            explorer.runAlgo(false);
            explorer.initLandmarkWeights(lmIdx, lmNodeId, LM_ROW_LENGTH, TO_OFFSET);

            if (lmIdx == 0) {
                if (explorer.setSubnetworks(subnetworks, subnetworkId))
                    return false;
            }

            if (lmIdx % logOffset == 0)
                LOGGER.info("Set landmarks weights [" + weighting + "]. "
                        + "Progress " + (int) (100.0 * lmIdx / tmpLandmarkNodeIds.length) + "%");
        }

        // TODO set weight to SHORT_MAX if entry has either no 'from' or no 'to' entry
        landmarkIDs.add(tmpLandmarkNodeIds);
        return true;
    }

    /**
     * This method specifies the polygons which should be used to split the world wide area to improve performance and
     * quality in this scenario.
     */
    public void setSpatialRuleLookup(SpatialRuleLookup ruleLookup) {
        this.ruleLookup = ruleLookup;
    }

    /**
     * This method makes edges crossing the specified border inaccessible to split a bigger area into smaller subnetworks.
     * This is important for the world wide use case to limit the maximum distance and also to detect unreasonable routes faster.
     */
    protected IntHashSet findBorderEdgeIds(SpatialRuleLookup ruleLookup) {
        AllEdgesIterator allEdgesIterator = graph.getAllEdges();
        NodeAccess nodeAccess = graph.getNodeAccess();
        IntHashSet inaccessible = new IntHashSet();
        while (allEdgesIterator.next()) {
            int adjNode = allEdgesIterator.getAdjNode();
            SpatialRule ruleAdj = ruleLookup.lookupRule(nodeAccess.getLatitude(adjNode), nodeAccess.getLongitude(adjNode));

            int baseNode = allEdgesIterator.getBaseNode();
            SpatialRule ruleBase = ruleLookup.lookupRule(nodeAccess.getLatitude(baseNode), nodeAccess.getLongitude(baseNode));
            if (ruleAdj != ruleBase)
                inaccessible.add(allEdgesIterator.getEdge());
        }
        return inaccessible;
    }

    /**
     * The factor is used to convert double values into more compact int values.
     */
    double getFactor() {
        return factor;
    }

    /**
     * @return the weight from the landmark to the specified node. Where the landmark integer is not
     * a node ID but the internal index of the landmark array.
     */
    public int getFromWeight(int landmarkIndex, int node) {
        int res = (int) landmarkWeightDA.getShort((long) node * LM_ROW_LENGTH + landmarkIndex * 4 + FROM_OFFSET)
                & 0x0000FFFF;
        assert res >= 0 : "Negative to weight " + res + ", landmark index:" + landmarkIndex + ", node:" + node;
        if (res == SHORT_INFINITY)
            // TODO can happen if endstanding oneway
            // we should set a 'from' value to SHORT_MAX if the 'to' value was already set to find real bugs
            // and what to return? Integer.MAX_VALUE i.e. convert to Double.pos_infinity upstream?
            return SHORT_MAX;
        // throw new IllegalStateException("Do not call getFromWeight for wrong landmark[" + landmarkIndex + "]=" + landmarkIDs[landmarkIndex] + " and node " + node);
        // TODO if(res == MAX) fallback to beeline approximation!?

        return res;
    }

    /**
     * @return the weight from the specified node to the landmark (*as index*)
     */
    public int getToWeight(int landmarkIndex, int node) {
        int res = (int) landmarkWeightDA.getShort((long) node * LM_ROW_LENGTH + landmarkIndex * 4 + TO_OFFSET)
                & 0x0000FFFF;
        assert res >= 0 : "Negative to weight " + res + ", landmark index:" + landmarkIndex + ", node:" + node;
        if (res == SHORT_INFINITY)
            return SHORT_MAX;
//            throw new IllegalStateException("Do not call getToWeight for wrong landmark[" + landmarkIndex + "]=" + landmarkIDs[landmarkIndex] + " and node " + node);

        return res;
    }

    // Short.MAX_VALUE = 2^15-1 but we have unsigned short so we need 2^16-1
    private static final int SHORT_INFINITY = Short.MAX_VALUE * 2 + 1;
    // We have large values that do not fit into a short, use a specific maximum value
    private static final int SHORT_MAX = SHORT_INFINITY - 1;

    /**
     * @return false if the value capacity was reached and instead of the real value the SHORT_MAX was stored.
     */
    final boolean setWeight(long pointer, double value) {
        double tmpVal = value / factor;
        if (tmpVal > Integer.MAX_VALUE)
            throw new UnsupportedOperationException("Cannot store infinity explicitely, pointer=" + pointer + ", value: " + value);

        if (tmpVal >= SHORT_MAX) {
            landmarkWeightDA.setShort(pointer, (short) SHORT_MAX);
            return false;
        } else {
            landmarkWeightDA.setShort(pointer, (short) tmpVal);
            return true;
        }
    }

    boolean isInfinity(long pointer) {
        return ((int) landmarkWeightDA.getShort(pointer) & 0x0000FFFF) == SHORT_INFINITY;
    }

    int calcWeight(EdgeIteratorState edge, boolean reverse) {
        return (int) (weighting.calcWeight(edge, reverse, EdgeIterator.NO_EDGE) / factor);
    }

    // From all available landmarks pick just a few active ones
    boolean initActiveLandmarks(int fromNode, int toNode, int[] activeLandmarkIndices,
                                int[] activeFroms, int[] activeTos, boolean reverse) {
        if (fromNode < 0 || toNode < 0)
            throw new IllegalStateException("from " + fromNode + " and to "
                    + toNode + " nodes have to be 0 or positive to init landmarks");

        int subnetworkFrom = subnetworkStorage.getSubnetwork(fromNode);
        int subnetworkTo = subnetworkStorage.getSubnetwork(toNode);
        if (subnetworkFrom <= UNCLEAR_SUBNETWORK || subnetworkTo <= UNCLEAR_SUBNETWORK)
            return false;
        if (subnetworkFrom != subnetworkTo) {
            throw new ConnectionNotFoundException("Connection between locations not found. Different subnetworks " + subnetworkFrom + " vs. " + subnetworkTo, new HashMap<String, Object>());
        }

        int[] tmpIDs = landmarkIDs.get(subnetworkFrom);

        // kind of code duplication to approximate
        List<Map.Entry<Integer, Integer>> list = new ArrayList<>(tmpIDs.length);
        for (int lmIndex = 0; lmIndex < tmpIDs.length; lmIndex++) {
            int fromWeight = getFromWeight(lmIndex, toNode) - getFromWeight(lmIndex, fromNode);
            int toWeight = getToWeight(lmIndex, fromNode) - getToWeight(lmIndex, toNode);

            list.add(new MapEntry<>(reverse
                    ? Math.max(-fromWeight, -toWeight)
                    : Math.max(fromWeight, toWeight), lmIndex));
        }

        Collections.sort(list, SORT_BY_WEIGHT);

        if (activeLandmarkIndices[0] >= 0) {
            IntHashSet set = new IntHashSet(activeLandmarkIndices.length);
            set.addAll(activeLandmarkIndices);
            int existingLandmarkCounter = 0;
            final int COUNT = Math.min(activeLandmarkIndices.length - 2, 2);
            for (int i = 0; i < activeLandmarkIndices.length; i++) {
                if (i >= activeLandmarkIndices.length - COUNT + existingLandmarkCounter) {
                    // keep at least two of the previous landmarks (pick the best)
                    break;
                } else {
                    activeLandmarkIndices[i] = list.get(i).getValue();
                    if (set.contains(activeLandmarkIndices[i]))
                        existingLandmarkCounter++;
                }
            }

        } else {
            for (int i = 0; i < activeLandmarkIndices.length; i++) {
                activeLandmarkIndices[i] = list.get(i).getValue();
            }
        }

        // store weight values of active landmarks in 'cache' arrays
        for (int i = 0; i < activeLandmarkIndices.length; i++) {
            int lmIndex = activeLandmarkIndices[i];
            activeFroms[i] = getFromWeight(lmIndex, toNode);
            activeTos[i] = getToWeight(lmIndex, toNode);
        }
        return true;
    }

    public int getLandmarkCount() {
        return landmarks;
    }

    public int[] getLandmarks(int subnetwork) {
        return landmarkIDs.get(subnetwork);
    }

    /**
     * @return the number of subnetworks that have landmarks
     */
    public int getSubnetworksWithLandmarks() {
        return landmarkIDs.size();
    }

    public boolean isEmpty() {
        return landmarkIDs.size() < 2;
    }

    @Override
    public String toString() {
        String str = "";
        for (int[] ints : landmarkIDs) {
            if (!str.isEmpty())
                str += ", ";
            str += Arrays.toString(ints);
        }
        return str;
    }

    /**
     * @return the calculated landmarks as GeoJSON string.
     */
    String getLandmarksAsGeoJSON() {
        NodeAccess na = graph.getNodeAccess();
        String str = "";
        for (int subnetwork = 1; subnetwork < landmarkIDs.size(); subnetwork++) {
            int[] lmArray = landmarkIDs.get(subnetwork);
            for (int lmIdx = 0; lmIdx < lmArray.length; lmIdx++) {
                int index = lmArray[lmIdx];
                if (!str.isEmpty())
                    str += ",";

                str += "{ \"type\": \"Feature\", \"geometry\": {\"type\": \"Point\", \"coordinates\": ["
                        + na.getLon(index) + ", " + na.getLat(index) + "]},";
                str += "  \"properties\":{\"node_index\":" + index + ","
                        + "\"subnetwork\":" + subnetwork + ","
                        + "\"lm_index\":" + lmIdx + "}"
                        + "}";
            }
        }

        return "{ \"type\": \"FeatureCollection\", \"features\": [" + str + "]}";
    }

    @Override
    public boolean loadExisting() {
        if (isInitialized())
            throw new IllegalStateException("Cannot call PrepareLandmarks.loadExisting if already initialized");
        if (landmarkWeightDA.loadExisting()) {
            if (!subnetworkStorage.loadExisting())
                throw new IllegalStateException("landmark weights loaded but not the subnetworks!?");

            int nodes = landmarkWeightDA.getHeader(0 * 4);
            if (nodes != graph.getNodes())
                throw new IllegalArgumentException("Cannot load landmark data as written for different graph storage with " + nodes + " nodes, not " + graph.getNodes());
            landmarks = landmarkWeightDA.getHeader(1 * 4);
            int subnetworks = landmarkWeightDA.getHeader(2 * 4);
            factor = landmarkWeightDA.getHeader(3 * 4) / DOUBLE_MLTPL;
            LM_ROW_LENGTH = landmarks * 4;
            long maxBytes = LM_ROW_LENGTH * nodes;
            long bytePos = maxBytes;

            // in the first subnetwork 0 there are no landmark IDs stored
            for (int j = 0; j < subnetworks; j++) {
                int[] tmpLandmarks = new int[landmarks];
                for (int i = 0; i < tmpLandmarks.length; i++) {
                    tmpLandmarks[i] = landmarkWeightDA.getInt(bytePos);
                    bytePos += 4;
                }
                landmarkIDs.add(tmpLandmarks);
            }

            initialized = true;
            return true;
        }
        return false;
    }

    @Override
    public LandmarkStorage create(long byteCount) {
        throw new IllegalStateException("Do not call LandmarkStore.create directly");
    }

    @Override
    public void flush() {
        landmarkWeightDA.flush();
        subnetworkStorage.flush();
    }

    @Override
    public void close() {
        landmarkWeightDA.close();
        subnetworkStorage.close();
    }

    @Override
    public boolean isClosed() {
        return landmarkWeightDA.isClosed();
    }

    @Override
    public long getCapacity() {
        return landmarkWeightDA.getCapacity() + subnetworkStorage.getCapacity();
    }

    /**
     * This class is used to calculate landmark location (equally distributed).
     */
    private static class LandmarkExplorer extends DijkstraBidirectionRef {
        private int lastNode;
        private boolean from;
        private final LandmarkStorage lms;

        public LandmarkExplorer(Graph g, LandmarkStorage lms, Weighting weighting, TraversalMode tMode) {
            super(g, weighting, tMode);
            this.lms = lms;
        }

        public void setFilter(IntHashSet set, boolean bwd, boolean fwd) {
            EdgeFilter ef = new BlockedEdgesFilter(flagEncoder, bwd, fwd, set);
            outEdgeExplorer = graph.createEdgeExplorer(ef);
            inEdgeExplorer = graph.createEdgeExplorer(ef);
        }

        int getFromCount() {
            return bestWeightMapFrom.size();
        }

        int getToCount() {
            return bestWeightMapTo.size();
        }

        public int getLastNode() {
            return lastNode;
        }

        public void runAlgo(boolean from) {
            // no path should be calculated
            setUpdateBestPath(false);
            // set one of the bi directions as already finished            
            if (from)
                finishedTo = true;
            else
                finishedFrom = true;

            this.from = from;
            super.runAlgo();
        }

        @Override
        public boolean finished() {
            if (from) {
                lastNode = currFrom.adjNode;
                return finishedFrom;
            } else {
                lastNode = currTo.adjNode;
                return finishedTo;
            }
        }

        public boolean setSubnetworks(final byte[] subnetworks, final int subnetworkId) {
            if (subnetworkId > 127)
                throw new IllegalStateException("Too many subnetworks " + subnetworkId);

            final AtomicBoolean failed = new AtomicBoolean(false);
            IntObjectMap<SPTEntry> map = from ? bestWeightMapFrom : bestWeightMapTo;
            map.forEach(new IntObjectPredicate<SPTEntry>() {
                @Override
                public boolean apply(int nodeId, SPTEntry value) {
                    int sn = subnetworks[nodeId];
                    if (sn != subnetworkId) {
                        if (sn != UNSET_SUBNETWORK && sn != UNCLEAR_SUBNETWORK) {
                            // this is ugly but can happen in real world, see testWithOnewaySubnetworks
                            LOGGER.error("subnetworkId for node " + nodeId
                                    + " (" + createPoint(graph, nodeId) + ") already set (" + sn + "). " + "Cannot change to " + subnetworkId);

                            failed.set(true);
                            return false;
                        }

                        subnetworks[nodeId] = (byte) subnetworkId;
                    }
                    return true;
                }
            });
            return failed.get();
        }

        public void initLandmarkWeights(final int lmIdx, int lmNodeId, final long rowSize, final int offset) {
            IntObjectMap<SPTEntry> map = from ? bestWeightMapFrom : bestWeightMapTo;
            final AtomicInteger maxedout = new AtomicInteger(0);
            final Map.Entry<Double, Double> finalMaxWeight = new MapEntry<>(0d, 0d);

            map.forEach(new IntObjectProcedure<SPTEntry>() {
                @Override
                public void apply(int nodeId, SPTEntry b) {
                    if (!lms.setWeight(nodeId * rowSize + lmIdx * 4 + offset, b.weight)) {
                        maxedout.incrementAndGet();
                        finalMaxWeight.setValue(Math.max(b.weight, finalMaxWeight.getValue()));
                    }
                }
            });

            if ((double) maxedout.get() / map.size() > 0.1) {
                LOGGER.warn("landmark " + lmIdx + " (" + nodeAccess.getLatitude(lmNodeId) + "," + nodeAccess.getLongitude(lmNodeId) + "): " +
                        "too many weights were maxed out (" + maxedout.get() + "/" + map.size() + "). Use a bigger factor than " + lms.factor
                        + ". For example use the following in the config.properties: weighting=" + weighting.getName() + "|maximum=" + finalMaxWeight.getValue() * 1.2);
            }
        }
    }

    /**
     * Sort landmark by weight and let maximum weight come first, to pick best active landmarks.
     */
    final static Comparator<Map.Entry<Integer, Integer>> SORT_BY_WEIGHT = new Comparator<Map.Entry<Integer, Integer>>() {
        @Override
        public int compare(Map.Entry<Integer, Integer> o1, Map.Entry<Integer, Integer> o2) {
            return Integer.compare(o2.getKey(), o1.getKey());
        }
    };

    static GHPoint createPoint(Graph graph, int nodeId) {
        return new GHPoint(graph.getNodeAccess().getLatitude(nodeId), graph.getNodeAccess().getLongitude(nodeId));
    }

    final static class RequireBothDirectionsEdgeFilter implements EdgeFilter {

        private FlagEncoder flagEncoder;

        public RequireBothDirectionsEdgeFilter(FlagEncoder flagEncoder) {
            this.flagEncoder = flagEncoder;
        }

        @Override
        public boolean accept(EdgeIteratorState edgeState) {
            return flagEncoder.isForward(edgeState.getFlags()) && flagEncoder.isBackward(edgeState.getFlags());
        }
    }

    private static class BlockedEdgesFilter implements EdgeFilter {
        private final IntHashSet blockedEdges;
        private final FlagEncoder encoder;
        private final boolean fwd;
        private final boolean bwd;

        public BlockedEdgesFilter(FlagEncoder encoder, boolean bwd, boolean fwd, IntHashSet blockedEdges) {
            this.encoder = encoder;
            this.bwd = bwd;
            this.fwd = fwd;
            this.blockedEdges = blockedEdges;
        }

        @Override
        public final boolean accept(EdgeIteratorState iter) {
            boolean blocked = blockedEdges.contains(iter.getEdge());
            return fwd && iter.isForward(encoder) && !blocked || bwd && iter.isBackward(encoder) && !blocked;
        }

        public boolean acceptsBackward() {
            return bwd;
        }

        public boolean acceptsForward() {
            return fwd;
        }

        @Override
        public String toString() {
            return encoder.toString() + ", bwd:" + bwd + ", fwd:" + fwd;
        }
    }
}
