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
package com.graphhopper.storage;

import com.graphhopper.routing.util.AllEdgesSkipIterator;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeSkipExplorer;

/**
 * A Graph necessary for shortcut algorithms like Contraction Hierarchies. This class enables the
 * storage to hold the level of a node and a shortcut edge per edge.
 * <p/>
 * @see GraphBuilder
 * @author Peter Karich
 */
public class LevelGraphStorage extends GraphStorage implements LevelGraph
{
    private final int I_SKIP_EDGE1;
    private final int I_SKIP_EDGE2;
    private final int I_LEVEL;

    public LevelGraphStorage( Directory dir, EncodingManager encodingManager )
    {
        super(dir, encodingManager);
        I_SKIP_EDGE1 = nextEdgeEntryIndex();
        I_SKIP_EDGE2 = nextEdgeEntryIndex();
        I_LEVEL = nextNodeEntryIndex();
        initNodeAndEdgeEntrySize();
    }

    @Override
    public final void setLevel( int index, int level )
    {
        ensureNodeIndex(index);
        nodes.setInt((long) index * nodeEntryBytes + I_LEVEL, level);
    }

    @Override
    public final int getLevel( int index )
    {
        ensureNodeIndex(index);
        return nodes.getInt((long) index * nodeEntryBytes + I_LEVEL);
    }

    @Override
    public EdgeSkipExplorer edge( int a, int b, double distance, boolean bothDir )
    {
        return (EdgeSkipExplorer) super.edge(a, b, distance, bothDir);
    }

    @Override
    public EdgeSkipExplorer edge( int a, int b, double distance, int flags )
    {
        ensureNodeIndex(Math.max(a, b));
        int edgeId = internalEdgeAdd(a, b, distance, flags);
        EdgeSkipIteratorImpl iter = new EdgeSkipIteratorImpl(null);
        iter.setBaseNode(a);
        iter.setEdgeId(edgeId);
        iter.next();
        iter.setSkippedEdges(EdgeIterator.NO_EDGE, EdgeIterator.NO_EDGE);
        return iter;
    }

    @Override
    public EdgeSkipExplorer createEdgeExplorer()
    {
        return createEdgeExplorer(allEdgesFilter);
    }

    @Override
    public EdgeSkipExplorer createEdgeExplorer( EdgeFilter filter )
    {
        return new EdgeSkipIteratorImpl(filter);
    }

    @Override
    public LevelGraphStorage create( long nodeCount )
    {
        super.create(nodeCount);
        return this;
    }

    class EdgeSkipIteratorImpl extends EdgeIterable implements EdgeSkipExplorer
    {
        public EdgeSkipIteratorImpl( EdgeFilter filter )
        {
            super(filter);
        }

        @Override
        public final void setSkippedEdges( int edge1, int edge2 )
        {
            if (EdgeIterator.Edge.isValid(edge1) != EdgeIterator.Edge.isValid(edge2))
            {
                throw new IllegalStateException("Skipped edges of a shortcut needs "
                        + "to be both valid or invalid but they were not " + edge1 + ", " + edge2);
            }
            edges.setInt(edgePointer + I_SKIP_EDGE1, edge1);
            edges.setInt(edgePointer + I_SKIP_EDGE2, edge2);
        }

        @Override
        public final int getSkippedEdge1()
        {
            return edges.getInt(edgePointer + I_SKIP_EDGE1);
        }

        @Override
        public final int getSkippedEdge2()
        {
            return edges.getInt(edgePointer + I_SKIP_EDGE2);
        }

        @Override
        public final boolean isShortcut()
        {
            return EdgeIterator.Edge.isValid(getSkippedEdge1());
        }

        @Override
        public EdgeIterator detach()
        {
            if (edgeId == nextEdge)
                throw new IllegalStateException("call next before detaching");
            EdgeSkipIteratorImpl iter = new EdgeSkipIteratorImpl(filter);
            iter.setBaseNode(baseNode);
            iter.setEdgeId(edgeId);
            iter.next();
            return iter;
        }
    }

    /**
     * Removes the edge in one direction. TODO hide this lower level API somehow.
     */
    public int disconnect( EdgeIteratorState iter, long prevEdgePointer, boolean sameDirection )
    {
        // open up package protected API for now ...
        if (sameDirection)
        {
            internalEdgeDisconnect(iter.getEdge(), prevEdgePointer, iter.getBaseNode(), iter.getAdjNode());
        } else
        {
            // prevEdgePointer belongs to baseNode ... but now we need it for adjNode()!           
            EdgeSkipExplorer tmpIter = createEdgeExplorer();
            tmpIter.setBaseNode(iter.getAdjNode());
            int tmpPrevEdge = EdgeIterator.NO_EDGE;
            boolean found = false;
            while (tmpIter.next())
            {
                if (tmpIter.getEdge() == iter.getEdge())
                {
                    found = true;
                    break;
                }

                tmpPrevEdge = tmpIter.getEdge();
            }
            if (found)
            {
                internalEdgeDisconnect(iter.getEdge(), (long) tmpPrevEdge * edgeEntryBytes, iter.getAdjNode(), iter.getBaseNode());
            }
        }
        return iter.getEdge();
    }

    @Override
    public AllEdgesSkipIterator getAllEdges()
    {
        return new AllEdgeSkipIterator();
    }

    class AllEdgeSkipIterator extends AllEdgeIterator implements AllEdgesSkipIterator
    {
        @Override
        public void setSkippedEdges( int edge1, int edge2 )
        {
            edges.setInt(edgePointer + I_SKIP_EDGE1, edge1);
            edges.setInt(edgePointer + I_SKIP_EDGE2, edge2);
        }

        @Override
        public int getSkippedEdge1()
        {
            return edges.getInt(edgePointer + I_SKIP_EDGE1);
        }

        @Override
        public int getSkippedEdge2()
        {
            return edges.getInt(edgePointer + I_SKIP_EDGE2);
        }

        @Override
        public boolean isShortcut()
        {
            return EdgeIterator.Edge.isValid(getSkippedEdge1());
        }
    }

    @Override
    public EdgeSkipExplorer getEdgeProps( int edgeId, int endNode )
    {
        return (EdgeSkipExplorer) super.getEdgeProps(edgeId, endNode);
    }

    @Override
    protected SingleEdge createSingleEdge( int edge, int nodeId )
    {
        return new SingleLevelEdge(edge, nodeId);
    }

    class SingleLevelEdge extends SingleEdge implements EdgeSkipExplorer
    {
        public SingleLevelEdge( int edge, int nodeId )
        {
            super(edge, nodeId);
        }

        @Override
        public void setSkippedEdges( int edge1, int edge2 )
        {
            edges.setInt(edgePointer + I_SKIP_EDGE1, edge1);
            edges.setInt(edgePointer + I_SKIP_EDGE2, edge2);
        }

        @Override
        public int getSkippedEdge1()
        {
            return edges.getInt(edgePointer + I_SKIP_EDGE1);
        }

        @Override
        public int getSkippedEdge2()
        {
            return edges.getInt(edgePointer + I_SKIP_EDGE2);
        }

        @Override
        public boolean isShortcut()
        {
            return EdgeIterator.Edge.isValid(getSkippedEdge1());
        }
    }
}
