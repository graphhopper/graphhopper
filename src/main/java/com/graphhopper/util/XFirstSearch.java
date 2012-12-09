/*
 *  Copyright 2012 Peter Karich 
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
package com.graphhopper.util;

import com.graphhopper.coll.MyBitSet;
import com.graphhopper.coll.MyBitSetImpl;
import com.graphhopper.storage.Graph;
import gnu.trove.stack.array.TIntArrayStack;

/**
 * This class can be used for breadth first search (BFS) or depth first search (DFS)
 *
 * @author Peter Karich,
 */
public class XFirstSearch {

    /**
     * interface to use a queue (FIFO) OR a stack (LIFO)
     */
    interface HelperColl {

        boolean isEmpty();

        int pop();

        void push(int v);
    }

    protected MyBitSet createBitSet(int size) {
        return new MyBitSetImpl(size);
    }

    public void start(Graph g, int startNode, boolean depthFirst) {
        HelperColl coll;
        if (depthFirst)
            coll = new MyIntStack();
        else
            coll = new MyHelperIntQueue();

        MyBitSet visited = createBitSet(g.getNodes());
        visited.add(startNode);
        coll.push(startNode);
        int current;
        while (!coll.isEmpty()) {
            current = coll.pop();
            if (goFurther(current)) {
                EdgeIterator iter = getEdges(g, current);
                while (iter.next()) {
                    int connectedId = iter.node();
                    if (checkConnected(connectedId) && !visited.contains(connectedId)) {
                        visited.add(connectedId);
                        coll.push(connectedId);
                    }
                }
            }
        }
    }

    protected EdgeIterator getEdges(Graph g, int current) {
        return g.getOutgoing(current);
    }

    protected boolean goFurther(int nodeId) {
        return true;
    }

    protected boolean checkConnected(int to) {
        return true;
    }

    static class MyIntStack extends TIntArrayStack implements HelperColl {

        @Override
        public boolean isEmpty() {
            return super.size() == 0;
        }
    }

    static class MyHelperIntQueue extends MyIntDeque implements HelperColl {
    }
}
