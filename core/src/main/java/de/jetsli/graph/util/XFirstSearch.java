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

import de.jetsli.graph.coll.MyBitSet;
import de.jetsli.graph.coll.MyOpenBitSet;
import de.jetsli.graph.storage.Graph;
import gnu.trove.stack.array.TIntArrayStack;

/**
 * breadth first search (BFS) or depth first search (DFS)
 *
 * @author Peter Karich, info@jetsli.de
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
        return new MyOpenBitSet(size);
    }

    public void start(Graph g, int node, boolean depthFirst) {
        HelperColl coll;
        if (depthFirst)
            coll = new MyIntStack();
        else
            coll = new MyHelperIntQueue();

        MyBitSet visited = createBitSet(g.getNodes());
        visited.add(node);
        coll.push(node);
        int current;

        while (!coll.isEmpty()) {
            current = coll.pop();
            if (goFurther(current)) {
                EdgeIdIterator iter = getEdges(g, current);
                while (iter.next()) {
                    int nodeId = iter.nodeId();
                    if (!visited.contains(nodeId)) {
                        visited.add(nodeId);
                        coll.push(nodeId);
                    }
                }
            }
        }
    }

    protected EdgeIdIterator getEdges(Graph g, int current) {
        return g.getOutgoing(current);
    }

    protected boolean goFurther(int nodeId) {
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
