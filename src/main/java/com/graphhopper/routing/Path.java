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
import com.graphhopper.storage.EdgeEntry;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.DouglasPeucker;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.PointList;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;

/**
 * Stores the nodes for the found path of an algorithm. It additionally needs
 * the edgeIds to make edge determination faster and less complex as there could
 * be several edges (u,v) especially for graphs with shortcuts.
 *
 * @author Peter Karich,
 */
public class Path {

    protected final static double INIT_VALUE = Double.MAX_VALUE;
    protected Graph graph;
    protected WeightCalculation weightCalculation;
    protected double weight;
    protected double distance;
    protected long time;
    protected boolean found;
    // we go upwards (via EdgeEntry.parent) from the goal node to the origin node
    protected boolean reverse = true;
    protected EdgeEntry edgeEntry;
    private int fromNode = EdgeIterator.NO_EDGE;
    private TIntList edgeIds = new TIntArrayList();
    private PointList cachedPoints;
    private TIntList cachedNodes;

    Path() {
        this(null, ShortestCarCalc.DEFAULT);
    }

    public Path(Graph graph, WeightCalculation weightCalculation) {
        this.graph = graph;
        this.weightCalculation = weightCalculation;
    }

    public Path edgeEntry(EdgeEntry edgeEntry) {
        this.edgeEntry = edgeEntry;
        return this;
    }

    protected void addEdge(int edge) {
        edgeIds.add(edge);
    }

    public void setFound(boolean found) {
        this.found = found;
    }

    public int getFromNode() {
        if (!EdgeIterator.Edge.isValid(fromNode))
            throw new IllegalStateException("Call extract() before retrieving fromNode");
        return fromNode;
    }

    public boolean found() {
        return found;
    }

    public Path found(boolean found) {
        this.found = found;
        return this;
    }

    public void reverseOrder() {
        reverse = !reverse;
        edgeIds.reverse();
    }

    /**
     * @return distance in meter
     */
    public double distance() {
        return distance;
    }

    /**
     * @return time in seconds
     */
    public long time() {
        return time;
    }

    /**
     * The final weight which is the sum from the weights of the used edges.
     */
    public double weight() {
        return weight;
    }

    public void weight(double weight) {
        this.weight = weight;
    }

    @Override public String toString() {
        return "weight:" + weight() + ", edges:" + edgeIds.size();
    }

    public String toDetailsString() {
        String str = "";
        TIntList nodes = nodes();
        for (int i = 0; i < nodes.size(); i++) {
            if (i > 0)
                str += "->";

            str += nodes.get(i);
        }
        return toString() + ", " + str;
    }

    /**
     * Extract path from shortest-path-tree.
     */
    public Path extract() {
        EdgeEntry goalEdge = edgeEntry;
        while (EdgeIterator.Edge.isValid(goalEdge.edge)) {
            processWeight(goalEdge.edge, goalEdge.endNode);
            goalEdge = goalEdge.parent;
        }

        setFromNode(goalEdge.endNode);
        reverseOrder();
        return found(true);
    }

    protected void processWeight(int tmpEdge, int endNode) {
        calcWeight(graph.getEdgeProps(tmpEdge, endNode));
        addEdge(tmpEdge);
    }

    /**
     * This method calculates not only the weight but also the distance in
     * kilometer.
     */
    public void calcWeight(EdgeIterator iter) {
        double dist = iter.distance();
        int fl = iter.flags();
        weight += weightCalculation.getWeight(dist, fl);
        distance += dist;
        time += weightCalculation.getTime(dist, fl);
    }

    /**
     * @return the node indices of the tower nodes in this path.
     */
    public TIntList nodes() {
        if (cachedNodes == null)
            calcNodes();
        return cachedNodes;
    }

    private TIntList calcNodes() {
        cachedNodes = new TIntArrayList(edgeIds.size() + 1);
        if (edgeIds.isEmpty())
            return cachedNodes;

        int tmpNode = getFromNode();
        cachedNodes.add(tmpNode);
        int len = edgeIds.size();
        for (int i = 0; i < len; i++) {
            EdgeIterator iter = graph.getEdgeProps(edgeIds.get(i), tmpNode);
            cachedNodes.add(tmpNode = iter.baseNode());
        }
        return cachedNodes;
    }

    public PointList points() {
        if (cachedPoints == null)
            calcPoints();
        return cachedPoints;
    }

    private PointList calcPoints() {
        cachedPoints = new PointList(edgeIds.size() + 1);
        int tmpNode = getFromNode();
        cachedPoints.add(graph.getLatitude(tmpNode), graph.getLongitude(tmpNode));
        int len = edgeIds.size();
        for (int i = 0; i < len; i++) {
            int edgeId = edgeIds.get(i);
            EdgeIterator iter = graph.getEdgeProps(edgeId, tmpNode);
            PointList pl = iter.pillarNodes();            
            pl.reverse();
            for (int j = 0; j < pl.size(); j++) {
                cachedPoints.add(pl.latitude(j), pl.longitude(j));
            }
            tmpNode = iter.baseNode();
            cachedPoints.add(graph.getLatitude(tmpNode), graph.getLongitude(tmpNode));
        }
        return cachedPoints;
    }

    public int simplify(DouglasPeucker algo) {
        return algo.simplify(points());
    }

    public TIntSet calculateIdenticalNodes(Path p2) {
        TIntHashSet thisSet = new TIntHashSet();
        TIntHashSet retSet = new TIntHashSet();
        TIntList nodes = nodes();
        int max = nodes.size();
        for (int i = 0; i < max; i++) {
            thisSet.add(nodes.get(i));
        }

        nodes = p2.nodes();
        max = nodes.size();
        for (int i = 0; i < max; i++) {
            if (thisSet.contains(nodes.get(i)))
                retSet.add(nodes.get(i));
        }
        return retSet;
    }

    /**
     * We need to remember fromNode explicitely as its not saved in one edgeId
     * of edgeIds.
     */
    protected void setFromNode(int node) {
        fromNode = node;
    }
}
