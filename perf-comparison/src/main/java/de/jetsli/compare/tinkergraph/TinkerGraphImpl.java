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
package de.jetsli.compare.tinkergraph;

import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.blueprints.Edge;
import com.tinkerpop.blueprints.Vertex;
import com.tinkerpop.blueprints.impls.tg.TinkerGraph;
import de.jetsli.graph.routing.util.CarStreetType;
import de.jetsli.graph.storage.Graph;
import de.jetsli.graph.util.EdgeIterator;
import java.util.Iterator;

/**
 * @author Peter Karich
 */
public class TinkerGraphImpl implements Graph {

    private static final String LAT = "_lat";
    private static final String LON = "_lon";
    private static final String WAY = "way";
    private static final String DISTANCE = "distance";
    private static final String NODES = "nodeCount";
    com.tinkerpop.blueprints.Graph g;
    Vertex refNode;

    public TinkerGraphImpl(String file) {
        if (file == null)
            g = new TinkerGraph();
        else {
            g = new TinkerGraph(file);
        }

        refNode = g.addVertex("ref");
        refNode.setProperty(NODES, 0);
    }

    public void close() {
        g.shutdown();
    }

    public void ensureCapacity(int cap) {
    }

    public int getNodes() {
        return (Integer) refNode.getProperty(NODES);
    }
    private int size = 0;

    Vertex createNode(int id) {
        Vertex v = g.addVertex(id);
        size = Math.max(id + 1, size);
        refNode.setProperty(NODES, getNodes() + 1);
        return v;
    }

    public void setNode(int index, double lat, double lon) {
        Vertex v = createNode(index);
        v.setProperty(LAT, lat);
        v.setProperty(LON, lon);
    }

    public double getLatitude(int index) {
        return (Double) g.getVertex(index).getProperty(LAT);
    }

    public double getLongitude(int index) {
        return (Double) g.getVertex(index).getProperty(LON);
    }

    public void edge(int a, int b, double distance, boolean bothDirections) {
        edge(a, b, distance, CarStreetType.flagsDefault(bothDirections));
    }

    public void edge(int a, int b, double distance, int flags) {
        Vertex from = ensureNode(a);
        Vertex to = ensureNode(b);
        Iterator<Edge> iter = from.getEdges(Direction.BOTH, WAY).iterator();
        Edge e = null;
        while (iter.hasNext()) {
            e = iter.next();
            if (to.equals(getOtherNode(e, from)))
                break;

            e = null;
        }

        if (e == null)
            e = g.addEdge(null, from, to, WAY);
        e.setProperty(DISTANCE, distance);
    }

    public EdgeIterator getEdges(int index) {
        return new MyTinkerIterable(index, g.getVertex(index));
    }

    public EdgeIterator getIncoming(int index) {
        return new MyTinkerIterable(index, g.getVertex(index));
    }

    public EdgeIterator getOutgoing(int index) {
        return new MyTinkerIterable(index, g.getVertex(index));
    }

    static class MyTinkerIterable implements EdgeIterator {

        private Iterator<Edge> iter;
        private Vertex node;
        private int fromNode;
        //
        int id;
        double dist;
        int flags = 3;

        public MyTinkerIterable(int fromId, Vertex n) {
            if (n == null)
                throw new IllegalArgumentException("Node does not exist");

            this.fromNode = fromId;
            node = n;
            iter = n.getEdges(Direction.BOTH, WAY).iterator();
        }

        public boolean hasNext() {
            return iter.hasNext();
        }

        public boolean next() {
            if (!hasNext())
                return false;

            Edge e = iter.next();
            Vertex other = getOtherNode(e, node);
            dist = (Double) e.getProperty(DISTANCE);
            id = Integer.parseInt((String) other.getId());
            return true;
        }

        public int node() {
            return id;
        }

        public double distance() {
            return dist;
        }

        public int flags() {
            return flags;
        }

        public int fromNode() {
            return fromNode;
        }
    }

    static Vertex getOtherNode(Edge e, Vertex node) {
        Vertex other = e.getVertex(Direction.OUT);
        if (other.getId() == node.getId())
            other = e.getVertex(Direction.IN);
        return other;
    }

    public Graph clone() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void markNodeDeleted(int index) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public boolean isDeleted(int index) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void optimize() {
    }

    private Vertex ensureNode(int a) {
        Vertex v = g.getVertex(a);
        if (v == null) {
            int delta = a - getNodes() + 1;
            if (delta == 0)
                throw new IllegalStateException("Couldn't found node with id " + a);

            int tmp = size;
            for (int i = 0; i < delta; i++) {
                v = createNode(tmp + i);
            }
        }
        assert v != null;
        return v;
    }
}
