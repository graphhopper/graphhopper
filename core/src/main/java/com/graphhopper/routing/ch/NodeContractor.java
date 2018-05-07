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
package com.graphhopper.routing.ch;

import com.graphhopper.routing.DijkstraOneToMany;
import com.graphhopper.routing.util.*;
import com.graphhopper.routing.weighting.AbstractWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.*;
import com.graphhopper.util.*;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static com.graphhopper.util.Helper.nf;

class NodeContractor {
    private final GraphHopperStorage ghStorage;
    private final CHGraph prepareGraph;
    private final PreparationWeighting prepareWeighting;
    // todo: so far node contraction can only be done for node-based graph traversal
    private final TraversalMode traversalMode;
    private final DataAccess originalEdges;
    private final Map<Shortcut, Shortcut> shortcuts = new HashMap<>();
    private final AddShortcutHandler addScHandler = new AddShortcutHandler();
    private final CalcShortcutHandler calcScHandler = new CalcShortcutHandler();
    private CHEdgeExplorer vehicleInExplorer;
    private CHEdgeExplorer vehicleOutExplorer;
    private CHEdgeExplorer calcPrioAllExplorer;
    private IgnoreNodeFilter ignoreNodeFilter;
    private DijkstraOneToMany prepareAlgo;
    private int addedShortcutsCount;
    private long dijkstraCount;
    private int maxVisitedNodes = Integer.MAX_VALUE;
    private StopWatch dijkstraSW = new StopWatch();
    private int maxEdgesCount;
    private int maxLevel;
    // meanDegree is the number of edges / number of nodes ratio of the graph, not really the average degree, because
    // each edge can exist in both directions
    private double meanDegree;

    NodeContractor(Directory dir, GraphHopperStorage ghStorage, CHGraph prepareGraph, Weighting weighting,
                   TraversalMode traversalMode) {
        if (traversalMode.isEdgeBased()) {
            throw new IllegalArgumentException("Contraction Hierarchies only support node based traversal so far, given: " + traversalMode);
        }
        // todo: it would be nice to check if ghStorage is frozen here
        this.ghStorage = ghStorage;
        this.prepareGraph = prepareGraph;
        this.prepareWeighting = new PreparationWeighting(weighting);
        this.traversalMode = traversalMode;
        originalEdges = dir.find("original_edges_" + AbstractWeighting.weightingToFileName(weighting));
        originalEdges.create(1000);
    }

    void initFromGraph() {
        // todo: do we really need this method ? the problem is that ghStorage/prepareGraph can potentially be modified
        // between the constructor call and contractNode,calcShortcutCount etc. ...
        maxLevel = prepareGraph.getNodes();
        maxEdgesCount = ghStorage.getAllEdges().length();
        ignoreNodeFilter = new IgnoreNodeFilter(prepareGraph, maxLevel);
        FlagEncoder prepareFlagEncoder = prepareWeighting.getFlagEncoder();
        vehicleInExplorer = prepareGraph.createEdgeExplorer(new DefaultEdgeFilter(prepareFlagEncoder, true, false));
        vehicleOutExplorer = prepareGraph.createEdgeExplorer(new DefaultEdgeFilter(prepareFlagEncoder, false, true));

        // filter by vehicle and level number
        final EdgeFilter allFilter = new DefaultEdgeFilter(prepareFlagEncoder, true, true);
        final EdgeFilter accessWithLevelFilter = new LevelEdgeFilter(prepareGraph) {
            @Override
            public final boolean accept(EdgeIteratorState edgeState) {
                return super.accept(edgeState) && allFilter.accept(edgeState);
            }
        };
        calcPrioAllExplorer = prepareGraph.createEdgeExplorer(accessWithLevelFilter);
        prepareAlgo = new DijkstraOneToMany(prepareGraph, prepareWeighting, traversalMode);
    }

    void close() {
        prepareAlgo.close();
        originalEdges.close();
    }

    long contractNode(int node) {
        shortcuts.clear();
        long degree = findShortcuts(addScHandler.setNode(node));
        addedShortcutsCount += addShortcuts(shortcuts.keySet());
        // put weight factor on meanDegree instead of taking the average => meanDegree is more stable
        meanDegree = (meanDegree * 2 + degree) / 3;
        return degree;
    }

    private CalcShortcutsResult calcShortcutCount(int node) {
        findShortcuts(calcScHandler.setNode(node));
        return calcScHandler.calcShortcutsResult;
    }

    /**
     * Searches for shortcuts and calls the given handler on each shortcut that is found. The graph is not directly
     * changed by this method.
     * Returns the 'degree' of the handler's node (disregarding edges from/to already contracted nodes). Note that
     * here the degree is not the total number of adjacent edges, but only the number of incoming edges
     */
    private long findShortcuts(ShortcutHandler sch) {
        this.maxVisitedNodes = getMaxVisitedNodesEstimate();
        long degree = 0;
        EdgeIterator incomingEdges = vehicleInExplorer.setBaseNode(sch.getNode());
        // collect outgoing nodes (goal-nodes) only once
        while (incomingEdges.next()) {
            int fromNode = incomingEdges.getAdjNode();
            // accept only uncontracted nodes
            if (prepareGraph.getLevel(fromNode) != maxLevel)
                continue;

            final double incomingEdgeDistance = incomingEdges.getDistance();
            double incomingEdgeWeight = prepareWeighting.calcWeight(incomingEdges, true, EdgeIterator.NO_EDGE);
            int incomingEdge = incomingEdges.getEdge();
            int incomingEdgeOrigCount = getOrigEdgeCount(incomingEdge);
            // collect outgoing nodes (goal-nodes) only once
            EdgeIterator outgoingEdges = vehicleOutExplorer.setBaseNode(sch.getNode());
            // force fresh maps etc as this cannot be determined by from node alone (e.g. same from node but different avoidNode)
            prepareAlgo.clear();
            degree++;
            while (outgoingEdges.next()) {
                int toNode = outgoingEdges.getAdjNode();
                // add only uncontracted nodes
                if (prepareGraph.getLevel(toNode) != maxLevel || fromNode == toNode)
                    continue;

                // Limit weight as ferries or forbidden edges can increase local search too much.
                // If we decrease the correct weight we only explore less and introduce more shortcuts.
                // I.e. no change to accuracy is made.
                double existingDirectWeight = incomingEdgeWeight + prepareWeighting.calcWeight(outgoingEdges, false, incomingEdges.getEdge());
                if (Double.isNaN(existingDirectWeight))
                    throw new IllegalStateException("Weighting should never return NaN values"
                            + ", in:" + getCoords(incomingEdges, prepareGraph) + ", out:" + getCoords(outgoingEdges, prepareGraph)
                            + ", dist:" + outgoingEdges.getDistance());

                if (Double.isInfinite(existingDirectWeight))
                    continue;

                final double existingDistSum = incomingEdgeDistance + outgoingEdges.getDistance();
                prepareAlgo.setWeightLimit(existingDirectWeight);
                prepareAlgo.setMaxVisitedNodes(maxVisitedNodes);
                prepareAlgo.setEdgeFilter(ignoreNodeFilter.setAvoidNode(sch.getNode()));

                dijkstraSW.start();
                dijkstraCount++;
                int endNode = prepareAlgo.findEndNode(fromNode, toNode);
                dijkstraSW.stop();

                // compare end node as the limit could force dijkstra to finish earlier
                if (endNode == toNode && prepareAlgo.getWeight(endNode) <= existingDirectWeight)
                    // FOUND witness path, so do not add shortcut
                    continue;

                sch.foundShortcut(fromNode, toNode,
                        existingDirectWeight, existingDistSum,
                        outgoingEdges.getEdge(), getOrigEdgeCount(outgoingEdges.getEdge()),
                        incomingEdge, incomingEdgeOrigCount);
            }
        }
        return degree;
    }

    /**
     * Adds the given shortcuts to the graph.
     *
     * @return the actual number of shortcuts that were added to the graph
     */
    private int addShortcuts(Collection<Shortcut> shortcuts) {
        int tmpNewShortcuts = 0;
        NEXT_SC:
        for (Shortcut sc : shortcuts) {
            boolean updatedInGraph = false;
            // check if we need to update some existing shortcut in the graph
            CHEdgeIterator iter = vehicleOutExplorer.setBaseNode(sc.from);
            while (iter.next()) {
                if (iter.isShortcut() && iter.getAdjNode() == sc.to) {
                    int status = iter.getMergeStatus(sc.flags);
                    if (status == 0)
                        continue;

                    if (sc.weight >= prepareWeighting.calcWeight(iter, false, EdgeIterator.NO_EDGE)) {
                        // special case if a bidirectional shortcut has worse weight and still has to be added as otherwise the opposite direction would be missing
                        // see testShortcutMergeBug
                        if (status == 2)
                            break;

                        continue NEXT_SC;
                    }

                    if (iter.getEdge() == sc.skippedEdge1 || iter.getEdge() == sc.skippedEdge2) {
                        throw new IllegalStateException("Shortcut cannot update itself! " + iter.getEdge()
                                + ", skipEdge1:" + sc.skippedEdge1 + ", skipEdge2:" + sc.skippedEdge2
                                + ", edge " + iter + ":" + getCoords(iter, prepareGraph)
                                + ", sc:" + sc
                                + ", skippedEdge1: " + getCoords(prepareGraph.getEdgeIteratorState(sc.skippedEdge1, sc.from), prepareGraph)
                                + ", skippedEdge2: " + getCoords(prepareGraph.getEdgeIteratorState(sc.skippedEdge2, sc.to), prepareGraph)
                                + ", neighbors:" + GHUtility.getNeighbors(iter));
                    }

                    // note: flags overwrite weight => call first
                    iter.setFlags(sc.flags);
                    iter.setWeight(sc.weight);
                    iter.setDistance(sc.dist);
                    iter.setSkippedEdges(sc.skippedEdge1, sc.skippedEdge2);
                    setOrigEdgeCount(iter.getEdge(), sc.originalEdges);
                    updatedInGraph = true;
                    break;
                }
            }

            if (!updatedInGraph) {
                CHEdgeIteratorState edgeState = prepareGraph.shortcut(sc.from, sc.to);
                // note: flags overwrite weight => call first
                edgeState.setFlags(sc.flags);
                edgeState.setWeight(sc.weight);
                edgeState.setDistance(sc.dist);
                edgeState.setSkippedEdges(sc.skippedEdge1, sc.skippedEdge2);
                setOrigEdgeCount(edgeState.getEdge(), sc.originalEdges);
                tmpNewShortcuts++;
            }
        }
        return tmpNewShortcuts;
    }

    private String getCoords(EdgeIteratorState edge, Graph graph) {
        NodeAccess na = graph.getNodeAccess();
        int base = edge.getBaseNode();
        int adj = edge.getAdjNode();
        return base + "->" + adj + " (" + edge.getEdge() + "); "
                + na.getLat(base) + "," + na.getLon(base) + " -> " + na.getLat(adj) + "," + na.getLon(adj);
    }

    int getAddedShortcutsCount() {
        return addedShortcutsCount;
    }

    private void setOrigEdgeCount(int edgeId, int value) {
        edgeId -= maxEdgesCount;
        if (edgeId < 0) {
            // ignore setting as every normal edge has original edge count of 1
            if (value != 1)
                throw new IllegalStateException("Trying to set original edge count for normal edge to a value = " + value
                        + ", edge:" + (edgeId + maxEdgesCount) + ", max:" + maxEdgesCount + ", graph.max:" +
                        prepareGraph.getAllEdges().length());
            return;
        }

        long tmp = (long) edgeId * 4;
        originalEdges.ensureCapacity(tmp + 4);
        originalEdges.setInt(tmp, value);
    }

    private int getOrigEdgeCount(int edgeId) {
        edgeId -= maxEdgesCount;
        if (edgeId < 0)
            return 1;

        long tmp = (long) edgeId * 4;
        originalEdges.ensureCapacity(tmp + 4);
        return originalEdges.getInt(tmp);
    }

    long getDijkstraCount() {
        return dijkstraCount;
    }

    float getDijkstraSeconds() {
        return dijkstraSW.getSeconds();
    }

    /**
     * Calculates the priority of a node v without changing the graph. Warning: the calculated
     * priority must NOT depend on priority(v) and therefore findShortcuts should also not depend on
     * the priority(v). Otherwise updating the priority before contracting in contractNodes() could
     * lead to a slowish or even endless loop.
     */
    public float calculatePriority(int node) {
        NodeContractor.CalcShortcutsResult calcShortcutsResult = calcShortcutCount(node);

        // # huge influence: the bigger the less shortcuts gets created and the faster is the preparation
        //
        // every adjNode has an 'original edge' number associated. initially it is r=1
        // when a new shortcut is introduced then r of the associated edges is summed up:
        // r(u,w)=r(u,v)+r(v,w) now we can define
        // originalEdgesCount = σ(v) := sum_{ (u,w) ∈ shortcuts(v) } of r(u, w)
        int originalEdgesCount = calcShortcutsResult.originalEdgesCount;

        // # lowest influence on preparation speed or shortcut creation count
        // (but according to paper should speed up queries)
        //
        // number of already contracted neighbors of v
        int contractedNeighbors = 0;
        int degree = 0;
        CHEdgeIterator iter = calcPrioAllExplorer.setBaseNode(node);
        while (iter.next()) {
            degree++;
            if (iter.isShortcut())
                contractedNeighbors++;
        }

        // from shortcuts we can compute the edgeDifference
        // # low influence: with it the shortcut creation is slightly faster
        //
        // |shortcuts(v)| − |{(u, v) | v uncontracted}| − |{(v, w) | v uncontracted}|
        // meanDegree is used instead of outDegree+inDegree as if one adjNode is in both directions
        // only one bucket memory is used. Additionally one shortcut could also stand for two directions.
        int edgeDifference = calcShortcutsResult.shortcutsCount - degree;

        // according to the paper do a simple linear combination of the properties to get the priority.
        // this is the current optimum for unterfranken:
        return 10 * edgeDifference + originalEdgesCount + contractedNeighbors;
    }

    public String getStatisticsString() {
        return String.format("meanDegree: %.2f, dijkstras: %10s, mem: %10s",
                meanDegree, nf(dijkstraCount), prepareAlgo.getMemoryUsageAsString());

    }

    public void prepareContraction() {
        // todo: initializing meanDegree here instead of in initFromGraph() means that in the first round of calculating
        // node priorities all shortcut searches are cancelled immediately and all possible shortcuts are counted because
        // no witness path can be found. this is not really what we want, but changing it requires re-optimizing the
        // graph contraction parameters, because it affects the node contraction order.
        // when this is done there should be no need for this method any longer.
        meanDegree = prepareGraph.getAllEdges().length() / prepareGraph.getNodes();
    }

    private int getMaxVisitedNodesEstimate() {
        // todo: we return 0 here if meanDegree is < 1, which is not really what we want, but changing this changes
        // the node contraction order and requires re-optimizing the parameters of the graph contraction
        return (int) meanDegree * 100;
    }

    static class IgnoreNodeFilter implements EdgeFilter {
        int avoidNode;
        int maxLevel;
        CHGraph graph;

        IgnoreNodeFilter(CHGraph chGraph, int maxLevel) {
            this.graph = chGraph;
            this.maxLevel = maxLevel;
        }

        IgnoreNodeFilter setAvoidNode(int node) {
            this.avoidNode = node;
            return this;
        }

        @Override
        public final boolean accept(EdgeIteratorState iter) {
            // ignore if it is skipNode or adjNode is already contracted
            int node = iter.getAdjNode();
            return avoidNode != node && graph.getLevel(node) == maxLevel;
        }
    }

    static class Shortcut {
        int from;
        int to;
        int skippedEdge1;
        int skippedEdge2;
        double dist;
        double weight;
        int originalEdges;
        long flags = PrepareEncoder.getScFwdDir();

        public Shortcut(int from, int to, double weight, double dist) {
            this.from = from;
            this.to = to;
            this.weight = weight;
            this.dist = dist;
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 23 * hash + from;
            hash = 23 * hash + to;
            return 23 * hash
                    + (int) (Double.doubleToLongBits(this.weight) ^ (Double.doubleToLongBits(this.weight) >>> 32));
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null || getClass() != obj.getClass())
                return false;

            final Shortcut other = (Shortcut) obj;
            return this.from == other.from && this.to == other.to &&
                    Double.doubleToLongBits(this.weight) == Double.doubleToLongBits(other.weight);

        }

        @Override
        public String toString() {
            String str;
            if (flags == PrepareEncoder.getScDirMask())
                str = from + "<->";
            else
                str = from + "->";

            return str + to + ", weight:" + weight + " (" + skippedEdge1 + "," + skippedEdge2 + ")";
        }
    }

    interface ShortcutHandler {
        void foundShortcut(int fromNode, int toNode,
                           double existingDirectWeight, double distance,
                           int outgoingEdge, int outgoingEdgeOrigCount,
                           int incomingEdge, int incomingEdgeOrigCount);

        int getNode();
    }

    class CalcShortcutHandler implements ShortcutHandler {
        int node;
        CalcShortcutsResult calcShortcutsResult = new CalcShortcutsResult();

        @Override
        public int getNode() {
            return node;
        }

        public CalcShortcutHandler setNode(int node) {
            this.node = node;
            calcShortcutsResult.originalEdgesCount = 0;
            calcShortcutsResult.shortcutsCount = 0;
            return this;
        }

        @Override
        public void foundShortcut(int fromNode, int toNode,
                                  double existingDirectWeight, double distance,
                                  int outgoingEdge, int outgoingEdgeOrigCount,
                                  int incomingEdge, int incomingEdgeOrigCount) {
            calcShortcutsResult.shortcutsCount++;
            calcShortcutsResult.originalEdgesCount += incomingEdgeOrigCount + outgoingEdgeOrigCount;
        }
    }

    class AddShortcutHandler implements ShortcutHandler {
        int node;

        @Override
        public int getNode() {
            return node;
        }

        public AddShortcutHandler setNode(int node) {
            shortcuts.clear();
            this.node = node;
            return this;
        }

        @Override
        public void foundShortcut(int fromNode, int toNode,
                                  double existingDirectWeight, double existingDistSum,
                                  int outgoingEdge, int outgoingEdgeOrigCount,
                                  int incomingEdge, int incomingEdgeOrigCount) {
            // FOUND shortcut
            // but be sure that it is the only shortcut in the collection
            // and also in the graph for u->w. If existing AND identical weight => update setProperties.
            // Hint: shortcuts are always one-way due to distinct level of every node but we don't
            // know yet the levels so we need to determine the correct direction or if both directions
            Shortcut sc = new Shortcut(fromNode, toNode, existingDirectWeight, existingDistSum);
            if (shortcuts.containsKey(sc))
                return;

            Shortcut tmpSc = new Shortcut(toNode, fromNode, existingDirectWeight, existingDistSum);
            Shortcut tmpRetSc = shortcuts.get(tmpSc);
            // overwrite flags only if skipped edges are identical
            if (tmpRetSc != null && tmpRetSc.skippedEdge2 == incomingEdge && tmpRetSc.skippedEdge1 == outgoingEdge) {
                tmpRetSc.flags = PrepareEncoder.getScDirMask();
                return;
            }

            Shortcut old = shortcuts.put(sc, sc);
            if (old != null)
                throw new IllegalStateException("Shortcut did not exist (" + sc + ") but was overwriting another one? " + old);

            sc.skippedEdge1 = incomingEdge;
            sc.skippedEdge2 = outgoingEdge;
            sc.originalEdges = incomingEdgeOrigCount + outgoingEdgeOrigCount;
        }
    }

    static class CalcShortcutsResult {
        int originalEdgesCount;
        int shortcutsCount;
    }
}
