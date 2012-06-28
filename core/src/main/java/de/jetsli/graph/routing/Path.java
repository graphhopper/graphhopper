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
package de.jetsli.graph.routing;

import de.jetsli.graph.storage.DistEntry;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Efficiently stores the entries for Dijkstra algorithm
 * 
 * @author Peter Karich, info@jetsli.de
 */
public class Path {

    // we cannot avoid this storage and e.g. use a linked list via distEntry.prevEntry = previousEntry; ...
    // as the prevEntry reference is already used for the shortest-path-tree back reference
    private List<DistEntry> distEntries = new ArrayList<DistEntry>();

    public void add(DistEntry distEntry) {
        distEntries.add(distEntry);
    }
    
    public boolean contains(int node) {
        for(DistEntry de : distEntries) {
            if(de.node == node)
                return true;
        }
        return false;
    }

    public void reverseOrder() {
        Collections.reverse(distEntries);
    }

    public int getFromLoc() {
        return distEntries.get(0).node;
    }

    public int locations() {
        return distEntries.size();
    }
    
    public int location(int index) {
        return distEntries.get(index).node;
    }

    public double distance() {
        return distEntries.get(distEntries.size() - 1).distance;
    }

    public void setDistance(double d) {
        distEntries.get(distEntries.size() - 1).distance = d;
    }

    @Override public String toString() {
        String str = "";
        for (int i = 0; i < distEntries.size(); i++) {
            if (i > 0)
                str += "->";

            str += distEntries.get(i).node;
        }
        return "distance:" + distance() + ", " + str;
    }

    public TIntSet and(Path p2) {
        TIntHashSet thisSet = new TIntHashSet();
        TIntHashSet retSet = new TIntHashSet();
        for (DistEntry de : distEntries) {
            thisSet.add(de.node);
        }
        
        for (DistEntry de : p2.distEntries) {
            if(thisSet.contains(de.node))
                retSet.add(de.node);
        }
        return retSet;
    }
}
