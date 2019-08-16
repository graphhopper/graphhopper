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
package com.graphhopper.routing.ar;

import com.carrotsearch.hppc.IntArrayList;
import com.graphhopper.routing.util.AbstractAlgoPreparation;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.storage.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;

/**
 * This class separates a ghStorage into the wished amount of areas. In each step the ghStorage will be coarsened.
 * This will be done until only as many nodes as areas remain.
 *
 * @author Maximilian Sturm
 */
public class GraphPartition extends AbstractAlgoPreparation {
    private static final Logger LOGGER = LoggerFactory.getLogger(GraphPartition.class);
    private static final Comparator<EdgeInfo> EDGE_COMPARATOR = new Comparator<EdgeInfo>() {
        @Override
        public int compare(EdgeInfo o1, EdgeInfo o2) {
            return Double.compare(o1.getSortBy(), o2.getSortBy());
        }
    };
    private final Graph ghStorage;
    private DataAccess partitionStorage;

    private double cutFactor = 0.5;
    private int precisionSize;
    private double maxAreaFactor = 2;

    private int areas;
    private int nodes;
    private int edges;

    private int partition[];
    private int nodeWeights[];
    private int edgeWeights[];
    private int baseNodes[];
    private int adjNodes[];

    private IntArrayList partitionNodes[];
    private boolean directlyConnected[][];

    public GraphPartition(GraphHopperStorage ghStorage, int areas) {
        this.ghStorage = ghStorage;
        partitionStorage = ghStorage.getDirectory().find("partition_" + areas);
        this.areas = areas;
        if (areas > this.ghStorage.getNodes())
            throw new IllegalArgumentException("The partition mustn't have more areas than the ghStorage has nodes" +
                    " (areas: " + areas + " | total nodes: " + this.ghStorage.getNodes() + ")");
        if (areas > 2 * Short.MAX_VALUE + 1)
            throw new IllegalArgumentException("The partition mustn't have more than " + (2 * Short.MAX_VALUE + 1) + " areas" +
                    " (areas: " + areas + ")");
        if (areas < 2)
            throw new IllegalArgumentException("The partition must have at least 2 areas" +
                    " (areas: " + areas + ")");
        precisionSize = (int) (areas * Math.pow((double) this.ghStorage.getNodes() / (double) areas, 0.5));
    }

    /**
     * @return true, if a previous partition has been loaded successfully
     */
    public boolean loadExisting() {
        if (isPrepared())
            throw new IllegalStateException("Cannot call GraphPartition.loadExisting if already prepared");
        if (partitionStorage.loadExisting()) {
            int nodes = partitionStorage.getHeader(0);
            int areas = partitionStorage.getHeader(4);
            if (nodes != ghStorage.getNodes())
                throw new IllegalStateException("The graph sizes do not match");
            if (areas != this.areas)
                throw new IllegalStateException("The amounts of areas do not match");
            partition = new int[nodes];
            partitionNodes = new IntArrayList[areas];
            for (int i = 0; i < areas; i++)
                partitionNodes[i] = new IntArrayList();
            for (int i = 0; i < nodes; i++) {
                int area = partitionStorage.getShort(2 * i);
                if (area < 0)
                    area -= 2 * Short.MIN_VALUE;
                partition[i] = area;
                partitionNodes[area].add(i);
            }
            directlyConnected = new boolean[areas][areas];
            byte connected[] = new byte[areas * areas];
            partitionStorage.getBytes(2 * nodes, connected, areas * areas);
            for (int i = 0; i < areas; i++) {
                for (int j = 0; j < areas; j++) {
                    if(connected[i * areas + j] != 0)
                        directlyConnected[i][j] = true;
                }
            }
            this.areas = areas;
            this.nodes = nodes;
            return true;
        }
        return false;
    }

    public void doSpecificWork() {
        createGraph();
        if (nodes > areas)
            while (coarsenGraph()) ;
        if (preparePartition())
            while (doPartition()) ;
        createStorage();
    }

    /**
     * @param cutFactor specifies whether the partition is minimized ( == 0; cut as little edges as possible)
     *                  or normalized ( == 1; spread out the nodes per area as equal as possible)
     *                  or something in between ( > 0 and < 1)
     */
    public void setCutFactor(double cutFactor) {
        this.cutFactor = cutFactor;
        if (cutFactor < 0 || cutFactor > 1)
            throw new IllegalStateException("The cutFactor must be between 0 and 1");
    }

    /**
     * @param precisionFactor specifies at which point the better heuristic to choose the next edge will be used
     *                        ( == 0 means using only the bad heuristic and == 1 means using only the good heuristic)
     *                        warning: computation time grows exponentially by increasing the factor with only slightly
     *                        better results!
     */
    public void setPrecisionFactor(double precisionFactor) {
        precisionSize = (int) (areas * Math.pow((double) ghStorage.getNodes() / (double) areas, precisionFactor));
        if (precisionFactor < 0 || precisionFactor > 1)
            throw new IllegalStateException("The precisionFactor must be between 0 and 1");
    }

    public int getTotalNodes() {
        return nodes;
    }

    /**
     * @return the amount of areas the ghStorage is divided into
     */
    public int getAreas() {
        return areas;
    }

    /**
     * @param node
     * @return the node's area
     */
    public int getArea(int node) {
        return partition[node];
    }

    /**
     * @param area
     * @return the list of nodes in this area
     */
    public IntArrayList getNodes(int area) {
        return partitionNodes[area];
    }

    /**
     * @param area1
     * @param area2
     * @return whether both areas are directly connected to each other, meaning at least one edge leads from area1 to
     * area2. If both areas are the same "true" will be returned
     */
    public boolean isDirectlyConnected(int area1, int area2) {
        return directlyConnected[area1][area2];
    }

    /**
     * This method stores the graph's important data in compact and directly accessible arrays
     */
    private void createGraph() {
        nodes = ghStorage.getNodes();
        edges = ghStorage.getAllEdges().length();
        partition = new int[nodes];
        nodeWeights = new int[nodes];
        for (int i = 0; i < nodes; i++) {
            partition[i] = i;
            nodeWeights[i] = 1;
        }
        baseNodes = new int[edges];
        adjNodes = new int[edges];
        AllEdgesIterator iterator = ghStorage.getAllEdges();
        while (iterator.next()) {
            baseNodes[iterator.getEdge()] = iterator.getBaseNode();
            adjNodes[iterator.getEdge()] = iterator.getAdjNode();
        }
    }

    /**
     * This method coarsens the graph until it's small enough to be separated
     *
     * @return true if coarsenGraph() has to be called another time
     */
    private boolean coarsenGraph() {
        boolean repeat = true;
        int maxAreaSize = (int) Math.round(maxAreaFactor * ghStorage.getNodes() / nodes);
        int newNodes = 0;
        int newEdges = 0;
        boolean matched[] = new boolean[nodes];
        int newPartition[] = new int[nodes];
        int newNodeWeights[] = new int[nodes];
        int newBaseNodes[] = new int[edges];
        int newAdjNodes[] = new int[edges];

        //create new nodes
        for (int i = 0; i < edges; i++) {
            int baseNode = baseNodes[i];
            int adjNode = adjNodes[i];
            if (!matched[baseNode] && !matched[adjNode]) {
                int newNodeWeight = nodeWeights[baseNode] + nodeWeights[adjNode];
                if (newNodeWeight <= maxAreaSize) {
                    matched[baseNode] = true;
                    matched[adjNode] = true;
                    newPartition[baseNode] = newNodes;
                    newPartition[adjNode] = newNodes;
                    newNodeWeights[newNodes] = newNodeWeight;
                    if (nodes - ++newNodes == precisionSize) {
                        repeat = false;
                        break;
                    }
                }
            }
        }
        for (int i = 0; i < nodes; i++) {
            if (!matched[i]) {
                matched[i] = true;
                newPartition[i] = newNodes;
                newNodeWeights[newNodes] = nodeWeights[i];
                newNodes++;
            }
        }

        //create new edges
        for (int i = 0; i < edges; i++) {
            int baseNode = newPartition[baseNodes[i]];
            int adjNode = newPartition[adjNodes[i]];
            if (baseNode != adjNode) {
                newBaseNodes[newEdges] = baseNode;
                newAdjNodes[newEdges] = adjNode;
                newEdges++;
            }
        }

        //update nodes
        if (nodes == newNodes) {
            if (maxAreaFactor >= areas) {
                LOGGER.warn("There are too many subnetworks (" + nodes + ") for " + areas + " areas!");
                areas = nodes;
                repeat = false;
            } else {
                maxAreaFactor *= 2;
            }
        }
        nodes = newNodes;
        nodeWeights = new int[nodes];
        for (int i = 0; i < ghStorage.getNodes(); i++)
            partition[i] = newPartition[partition[i]];
        for (int i = 0; i < nodes; i++)
            nodeWeights[i] = newNodeWeights[i];

        //update edges
        edges = newEdges;
        baseNodes = new int[edges];
        adjNodes = new int[edges];
        for (int i = 0; i < edges; i++) {
            baseNodes[i] = newBaseNodes[i];
            adjNodes[i] = newAdjNodes[i];
        }

        return repeat;
    }

    /**
     * This method updates the edge weights after coarsening and before partitioning the graph
     *
     * @return false if the graph is already partitioned after just calling coarsenGraph() (This will only happen with
     * small graphs)
     */
    private boolean preparePartition() {
        if (nodes <= areas)
            return false;
        edgeWeights = new int[edges];
        for (int i = 0; i < edges; i++)
            edgeWeights[i] = 1;
        updateEdges();
        return true;
    }

    /**
     * This method partitions the graph by retracting edge after edge. During each step, the best edge is chosen by
     * calling calcSortBy()
     *
     * @return true if doPartition() has to be called another time
     */
    private boolean doPartition() {
        boolean repeat = true;
        int maxAreaSize = (int) Math.round(maxAreaFactor * ghStorage.getNodes() / nodes);
        ArrayList<EdgeInfo> edgeList = new ArrayList<>(edges);
        int maxNodeWeight = 0;
        int maxEdgeWeight = 0;
        int newNodes = 0;
        int newEdges = 0;
        boolean matched[] = new boolean[nodes];
        int newPartition[] = new int[nodes];
        int newNodeWeights[] = new int[nodes];
        int newBaseNodes[] = new int[edges];
        int newAdjNodes[] = new int[edges];

        //choose the edges
        for (int i = 0; i < edges; i++) {
            int nodeWeight = nodeWeights[baseNodes[i]] + nodeWeights[adjNodes[i]];
            int edgeWeight = edgeWeights[i];
            if (nodeWeight > maxNodeWeight)
                maxNodeWeight = nodeWeight;
            if (edgeWeight > maxEdgeWeight)
                maxEdgeWeight = edgeWeight;
        }
        for (int i = 0; i < edges; i++) {
            double nodeWeight = (double) (nodeWeights[baseNodes[i]] + nodeWeights[adjNodes[i]]) / maxNodeWeight;
            double edgeWeight = (double) edgeWeights[i] / maxEdgeWeight;
            edgeList.add(new EdgeInfo(i, calcSortBy(nodeWeight, edgeWeight)));
        }
        edgeList.sort(EDGE_COMPARATOR);

        //create new nodes
        for (EdgeInfo e : edgeList) {
            int baseNode = baseNodes[e.getEdge()];
            int adjNode = adjNodes[e.getEdge()];
            if (!matched[baseNode] && !matched[adjNode]) {
                int newNodeWeight = nodeWeights[baseNode] + nodeWeights[adjNode];
                if (newNodeWeight <= maxAreaSize) {
                    matched[baseNode] = true;
                    matched[adjNode] = true;
                    newPartition[baseNode] = newNodes;
                    newPartition[adjNode] = newNodes;
                    newNodeWeights[newNodes] = newNodeWeight;
                    if (nodes - ++newNodes == areas) {
                        repeat = false;
                        break;
                    }
                }
            }
        }
        for (int i = 0; i < nodes; i++) {
            if (!matched[i]) {
                matched[i] = true;
                newPartition[i] = newNodes;
                newNodeWeights[newNodes] = nodeWeights[i];
                newNodes++;
            }
        }

        //create new edges
        for (int i = 0; i < edges; i++) {
            int baseNode = newPartition[baseNodes[i]];
            int adjNode = newPartition[adjNodes[i]];
            if (baseNode != adjNode) {
                newBaseNodes[newEdges] = baseNode;
                newAdjNodes[newEdges] = adjNode;
                newEdges++;
            }
        }

        //update nodes
        if (nodes == newNodes) {
            if (maxAreaFactor >= areas) {
                LOGGER.warn("There are too many subnetworks (" + nodes + ") for " + areas + " areas!");
                areas = nodes;
                repeat = false;
            } else {
                maxAreaFactor *= 2;
            }
        }
        nodes = newNodes;
        nodeWeights = new int[nodes];
        for (int i = 0; i < ghStorage.getNodes(); i++)
            partition[i] = newPartition[partition[i]];
        for (int i = 0; i < nodes; i++)
            nodeWeights[i] = newNodeWeights[i];

        //update edges
        edges = newEdges;
        baseNodes = new int[edges];
        adjNodes = new int[edges];
        for (int i = 0; i < edges; i++) {
            baseNodes[i] = newBaseNodes[i];
            adjNodes[i] = newAdjNodes[i];
        }
        updateEdges();

        return repeat;
    }

    /**
     * In this method the final usable data is created and saved
     */
    private void createStorage() {
        nodes = ghStorage.getNodes();
        partitionStorage.setHeader(0, nodes);
        partitionStorage.setHeader(4, areas);
        partitionStorage.create(2 * nodes + areas * areas);
        HashMap<Integer, Integer> indices = new HashMap<>(areas);
        int index = 0;
        for (int i = 0; i < nodes; i++)
            if (!indices.containsKey(partition[i]))
                indices.put(partition[i], index++);
        partitionNodes = new IntArrayList[areas];
        for (int i = 0; i < areas; i++)
            partitionNodes[i] = new IntArrayList(nodeWeights[i]);
        for (int i = 0; i < nodes; i++) {
            int area = indices.get(partition[i]);
            partition[i] = area;
            partitionNodes[area].add(i);
            if (area > 2 * Short.MAX_VALUE + 1)
                throw new IllegalStateException("There are too many areas after finishing the partition");
            if (area > Short.MAX_VALUE)
                area += 2 * Short.MIN_VALUE;
                partitionStorage.setShort(2 * i, (short) area);
        }
        directlyConnected = new boolean[areas][areas];
        for (int i = 0; i < areas; i++)
            directlyConnected[i][i] = true;
        for (int i = 0; i < edges; i++) {
            int area1 = indices.get(baseNodes[i]);
            int area2 = indices.get(adjNodes[i]);
            directlyConnected[area1][area2] = true;
            directlyConnected[area2][area1] = true;
        }
        byte connected[] = new byte[areas * areas];
        for (int i = 0; i < areas; i++) {
            for (int j = 0; j < areas; j++) {
                if (directlyConnected[i][j])
                    connected[i * areas + j] = 1;
                else
                    connected[i * areas + j] = 0;
            }
        }
        partitionStorage.setBytes(2 * nodes, connected, connected.length);
        partitionStorage.flush();
    }

    /**
     * This method updates the edge weight and adjacent nodes after retracting an edge
     */
    private void updateEdges() {
        for (int i = 0; i < edges; i++) {
            int baseNode = baseNodes[i];
            int adjNode = adjNodes[i];
            if (adjNode > baseNode) {
                baseNodes[i] = adjNode;
                adjNodes[i] = baseNode;
            }
        }
        int newEdges = edges;
        for (int i = 0; i < edges; i++) {
            int edgeWeight = edgeWeights[i];
            if (edgeWeight < 0)
                continue;
            int baseNode = baseNodes[i];
            int adjNode = adjNodes[i];
            for (int j = i + 1; j < edges; j++) {
                if (baseNodes[j] == baseNode) {
                    if (adjNodes[j] == adjNode) {
                        edgeWeight += edgeWeights[j];
                        edgeWeights[j] = -1;
                        newEdges--;
                    }
                }
            }
            edgeWeights[i] = edgeWeight;
        }
        int newEdgeWeights[] = new int[newEdges];
        int newBaseNodes[] = new int[newEdges];
        int newAdjNodes[] = new int[newEdges];
        int i = 0;
        for (int j = 0; j < edges; j++) {
            if (edgeWeights[j] < 0)
                continue;
            newEdgeWeights[i] = edgeWeights[j];
            newBaseNodes[i] = baseNodes[j];
            newAdjNodes[i] = adjNodes[j];
            i++;
        }
        edges = newEdges;
        edgeWeights = newEdgeWeights;
        baseNodes = newBaseNodes;
        adjNodes = newAdjNodes;
    }

    private double calcSortBy(double nodeWeight, double edgeWeight) {
        return cutFactor * nodeWeight + (1 - cutFactor) * (1 - edgeWeight);
    }

    private class EdgeInfo {
        private final int edge;
        private final double sortBy;

        private EdgeInfo(int edge, double sortBy) {
            this.edge = edge;
            this.sortBy = sortBy;
        }

        private int getEdge() {
            return edge;
        }

        private double getSortBy() {
            return sortBy;
        }
    }
}
