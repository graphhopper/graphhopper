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

import com.graphhopper.routing.PathBidir;
import gnu.trove.map.hash.TIntIntHashMap;
import java.util.Arrays;

/**
 * This class acts as a HashMap (nodes to weights) and is used to implement references from one edge
 * to its parent.
 * <p/>
 * @see PathBidir
 * @author Peter Karich
 */
@NotThreadSafe
public class EdgeWrapper
{
    private static final float GROW_FACTOR = 1.5f;
    private int refCounter;
    private int[] nodes;
    private int[] edgeIds;
    private int[] parents;
    private float[] weights;
    protected TIntIntHashMap node2ref;

    public EdgeWrapper()
    {
        this(10);
    }

    public EdgeWrapper( int size )
    {
        nodes = new int[size];
        parents = new int[size];
        edgeIds = new int[size];
        weights = new float[size];
        node2ref = new TIntIntHashMap(size, GROW_FACTOR, -1, -1);
    }

    /**
     * @return edge id of current added (node,distance) tuple
     */
    public int add( int nodeId, double distance, int edgeId )
    {
        int ref = refCounter;
        refCounter++;
        node2ref.put(nodeId, ref);
        ensureCapacity(ref);
        weights[ref] = (float) distance;
        nodes[ref] = nodeId;
        parents[ref] = -1;
        edgeIds[ref] = edgeId;
        return ref;
    }

    public void putWeight( int ref, double dist )
    {
        if (ref < 1)
            throw new IllegalStateException("You cannot save a reference with values smaller 1. 0 is reserved");

        weights[ref] = (float) dist;
    }

    public void putEdgeId( int ref, int edgeId )
    {
        if (ref < 1)
            throw new IllegalStateException("You cannot save a reference with values smaller 1. 0 is reserved");

        edgeIds[ref] = edgeId;
    }

    public void putParent( int ref, int link )
    {
        if (ref < 1)
            throw new IllegalStateException("You cannot save a reference with values smaller 1. 0 is reserved");

        parents[ref] = link;
    }

    public double getWeight( int ref )
    {
        return weights[ref];
    }

    public int getNode( int ref )
    {
        return nodes[ref];
    }

    public int getParent( int ref )
    {
        return parents[ref];
    }

    public int getEdgeId( int ref )
    {
        return edgeIds[ref];
    }

    private void ensureCapacity( int size )
    {
        if (size < nodes.length)
            return;

        resize(Math.round(GROW_FACTOR * size));
    }

    private void resize( int cap )
    {
        weights = Arrays.copyOf(weights, cap);
        nodes = Arrays.copyOf(nodes, cap);
        parents = Arrays.copyOf(parents, cap);
        edgeIds = Arrays.copyOf(edgeIds, cap);
        node2ref.ensureCapacity(cap);
    }

    public void clear()
    {
        refCounter = 0;
        Arrays.fill(weights, 0);
        Arrays.fill(nodes, 0);
        Arrays.fill(parents, 0);
        Arrays.fill(edgeIds, EdgeIterator.NO_EDGE);
        node2ref.clear();
    }

    public int getRef( int node )
    {
        return node2ref.get(node);
    }

    public boolean isEmpty()
    {
        return refCounter == 0;
    }
}
