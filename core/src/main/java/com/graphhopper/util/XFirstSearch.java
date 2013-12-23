/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper licenses this file to you under the Apache License, 
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
package com.graphhopper.util;

import com.graphhopper.coll.GHBitSet;
import com.graphhopper.coll.GHBitSetImpl;
import gnu.trove.stack.array.TIntArrayStack;

/**
 * This class can be used for breadth first search (BFS) or depth first search (DFS)
 * <p/>
 * @author Peter Karich
 */
public class XFirstSearch
{
    /**
     * interface to use a queue (FIFO) OR a stack (LIFO)
     */
    interface HelperColl
    {
        boolean isEmpty();

        int pop();

        void push( int v );
    }

    protected GHBitSet createBitSet()
    {
        return new GHBitSetImpl();
    }

    public void start( EdgeExplorer explorer, int startNode, boolean depthFirst )
    {
        HelperColl coll;
        if (depthFirst)
        {
            coll = new MyIntStack();
        } else
        {
            coll = new MyHelperIntQueue();
        }

        GHBitSet visited = createBitSet();
        visited.add(startNode);
        coll.push(startNode);
        int current;
        while (!coll.isEmpty())
        {
            current = coll.pop();
            if (goFurther(current))
            {
                EdgeIterator iter = explorer.setBaseNode(current);
                while (iter.next())
                {
                    int connectedId = iter.getAdjNode();
                    if (checkAdjacent(iter) && !visited.contains(connectedId))
                    {
                        visited.add(connectedId);
                        coll.push(connectedId);
                    }
                }
            }
        }
    }

    protected boolean goFurther( int nodeId )
    {
        return true;
    }

    protected boolean checkAdjacent( EdgeIteratorState edge )
    {
        return true;
    }

    static class MyIntStack extends TIntArrayStack implements HelperColl
    {
        @Override
        public boolean isEmpty()
        {
            return super.size() == 0;
        }
    }

    static class MyHelperIntQueue extends SimpleIntDeque implements HelperColl
    {
    }
}
