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
package de.jetsli.compare.neo4j;

import de.jetsli.graph.routing.util.CarStreetType;
import de.jetsli.graph.storage.Graph;
import de.jetsli.graph.util.EdgeIterator;
import de.jetsli.graph.util.Helper;
import java.io.File;
import java.util.Iterator;
import java.util.Random;
import org.neo4j.graphdb.*;
import org.neo4j.kernel.EmbeddedGraphDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Peter Karich
 */
public class Neo4JGraphImpl implements Graph {

    private static final String LAT = "_lat";
    private static final String LON = "_lon";
    private static final String DISTANCE = "distance";
    private static final String NODES = "nodeCount";
    private static final Logger logger = LoggerFactory.getLogger(Neo4JStorage.class);
    private final BulkTA ta = new BulkTA();
    private GraphDatabaseService graphDb;
    private boolean temporary;
    private File storeDir;
    private int bulkSize = 10000;
    // TODO uh this is complicated (they should code it like lumeo!) ... so, we need to use bulk mode for lucene as well:
    // https://github.com/neo4j/community/blob/master/lucene-index/src/test/java/examples/ImdbExampleTest.java#L601
    //

    public Neo4JGraphImpl(String storeDir) {
        if (storeDir != null) {
            temporary = false;
            this.storeDir = new File(storeDir);
        } else {
            temporary = true;
            this.storeDir = new File("neo4j." + new Random().nextLong() + ".db");
        }
    }

    public Neo4JGraphImpl setBulkSize(int bulkSize) {
        this.bulkSize = bulkSize;
        return this;
    }

    public boolean init(boolean forceCreateNew) {
        if (!forceCreateNew && !storeDir.exists())
            return false;

        try {
            graphDb = new EmbeddedGraphDatabase(storeDir.getAbsolutePath());
            ta.ensureStart();
            try {
                if (!graphDb.getReferenceNode().hasProperty(NODES))
                    graphDb.getReferenceNode().setProperty(NODES, 0);
            } finally {
                ta.ensureEnd();
            }
            if (!temporary)
                Runtime.getRuntime().addShutdownHook(new Thread() {
                    @Override
                    public void run() {
                        try {
                            close();
                        } catch (Exception ex) {
                            logger.error("problem while closing neo4j graph db", ex);
                        }
                    }
                });
            logger.info("initialized neo4j graph at:" + storeDir);
            return true;
        } catch (Exception ex) {
            logger.error("problem while initialization", ex);
            return false;
        }
    }

    public void close() {
        ta.forceEnd();
        graphDb.shutdown();
        if (temporary)
            Helper.deleteDir(storeDir);
    }

    void flush() {
        ta.forceEnd();
    }

    // TODO should be static and graph should then be a weak ref
    class BulkTA {

        int index;
        Transaction ta;

        public BulkTA ensureStart() {
            if (ta == null)
                ta = graphDb.beginTx();

            index++;
            return this;
        }

        public BulkTA ensureEnd() {
            assert (ta != null);
            if (index >= bulkSize)
                forceEnd();
            return this;
        }

        public BulkTA forceEnd() {
            index = 0;
            if (ta != null) {
                ta.success();
                ta.finish();
                ta = null;
            }
            return this;
        }
    }

    enum MyRelations implements RelationshipType {

        WAY
    }

    public void ensureCapacity(int cap) {
    }

    public int getNodes() {
        return (Integer) graphDb.getReferenceNode().getProperty(NODES);
    }

    private static int getOurId(Node n) {
        return (int) n.getId() - 1;
    }

    public void setNode(int index, double lat, double lon) {
        ta.ensureStart();
        try {
            Node n = createNode();
            n.setProperty(LAT, lat);
            n.setProperty(LON, lon);
        } finally {
            ta.ensureEnd();
        }
    }

    public double getLatitude(int ghId) {
        ta.ensureStart();
        try {
            return (Double) getNode(ghId).getProperty(LAT);
        } finally {
            ta.ensureEnd();
        }
    }

    public double getLongitude(int ghId) {
        ta.ensureStart();
        try {
            return (Double) getNode(ghId).getProperty(LON);
        } finally {
            ta.ensureEnd();
        }
    }

    private Node ensureNodeWithId(int ghId) {
        try {
            return getNode(ghId);
        } catch (NotFoundException ex) {
            int delta = ghId - getNodes() + 1;
            if (delta == 0)
                throw new IllegalStateException("Couldn't found node with id " + ghId);
            Node lastN = null;
            for (int i = 0; i < delta; i++) {
                lastN = createNode();
            }
            return lastN;
        }
    }

    private Node createNode() {
        Node n = graphDb.createNode();
        graphDb.getReferenceNode().setProperty(NODES, getNodes() + 1);
        return n;
    }

    public void edge(int a, int b, double distance, boolean ignore_bothDirections) {
        edge(a, b, distance, CarStreetType.flagsDefault(ignore_bothDirections));
    }

    public void edge(int a, int b, double distance, int flags) {
        ta.ensureStart();
        try {
            Node from = ensureNodeWithId(a);
            Node to = ensureNodeWithId(b);
            Iterator<Relationship> iter = from.getRelationships(MyRelations.WAY).iterator();
            Relationship r = null;
            while (iter.hasNext()) {
                r = iter.next();
                if (to.equals(r.getOtherNode(from)))
                    break;

                r = null;
            }
            if (r == null)
                r = from.createRelationshipTo(to, MyRelations.WAY);
            r.setProperty(DISTANCE, distance);
        } finally {
            ta.ensureEnd();
        }
    }

    static class MyNeoIterable implements EdgeIterator {

        private Iterator<Relationship> iter;
        // TODO should be a weak reference!
        private BulkTA ta;
        private Node node;
        private int fromNode;
        //
        int id;
        double dist;
        int flags = 3;

        public MyNeoIterable(BulkTA ta, int fromId, Node n) {
            if (n == null)
                throw new IllegalArgumentException("Node does not exist");

            this.fromNode = fromId;
            this.node = n;
            this.ta = ta;
            this.ta.ensureStart();
            this.iter = n.getRelationships(MyRelations.WAY).iterator();
        }

        public boolean hasNext() {
            boolean ret = iter.hasNext();
            if (ret)
                return true;

            // TODO use one TA per 'next' request
            ta.ensureEnd();
            return false;
        }

        public boolean next() {
            if (!hasNext())
                return false;

            Relationship r = iter.next();
            // this is a hack and probably relies on the fact that we don't delete anything
            // but it gives us some speed improvements as inbuilt getOtherNode is using a concurrenthashmap (!?)
            // so dont do Node other = r.getOtherNode(node); and do this:
            Node other = r.getStartNode();
            if (other.getId() == node.getId())
                other = r.getEndNode();
            id = getOurId(other);
            dist = (Double) r.getProperty(DISTANCE);
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

    public EdgeIterator getEdges(int ghId) {
        return new MyNeoIterable(ta, ghId, getNode(ghId));
    }

    public EdgeIterator getIncoming(int ghId) {
        return new MyNeoIterable(ta, ghId, getNode(ghId));
    }

    public EdgeIterator getOutgoing(int ghId) {
        return new MyNeoIterable(ta, ghId, getNode(ghId));
    }

    protected Node getNode(int ghId) {
        return graphDb.getNodeById(ghId + 1);
    }

    public Graph clone() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void markNodeDeleted(int index) {
    }

    public boolean isDeleted(int index) {
        return true;
    }

    public void optimize() {
    }
}
