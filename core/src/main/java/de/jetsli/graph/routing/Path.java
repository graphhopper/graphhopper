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

import de.jetsli.graph.reader.CarFlags;
import de.jetsli.graph.util.EdgeIdIterator;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;

/**
 * Efficiently stores the entries for Dijkstra algorithm
 *
 * @author Peter Karich, info@jetsli.de
 */
public class Path {

    private int timeInSec;
    private double distance;
    private TIntArrayList locations = new TIntArrayList();

    public void setTimeInSec(int timeInSec) {
        this.timeInSec = timeInSec;
    }

    public int timeInSec() {
        return timeInSec;
    }

    public void add(int node) {
        locations.add(node);
    }

    public boolean contains(int node) {
        return locations.contains(node);
    }

    public void reverseOrder() {
        locations.reverse();
    }

    public int getFromLoc() {
        return locations.get(0);
    }

    public int locations() {
        return locations.size();
    }

    public int location(int index) {
        return locations.get(index);
    }

    public double distance() {
        return distance;
    }

    public void setDistance(double d) {
        distance = d;
    }

    @Override public String toString() {
        String str = "";
        for (int i = 0; i < locations.size(); i++) {
            if (i > 0)
                str += "->";

            str += locations.get(i);
        }
        return "distance:" + distance() + ", " + str;
    }

    public TIntSet and(Path p2) {
        TIntHashSet thisSet = new TIntHashSet();
        TIntHashSet retSet = new TIntHashSet();
        for (int i = 0; i < locations.size(); i++) {
            thisSet.add(locations.get(i));
        }

        for (int i = 0; i < p2.locations.size(); i++) {
            if (thisSet.contains(p2.locations.get(i)))
                retSet.add(p2.locations.get(i));
        }
        return retSet;
    }

    public void updateProperties(EdgeIdIterator iter, int to) {
        while (iter.next()) {
            if (iter.nodeId() == to) {
                setDistance(distance() + iter.distance());
                setTimeInSec((int) (timeInSec() + iter.distance() * 3600.0 / CarFlags.getSpeed(iter.flags())));
                return;
            }
        }
        throw new IllegalStateException("couldn't extract path. distance for " + iter.nodeId()
                + " to " + to + " not found!?");
    }
}
