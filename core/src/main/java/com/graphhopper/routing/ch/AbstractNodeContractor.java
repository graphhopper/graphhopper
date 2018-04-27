package com.graphhopper.routing.ch;

import com.graphhopper.routing.weighting.AbstractWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.CHGraph;
import com.graphhopper.storage.DataAccess;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.CHEdgeExplorer;
import com.graphhopper.util.StopWatch;

abstract class AbstractNodeContractor implements NodeContractor {
    final GraphHopperStorage ghStorage;
    final CHGraph prepareGraph;
    CHEdgeExplorer inEdgeExplorer;
    CHEdgeExplorer outEdgeExplorer;
    private final DataAccess originalEdges;
    int addedShortcutsCount;
    long dijkstraCount;
    final StopWatch dijkstraSW = new StopWatch();
    int maxLevel;
    private int maxEdgesCount;

    AbstractNodeContractor(Directory dir, GraphHopperStorage ghStorage, CHGraph prepareGraph, Weighting weighting) {
        this.ghStorage = ghStorage;
        this.prepareGraph = prepareGraph;
        originalEdges = dir.find("original_edges_" + AbstractWeighting.weightingToFileName(weighting));
        originalEdges.create(1000);
    }

    @Override
    public void initFromGraph() {
        // todo: do we really need this method ? the problem is that ghStorage/prepareGraph can potentially be modified
        // between the constructor call and contractNode,calcShortcutCount etc. ...
        maxLevel = prepareGraph.getNodes();
        maxEdgesCount = ghStorage.getAllEdges().length();
    }

    @Override
    public void close() {
        originalEdges.close();
    }

    boolean isContracted(int node) {
        return prepareGraph.getLevel(node) != maxLevel;
    }

    @Override
    public int getAddedShortcutsCount() {
        return addedShortcutsCount;
    }

    @Override
    public long getDijkstraCount() {
        return dijkstraCount;
    }

    @Override
    public float getDijkstraSeconds() {
        return dijkstraSW.getCurrentSeconds();
    }

    void setOrigEdgeCount(int edgeId, int value) {
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

    int getOrigEdgeCount(int edgeId) {
        edgeId -= maxEdgesCount;
        if (edgeId < 0)
            return 1;

        long tmp = (long) edgeId * 4;
        originalEdges.ensureCapacity(tmp + 4);
        return originalEdges.getInt(tmp);
    }
}
