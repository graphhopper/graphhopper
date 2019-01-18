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
package com.graphhopper.routing;

import com.graphhopper.routing.util.AbstractAlgoPreparation;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.storage.*;

import java.util.ArrayList;

/**
 * This class separates a graph into the wished amount of areas. In each step it chooses an edge to remove from the
 * graph and combines its two adjacent nodes into one. This will be done until only as many nodes as areas remain.
 *
 * @author Maximilian Sturm
 */
public final class GraphPartition extends AbstractAlgoPreparation {
    private final int NULL = Integer.MIN_VALUE;
    private final Graph graph;
    private final int totalNodes;
    private final int areas;

    private double cutFactor = 0.25;
    private double cutFactorCorrection;
    private int precisionSize;

    private double maxAreaFactor = 2;
    private int maxAreaSize;
    private int nextEdge;

    private NodeList nodes;
    private EdgeList edges;
    private ItemList items;

    private int area[];
    private ArrayList<Integer> areaNodes[];
    private ArrayList<Integer> borderNodes[];
    private boolean directlyConnected[][];

    public GraphPartition(Graph graph, int areas) {
        this.graph = graph;
        totalNodes = graph.getNodes();
        this.areas = areas;
        if (areas > totalNodes)
            throw new IllegalStateException("The partition mustn't have more areas than the graph has nodes" +
                    " (areas: " + areas + " | total nodes: " + totalNodes + ")");
        if (areas < 2)
            throw new IllegalStateException("The partition must have at least 2 areas" +
                    " (areas: " + areas + ")");
        cutFactorCorrection = (double) graph.getAllEdges().length() / (double) totalNodes;
        precisionSize = areas * (int) Math.pow((double) graph.getNodes() / (double) areas, 0.5);
        nodes = new NodeList();
        edges = new EdgeList();
        items = new ItemList();
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
        precisionSize = areas * (int) Math.pow((double) graph.getNodes() / (double) areas, precisionFactor);
        if (precisionFactor < 0 || precisionFactor > 1)
            throw new IllegalStateException("The precisionFactor must be between 0 and 1");
    }

    @Override
    public void doSpecificWork() {
        //First of all the original graph must be transformed, adding all nodes to the node list, all edges to the
        //edge list and creating every item
        createGraph();

        //In this loop the partition is processed. During each step one edge to remove will be chosen and its adjacent
        //nodes will be contracted
        while (nodes.count > areas) {
            if (edges.count == 0)
                throw new IllegalStateException("Too many subnetworks for " + areas + " areas! Set areas to at least " + nodes.count + " for this graph");
            contractGraph(chooseEdge());
        }

        //Finally the usable data will be created
        createData();
    }

    private void createGraph() {
        for (int i = 0; i < totalNodes; i++) {
            nodes.create(i);
        }
        AllEdgesIterator iterator = graph.getAllEdges();
        while (iterator.next()) {
            int edge = iterator.getEdge();
            int baseNode = iterator.getBaseNode();
            int adjNode = iterator.getAdjNode();
            if (baseNode != adjNode) {
                edges.create(edge, baseNode, adjNode);
            }
        }
    }

    private int chooseEdge() {
        //Here the next edge to remove will be chosen.
        int currNodes = nodes.count;
        if (currNodes > precisionSize) {
            //If the current amount of nodes is bigger than precisionSize the bad heuristic will be used in order to
            //save time. This heuristic doesn't loop over every edge and only takes into account the area size.
            int currAreaSize = (int) ((double) totalNodes / (double) currNodes * maxAreaFactor);
            if (currAreaSize > maxAreaSize) {
                maxAreaSize = currAreaSize;
                for (int edge = edges.first; edge != NULL; edge = edges.getNext(edge))
                    if (nodes.getAreaCount(edges.getBaseNode(edge)) + nodes.getAreaCount(edges.getAdjNode(edge))
                            <= maxAreaSize) {
                        nextEdge = edges.getNext(edge);
                        if (nextEdge == NULL) {
                            nextEdge = edges.first;
                            maxAreaFactor += 0.5;
                        }
                        return edge;
                    }
            } else {
                for (int edge = nextEdge; edge != NULL; edge = edges.getNext(edge))
                    if (nodes.getAreaCount(edges.getBaseNode(edge)) + nodes.getAreaCount(edges.getAdjNode(edge))
                            <= maxAreaSize) {
                        nextEdge = edges.getNext(edge);
                        if (nextEdge == NULL) {
                            nextEdge = edges.first;
                            maxAreaFactor += 0.5;
                        }
                        return edge;
                    }
            }
            return edges.first;
        } else {
            //The good heuristic calculates a value for each edge, depending on the cutFactor. This heuristic takes
            //much longer to compute but delivers better results.
            int bestEdge = edges.first;
            double bestSortBy = Double.MAX_VALUE;
            for (int edge = edges.first; edge != NULL; edge = edges.getNext(edge)) {
                double sortBy = calcSortBy(edge);
                if (sortBy < bestSortBy) {
                    bestEdge = edge;
                    bestSortBy = sortBy;
                }
            }
            return bestEdge;
        }
    }

    private void contractGraph(int edge) {
        int baseNode = edges.getBaseNode(edge);
        int adjNode = edges.getAdjNode(edge);
        Loop:
        while (nodes.getItemCount(adjNode) > 0) {
            int item_adj = nodes.getFirstItem(adjNode);
            int node_adj = items.getNode(item_adj);
            int edge_adj = items.getEdge(item_adj);
            if (baseNode == node_adj) {
                edges.remove(edge_adj);
                continue Loop;
            }
            for (int item_base = nodes.getFirstItem(baseNode); item_base != NULL; item_base = items.getNext(item_base)) {
                int node_base = items.getNode(item_base);
                int edge_base = items.getEdge(item_base);
                if (node_base == node_adj) {
                    edges.moveTo(edge_adj, edge_base);
                    continue Loop;
                }
            }
            edges.change(edge_adj, baseNode, node_adj);
        }
        nodes.moveTo(adjNode, baseNode);
    }

    private void createData() {
        area = new int[totalNodes];
        areaNodes = new ArrayList[areas];
        for (int i = 0, area = nodes.first; area != NULL; i++, area = nodes.getNext(area)) {
            areaNodes[i] = new ArrayList<>();
            for (int node = nodes.getFirstArea(area); node != NULL; node = nodes.getNextArea(node)) {
                this.area[node] = i;
                areaNodes[i].add(node);
            }
        }
        AllEdgesIterator iterator = graph.getAllEdges();
        boolean borderNode[] = new boolean[totalNodes];
        while (iterator.next()) {
            int baseNode = iterator.getBaseNode();
            int adjNode = iterator.getAdjNode();
            if (getArea(baseNode) != getArea(adjNode)) {
                borderNode[baseNode] = true;
                borderNode[adjNode] = true;
            }
        }
        borderNodes = new ArrayList[areas];
        for (int i = 0; i < areas; i++)
            borderNodes[i] = new ArrayList<>();
        for (int area = nodes.first, i = 0; area != NULL; area = nodes.getNext(area), i++)
            for (int node = nodes.getFirstArea(area); node != NULL; node = nodes.getNextArea(node))
                if (borderNode[node])
                    borderNodes[i].add(node);
        directlyConnected = new boolean[areas][areas];
        for (int i = 0; i < areas; i++) {
            directlyConnected[i][i] = true;
        }
        for (int edge = edges.first; edge != NULL; edge = edges.getNext(edge)) {
            int area1 = getArea(edges.getBaseNode(edge));
            int area2 = getArea(edges.getAdjNode(edge));
            directlyConnected[area1][area2] = true;
            directlyConnected[area2][area1] = true;
        }
    }

    private double calcSortBy(int edge) {
        int areaSize = nodes.getAreaCount(edges.getBaseNode(edge)) + nodes.getAreaCount(edges.getAdjNode(edge));
        if (cutFactor == 1) {
            return areaSize;
        } else {
            int borderSize = 0;
            int baseNode = edges.getBaseNode(edge);
            int adjNode = edges.getAdjNode(edge);
            for (int item = nodes.getFirstItem(baseNode); item != NULL; item = items.getNext(item)) {
                if (adjNode != items.getNode(item))
                    borderSize += edges.getWeight(items.getEdge(item));
            }
            for (int item = nodes.getFirstItem(adjNode); item != NULL; item = items.getNext(item)) {
                if (baseNode != items.getNode(item))
                    borderSize += edges.getWeight(items.getEdge(item));
            }
            return cutFactorCorrection * cutFactor * (double) areaSize + (1 - cutFactor) * (double) borderSize;
        }
    }

    /**
     * @return the amount of areas the graph is divided into
     */
    public int getAreas() {
        return areas;
    }

    /**
     * @param node
     * @return the node's area
     */
    public int getArea(int node) {
        return area[node];
    }

    /**
     * @param area
     * @return the list of nodes in this area
     */
    public ArrayList<Integer> getNodes(int area) {
        return areaNodes[area];
    }

    /**
     * @param area
     * @return the list of nodes in this area with at least one edge leading to another area
     */
    public ArrayList<Integer> getBorderNodes(int area) {
        return borderNodes[area];
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
     * This algorithm would be very slow using only the default java lists. Therefore I'm using DataAccess to store
     * the data for nodes and edges
     */
    private class NodeList {
        private final int size = 4 * 10;
        private final int offset_next = 0;
        private final int offset_prev = 4;
        private final int offset_firstItem = 8;
        private final int offset_lastItem = 12;
        private final int offset_itemCount = 16;
        private final int offset_firstArea = 20;
        private final int offset_lastArea = 24;
        private final int offset_nextArea = 28;
        private final int offset_prevArea = 32;
        private final int offset_areaCount = 36;

        private Directory directory;
        private DataAccess nodeList;

        private int first = 0;
        private int last = 0;
        private int count = 0;

        public NodeList() {
            directory = new RAMDirectory();
            nodeList = directory.find("");
            nodeList.create(totalNodes * size);
        }

        public void create(int node) {
            setNext(node, NULL);
            if (node != 0) {
                setNext(node - 1, node);
                setPrev(node, node - 1);
            } else {
                setPrev(node, NULL);
            }
            setFirstItem(node, NULL);
            setLastItem(node, NULL);
            setItemCount(node, 0);
            setFirstArea(node, node);
            setLastArea(node, node);
            setNextArea(node, NULL);
            setPrevArea(node, NULL);
            setAreaCount(node, 1);
            last = node;
            count++;
        }

        public void remove(int node) {
            if (node == first) {
                int next = getNext(node);
                first = next;
                setPrev(next, NULL);
            } else if (node == last) {
                int prev = getPrev(node);
                last = prev;
                setNext(prev, NULL);
            } else {
                int next = getNext(node);
                int prev = getPrev(node);
                setNext(prev, next);
                setPrev(next, prev);
            }
            setNext(node, NULL);
            setPrev(node, NULL);
            setFirstItem(node, NULL);
            setLastItem(node, NULL);
            setItemCount(node, NULL);
            count--;
        }

        public void moveTo(int node, int moveTo) {
            int firstArea_node = getFirstArea(node);
            int lastArea_node = getLastArea(node);
            int firstArea_moveTo = getFirstArea(moveTo);
            int lastArea_moveTo = getLastArea(moveTo);
            setNextArea(lastArea_moveTo, firstArea_node);
            setPrevArea(firstArea_node, lastArea_moveTo);
            int areaCount = getAreaCount(node) + getAreaCount(moveTo);
            for (int area = firstArea_moveTo; area != NULL; area = getNextArea(area)) {
                setFirstArea(area, firstArea_moveTo);
                setLastArea(area, lastArea_node);
                setAreaCount(area, areaCount);
            }
            remove(node);
        }

        private void setNext(int node, int next) {
            nodeList.setInt(size * node + offset_next, next);
        }

        private void setPrev(int node, int prev) {
            nodeList.setInt(size * node + offset_prev, prev);
        }

        private void setFirstItem(int node, int item) {
            nodeList.setInt(size * node + offset_firstItem, item);
        }

        private void setLastItem(int node, int item) {
            nodeList.setInt(size * node + offset_lastItem, item);
        }

        private void setItemCount(int node, int count) {
            nodeList.setInt(size * node + offset_itemCount, count);
        }

        private void setFirstArea(int node, int area) {
            nodeList.setInt(size * node + offset_firstArea, area);
        }

        private void setLastArea(int node, int area) {
            nodeList.setInt(size * node + offset_lastArea, area);
        }

        private void setNextArea(int node, int next) {
            nodeList.setInt(size * node + offset_nextArea, next);
        }

        private void setPrevArea(int node, int prev) {
            nodeList.setInt(size * node + offset_prevArea, prev);
        }

        private void setAreaCount(int node, int count) {
            nodeList.setInt(size * node + offset_areaCount, count);
        }

        private int getNext(int node) {
            return nodeList.getInt(size * node + offset_next);
        }

        private int getPrev(int node) {
            return nodeList.getInt(size * node + offset_prev);
        }

        private int getFirstItem(int node) {
            return nodeList.getInt(size * node + offset_firstItem);
        }

        private int getLastItem(int node) {
            return nodeList.getInt(size * node + offset_lastItem);
        }

        private int getItemCount(int node) {
            return nodeList.getInt(size * node + offset_itemCount);
        }

        private int getFirstArea(int node) {
            return nodeList.getInt(size * node + offset_firstArea);
        }

        private int getLastArea(int node) {
            return nodeList.getInt(size * node + offset_lastArea);
        }

        private int getNextArea(int node) {
            return nodeList.getInt(size * node + offset_nextArea);
        }

        private int getPrevArea(int node) {
            return nodeList.getInt(size * node + offset_prevArea);
        }

        private int getAreaCount(int node) {
            return nodeList.getInt(size * node + offset_areaCount);
        }
    }

    private class EdgeList {
        private final int size = 4 * 5;
        private final int offset_next = 0;
        private final int offset_prev = 4;
        private final int offset_baseNode = 8;
        private final int offset_adjNode = 12;
        private final int offset_weight = 16;

        private Directory directory;
        private DataAccess edgeList;

        private int first = 0;
        private int last = 0;
        private int count = 0;

        public EdgeList() {
            directory = new RAMDirectory();
            edgeList = directory.find("");
            edgeList.create(graph.getAllEdges().length() * size);
        }

        public void create(int edge, int baseNode, int adjNode) {
            setNext(edge, NULL);
            if (edge != 0) {
                int prev = edge - 1;
                while (getNext(prev) != NULL)
                    prev--;
                setNext(prev, edge);
                setPrev(edge, prev);
            } else {
                setPrev(edge, NULL);
            }
            setBaseNode(edge, baseNode);
            setAdjNode(edge, adjNode);
            setWeight(edge, 1);
            last = edge;
            count++;
            items.create(baseNode, adjNode, edge);
            items.create(adjNode, baseNode, edge);
        }

        public void remove(int edge) {
            if (edge == nextEdge)
                nextEdge = edges.getNext(edge);
            if (nextEdge == NULL) {
                nextEdge = first;
                maxAreaFactor += 0.5;
            }
            for (int item = nodes.getFirstItem(getBaseNode(edge)); item != NULL; item = items.getNext(item))
                if (edge == items.getEdge(item)) {
                    items.remove(item);
                    break;
                }
            for (int item = nodes.getFirstItem(getAdjNode(edge)); item != NULL; item = items.getNext(item))
                if (edge == items.getEdge(item)) {
                    items.remove(item);
                    break;
                }
            if (edge == first) {
                int next = getNext(edge);
                first = next;
                setPrev(next, NULL);
            } else if (edge == last) {
                int prev = getPrev(edge);
                last = prev;
                setNext(prev, NULL);
            } else {
                int next = getNext(edge);
                int prev = getPrev(edge);
                setNext(prev, next);
                setPrev(next, prev);
            }
            setNext(edge, NULL);
            setPrev(edge, NULL);
            setBaseNode(edge, NULL);
            setAdjNode(edge, NULL);
            setWeight(edge, NULL);
            count--;
        }

        public void moveTo(int edge, int moveTo) {
            setWeight(moveTo, getWeight(edge) + getWeight(moveTo));
            remove(edge);
        }

        public void change(int edge, int baseNode, int adjNode) {
            int removedNode = getBaseNode(edge);
            if (adjNode == removedNode)
                removedNode = getAdjNode(edge);
            for (int item = nodes.getFirstItem(removedNode); item != NULL; item = items.getNext(item))
                if (edge == items.getEdge(item)) {
                    items.remove(item);
                    break;
                }
            for (int item = nodes.getFirstItem(adjNode); item != NULL; item = items.getNext(item))
                if (edge == items.getEdge(item)) {
                    items.setNode(item, baseNode);
                    break;
                }
            setBaseNode(edge, baseNode);
            setAdjNode(edge, adjNode);
            items.create(baseNode, adjNode, edge);
        }

        private void setNext(int edge, int next) {
            edgeList.setInt(size * edge + offset_next, next);
        }

        private void setPrev(int edge, int prev) {
            edgeList.setInt(size * edge + offset_prev, prev);
        }

        private void setBaseNode(int edge, int node) {
            edgeList.setInt(size * edge + offset_baseNode, node);
        }

        private void setAdjNode(int edge, int node) {
            edgeList.setInt(size * edge + offset_adjNode, node);
        }

        private void setWeight(int edge, int weight) {
            edgeList.setInt(size * edge + offset_weight, weight);
        }

        private int getNext(int edge) {
            return edgeList.getInt(size * edge + offset_next);
        }

        private int getPrev(int edge) {
            return edgeList.getInt(size * edge + offset_prev);
        }

        private int getBaseNode(int edge) {
            return edgeList.getInt(size * edge + offset_baseNode);
        }

        private int getAdjNode(int edge) {
            return edgeList.getInt(size * edge + offset_adjNode);
        }

        private int getWeight(int edge) {
            return edgeList.getInt(size * edge + offset_weight);
        }
    }

    /**
     * Here an item is one of a node's adjacent edges
     */
    private class ItemList {
        private final int size = 4 * 4;
        private final int offset_next = 0;
        private final int offset_prev = 4;
        private final int offset_node = 8;
        private final int offset_edge = 12;

        private Directory directory;
        private DataAccess itemList;

        private int totalCount = 0;

        public ItemList() {
            directory = new RAMDirectory();
            itemList = directory.find("");
            //itemList.create(totalNodes * graph.getAllEdges().length() * size);
            itemList.create(-1);
        }

        public void create(int baseNode, int adjNode, int edge) {
            itemList.ensureCapacity((totalCount + 1) * size);
            int count = nodes.getItemCount(baseNode);
            int item = totalCount;
            if (count != 0) {
                int prev = nodes.getLastItem(baseNode);
                setNext(prev, item);
                setPrev(item, prev);
            } else {
                nodes.setFirstItem(baseNode, item);
                setPrev(item, NULL);
            }
            setNext(item, NULL);
            setNode(item, adjNode);
            setEdge(item, edge);
            nodes.setLastItem(baseNode, item);
            nodes.setItemCount(baseNode, ++count);
            totalCount++;
        }

        public void remove(int item) {
            int edge = getEdge(item);
            int node = edges.getBaseNode(edge);
            if (node == getNode(item)) {
                node = edges.getAdjNode(edge);
            }
            int prev = getPrev(item);
            int next = getNext(item);
            if (prev != NULL || next != NULL) {
                if (prev == NULL) {
                    nodes.setFirstItem(node, next);
                    setPrev(next, NULL);
                } else if (next == NULL) {
                    nodes.setLastItem(node, prev);
                    setNext(prev, NULL);
                } else {
                    setNext(prev, next);
                    setPrev(next, prev);
                }
            }
            setNext(item, NULL);
            setPrev(item, NULL);
            setNode(item, NULL);
            setEdge(item, NULL);
            nodes.setItemCount(node, nodes.getItemCount(node) - 1);
        }

        private void setNext(int item, int next) {
            itemList.setInt(size * item + offset_next, next);
        }

        private void setPrev(int item, int prev) {
            itemList.setInt(size * item + offset_prev, prev);
        }

        private void setNode(int item, int node) {
            itemList.setInt(size * item + offset_node, node);
        }

        private void setEdge(int item, int edge) {
            itemList.setInt(size * item + offset_edge, edge);
        }

        private int getNext(int item) {
            return itemList.getInt(size * item + offset_next);
        }

        private int getPrev(int item) {
            return itemList.getInt(size * item + offset_prev);
        }

        private int getNode(int item) {
            return itemList.getInt(size * item + offset_node);
        }

        private int getEdge(int item) {
            return itemList.getInt(size * item + offset_edge);
        }
    }
}
