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
import de.jetsli.graph.storage.EdgeWithFlags;
import de.jetsli.graph.storage.Graph;
import de.jetsli.graph.util.MyIteratorable;
import java.io.File;
import java.io.FileNotFoundException;
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

    public int getLocations() {
        return (Integer) refNode.getProperty(NODES);
    }
    int locCounter = 0;

    Vertex createNode() {
        Vertex v = g.addVertex(locCounter);
        locCounter++;
        refNode.setProperty(NODES, getLocations() + 1);
        return v;
    }

    public int addLocation(double lat, double lon) {
        int tmp = locCounter;
        Vertex v = createNode();
        v.setProperty(LAT, lat);
        v.setProperty(LON, lon);
        return tmp;
    }

    public double getLatitude(int index) {
        return (Double) g.getVertex(index).getProperty(LAT);
    }

    public double getLongitude(int index) {
        return (Double) g.getVertex(index).getProperty(LON);
    }

    public void edge(int a, int b, double distance, boolean bothDirections) {
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

    public MyIteratorable<EdgeWithFlags> getEdges(int index) {
        return new MyTinkerIterable(g.getVertex(index));
    }

    public MyIteratorable<EdgeWithFlags> getIncoming(int index) {
        return new MyTinkerIterable(g.getVertex(index));
    }

    public MyIteratorable<EdgeWithFlags> getOutgoing(int index) {
        return new MyTinkerIterable(g.getVertex(index));
    }

    static class MyTinkerIterable extends MyIteratorable<EdgeWithFlags> {

        private Iterator<Edge> iter;
        private Vertex node;

        public MyTinkerIterable(Vertex n) {
            if (n == null)
                throw new IllegalArgumentException("Node does not exist");

            node = n;
            iter = n.getEdges(Direction.BOTH, WAY).iterator();
        }

        public boolean hasNext() {
            return iter.hasNext();
        }

        public EdgeWithFlags next() {
            Edge e = iter.next();
            Vertex other = getOtherNode(e, node);
            double dist = (Double) e.getProperty(DISTANCE);
            return new EdgeWithFlags(Integer.parseInt((String) other.getId()), dist, (byte) 3);
        }

        public void remove() {
            throw new UnsupportedOperationException("Not supported yet.");
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

    public boolean markDeleted(int index) {
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
            int delta = a - getLocations() + 1;
            if (delta == 0)
                throw new IllegalStateException("Couldn't found node with id " + a);

            for (int i = 0; i < delta; i++) {
                v = createNode();
            }
        }
        return v;
    }
}
