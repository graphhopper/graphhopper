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
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.shapes.BBox;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * This class manages all storage related methods and delegates the calls to the associated graphs.
 * The associated graphs manage their own necessary data structures and are used to provide e.g.
 * different traversal methods. By default this class implements the graph interface and results in
 * identical behavior as the Graph instance from getBaseGraph()
 * <p>
 *
 * @author Peter Karich
 * @see GraphBuilder to create a (CH)Graph easier
 */
public final class GraphHopperStorage implements GraphStorage, Graph {
    private final Directory dir;
    private final EncodingManager encodingManager;
    private final StorableProperties properties;
    private final BaseGraph baseGraph;
    // same flush order etc
    private final Collection<CHGraphImpl> chGraphs;
    private final int segmentSize;

    public GraphHopperStorage(Directory dir, EncodingManager encodingManager, boolean withElevation) {
        this(dir, encodingManager, withElevation, false);
    }

    public GraphHopperStorage(Directory dir, EncodingManager encodingManager, boolean withElevation, boolean withTurnCosts) {
        this(dir, encodingManager, withElevation, withTurnCosts, -1);
    }

    public GraphHopperStorage(Directory dir, EncodingManager encodingManager, boolean withElevation, boolean withTurnCosts, int segmentSize) {
        if (encodingManager == null)
            throw new IllegalArgumentException("EncodingManager needs to be non-null since 0.7. Create one using EncodingManager.create or EncodingManager.create(flagEncoderFactory, ghLocation)");

        this.encodingManager = encodingManager;
        this.dir = dir;
        this.properties = new StorableProperties(dir);
        this.segmentSize = segmentSize;
        InternalGraphEventListener listener = new InternalGraphEventListener() {
            @Override
            public void initStorage() {
                for (CHGraphImpl cg : chGraphs) {
                    cg.initStorage();
                }
            }

            @Override
            public void freeze() {
                for (CHGraphImpl cg : chGraphs) {
                    cg._prepareForContraction();
                }
            }
        };
        baseGraph = new BaseGraph(dir, encodingManager, withElevation, listener, withTurnCosts, segmentSize);
        chGraphs = new ArrayList<>();
    }

    /**
     * Adds a {@link CHGraph} for the given {@link CHProfile}. You need to call this method before calling {@link #create(long)}
     * or {@link #loadExisting()}.
     */
    public void addCHGraph(CHProfile chProfile) {
        baseGraph.checkNotInitialized();
        if (getCHProfiles().contains(chProfile)) {
            throw new IllegalArgumentException("For the given CH profile a CHGraph already exists: " + chProfile);
        }
        chGraphs.add(new CHGraphImpl(chProfile, dir, baseGraph, segmentSize));
    }

    /**
     * @see #addCHGraph(CHProfile)
     */
    public void addCHGraphs(List<CHProfile> chProfiles) {
        for (CHProfile chProfile : chProfiles) {
            addCHGraph(chProfile);
        }
    }

    public CHGraph getCHGraph() {
        if (chGraphs.isEmpty()) {
            throw new IllegalStateException("There is no CHGraph");
        } else if (chGraphs.size() > 1) {
            throw new IllegalStateException("There are multiple CHGraphs, use getCHGraph(CHProfile) to retrieve a specific one");
        } else {
            return chGraphs.iterator().next();
        }
    }

    /**
     * @return the {@link CHGraph} for the specified {@link CHProfile}
     */
    public CHGraph getCHGraph(CHProfile profile) {
        if (chGraphs.isEmpty())
            throw new IllegalStateException("There is no CHGraph");

        if (profile == null)
            throw new IllegalStateException("Cannot find CHGraph with null CHProfile");

        List<CHProfile> existing = new ArrayList<>();
        for (CHGraphImpl cg : chGraphs) {
            if (cg.getCHProfile().equals(profile))
                return cg;
            existing.add(cg.getCHProfile());
        }

        throw new IllegalStateException("Cannot find CHGraph for the specified profile: " + profile + ", existing:" + existing);
    }

    public boolean isCHPossible() {
        return !chGraphs.isEmpty();
    }

    public List<CHProfile> getCHProfiles() {
        List<CHProfile> result = new ArrayList<>(chGraphs.size());
        for (CHGraphImpl chGraph : chGraphs) {
            result.add(chGraph.getCHProfile());
        }
        return result;
    }

    public List<CHProfile> getCHProfiles(boolean edgeBased) {
        List<CHProfile> result = new ArrayList<>();
        List<CHProfile> chProfiles = getCHProfiles();
        for (CHProfile profile : chProfiles) {
            if (edgeBased == profile.isEdgeBased()) {
                result.add(profile);
            }
        }
        return result;
    }

    /**
     * @return the directory where this graph is stored.
     */
    @Override
    public Directory getDirectory() {
        return dir;
    }

    /**
     * After configuring this storage you need to create it explicitly.
     */
    @Override
    public GraphHopperStorage create(long byteCount) {
        baseGraph.checkNotInitialized();
        if (encodingManager == null)
            throw new IllegalStateException("EncodingManager can only be null if you call loadExisting");

        dir.create();
        long initSize = Math.max(byteCount, 100);
        properties.create(100);

        properties.put("graph.encoded_values", encodingManager.toEncodedValuesAsString());
        properties.put("graph.flag_encoders", encodingManager.toFlagEncodersAsString());

        properties.put("graph.byte_order", dir.getByteOrder());
        properties.put("graph.dimension", baseGraph.nodeAccess.getDimension());
        properties.putCurrentVersions();

        baseGraph.create(initSize);

        for (CHGraphImpl cg : chGraphs) {
            cg.create(byteCount);
        }

        properties.put("graph.ch.profiles", getCHProfiles().toString());
        return this;
    }

    @Override
    public EncodingManager getEncodingManager() {
        return encodingManager;
    }

    @Override
    public StorableProperties getProperties() {
        return properties;
    }

    @Override
    public void markNodeRemoved(int index) {
        baseGraph.getRemovedNodes().add(index);
    }

    @Override
    public boolean isNodeRemoved(int index) {
        return baseGraph.getRemovedNodes().contains(index);
    }

    @Override
    public void optimize() {
        if (isFrozen())
            throw new IllegalStateException("do not optimize after graph was frozen");

        int delNodes = baseGraph.getRemovedNodes().getCardinality();
        if (delNodes <= 0)
            return;

        // Deletes only nodes.
        // It reduces the fragmentation of the node space but introduces new unused edges.
        baseGraph.inPlaceNodeRemove(delNodes);

        // Reduce memory usage
        baseGraph.trimToSize();
    }

    @Override
    public boolean loadExisting() {
        baseGraph.checkNotInitialized();
        if (properties.loadExisting()) {
            properties.checkVersions(false);
            // check encoding for compatibility
            String flagEncodersStr = properties.get("graph.flag_encoders");

            if (!encodingManager.toFlagEncodersAsString().equalsIgnoreCase(flagEncodersStr)) {
                throw new IllegalStateException("Encoding does not match:"
                        + "\nGraphhopper config: " + encodingManager.toFlagEncodersAsString()
                        + "\nGraph: " + flagEncodersStr
                        + "\nChange configuration to match the graph or delete " + dir.getLocation());
            }

            String encodedValueStr = properties.get("graph.encoded_values");
            if (!encodingManager.toEncodedValuesAsString().equalsIgnoreCase(encodedValueStr)) {
                throw new IllegalStateException("Encoded values do not match:"
                        + "\nGraphhopper config: " + encodingManager.toEncodedValuesAsString()
                        + "\nGraph: " + encodedValueStr
                        + "\nChange configuration to match the graph or delete " + dir.getLocation());
            }

            String byteOrder = properties.get("graph.byte_order");
            if (!byteOrder.equalsIgnoreCase("" + dir.getByteOrder()))
                throw new IllegalStateException("Configured graph.byte_order (" + dir.getByteOrder() + ") is not equal to loaded " + byteOrder + "");

            String dim = properties.get("graph.dimension");
            baseGraph.loadExisting(dim);

            checkIfConfiguredAndLoadedWeightingsCompatible();

            for (CHGraphImpl cg : chGraphs) {
                if (!cg.loadExisting())
                    throw new IllegalStateException("Cannot load " + cg);
            }

            return true;
        }
        return false;
    }

    private void checkIfConfiguredAndLoadedWeightingsCompatible() {
        String loadedStr = properties.get("graph.ch.profiles");
        List<String> loaded = parseList(loadedStr);
        List<CHProfile> configured = getCHProfiles();
        // todo: not entirely sure here. when no ch is configured at all (neither edge nor node), but there are any
        // ch graphs (edge or node) we throw an error ? previously we threw an error when no ch weighting was configured
        // even though there was a ch graph.
        if (configured.isEmpty() && !loaded.isEmpty()) {
            throw new IllegalStateException("You loaded a CH graph, but you did not specify any CH weightings in prepare.ch.weightings");
        }
        for (CHProfile chProfile : configured) {
            if (!loaded.contains(chProfile.toString())) {
                throw new IllegalStateException("Configured CH profile: " + chProfile.toString() + " is not contained in loaded weightings for CH" + loadedStr + ".\n" +
                        "You configured: " + configured);
            }
        }
    }

    /**
     * parses a string like [a,b,c]
     */
    private List<String> parseList(String listStr) {
        String trimmed = listStr.trim();
        if (trimmed.length() < 2)
            return Collections.emptyList();
        String[] items = trimmed.substring(1, trimmed.length() - 1).split(",");
        List<String> result = new ArrayList<>();
        for (String item : items) {
            String s = item.trim();
            if (!s.isEmpty()) {
                result.add(s);
            }
        }
        return result;
    }

    @Override
    public void flush() {
        for (CHGraphImpl cg : chGraphs) {
            if (!cg.isClosed())
                cg.flush();
        }

        baseGraph.flush();
        properties.flush();
    }

    @Override
    public void close() {
        properties.close();
        baseGraph.close();

        for (CHGraphImpl cg : chGraphs) {
            if (!cg.isClosed())
                cg.close();
        }
    }

    @Override
    public boolean isClosed() {
        return baseGraph.nodes.isClosed();
    }

    @Override
    public long getCapacity() {
        long cnt = baseGraph.getCapacity() + properties.getCapacity();

        for (CHGraphImpl cg : chGraphs) {
            cnt += cg.getCapacity();
        }
        return cnt;
    }

    /**
     * Avoid that edges and nodes of the base graph are further modified. Necessary as hook for e.g.
     * ch graphs on top to initialize themselves
     */
    public synchronized void freeze() {
        if (!baseGraph.isFrozen())
            baseGraph.freeze();
    }

    boolean isFrozen() {
        return baseGraph.isFrozen();
    }

    @Override
    public String toDetailsString() {
        String str = baseGraph.toDetailsString();
        for (CHGraphImpl cg : chGraphs) {
            str += ", " + cg.toDetailsString();
        }

        return str;
    }

    @Override
    public String toString() {
        return (isCHPossible() ? "CH|" : "")
                + encodingManager
                + "|" + getDirectory().getDefaultType()
                + "|" + baseGraph.nodeAccess.getDimension() + "D"
                + "|" + (baseGraph.supportsTurnCosts() ? baseGraph.turnCostStorage : "no_turn_cost")
                + "|" + getProperties().versionsToString();
    }

    // now delegate all Graph methods to BaseGraph to avoid ugly programming flow ala
    // GraphHopperStorage storage = ..;
    // Graph g = storage.getBaseGraph();
    // instead directly the storage can be used to traverse the base graph
    @Override
    public Graph getBaseGraph() {
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
    public EdgeIteratorState edge(int a, int b, double distance, boolean bothDirections) {
        return baseGraph.edge(a, b, distance, bothDirections);
    }

    @Override
    public EdgeIteratorState getEdgeIteratorState(int edgeId, int adjNode) {
        return baseGraph.getEdgeIteratorState(edgeId, adjNode);
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
    public EdgeExplorer createEdgeExplorer() {
        return baseGraph.createEdgeExplorer();
    }

    @Override
    public Graph copyTo(Graph g) {
        return baseGraph.copyTo(g);
    }

    @Override
    public TurnCostStorage getTurnCostStorage() {
        return baseGraph.getTurnCostStorage();
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
     * Flush and close resources like wayGeometry that are not needed for CH preparation.
     */
    public void flushAndCloseEarly() {
        baseGraph.flushAndCloseGeometryAndNameStorage();
    }
}
