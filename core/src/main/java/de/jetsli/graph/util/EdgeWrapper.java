/*
 *  Copyright 2012 Peter Karich info@jetsli.de
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package de.jetsli.graph.util;

import gnu.trove.map.hash.TIntIntHashMap;
import java.util.Arrays;

/**
 * Not thread safe. Use one per thread or sync.
 *
 * @author Peter Karich
 */
public class EdgeWrapper {

    private static final float FACTOR = 1.5f;
    private int edgeCounter;
    private int[] nodes;
    private int[] links;
    private float[] weights;
    protected TIntIntHashMap node2edge;

    public EdgeWrapper() {
        this(10);
    }

    public EdgeWrapper(int size) {
        edgeCounter = 1;
        nodes = new int[size];
        links = new int[size];
        weights = new float[size];
        node2edge = new TIntIntHashMap(size, FACTOR, -1, -1);
    }

    /**
     * @return edge id of current added (node,distance) tuple
     */
    public int add(int nodeId, double distance) {
        int tmpEdgeId = edgeCounter;
        edgeCounter++;
        node2edge.put(nodeId, tmpEdgeId);        
        ensureCapacity(tmpEdgeId);
        weights[tmpEdgeId] = (float) distance;
        nodes[tmpEdgeId] = nodeId;
        links[tmpEdgeId] = -1;
        return tmpEdgeId;
    }

    public void putWeight(int edgeId, double dist) {
        if (edgeId < 1)
            throw new IllegalStateException("You cannot save edge id's with values smaller 1. 0 is reserved");
        weights[edgeId] = (float) dist;
    }

    public void putLink(int edgeId, int link) {
        if (edgeId < 1)
            throw new IllegalStateException("You cannot save edge id's with values smaller 1. 0 is reserved");
        links[edgeId] = link;
    }

    public double getWeight(int edgeId) {
        return weights[edgeId];
    }

    public int getNode(int edgeId) {
        return nodes[edgeId];
    }

    public int getLink(int edgeId) {
        return links[edgeId];
    }

    private void ensureCapacity(int size) {
        if (size < nodes.length)
            return;

        resize(Math.round(FACTOR * size));
    }

    private void resize(int cap) {
        weights = Arrays.copyOf(weights, cap);
        nodes = Arrays.copyOf(nodes, cap);
        links = Arrays.copyOf(links, cap);
        node2edge.ensureCapacity(cap);
    }

    public void clear() {
        edgeCounter = 1;
        Arrays.fill(weights, 0);
        Arrays.fill(nodes, 0);
        Arrays.fill(links, 0);
        node2edge.clear();
    }

    public int getEdgeId(int node) {
        return node2edge.get(node);
    }
}
