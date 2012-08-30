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

import de.jetsli.graph.util.EdgeFilter;
import de.jetsli.graph.util.EdgeIterator;
import de.jetsli.graph.util.Helper;
import java.io.IOException;
import java.util.Arrays;

/**
 * A graph implementation adding node priorities.
 *
 * @author Peter Karich
 */
public class PriorityGraphImpl extends MemoryGraphSafe implements PriorityGraph {

    private int[] priorities;
    private EdgeFilter edgeFilter;

    public PriorityGraphImpl(int cap) {
        super(cap);
    }

    public PriorityGraphImpl(String storageDir, int cap) {
        super(storageDir, cap);
    }

    public PriorityGraphImpl(String storageDir, int cap, int capEdge) {
        super(storageDir, cap, capEdge);
    }

    @Override
    protected int ensureNodeIndex(int index) {
        int cap = super.ensureNodeIndex(index);
        if (cap > 0) {
            int oldLen = priorities.length;
            priorities = Arrays.copyOf(priorities, cap);
            Arrays.fill(priorities, oldLen, priorities.length, Integer.MIN_VALUE);
        }
        return cap;
    }

    @Override
    public int getPriority(int index) {
        ensureNodeIndex(index);
        return priorities[index];
    }

    @Override
    public void setPriority(int index, int prio) {
        ensureNodeIndex(index);
        priorities[index] = prio;
    }

    @Override
    protected void initNodes(int cap) {
        super.initNodes(cap);
        priorities = new int[cap];
        Arrays.fill(priorities, Integer.MIN_VALUE);
    }

    @Override
    protected void inPlaceDeleteNodeHook(int oldI, int newI) {
        priorities[newI] = priorities[oldI];
        super.inPlaceDeleteNodeHook(oldI, newI);
    }

    @Override
    protected MemoryGraphSafe creatThis(String storage, int nodes, int edges) {
        return new PriorityGraphImpl(storage, nodes, edges);
    }

    @Override
    public Graph clone() {
        PriorityGraphImpl clonedGraph = (PriorityGraphImpl) super.clone();
        System.arraycopy(priorities, 0, clonedGraph.priorities, 0, priorities.length);
        return clonedGraph;
    }

    @Override
    public boolean save() {
        boolean saved = super.save();
        if (saved) {
            try {
                Helper.writeInts(getStorageLocation() + "/priorities", priorities);
            } catch (IOException ex) {
                throw new RuntimeException("Couldn't write data to disc. location=" + getStorageLocation(), ex);
            }
        }
        return saved;
    }

    @Override
    public boolean loadExisting(String storageDir) {
        if (super.loadExisting(storageDir)) {
            try {
                priorities = Helper.readInts(getStorageLocation() + "/priorities");
                return true;
            } catch (IOException ex) {
                throw new RuntimeException("Couldn't load data from disc. location=" + getStorageLocation(), ex);
            }
        }
        return false;
    }

    @Override
    public EdgeIterator getEdges(int nodeId) {
        return new EdgeFilterIterable(nodeId, true, true);
    }

    @Override
    public EdgeIterator getIncoming(int nodeId) {
        return new EdgeFilterIterable(nodeId, true, false);
    }

    @Override
    public EdgeIterator getOutgoing(int nodeId) {
        return new EdgeFilterIterable(nodeId, false, true);
    }

    @Override
    public void setEdgeFilter(EdgeFilter edgeFilter) {
        this.edgeFilter = edgeFilter;
    }

    protected class EdgeFilterIterable extends EdgeIterable {

        public EdgeFilterIterable(int node, boolean in, boolean out) {
            super(node, in, out);
        }

        @Override public boolean next() {            
            while (super.next()) {
                if (edgeFilter != null && !edgeFilter.accept(fromNode, this))
                    continue;
                return true;
            }
            return false;
        }
    }
}
