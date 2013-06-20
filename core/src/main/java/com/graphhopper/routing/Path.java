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
package com.graphhopper.routing;

import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.EdgeEntry;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.PointList;
import com.graphhopper.util.StopWatch;
import gnu.trove.list.TDoubleList;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TDoubleArrayList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;

/**
 * Stores the nodes for the found path of an algorithm. It additionally needs
 * the edgeIds to make edge determination faster and less complex as there could
 * be several edges (u,v) especially for graphs with shortcuts.
 *
 * @author Peter Karich
 */
public class Path {

    protected Graph graph;
    protected FlagEncoder encoder;
    protected double distance;
    // we go upwards (via EdgeEntry.parent) from the goal node to the origin node
    protected boolean reverseOrder = true;
    private long time;
    private boolean found;
    protected EdgeEntry edgeEntry;
    StopWatch sw = new StopWatch("extract");
    private int fromNode = EdgeIterator.NO_EDGE;
    private TIntList edgeIds;
    private PointList cachedPoints;
    private double weight;

    public Path(Graph graph, FlagEncoder encoder) {
        this.weight = Double.MAX_VALUE;
        this.graph = graph;
        this.encoder = encoder;
        this.edgeIds = new TIntArrayList();
    }

    /**
     * Populates an unextracted path instances from the specified path p.
     */
    Path(Path p) {
        this(p.graph, p.encoder);
        weight = p.weight;
        edgeIds = new TIntArrayList(edgeIds);
        edgeEntry = p.edgeEntry;
    }

    public Path edgeEntry(EdgeEntry edgeEntry) {
        this.edgeEntry = edgeEntry;
        return this;
    }

    protected void addEdge(int edge) {
        edgeIds.add(edge);
    }

    /**
     * We need to remember fromNode explicitely as its not saved in one edgeId
     * of edgeIds.
     */
    protected Path fromNode(int node) {
        fromNode = node;
        return this;
    }

    /**
     * @return the first node of this Path.
     */
    public int fromNode() {
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

    void reverseOrder() {
        reverseOrder = !reverseOrder;
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
     * This weight will be updated during the algorithm. The initial value is
     * maximum double.
     */
    public double weight() {
        return weight;
    }

    public void weight(double w) {
        this.weight = w;
    }

    /**
     * Extracts the Path from the shortest-path-tree determined by edgeEntry.
     */
    public Path extract() {
        sw.start();
        EdgeEntry goalEdge = edgeEntry;
        while (EdgeIterator.Edge.isValid(goalEdge.edge)) {
            processDistance(goalEdge.edge, goalEdge.endNode);
            goalEdge = goalEdge.parent;
        }

        fromNode(goalEdge.endNode);
        reverseOrder();
        sw.stop();
        return found(true);
    }

    public String debugInfo() {
        return sw.toString();
    }

    /**
     * Calls calcDistance and adds the edgeId.
     */
    protected void processDistance(int edgeId, int endNode) {
        EdgeIterator iter = graph.getEdgeProps(edgeId, endNode);
        calcDistance(iter);
        calcTime(iter.distance(), iter.flags());
        addEdge(edgeId);
    }

    /**
     * This method calculates not only the weight but also the distance in
     * kilometer for the specified edge.
     */
    protected void calcDistance(EdgeIterator iter) {
        distance += iter.distance();
    }

    protected void calcTime(double distance, int flags) {
        time += (long) (distance * 3.6 / encoder.getSpeed(flags));
    }

    /**
     * Used in combination with forEveryEdge.
     */
    public static interface EdgeVisitor {

        void next(EdgeIterator iter);
    }

    /**
     * Iterates over all edges in this path and calls the visitor for it.
     */
    public void forEveryEdge(EdgeVisitor visitor) {
        int tmpNode = fromNode();
        int len = edgeIds.size();
        for (int i = 0; i < len; i++) {
            EdgeIterator iter = graph.getEdgeProps(edgeIds.get(i), tmpNode);
            if (iter.isEmpty())
                throw new IllegalStateException("Edge " + edgeIds.get(i)
                        + " was empty when requested with node " + tmpNode
                        + ", array index:" + i + ", edges:" + edgeIds.size());
            tmpNode = iter.baseNode();
            visitor.next(iter);
        }
    }

    /**
     * @return the uncached node indices of the tower nodes in this path.
     */
    public TIntList calcNodes() {
        final TIntArrayList nodes = new TIntArrayList(edgeIds.size() + 1);
        if (edgeIds.isEmpty())
            return nodes;

        int tmpNode = fromNode();
        nodes.add(tmpNode);
        forEveryEdge(new EdgeVisitor() {
            @Override public void next(EdgeIterator iter) {
                nodes.add(iter.baseNode());
            }
        });
        return nodes;
    }

    /**
     * @return the cached list of lat,lon for this path
     */
    public PointList calcPoints() {
        if (cachedPoints != null)
            return cachedPoints;
        cachedPoints = new PointList(edgeIds.size() + 1);
        if (edgeIds.isEmpty())
            return cachedPoints;
        int tmpNode = fromNode();
        cachedPoints.add(graph.getLatitude(tmpNode), graph.getLongitude(tmpNode));
        forEveryEdge(new EdgeVisitor() {
            @Override public void next(EdgeIterator iter) {
                PointList pl = iter.wayGeometry();
                pl.reverse();
                for (int j = 0; j < pl.size(); j++) {
                    cachedPoints.add(pl.latitude(j), pl.longitude(j));
                }
                int baseNode = iter.baseNode();
                cachedPoints.add(graph.getLatitude(baseNode), graph.getLongitude(baseNode));
            }
        });
        return cachedPoints;
    }

    public TDoubleList calcDistances() {
        final TDoubleList distances = new TDoubleArrayList(edgeIds.size());
        if (edgeIds.isEmpty())
            return distances;

        forEveryEdge(new EdgeVisitor() {
            @Override public void next(EdgeIterator iter) {
                distances.add(iter.distance());
            }
        });
        return distances;
    }

    public TIntSet calculateIdenticalNodes(Path p2) {
        TIntHashSet thisSet = new TIntHashSet();
        TIntHashSet retSet = new TIntHashSet();
        TIntList nodes = calcNodes();
        int max = nodes.size();
        for (int i = 0; i < max; i++) {
            thisSet.add(nodes.get(i));
        }

        nodes = p2.calcNodes();
        max = nodes.size();
        for (int i = 0; i < max; i++) {
            if (thisSet.contains(nodes.get(i)))
                retSet.add(nodes.get(i));
        }
        return retSet;
    }

    @Override public String toString() {
        return "distance:" + distance() + ", edges:" + edgeIds.size();
    }

    public String toDetailsString() {
        String str = "";
        for (int i = 0; i < edgeIds.size(); i++) {
            if (i > 0)
                str += "->";

            str += edgeIds.get(i);
        }
        return toString() + ", " + str;
    }
}
