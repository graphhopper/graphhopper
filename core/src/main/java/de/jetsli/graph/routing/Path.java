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

import de.jetsli.graph.routing.util.EdgeFlags;
import de.jetsli.graph.routing.util.WeightCalculation;
import de.jetsli.graph.storage.Graph;
import de.jetsli.graph.util.EdgeIterator;
import de.jetsli.graph.util.GraphUtility;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;

/**
 * Efficiently stores the entries for Dijkstra algorithm
 *
 * @author Peter Karich, info@jetsli.de
 */
public class Path {

    private WeightCalculation weightCalculation;
    private int timeInSec;
    private double distance;
    private TIntArrayList locations = new TIntArrayList();

    public Path() {
        this(WeightCalculation.SHORTEST);
    }

    public Path(WeightCalculation weightCalculation) {
        this.weightCalculation = weightCalculation;
    }

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

    public int indexOf(int node) {
        return locations.indexOf(node);
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
        return "distance:" + distance() + ", time:" + timeInSec() + ", locations:" + locations.size();
    }

    public String toDetailsString() {
        String str = "";
        for (int i = 0; i < locations.size(); i++) {
            if (i > 0)
                str += "->";

            str += locations.get(i);
        }
        return toString() + ", " + str;
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

    public void updateProperties(EdgeIterator iter, int to) {
        while (iter.next()) {
            if (iter.node() == to) {
                // TODO NOW weight vs. distance
                setDistance(distance() + iter.distance());
                setTimeInSec((int) (timeInSec() + iter.distance() * 3600.0 / EdgeFlags.getSpeed(iter.flags())));
                return;
            }
        }
        throw new IllegalStateException("couldn't extract path. distance for " + iter.node()
                + " to " + to + " not found!?");
    }

    public double calcDistance(Graph g) {
        double dist1 = 0;
        int l = locations() - 1;
        for (int i = 0; i < l; i++) {
            Path p1 = new DijkstraSimple(g).calcPath(location(i), location(i + 1));
            dist1 += p1.distance();
        }
        return dist1;
    }

    public static void debugDifference(Graph g, Path p1, Path p2) {
        int l = p1.locations() - 1;
        for (int i = 0; i < l; i++) {
            int from = p1.location(i);
            int to = p1.location(i + 1);
            Path tmp = new DijkstraSimple(g).calcPath(from, to);

            double dist = 0;
            int p2From = p2.indexOf(from);
            int p2To = p2.indexOf(to);
            for (int j = p2From; j < p2To; j++) {
                EdgeIterator iter = GraphUtility.until(g.getOutgoing(p2.location(j)), p2.location(j + 1));
                dist += iter.distance();
            }

            if (Math.abs(dist - tmp.distance()) > 0.01) {
                System.out.println("p.dist:" + tmp.distance() + ", dist:" + dist + ", " + from + "->" + to);
            }
        }
    }
}
