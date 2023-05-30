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
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.Helper;
import com.graphhopper.util.shapes.BBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
// ORS-GH MOD START - additional imports
import java.util.Iterator;
// ORS-GH MOD END

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
// ORS-GH MOD START remove final in order to allow for ORS subclass
public class GraphHopperStorage implements Graph, Closeable {
// ORS-GH END
    private static final Logger LOGGER = LoggerFactory.getLogger(GraphHopperStorage.class);
    private final Directory dir;
    private final EncodingManager encodingManager;
    private final StorableProperties properties;
    private final BaseGraph baseGraph;

    // ORS-GH MOD START - additional code
    private ExtendedStorageSequence graphExtensions;

    public ExtendedStorageSequence getExtensions() {
        return graphExtensions;
    }

    private ConditionalEdges conditionalAccess;
    private ConditionalEdges conditionalSpeed;

    public ConditionalEdgesMap getConditionalAccess(FlagEncoder encoder) {
        return getConditionalAccess(encoder.toString());
    }

    public ConditionalEdgesMap getConditionalAccess(String encoderName) {
        return conditionalAccess.getConditionalEdgesMap(encoderName);
    }

    public ConditionalEdgesMap getConditionalSpeed(FlagEncoder encoder) {
        return getConditionalSpeed(encoder.toString());
    }

    public ConditionalEdgesMap getConditionalSpeed(String encoderName) {
        return conditionalSpeed.getConditionalEdgesMap(encoderName);
    }

    // ORS-GH MOD END

    // same flush order etc
    private final Collection<CHEntry> chEntries;
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
        // ORS-GH MOD START - additional storages
        if (encodingManager.hasConditionalAccess()) {
            this.conditionalAccess = new ConditionalEdges(encodingManager, ConditionalEdges.ACCESS, dir);
        }

        if (encodingManager.hasConditionalSpeed()) {
            this.conditionalSpeed = new ConditionalEdges(encodingManager, ConditionalEdges.SPEED, dir);
        }
        // ORS-GH MOD END
        baseGraph = new BaseGraph(dir, encodingManager.getIntsForFlags(), withElevation, withTurnCosts, segmentSize);
        chEntries = new ArrayList<>();
    }

    // ORS-GH MOD START: additional method
    public void setExtendedStorages(ExtendedStorageSequence seq) {
        this.graphExtensions = seq;
    }
    // ORS-GH MOD END

    /**
     * Adds a {@link CHStorage} for the given {@link CHConfig}. You need to call this method before calling {@link #create(long)}
     * or {@link #loadExisting()}.
     */
    public GraphHopperStorage addCHGraph(CHConfig chConfig) {
// ORS-GH MOD START allow overriding in ORS
        if (getCHConfigs().contains(chConfig))
            throw new IllegalArgumentException("For the given CH profile a CHStorage already exists: '" + chConfig.getName() + "'");
        chEntries.add(createCHEntry(chConfig));
        return this;
    }

    protected CHEntry createCHEntry(CHConfig chConfig) {
        baseGraph.checkNotInitialized();
        if (chConfig.getWeighting() == null)
            throw new IllegalStateException("Weighting for CHConfig must not be null");

        CHStorage store = new CHStorage(dir, chConfig.getName(), segmentSize, chConfig.isEdgeBased(), chConfig.getType());
// ORS-GH MOD END
        store.setLowShortcutWeightConsumer(s -> {
            // we just log these to find mapping errors
            NodeAccess nodeAccess = baseGraph.getNodeAccess();
            LOGGER.warn("Setting weights smaller than " + s.minWeight + " is not allowed. " +
                    "You passed: " + s.weight + " for the shortcut " +
                    " nodeA (" + nodeAccess.getLat(s.nodeA) + "," + nodeAccess.getLon(s.nodeA) +
                    " nodeB " + nodeAccess.getLat(s.nodeB) + "," + nodeAccess.getLon(s.nodeB));
        });

        return new CHEntry(chConfig, store, new RoutingCHGraphImpl(baseGraph, store, chConfig.getWeighting()));
    }

    /**
     * @see #addCHGraph(CHConfig)
     */
    public GraphHopperStorage addCHGraphs(List<CHConfig> chConfigs) {
        for (CHConfig chConfig : chConfigs) {
            addCHGraph(chConfig);
        }
        return this;
    }

    /**
     * @return the (only) {@link CHStorage}, or error if there are none or multiple ones
     */
    public CHStorage getCHStore() {
        return getCHEntry().chStore;
    }

    /**
     * @return the {@link CHStorage} for the specified profile name, or null if it does not exist
     */
    public CHStorage getCHStore(String chName) {
        CHEntry chEntry = getCHEntry(chName);
        return chEntry == null ? null : chEntry.chStore;
    }

    /**
     * @return the (only) {@link CHConfig}, or error if there are none or multiple ones
     */
    public CHConfig getCHConfig() {
        // todo: there is no need to expose CHConfig. The RoutingCHGraphs already keep a reference to their weighting.
        return getCHEntry().chConfig;
    }

    /**
     * @return the {@link CHConfig} for the specified profile name, or null if it does not exist
     */
    public CHConfig getCHConfig(String chName) {
        CHEntry chEntry = getCHEntry(chName);
        return chEntry == null ? null : chEntry.chConfig;
    }

    /**
     * @return the (only) {@link RoutingCHGraph}, or error if there are none or multiple ones
     */
    public RoutingCHGraph getRoutingCHGraph() {
        return getCHEntry().chGraph;
    }

    /**
     * @return the {@link RoutingCHGraph} for the specified profile name, or null if it does not exist
     */
    public RoutingCHGraph getRoutingCHGraph(String chName) {
        CHEntry chEntry = getCHEntry(chName);
        return chEntry == null ? null : chEntry.chGraph;
    }

    private CHEntry getCHEntry() {
        if (chEntries.isEmpty()) {
            throw new IllegalStateException("There are no CHs");
        } else if (chEntries.size() > 1) {
            throw new IllegalStateException("There are multiple CHs, use get...(name) to retrieve a specific one");
        } else {
            return chEntries.iterator().next();
        }
    }

    public CHEntry getCHEntry(String chName) {
        for (CHEntry cg : chEntries) {
            if (cg.chConfig.getName().equals(chName))
                return cg;
        }
        return null;
    }

    public List<String> getCHGraphNames() {
        return chEntries.stream().map(ch -> ch.chConfig.getName()).collect(Collectors.toList());
    }

    // ORS-GH MOD START
    // CALT
    // TODO ORS: should calt provide its own classes instead of modifying ch?
    public RoutingCHGraphImpl getIsochroneGraph(Weighting weighting) {
        if (chEntries.isEmpty())
            throw new IllegalStateException("Cannot find graph implementation");
        Iterator<CHEntry> iterator = chEntries.iterator();
        while(iterator.hasNext()){
            CHEntry cg = iterator.next();
            if(cg.chConfig.getType() == "isocore"
                    && cg.chConfig.getWeighting().getName() == weighting.getName()
                    && cg.chConfig.getWeighting().getFlagEncoder().toString() == weighting.getFlagEncoder().toString())
                return cg.chGraph;
        }
        throw new IllegalStateException("No isochrone graph was found");
    }
    // ORS-GH MOD END

    public boolean isCHPossible() {
        return !chEntries.isEmpty();
    }

    public List<CHConfig> getCHConfigs() {
        return chEntries.stream().map(c -> c.chConfig).collect(Collectors.toList());
    }

    public List<CHConfig> getCHConfigs(boolean edgeBased) {
        List<CHConfig> result = new ArrayList<>();
        List<CHConfig> chConfigs = getCHConfigs();
        for (CHConfig profile : chConfigs) {
            if (edgeBased == profile.isEdgeBased()) {
                result.add(profile);
            }
        }
        return result;
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

        // ORS-GH MOD START - create extended/conditional storages
        if (graphExtensions != null) {
            graphExtensions.create(initSize);
        }
        // TODO ORS: Find out byteCount to create these
        if (conditionalAccess != null) {
            conditionalAccess.create(initSize);
        }
        if (conditionalSpeed != null) {
            conditionalSpeed.create(initSize);
        }
        // ORS-GH MOD END

        chEntries.forEach(ch -> ch.chStore.create());

        List<CHConfig> chConfigs = getCHConfigs();
        List<String> chProfileNames = new ArrayList<>(chConfigs.size());
        for (CHConfig chConfig : chConfigs) {
            chProfileNames.add(chConfig.getName());
        }
        properties.put("graph.ch.profiles", chProfileNames.toString());
        return this;
    }

    public EncodingManager getEncodingManager() {
        return encodingManager;
    }

    public StorableProperties getProperties() {
        return properties;
    }

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

            chEntries.forEach(cg -> {
                if (!cg.chStore.loadExisting())
                    throw new IllegalStateException("Cannot load " + cg);
            });

            // ORS-GH MOD START
            if (this.conditionalAccess != null) {
                conditionalAccess.loadExisting();
            }

            if (this.conditionalSpeed != null) {
                conditionalSpeed.loadExisting();
            }
            // ORS-GH MOD END

// ORS-GH MOD START add ORS hook
            loadExistingORS();
// ORS-GH MOD END

            return true;
        }
        return false;
    }

// ORS-GH MOD START add ORS hook
    public void loadExistingORS() {};
// ORS-GH MOD END

    private void checkIfConfiguredAndLoadedWeightingsCompatible() {
        String loadedStr = properties.get("graph.ch.profiles");
        List<String> loaded = Helper.parseList(loadedStr);
        List<CHConfig> configured = getCHConfigs();
        List<String> configuredNames = new ArrayList<>(configured.size());
        for (CHConfig p : configured) {
            configuredNames.add(p.getName());
        }
        for (String configuredName : configuredNames) {
            if (!loaded.contains(configuredName)) {
                throw new IllegalStateException("Configured CH profile: '" + configuredName + "' is not contained in loaded CH profiles: '" + loadedStr + "'.\n" +
                        "You configured: " + configuredNames);
            }
        }
    }

    public void flush() {
        chEntries.stream().map(ch -> ch.chStore).filter(s -> !s.isClosed()).forEach(CHStorage::flush);
        baseGraph.flush();
        properties.flush();
        // ORS-GH MOD START - additional code
        if (graphExtensions != null) {
            graphExtensions.flush();
        }
        if (conditionalAccess != null) {
            conditionalAccess.flush();
        }
        if (conditionalSpeed != null) {
            conditionalSpeed.flush();
        }
        // ORS-GH MOD END
    }

    @Override
    public void close() {
        properties.close();
        baseGraph.close();
        // ORS-GH MOD START - additional code
        if (graphExtensions != null) {
            graphExtensions.close();
        }
        if (conditionalAccess != null) {
            conditionalAccess.close();
        }
        if (conditionalSpeed != null) {
            conditionalSpeed.close();
        }
        // ORS-GH MOD END
        chEntries.stream().map(ch -> ch.chStore).filter(s -> !s.isClosed()).forEach(CHStorage::close);
    }

    public boolean isClosed() {
        return baseGraph.nodes.isClosed();
    }

    public long getCapacity() {
        long cnt = baseGraph.getCapacity() + properties.getCapacity();
        long cgs = chEntries.stream().mapToLong(ch -> ch.chStore.getCapacity()).sum();
        // ORS-GH MOD START - additional code
        if (graphExtensions != null) {
            cnt += graphExtensions.getCapacity();
        }
        if (conditionalAccess != null) {
            cnt += conditionalAccess.getCapacity();
        }
        if (conditionalSpeed != null) {
            cnt += conditionalSpeed.getCapacity();
        }
        // ORS-GH MOD END
        return cnt + cgs;
    }

    /**
     * Avoid that edges and nodes of the base graph are further modified. Necessary as hook for e.g.
     * ch graphs on top to initialize themselves
     */
    public synchronized void freeze() {
        if (isFrozen())
            return;
        baseGraph.freeze();
        chEntries.forEach(ch -> {
            // we use a rather small value here. this might result in more allocations later, but they should
            // not matter that much. if we expect a too large value the shortcuts DataAccess will end up
            // larger than needed, because we do not do something like trimToSize in the end.
            double expectedShortcuts = 0.3 * baseGraph.getEdges();
            ch.chStore.init(baseGraph.getNodes(), (int) expectedShortcuts);
        });
    }

    public boolean isFrozen() {
        return baseGraph.isFrozen();
    }

    public String toDetailsString() {
        String str = baseGraph.toDetailsString();
        for (CHEntry ch : chEntries) {
            str += ", " + ch.chStore.toDetailsString();
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
    public void flushAndCloseEarly() {
        baseGraph.flushAndCloseGeometryAndNameStorage();
    }

// ORS-GH MOD START change to public in order to be able to access it from ORSGraphHopperStorage subclass
    public static class CHEntry {
        public CHConfig chConfig;
        public CHStorage chStore;
        public RoutingCHGraphImpl chGraph;
// ORS-GH MOD END

        public CHEntry(CHConfig chConfig, CHStorage chStore, RoutingCHGraphImpl chGraph) {
            this.chConfig = chConfig;
            this.chStore = chStore;
            this.chGraph = chGraph;
        }
    }
}
