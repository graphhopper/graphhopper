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

import de.jetsli.graph.util.EdgeUpdateIterator;
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
    protected int ensureNodeIndex(int index) {
        int cap = super.ensureNodeIndex(index);
        if (cap > 0)
            priorities = Arrays.copyOf(priorities, cap);

        return cap;
    }

    @Override
    protected void initNodes(int cap) {
        super.initNodes(cap);
        priorities = new int[cap];
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

    @Override public EdgeUpdateIterator getEdges(int nodeId) {
        return new EdgeIterable(nodeId, true, true);
    }

    @Override public EdgeUpdateIterator getIncoming(int nodeId) {
        return new EdgeIterable(nodeId, true, false);
    }

    @Override public EdgeUpdateIterator getOutgoing(int nodeId) {
        return new EdgeIterable(nodeId, false, true);
    }
}
