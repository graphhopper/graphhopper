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

import de.jetsli.graph.util.Helper;
import java.io.IOException;
import java.util.Arrays;

/**
 * Store node priorities to use edge short cut efficiently.
 *
 * @author Peter Karich
 */
public class MemoryGraphExt extends MemoryGraphSafe {

    private int[] priorities;

    public MemoryGraphExt(int cap) {
        super(cap);
    }

    public MemoryGraphExt(String storageDir, int cap, int capEdge) {
        super(storageDir, cap, capEdge);
    }

    @Override
    protected int ensureNodeIndex(int index) {
        int cap = super.ensureNodeIndex(index);
        if (cap > 0)
            priorities = Arrays.copyOf(priorities, cap);
        return cap;
    }

    public int getPriority(int index) {
        return priorities[index];
    }

    @Override
    protected void initNodes(int cap) {
        super.initNodes(cap);
        priorities = new int[cap];
    }

    @Override
    protected void internalEdgeAdd(int fromNodeId, int toNodeId, double dist, int flags) {
        super.internalEdgeAdd(fromNodeId, toNodeId, dist, flags);

        // TODO sort by priority but include the latest entry too!        
        // Collections.sort(list, listPrioSorter);
        // int len = list.size();
        // for (int i = 0; i < len; i++) {
        //    int pointer = list.get(i);
        //    copyEdge();
        // }
    }

    @Override
    public void optimize() {
        super.optimize();
        // TODO change priorities too!
    }

    @Override
    protected MemoryGraphSafe creatThis(String storage, int nodes, int edges) {
        return new MemoryGraphExt(storage, nodes, edges);
    }

    @Override
    public Graph clone() {
        MemoryGraphExt clonedGraph = (MemoryGraphExt) super.clone();
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
}
