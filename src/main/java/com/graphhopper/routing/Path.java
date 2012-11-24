/*
 *  Copyright 2012 Peter Karich 
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
package com.graphhopper.routing;

import com.graphhopper.routing.util.ShortestCarCalc;
import com.graphhopper.routing.util.WeightCalculation;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.DouglasPeucker;
import com.graphhopper.util.EdgeIterator;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;
import java.util.ArrayList;
import java.util.List;

/**
 * Stores the nodes for the found path of an algorithm. It additionally needs the edgeIds to make
 * edge determination faster and less complex as there could be several edges (u,v) especially for
 * graphs with shortcuts.
 *
 * @author Peter Karich,
 */
public class Path {

    protected final static double INIT_VALUE = Double.MAX_VALUE;
    protected Graph g;
    protected WeightCalculation weightCalculation;
    protected double weight;
    protected double distance;
    protected long time;
    protected boolean found;
    private TIntArrayList nodeIds = new TIntArrayList();

    Path() {
        this(null, ShortestCarCalc.DEFAULT);
    }

    public Path(Graph graph, WeightCalculation weightCalculation) {
        this.weightCalculation = weightCalculation;
        this.g = graph;
    }

    public boolean found() {
        return found;
    }

    public Path found(boolean found) {
        this.found = found;
        return this;
    }

    public void addFrom(int node) {
        add(node);
    }

    public void add(int node) {
        nodeIds.add(node);
    }

    public boolean contains(int node) {
        return nodeIds.contains(node);
    }

    public void reverseOrder() {
        nodeIds.reverse();
    }

    public int getFromNode() {
        return nodeIds.get(0);
    }

    public int nodes() {
        return nodeIds.size();
    }

    public int node(int index) {
        return nodeIds.get(index);
    }

    public double distance() {
        return distance;
    }

    public double weight() {
        return weight;
    }

    public void weight(double weight) {
        this.weight = weight;
    }

    @Override public String toString() {
        return "weight:" + weight() + ", nodes:" + nodeIds.size();
    }

    public String toDetailsString() {
        String str = "";
        for (int i = 0; i < nodes(); i++) {
            if (i > 0)
                str += "->";

            str += node(i);
        }
        return toString() + ", " + str;
    }

    public TIntSet and(Path p2) {
        TIntHashSet thisSet = new TIntHashSet();
        TIntHashSet retSet = new TIntHashSet();
        for (int i = 0; i < nodes(); i++) {
            thisSet.add(node(i));
        }

        for (int i = 0; i < p2.nodes(); i++) {
            if (thisSet.contains(p2.node(i)))
                retSet.add(p2.node(i));
        }
        return retSet;
    }

    public Path extract() {
        return this;
    }

    /**
     * This method calculates not only the weight but also the distance in kilometer.
     */
    public void calcWeight(EdgeIterator iter) {
        double dist = iter.distance();
        int fl = iter.flags();
        weight += weightCalculation.getWeight(dist, fl);
        distance += dist;
        time += weightCalculation.getTime(dist, fl);
    }

    /**
     * @return time in seconds
     */
    public long time() {
        return time;
    }

    public List<Integer> toNodeList() {
        List<Integer> list = new ArrayList<Integer>();
        int len = nodes();
        for (int i = 0; i < len; i++) {
            list.add(node(i));
        }
        return list;
    }

    public int simplify(DouglasPeucker algo) {
        int deleted = algo.simplify(nodeIds);
        if (deleted == 0)
            return 0;
        TIntArrayList res = new TIntArrayList(nodeIds.size() - deleted);
        int tmp = nodeIds.size();
        for (int i = 0; i < tmp; i++) {
            int n = nodeIds.get(i);
            if (n != DouglasPeucker.EMPTY)
                res.add(n);
        }
        nodeIds = res;
        return deleted;
    }
}
