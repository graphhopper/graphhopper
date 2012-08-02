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

import de.jetsli.graph.storage.WeightedEntry;
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

    private List<WeightedEntry> distEntries = new ArrayList<WeightedEntry>();

    public void add(WeightedEntry distEntry) {
        distEntries.add(distEntry);
    }
    
    public boolean contains(int node) {
        for(WeightedEntry de : distEntries) {
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
        return distEntries.get(distEntries.size() - 1).weight;
    }

    public void setDistance(double d) {
        distEntries.get(distEntries.size() - 1).weight = d;
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
        for (WeightedEntry de : distEntries) {
            thisSet.add(de.node);
        }
        
        for (WeightedEntry de : p2.distEntries) {
            if(thisSet.contains(de.node))
                retSet.add(de.node);
        }
        return retSet;
    }
}
