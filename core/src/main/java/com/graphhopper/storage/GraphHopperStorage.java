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
import com.graphhopper.routing.weighting.Weighting;
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
 * identical behavior as the Graph instance from getGraph(Graph.class)
 * <p>
 *
 * @author Peter Karich
 * @see GraphBuilder to create a (CH)Graph easier
 * @see #getGraph(java.lang.Class)
 */
public final class GraphHopperStorage implements GraphStorage, Graph {
    private final Directory dir;
    private final EncodingManager encodingManager;
    private final StorableProperties properties;
    private final BaseGraph baseGraph;
    // same flush order etc
    private final Collection<CHGraphImpl> nodeBasedCHGraphs = new ArrayList<>(5);
    private final Collection<CHGraphImpl> edgeBasedCHGraphs = new ArrayList<>(5);

    public GraphHopperStorage(Directory dir, EncodingManager encodingManager, boolean withElevation, GraphExtension extendedStorage) {
        this(Collections.<Weighting>emptyList(), Collections.<Weighting>emptyList(), dir, encodingManager, withElevation, extendedStorage);
    }

    public GraphHopperStorage(List<? extends Weighting> nodeBasedCHWeightings, Directory dir, EncodingManager encodingManager,
                              boolean withElevation, GraphExtension extendedStorage) {
        this(nodeBasedCHWeightings, Collections.<Weighting>emptyList(), dir, encodingManager, withElevation, extendedStorage);
    }

    public GraphHopperStorage(List<? extends Weighting> nodeBasedCHWeightings, List<? extends Weighting> edgeBasedCHWeightings,
                              Directory dir, EncodingManager encodingManager, boolean withElevation, GraphExtension extendedStorage) {
        if (extendedStorage == null)
            throw new IllegalArgumentException("GraphExtension cannot be null, use NoOpExtension");

        if (encodingManager == null)
            throw new IllegalArgumentException("EncodingManager needs to be non-null since 0.7. Create one using EncodingManager.create or EncodingManager.create(flagEncoderFactory, ghLocation)");

        this.encodingManager = encodingManager;
        this.dir = dir;
        this.properties = new StorableProperties(dir);
        InternalGraphEventListener listener = new InternalGraphEventListener() {
            @Override
            public void initStorage() {
                for (CHGraphImpl cg : getAllCHGraphs()) {
                    cg.initStorage();
                }
            }

            @Override
            public void freeze() {
                for (CHGraphImpl cg : getAllCHGraphs()) {
                    cg._prepareForContraction();
                }
            }
        };

        this.baseGraph = new BaseGraph(dir, encodingManager, withElevation, listener, extendedStorage);
        for (Weighting w : nodeBasedCHWeightings) {
            nodeBasedCHGraphs.add(new CHGraphImpl(w, dir, this.baseGraph, false));
        }
        for (Weighting w : edgeBasedCHWeightings) {
            edgeBasedCHGraphs.add(new CHGraphImpl(w, dir, this.baseGraph, true));
        }
    }

    /**
     * This method returns the routing graph for the specified weighting, could be potentially
     * filled with shortcuts.
     */
    public <T extends Graph> T getGraph(Class<T> clazz, Weighting weighting) {
        if (clazz.equals(Graph.class))
            return (T) baseGraph;

        Collection<CHGraphImpl> chGraphs = getAllCHGraphs();
        if (chGraphs.isEmpty())
            throw new IllegalStateException("Cannot find graph implementation for " + clazz);

        if (weighting == null)
            throw new IllegalStateException("Cannot find CHGraph with null weighting");

        List<Weighting> existing = new ArrayList<>();
        for (CHGraphImpl cg : chGraphs) {
            if (cg.getWeighting() == weighting)
                return (T) cg;

            existing.add(cg.getWeighting());
        }

        throw new IllegalStateException("Cannot find CHGraph for specified weighting: " + weighting + ", existing:" + existing);
    }

    public <T extends Graph> T getGraph(Class<T> clazz) {
        if (clazz.equals(Graph.class))
            return (T) baseGraph;

        Collection<CHGraphImpl> chGraphs = getAllCHGraphs();
        if (chGraphs.isEmpty())
            throw new IllegalStateException("Cannot find graph implementation for " + clazz);

        CHGraph cg = chGraphs.iterator().next();
        return (T) cg;
    }

    public boolean isCHPossible() {
        return !getAllCHGraphs().isEmpty();
    }

    public List<Weighting> getNodeBasedCHWeightings() {
        return getWeightingsFromGraphs(nodeBasedCHGraphs);
    }

    public List<Weighting> getEdgeBasedCHWeightings() {
        return getWeightingsFromGraphs(edgeBasedCHGraphs);
    }

    private List<Weighting> getWeightingsFromGraphs(Collection<CHGraphImpl> graphs) {
        List<Weighting> list = new ArrayList<>(graphs.size());
        for (CHGraphImpl cg : graphs) {
            list.add(cg.getWeighting());
        }
        return list;
    }

    /**
     * @return the directory where this graph is stored.
     */
    @Override
    public Directory getDirectory() {
        return dir;
    }

    @Override
    public void setSegmentSize(int bytes) {
        baseGraph.setSegmentSize(bytes);

        for (CHGraphImpl cg : getAllCHGraphs()) {
            cg.setSegmentSize(bytes);
        }
    }

    /**
     * After configuring this storage you need to create it explicitly.
     */
    @Override
    public GraphHopperStorage create(long byteCount) {
        baseGraph.checkInit();
        if (encodingManager == null)
            throw new IllegalStateException("EncodingManager can only be null if you call loadExisting");

        dir.create();
        long initSize = Math.max(byteCount, 100);
        properties.create(100);

        properties.put("graph.bytes_for_flags", encodingManager.getBytesForFlags());
        properties.put("graph.flag_encoders", encodingManager.toDetailsString());

        properties.put("graph.byte_order", dir.getByteOrder());
        properties.put("graph.dimension", baseGraph.nodeAccess.getDimension());
        properties.putCurrentVersions();

        baseGraph.create(initSize);

        for (CHGraphImpl cg : getAllCHGraphs()) {
            cg.create(byteCount);
        }

        properties.put("graph.ch.weightings", getNodeBasedCHWeightings().toString());
        properties.put("graph.ch.edge.weightings", getEdgeBasedCHWeightings().toString());
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

    public void setAdditionalEdgeField(long edgePointer, int value) {
        baseGraph.setAdditionalEdgeField(edgePointer, value);
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
        baseGraph.checkInit();
        if (properties.loadExisting()) {
            properties.checkVersions(false);
            // check encoding for compatibility
            String flagEncodersStr = properties.get("graph.flag_encoders");

            if (!flagEncodersStr.isEmpty() && !encodingManager.toDetailsString().equalsIgnoreCase(flagEncodersStr)) {
                throw new IllegalStateException("Encoding does not match:"
                        + "\nGraphhopper config: " + encodingManager.toDetailsString()
                        + "\nGraph: " + flagEncodersStr
                        + "\nChange configuration to match the graph or delete " + dir.getLocation());
            }

            String byteOrder = properties.get("graph.byte_order");
            if (!byteOrder.equalsIgnoreCase("" + dir.getByteOrder()))
                throw new IllegalStateException("Configured graph.byte_order (" + dir.getByteOrder() + ") is not equal to loaded " + byteOrder + "");

            String bytesForFlags = properties.get("graph.bytes_for_flags");
            if (!bytesForFlags.equalsIgnoreCase("" + encodingManager.getBytesForFlags()))
                throw new IllegalStateException("Configured graph.bytes_for_flags (" + encodingManager.getBytesForFlags() + ") is not equal to loaded " + bytesForFlags);

            String dim = properties.get("graph.dimension");
            baseGraph.loadExisting(dim);

            checkIfConfiguredAndLoadedWeightingsCompatible();

            for (CHGraphImpl cg : getAllCHGraphs()) {
                if (!cg.loadExisting())
                    throw new IllegalStateException("Cannot load " + cg);
            }

            return true;
        }
        return false;
    }

    private void checkIfConfiguredAndLoadedWeightingsCompatible() {
        String loadedStrNode = properties.get("graph.ch.weightings");
        List<String> loadedNode = parseList(loadedStrNode);
        String loadedStrEdge = properties.get("graph.ch.edge.weightings");
        List<String> loadedEdge = parseList(loadedStrEdge);
        List<Weighting> configuredNode = getNodeBasedCHWeightings();
        List<Weighting> configuredEdge = getEdgeBasedCHWeightings();
        // todo: not entirely sure here. when no ch is configured at all (neither edge nor node), but there are any
        // ch graphs (edge or node) we throw an error ? previously we threw an error when no ch weighting was configured
        // even though there was a ch graph.
        if ((configuredNode.isEmpty() && configuredEdge.isEmpty()) && (!loadedNode.isEmpty() || !loadedEdge.isEmpty())) {
            throw new IllegalStateException("You loaded a CH graph, but you did not specify graph.ch.weightings");
        }
        for (Weighting w : configuredNode) {
            if (!loadedNode.contains(w.toString())) {
                throw new IllegalStateException("Configured weighting: " + w.toString() + " is not contained in loaded weightings for CH" + loadedStrNode + ".\n" +
                        "You configured graph.ch.weightings: " + configuredNode);
            }
        }
        for (Weighting w : configuredEdge) {
            if (!loadedEdge.contains(w.toString())) {
                throw new IllegalStateException("Configured weighting: " + w.toString() + " is not contained in loaded weightings for edge-based CH" + loadedStrEdge + ".\n" +
                        "You configured graph.ch.edge.weightings: " + configuredEdge);
            }
        }
    }

    /**
     * parses a string like [a,b,c]
     */
    private List<String> parseList(String listStr) {
        String trimmed = listStr.trim();
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
        for (CHGraphImpl cg : getAllCHGraphs()) {
            cg.setNodesHeader();
            cg.setEdgesHeader();
            cg.flush();
        }

        baseGraph.flush();
        properties.flush();
    }

    @Override
    public void close() {
        properties.close();
        baseGraph.close();

        for (CHGraphImpl cg : getAllCHGraphs()) {
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

        for (CHGraphImpl cg : getAllCHGraphs()) {
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
        for (CHGraphImpl cg : getAllCHGraphs()) {
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
                + "|" + baseGraph.extStorage
                + "|" + getProperties().versionsToString();
    }

    // now all delegation graph method to avoid ugly programming flow ala
    // GraphHopperStorage storage = ..;
    // Graph g = storage.getGraph(Graph.class);
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
        return getAllEdges().length();
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
    public GraphExtension getExtension() {
        return baseGraph.getExtension();
    }

    @Override
    public int getOtherNode(int edge, int node) {
        return baseGraph.getOtherNode(edge, node);
    }

    private Collection<CHGraphImpl> getAllCHGraphs() {
        // todo: this method is only used to have a 'view' on the two collections. we could also create this only once
        // as long as the graph collections are only modified in the constructor (otherwise we would have to make sure
        // the lists are in sync). another option would be something like guava concat.
        List<CHGraphImpl> result = new ArrayList<>(nodeBasedCHGraphs.size() + edgeBasedCHGraphs.size());
        result.addAll(nodeBasedCHGraphs);
        result.addAll(edgeBasedCHGraphs);
        return result;
    }
}
