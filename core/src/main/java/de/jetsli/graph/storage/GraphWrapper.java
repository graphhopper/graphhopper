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
package de.jetsli.graph.storage;

import de.jetsli.graph.coll.MyBitSet;
import de.jetsli.graph.util.MyIteratorable;

/**
 *
 * @author Peter Karich, info@jetsli.de
 */
public class GraphWrapper implements Graph {

    private MyBitSet ignoreNodes;
    private int ignoreNodesSize;
    private Graph g;

    public GraphWrapper(Graph g) {
        this.g = g;
    }

    public void setIgnoreNodes(MyBitSet bitSet) {
        ignoreNodes = bitSet;
        ignoreNodesSize = (int) bitSet.getCardinality();
    }

    @Override
    public int getNodes() {
        int tmp = g.getNodes() - ignoreNodesSize;
        assert tmp >= 0;
        return tmp;
    }

    @Override
    public MyIteratorable<EdgeWithFlags> getEdges(int index) {
        if (ignoreNodes.contains(index))
            return EdgeWithFlags.EMPTY_ITER;

        return g.getEdges(index);
    }

    @Override
    public MyIteratorable<EdgeWithFlags> getOutgoing(int index) {
        if (ignoreNodes.contains(index))
            return EdgeWithFlags.EMPTY_ITER;

        return g.getOutgoing(index);
    }

    @Override
    public MyIteratorable<EdgeWithFlags> getIncoming(int index) {
        if (ignoreNodes.contains(index))
            return EdgeWithFlags.EMPTY_ITER;

        return g.getIncoming(index);
    }

    @Override
    public int addNode(double lat, double lon) {
        return g.addNode(lat, lon);
    }

    @Override
    public double getLatitude(int index) {
        return g.getLatitude(index);
    }

    @Override
    public double getLongitude(int index) {
        return g.getLongitude(index);
    }

    @Override
    public void edge(int a, int b, double distance, boolean bothDirections) {
        g.edge(a, b, distance, bothDirections);
    }

    @Override
    public Graph clone() {
        return g.clone();
    }

    @Override
    public void ensureCapacity(int cap) {
        g.ensureCapacity(cap);
    }

    @Override
    public boolean markNodeDeleted(int index) {
        return g.markNodeDeleted(index);
    }

    @Override public boolean isDeleted(int index) {
        return g.isDeleted(index);
    }

    @Override
    public void optimize() {
        g.optimize();
    }
}
