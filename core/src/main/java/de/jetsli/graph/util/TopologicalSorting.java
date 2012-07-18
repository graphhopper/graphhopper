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
import gnu.trove.iterator.TIntIterator;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.set.hash.TIntHashSet;

/**
 *
 * @author Peter Karich, info@jetsli.de
 */
public class TopologicalSorting {

    /**
     * conditions: acyclicGraph and all reachable from 0
     */
    public TIntArrayList sort(Graph g) {
        final TIntArrayList list = new TIntArrayList();
        if (g.getNodes() == 0)
            return list;
        else if (g.getNodes() == 1) {
            list.add(0);
            return list;
        }

        int startingNode = 0;
        // TODO in worst case 2*(|V|+|E|) traversals necessary. I feel it can be done more efficient
        // there was a solution with black and white markers ...

        final TIntHashSet noIncomingEdges = new TIntHashSet();
        new XFirstSearch() {

            @Override
            protected EdgeIdIterator getEdges(Graph g, int current) {
                if (!g.getIncoming(current).next())
                    noIncomingEdges.add(current);

                return g.getEdges(current);
            }
        }.start(g, startingNode, true);

        if (noIncomingEdges.size() == 0)
            throw new IllegalStateException("No beginning nodes found! Only acyclic graphs are allowed");

        MyBitSet visited = new MyOpenBitSet(g.getNodes());
        final MyIntDeque noIncomingDeque = new MyIntDeque(noIncomingEdges.size());
        for (TIntIterator iter = noIncomingEdges.iterator(); iter.hasNext();) {
            int tmp = iter.next();
            visited.add(tmp);
            noIncomingDeque.push(tmp);
        }

        int current;
        while (noIncomingDeque.size() > 0) {
            current = noIncomingDeque.pop();
            list.add(current);
            EdgeIdIterator iter = g.getOutgoing(current);
            while(iter.next()) {
                int nodeId = iter.nodeId();
                if (!visited.contains(nodeId)) {
                    visited.add(nodeId);
                    noIncomingDeque.push(nodeId);
                }
            }
        }

        return list;
    }
}
