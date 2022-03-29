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
package com.graphhopper.storage;

import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.TagParserManager;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.util.Constants;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.shapes.BBox;

import java.io.Closeable;

/**
 * This class manages all storage related methods and delegates the calls to the associated graphs.
 * The associated graphs manage their own necessary data structures and are used to provide e.g.
 * different traversal methods. By default this class implements the graph interface and results in
 * identical behavior as the Graph instance from getBaseGraph()
 * <p>
 *
 * @author Peter Karich
 */
public final class GraphHopperStorage implements Graph, Closeable {
    private final Directory dir;
    private final TagParserManager tagParserManager;
    private final StorableProperties properties;
    private final BaseGraph baseGraph;

    /**
     * Use {@link GraphBuilder} to create a graph
     */
    public GraphHopperStorage(Directory dir, TagParserManager tagParserManager, boolean withElevation, boolean withTurnCosts, int segmentSize) {
        if (tagParserManager == null)
            throw new IllegalArgumentException("EncodingManager needs to be non-null since 0.7. Create one using EncodingManager.create or EncodingManager.create(flagEncoderFactory, ghLocation)");

        this.tagParserManager = tagParserManager;
        this.dir = dir;
        this.properties = new StorableProperties(dir);
        baseGraph = new BaseGraph(dir, tagParserManager.getIntsForFlags(), withElevation, withTurnCosts, segmentSize);
    }

    /**
     * @return the directory where this graph is stored.
     */
    public Directory getDirectory() {
        return dir;
    }

    /**
     * After configuring this storage you need to create it explicitly.
     */
    public GraphHopperStorage create(long byteCount) {
        baseGraph.checkNotInitialized();
        if (tagParserManager == null)
            throw new IllegalStateException("EncodingManager can only be null if you call loadExisting");

        dir.create();

        properties.create(100);
        properties.put("graph.encoded_values", tagParserManager.toEncodedValuesAsString());
        properties.put("graph.flag_encoders", tagParserManager.toFlagEncodersAsString());

        baseGraph.create(Math.max(byteCount, 100));
        return this;
    }

    public TagParserManager getTagParserManager() {
        return tagParserManager;
    }

    public EncodingManager getEncodingManager() {
        return tagParserManager.getEncodingManager();
    }

    public StorableProperties getProperties() {
        return properties;
    }

    public boolean loadExisting() {
        baseGraph.checkNotInitialized();
        if (properties.loadExisting()) {
            if (properties.containsVersion())
                throw new IllegalStateException("The GraphHopper file format is not compatible with the data you are " +
                        "trying to load. You either need to use an older version of GraphHopper or run a new import");
            // check encoding for compatibility
            String flagEncodersStr = properties.get("graph.flag_encoders");

            if (!tagParserManager.toFlagEncodersAsString().equalsIgnoreCase(flagEncodersStr)) {
                throw new IllegalStateException("Encoding does not match:"
                        + "\nGraphhopper config: " + tagParserManager.toFlagEncodersAsString()
                        + "\nGraph: " + flagEncodersStr
                        + "\nChange configuration to match the graph or delete " + dir.getLocation());
            }

            String encodedValueStr = properties.get("graph.encoded_values");
            if (!tagParserManager.toEncodedValuesAsString().equalsIgnoreCase(encodedValueStr)) {
                throw new IllegalStateException("Encoded values do not match:"
                        + "\nGraphhopper config: " + tagParserManager.toEncodedValuesAsString()
                        + "\nGraph: " + encodedValueStr
                        + "\nChange configuration to match the graph or delete " + dir.getLocation());
            }
            baseGraph.loadExisting();
            return true;
        }
        return false;
    }

    public void flush() {
        baseGraph.flush();
        properties.flush();
    }

    @Override
    public void close() {
        properties.close();
        baseGraph.close();
    }

    public boolean isClosed() {
        return baseGraph.isClosed();
    }

    public long getCapacity() {
        return baseGraph.getCapacity() + properties.getCapacity();
    }

    /**
     * Avoid that edges and nodes of the base graph are further modified. Necessary as hook for e.g.
     * ch graphs on top to initialize themselves
     */
    public synchronized void freeze() {
        if (isFrozen())
            return;
        baseGraph.freeze();
    }

    public boolean isFrozen() {
        return baseGraph.isFrozen();
    }

    public String toDetailsString() {
        return baseGraph.toDetailsString();
    }

    @Override
    public String toString() {
        return tagParserManager
                + "|" + getDirectory().getDefaultType()
                + "|" + baseGraph.nodeAccess.getDimension() + "D"
                + "|" + (baseGraph.supportsTurnCosts() ? baseGraph.turnCostStorage : "no_turn_cost")
                + "|" + getVersionsString();
    }

    private String getVersionsString() {
        return "nodes:" + Constants.VERSION_NODE +
                ",edges:" + Constants.VERSION_EDGE +
                ",geometry:" + Constants.VERSION_GEOMETRY +
                ",location_index:" + Constants.VERSION_LOCATION_IDX +
                ",string_index:" + Constants.VERSION_STRING_IDX +
                ",nodesCH:" + Constants.VERSION_NODE_CH +
                ",shortcuts:" + Constants.VERSION_SHORTCUT;
    }

    // now delegate all Graph methods to BaseGraph to avoid ugly programming flow ala
    // GraphHopperStorage storage = ..;
    // Graph g = storage.getBaseGraph();
    // instead directly the storage can be used to traverse the base graph
    @Override
    public BaseGraph getBaseGraph() {
        return baseGraph;
    }

    @Override
    public int getNodes() {
        return baseGraph.getNodes();
    }

    @Override
    public int getEdges() {
        return baseGraph.getEdges();
    }

    @Override
    public NodeAccess getNodeAccess() {
        return baseGraph.getNodeAccess();
    }

    @Override
    public BBox getBounds() {
        return baseGraph.getBounds();
    }

    @Override
    public EdgeIteratorState edge(int a, int b) {
        return baseGraph.edge(a, b);
    }

    @Override
    public EdgeIteratorState getEdgeIteratorState(int edgeId, int adjNode) {
        return baseGraph.getEdgeIteratorState(edgeId, adjNode);
    }

    @Override
    public EdgeIteratorState getEdgeIteratorStateForKey(int edgeKey) {
        return baseGraph.getEdgeIteratorStateForKey(edgeKey);
    }

    @Override
    public AllEdgesIterator getAllEdges() {
        return baseGraph.getAllEdges();
    }

    @Override
    public EdgeExplorer createEdgeExplorer(EdgeFilter filter) {
        return baseGraph.createEdgeExplorer(filter);
    }

    @Override
    public TurnCostStorage getTurnCostStorage() {
        return baseGraph.getTurnCostStorage();
    }

    @Override
    public Weighting wrapWeighting(Weighting weighting) {
        return weighting;
    }

    @Override
    public int getOtherNode(int edge, int node) {
        return baseGraph.getOtherNode(edge, node);
    }

    @Override
    public boolean isAdjacentToNode(int edge, int node) {
        return baseGraph.isAdjacentToNode(edge, node);
    }

    /**
     * Flush and free base graph resources like way geometries and StringIndex
     */
    public void flushAndCloseGeometryAndNameStorage() {
        baseGraph.flushAndCloseGeometryAndNameStorage();
    }

}
