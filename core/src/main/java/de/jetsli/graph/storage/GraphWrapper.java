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
package de.jetsli.graph.storage;

import de.jetsli.graph.coll.MyBitSet;
import de.jetsli.graph.util.MyIteratorable;

/**
 *
 * @author Peter Karich, info@jetsli.de
 */
public class GraphWrapper implements Graph {

    private MyBitSet ignoreNodes;
    private int ignoreNodesSize;
    private Graph g;

    public GraphWrapper(Graph g) {
        this.g = g;
    }        

    public void setIgnoreNodes(MyBitSet bitSet) {
        ignoreNodes = bitSet;
        ignoreNodesSize = (int) bitSet.cardinality();
    }

    @Override
    public int getLocations() {
        int tmp = g.getLocations() - ignoreNodesSize;
        assert tmp >= 0;
        return tmp;
    }
    
    @Override
    public MyIteratorable<DistEntry> getEdges(int index) {
        if(ignoreNodes.contains(index))
            return DistEntry.EMPTY_ITER;
        
        return g.getEdges(index);
    }

    @Override
    public MyIteratorable<DistEntry> getOutgoing(int index) {
        if(ignoreNodes.contains(index))
            return DistEntry.EMPTY_ITER;
        
        return g.getOutgoing(index);
    }

    @Override
    public MyIteratorable<DistEntry> getIncoming(int index) {
        if(ignoreNodes.contains(index))
            return DistEntry.EMPTY_ITER;
        
        return g.getIncoming(index);
    }

    @Override
    public int addLocation(double lat, double lon) {
        return g.addLocation(lat, lon);
    }

    @Override
    public double getLatitude(int index) {
        return g.getLatitude(index);
    }

    @Override
    public double getLongitude(int index) {
        return g.getLongitude(index);
    }

    @Override
    public void edge(int a, int b, double distance, boolean bothDirections) {
        g.edge(a, b, distance, bothDirections);
    }

    @Override
    public Graph clone() {
        return g.clone();
    }

    @Override
    public void ensureCapacity(int cap) {
        g.ensureCapacity(cap);
    }
}
