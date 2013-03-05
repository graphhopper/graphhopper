/*
 *  Licensed to Peter Karich under one or more contributor license
 *  agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  Peter Karich licenses this file to you under the Apache License,
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

import com.graphhopper.geohash.KeyAlgo;
import com.graphhopper.geohash.SpatialKeyAlgo;
import com.graphhopper.routing.util.VehicleEncoder;
import com.graphhopper.storage.DataAccess;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.DistancePlaneProjection;
import com.graphhopper.util.RawEdgeIterator;
import com.graphhopper.util.shapes.GHPlace;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;

/**
 * This implementation implements a spare n-tree to get edgeIds from GPS
 * location. This will replace Location2IDQuadtree.
 *
 * @author Peter Karich
 */
public class Location2EdgesNtree implements Location2EdgesIndex {

    private DistanceCalc dist = new DistancePlaneProjection();
    private DataAccess dataAccess;
    private KeyAlgo algo;
    private Graph g;
    /**
     * With maximum depth you control precision versus memory usage. The higher
     * the more memory wasted due to references but more precise value selection
     * can happen.
     */
    private int maxDepth;
    private int n;
    private int maxEntriesPerLeaf = 10;

    public Location2EdgesNtree(Graph g, Directory dir) {
        // 4 * 4 tiles => 2*2^2
        // 8 * 8 tiles => 2*2^3
        // 16 * 16 tiles => 2*2^4        
        n(4);
        maxDepth = 6;
        // (4 * 4)^6 = 16mio
        dataAccess = dir.findCreate("spatialIndex");
        algo = new SpatialKeyAlgo(maxDepth * (2 * n));
        this.g = g;
    }

    /**
     * @param n with l=2^n where l is the length of an internal tile. If n=2
     * then this is a quadtree.
     */
    Location2EdgesNtree n(int n) {
        // this.length = 1 << n;
        this.n = n;
        return this;
    }

    public boolean loadExisting() {
        // TODO NOW
        if (dataAccess.loadExisting()) {
            return true;
        }
        return false;
    }

    public void prepareIndex() {
        RawEdgeIterator iter = g.getAllEdges();
        while (iter.next()) {
        }
    }

    @Override
    public TIntList findEdges(GHPlace point) {
        long spatialKey = algo.encode(point.lat, point.lon);
        TIntArrayList list = new TIntArrayList();
        return list;
    }

    public TIntList findClosestEdge(TIntList edges, Graph g, VehicleEncoder encoder) {
        TIntArrayList list = new TIntArrayList();

//        g.getEdges(index);
//        if(encoder.isValidEdge(edgeIter)) {
//            
//        }
        return list;
    }
}
