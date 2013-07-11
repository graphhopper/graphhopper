/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor license
 *  agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the
 *  License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.storage.index;

import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.routing.util.AllEdgesSkipIterator;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.LevelGraph;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeSkipIterator;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PointList;
import gnu.trove.list.TIntList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * The LevelGraph has some edges disconnected (to be more efficient), but this happens before the
 * index is created! So we need to take care of this and also ignore the introduced shortcuts e.g.
 * for calculating closest edges.
 * <p/>
 * TODO avoid some of the tricks if we move a disconnected edge to the end of the edge-list (instead
 * of just disconnecting them). And then while accessing them break iteration if we encounter the
 * first of those disconnected edges (this should have the same speed). Therefor we also need to
 * change the EdgeFilter interface and add a stop(EdgeIterator) method or similar.
 * <p/>
 * @author Peter Karich
 */
public class Location2NodesNtreeLG extends Location2NodesNtree
{
    private final static EdgeFilter NO_SHORTCUT = new EdgeFilter()
    {
        @Override
        public boolean accept( EdgeIterator iter )
        {
            return !((EdgeSkipIterator) iter).isShortcut();
        }
    };
    private LevelGraph lg;

    public Location2NodesNtreeLG( LevelGraph g, Directory dir )
    {
        super(g, dir);
        lg = g;
    }

    @Override
    protected void sortNodes( TIntList nodes )
    {
        // nodes with high level should come first to be covered by lower level nodes
        ArrayList<Integer> list = Helper.tIntListToArrayList(nodes);
        Collections.sort(list, new Comparator<Integer>()
        {
            @Override
            public int compare( Integer o1, Integer o2 )
            {
                return lg.getLevel(o2) - lg.getLevel(o1);
            }
        });
        nodes.clear();
        nodes.addAll(list);
    }

    @Override
    protected int pickBestNode( int nodeA, int nodeB )
    {
        // return lower level nodes as those nodes are always connected to higher ones
        // (high level nodes are potentially disconnected from lower ones in order to improve performance on Android)
        if (lg.getLevel(nodeA) < lg.getLevel(nodeB))
        {
            return nodeA;
        }
        return nodeB;
    }

    @Override
    protected AllEdgesIterator getAllEdges()
    {
        final AllEdgesSkipIterator tmpIter = lg.getAllEdges();
        return new AllEdgesIterator()
        {

            @Override
            public EdgeIterator detach()
            {
                return tmpIter.detach();
            }            
            
            @Override
            public int getMaxId()
            {
                return tmpIter.getMaxId();
            }
            
            @Override
            public boolean next()
            {
                while (tmpIter.next())
                {
                    if (!tmpIter.isShortcut())
                    {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public int getEdge()
            {
                return tmpIter.getEdge();
            }

            @Override
            public int getBaseNode()
            {
                return tmpIter.getBaseNode();
            }

            @Override
            public int getAdjNode()
            {
                return tmpIter.getAdjNode();
            }

            @Override
            public PointList getWayGeometry()
            {
                return tmpIter.getWayGeometry();
            }

            @Override
            public void setWayGeometry( PointList list )
            {
                tmpIter.setWayGeometry(list);
            }

            @Override
            public double getDistance()
            {
                return tmpIter.getDistance();
            }

            @Override
            public void setDistance( double dist )
            {
                tmpIter.setDistance(dist);
            }

            @Override
            public int getFlags()
            {
                return tmpIter.getFlags();
            }

            @Override
            public void setFlags( int flags )
            {
                tmpIter.setFlags(flags);
            }

            @Override
            public boolean isEmpty()
            {
                return tmpIter.isEmpty();
            }
            
            @Override
            public String getName() {
                return tmpIter.getName();
            }

            @Override
            public void setName(String name) {
                tmpIter.setName(name);
            }
        };
    }

    @Override
    protected EdgeIterator getEdges( int node )
    {
        return lg.getEdges(node, NO_SHORTCUT);
    }
}
