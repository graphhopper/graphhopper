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
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.routing.util.AllEdgesSkipIterator;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.*;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import java.util.*;

/**
 * A helper class to avoid cluttering the Graph interface with all the common methods. Most of the
 * methods are useful for unit tests or debugging only.
 * <p/>
 * @author Peter Karich
 */
public class GHUtility
{
    /**
     * @throws could throw exception if uncatched problems like index out of bounds etc
     */
    public static List<String> getProblems( Graph g )
    {
        List<String> problems = new ArrayList<String>();
        int nodes = g.getNodes();
        int nodeIndex = 0;
        try
        {
            EdgeExplorer explorer = g.createEdgeExplorer();
            for (; nodeIndex < nodes; nodeIndex++)
            {
                double lat = g.getLatitude(nodeIndex);
                if (lat > 90 || lat < -90)
                    problems.add("latitude is not within its bounds " + lat);

                double lon = g.getLongitude(nodeIndex);
                if (lon > 180 || lon < -180)
                    problems.add("longitude is not within its bounds " + lon);

                EdgeIterator iter = explorer.setBaseNode(nodeIndex);
                while (iter.next())
                {
                    if (iter.getAdjNode() >= nodes)
                    {
                        problems.add("edge of " + nodeIndex + " has a node " + iter.getAdjNode() + " greater or equal to getNodes");
                    }
                    if (iter.getAdjNode() < 0)
                    {
                        problems.add("edge of " + nodeIndex + " has a negative node " + iter.getAdjNode());
                    }
                }
            }
        } catch (Exception ex)
        {
            throw new RuntimeException("problem with node " + nodeIndex, ex);
        }

//        for (int i = 0; i < nodes; i++) {
//            new XFirstSearch().start(g, i, false);
//        }
        return problems;
    }

    /**
     * Counts reachable edges.
     */
    public static int count( EdgeIterator iter )
    {
        int counter = 0;
        while (iter.next())
        {
            counter++;
        }
        return counter;
    }

    public static Set<Integer> asSet( int... values )
    {
        Set<Integer> s = new HashSet<Integer>();
        for (int v : values)
        {
            s.add(v);
        }
        return s;
    }

    public static Set<Integer> getNeighbors( EdgeIterator iter )
    {
        Set<Integer> list = new HashSet<Integer>();
        while (iter.next())
        {
            list.add(iter.getAdjNode());
        }
        return list;
    }

    public static void printEdgeInfo( final Graph g, FlagEncoder encoder )
    {
        System.out.println("-- Graph n:" + g.getNodes() + " e:" + g.getAllEdges().getMaxId() + " ---");
        AllEdgesIterator iter = g.getAllEdges();
        while (iter.next())
        {
            String sc = "";
            if (iter instanceof AllEdgesSkipIterator)
            {
                AllEdgesSkipIterator aeSkip = (AllEdgesSkipIterator) iter;
                sc = aeSkip.isShortcut() ? "sc" : "  ";
            }
            String fwdStr = encoder.isForward(iter.getFlags()) ? "fwd" : "   ";
            String bckStr = encoder.isBackward(iter.getFlags()) ? "bckwd" : "";
            System.out.println(sc + " " + iter + " " + fwdStr + " " + bckStr);
        }
    }

    public static void printInfo( final Graph g, int startNode, final int counts, final EdgeFilter filter )
    {
        new XFirstSearch()
        {
            int counter = 0;

            @Override
            protected boolean goFurther( int nodeId )
            {
                System.out.println(getNodeInfo(g, nodeId, filter));
                if (counter++ > counts)
                {
                    return false;
                }
                return true;
            }
        }.start(g.createEdgeExplorer(), startNode, false);
    }

    public static String getNodeInfo( LevelGraph g, int nodeId, EdgeFilter filter )
    {
        EdgeSkipExplorer ex = g.createEdgeExplorer(filter);
        EdgeSkipIterator iter = ex.setBaseNode(nodeId);
        String str = nodeId + ":" + g.getLatitude(nodeId) + "," + g.getLongitude(nodeId) + "\n";
        while (iter.next())
        {
            str += "  ->" + iter.getAdjNode() + "(" + iter.getSkippedEdge1() + "," + iter.getSkippedEdge2() + ") "
                    + iter.getEdge() + " \t" + BitUtil.BIG.toBitString(iter.getFlags(), 8) + "\n";
        }
        return str;
    }

    public static String getNodeInfo( Graph g, int nodeId, EdgeFilter filter )
    {
        EdgeIterator iter = g.createEdgeExplorer(filter).setBaseNode(nodeId);
        String str = nodeId + ":" + g.getLatitude(nodeId) + "," + g.getLongitude(nodeId) + "\n";
        while (iter.next())
        {
            str += "  ->" + iter.getAdjNode() + " (" + iter.getDistance() + ") pillars:"
                    + iter.fetchWayGeometry(0).getSize() + ", edgeId:" + iter.getEdge()
                    + "\t" + BitUtil.BIG.toBitString(iter.getFlags(), 8) + "\n";
        }
        return str;
    }

    public static Graph shuffle( Graph g, Graph sortedGraph )
    {
        int len = g.getNodes();
        TIntList list = new TIntArrayList(len, -1);
        list.fill(0, len, -1);
        for (int i = 0; i < len; i++)
        {
            list.set(i, i);
        }
        list.shuffle(new Random());
        return createSortedGraph(g, sortedGraph, list);
    }

    /**
     * Sorts the graph according to depth-first search traversal. Other traversals have either no
     * significant difference (bfs) for querying or are worse (z-curve).
     */
    public static Graph sortDFS( Graph g, Graph sortedGraph )
    {
        final TIntList list = new TIntArrayList(g.getNodes(), -1);
        int nodes = g.getNodes();
        list.fill(0, nodes, -1);
        final GHBitSetImpl bitset = new GHBitSetImpl(nodes);
        final IntRef ref = new IntRef(0);
        EdgeExplorer explorer = g.createEdgeExplorer();
        for (int startNode = 0; startNode >= 0 && startNode < nodes;
                startNode = bitset.nextClear(startNode + 1))
        {
            new XFirstSearch()
            {
                @Override
                protected GHBitSet createBitSet()
                {
                    return bitset;
                }

                @Override
                protected boolean goFurther( int nodeId )
                {
                    list.set(nodeId, ref.val);
                    ref.val++;
                    return super.goFurther(nodeId);
                }
            }.start(explorer, startNode, false);
        }
        return createSortedGraph(g, sortedGraph, list);
    }

    static Graph createSortedGraph( Graph fromGraph, Graph toSortedGraph, final TIntList oldToNewNodeList )
    {
        // copy nodes
        int maxNodeId = fromGraph.getNodes();
        for (int node = 0; node < maxNodeId; node++)
        {
            int newNode = oldToNewNodeList.get(node);
            toSortedGraph.setNode(newNode, fromGraph.getLatitude(node), fromGraph.getLongitude(node));
        }

        AllEdgesIterator eIter = fromGraph.getAllEdges();
        while (eIter.next())
        {
            int base = eIter.getBaseNode();
            int newBaseIndex = oldToNewNodeList.get(base);
            int adj = eIter.getAdjNode();
            int newAdjIndex = oldToNewNodeList.get(adj);

            // ignore empty entries
            if (newBaseIndex < 0 || newAdjIndex < 0)
                continue;

            toSortedGraph.edge(newBaseIndex, newAdjIndex).copyProperties(eIter);
        }
        return toSortedGraph;
    }

    /**
     * @return the specified toGraph which is now filled with data from fromGraph
     */
    // TODO very similar to createSortedGraph -> use a 'int map(int)' interface
    public static Graph copyTo( Graph fromGraph, Graph toGraph )
    {
        // copy nodes
        int maxNodeId = fromGraph.getNodes();
        for (int node = 0; node < maxNodeId; node++)
        {
            toGraph.setNode(node, fromGraph.getLatitude(node), fromGraph.getLongitude(node));
        }

        AllEdgesIterator eIter = fromGraph.getAllEdges();
        while (eIter.next())
        {
            int base = eIter.getBaseNode();
            int adj = eIter.getAdjNode();
            toGraph.edge(base, adj).copyProperties(eIter);
        }
        return toGraph;
    }

    static Directory guessDirectory( GraphStorage store )
    {
        String location = store.getDirectory().getLocation();
        Directory outdir;
        if (store.getDirectory() instanceof MMapDirectory)
        {
            throw new IllegalStateException("not supported yet: mmap will overwrite existing storage at the same location");
        } else
        {
            boolean isStoring = ((GHDirectory) store.getDirectory()).isStoring();
            outdir = new RAMDirectory(location, isStoring);
        }
        return outdir;
    }

    static GraphStorage guessStorage( Graph g, Directory outdir, EncodingManager encodingManager )
    {
        GraphStorage store;
        if (g instanceof LevelGraphStorage)
        {
            store = new LevelGraphStorage(outdir, encodingManager);
        } else
        {
            store = new GraphHopperStorage(outdir, encodingManager);
        }
        return store;
    }

    /**
     * Create a new storage from the specified one without copying the data.
     */
    public static GraphStorage newStorage( GraphStorage store )
    {
        return guessStorage(store, guessDirectory(store), store.getEncodingManager()).create(store.getNodes());
    }

    /**
     * @return the graph outGraph
     */
    public static Graph clone( Graph g, GraphStorage outGraph )
    {
        return g.copyTo(outGraph.create(g.getNodes()));
    }

    public static int getToNode( Graph g, int edge, int endNode )
    {
        if (EdgeIterator.Edge.isValid(edge))
        {
            EdgeIteratorState iterTo = g.getEdgeProps(edge, endNode);
            return iterTo.getAdjNode();
        }
        return endNode;
    }

    public static class DisabledEdgeIterator implements EdgeSkipIterator
    {
        @Override
        public EdgeIterator detach( boolean reverse )
        {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public boolean isShortcut()
        {
            return false;
        }

        @Override
        public int getSkippedEdge1()
        {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public int getSkippedEdge2()
        {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public void setSkippedEdges( int edge1, int edge2 )
        {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public EdgeIteratorState setDistance( double dist )
        {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public EdgeIteratorState setFlags( long flags )
        {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public boolean next()
        {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public int getEdge()
        {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public int getBaseNode()
        {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public int getAdjNode()
        {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public double getDistance()
        {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public long getFlags()
        {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public PointList fetchWayGeometry( int type )
        {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public EdgeIteratorState setWayGeometry( PointList list )
        {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public String getName()
        {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public EdgeIteratorState setName( String name )
        {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public int getAdditionalField()
        {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public EdgeIteratorState setAdditionalField( int value )
        {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public void copyProperties( EdgeIteratorState edge )
        {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public double getWeight()
        {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }

        @Override
        public EdgeSkipIterState setWeight( double weight )
        {
            throw new UnsupportedOperationException("Not supported. Edge is empty.");
        }
    };

    /**
     * @return the <b>first</b> edge containing the specified nodes base and adj. Returns null if
     * not found.
     */
    public static EdgeIteratorState getEdge( Graph graph, int base, int adj )
    {
        EdgeIterator iter = graph.createEdgeExplorer().setBaseNode(base);
        while (iter.next())
        {
            if (iter.getAdjNode() == adj)
                return iter;
        }
        return null;
    }
}
