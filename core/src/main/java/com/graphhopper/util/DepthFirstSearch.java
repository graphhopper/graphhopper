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
import gnu.trove.stack.array.TIntArrayStack;

/**
 * Implementation of depth first search (DFS) by LIFO queue
 * <p/>
 * @author Peter Karich & Jan Sölter
 */
public class DepthFirstSearch extends XFirstSearch
{

    /**
     * beginning with startNode add all following nodes to LIFO queue. If node has been already
     * explored before, skip reexploration.
     */
    @Override
    public void start( EdgeExplorer explorer, int startNode)
    {
        TIntArrayStack stack = new TIntArrayStack();

        GHBitSet explored = createBitSet();
        stack.push(startNode);
        int current;
        while (stack.size()>0)
        {
            current = stack.pop();
            if (!explored.contains(current) && goFurther(current))
            {   
                EdgeIterator iter = explorer.setBaseNode(current);
                while (iter.next())
                {
                    int connectedId = iter.getAdjNode();
                    if (checkAdjacent(iter))
                    {
                        stack.push(connectedId);
                    }
                }
                explored.add(current);
            }
        }
    }

}
