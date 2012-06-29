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

import de.jetsli.graph.storage.EdgeWithFlags;
import de.jetsli.graph.storage.Graph;
import de.jetsli.graph.util.Helper;
import de.jetsli.graph.util.MyIteratorable;
import java.io.File;
import java.util.Iterator;
import java.util.Random;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.index.IndexManager;
import org.neo4j.kernel.EmbeddedGraphDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Peter Karich
 */
public class Neo4JGraphImpl implements Graph {

    private static final String ID = "_id";
    private static final String LAT = "_lat";
    private static final String LON = "_lon";
    private static final String DISTANCE = "distance";
    private static final Logger logger = LoggerFactory.getLogger(Neo4JStorage.class);
    private final BulkTA ta = new BulkTA();
    private GraphDatabaseService graphDb;
    private boolean temporary;
    private File storeDir;
    private Index<Node> idIndex;
    private int locCounter = 0;
    private int bulkSize = 20000;
    // TODO uh this is complicated (they should code it like lumeo!) ... but we need to use bulk mode for lucene as well:
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
            IndexManager index = graphDb.index();
            idIndex = index.forNodes(ID);
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
            ta.success();
            ta.finish();
            ta = null;
            return this;
        }
    }

    enum MyRelations implements RelationshipType {

        WAY
    }

    public void ensureCapacity(int cap) {
    }

    public int getLocations() {
        return locCounter;
    }

    public int addLocation(double lat, double lon) {
        ta.ensureStart();
        try {
            int tmp = locCounter;
            Node n = createNode(locCounter);
            n.setProperty(LAT, lat);
            n.setProperty(LON, lon);
            return tmp;
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

    private Node ensureExistence(int ghId) {
        Node n = getNode(ghId);
        if (n == null)
            return createNode(ghId);
        return n;
    }

    private Node createNode(int id) {
        Node n = graphDb.createNode();
        n.setProperty(ID, id);
        // internal ID via getId is different to our locCounter! 
        // actually it is: internalId - 1 == locCounter
        idIndex.add(n, ID, id);
        if (id >= locCounter)
            locCounter = id + 1;
        return n;
    }

    public void edge(int a, int b, double distance, boolean ignore_bothDirections) {
        ta.ensureStart();
        try {
            Node from = ensureExistence(a);
            Node to = ensureExistence(b);
            Relationship r = from.createRelationshipTo(to, MyRelations.WAY);
            r.setProperty(DISTANCE, distance);
        } finally {
            ta.ensureEnd();
        }
    }

    static class MyNeoIterable extends MyIteratorable<EdgeWithFlags> {

        private Iterator<Relationship> iter;
        // TODO should be a weak reference!
        private BulkTA ta;

        public MyNeoIterable(BulkTA ta, Node n) {
            if (n == null)
                throw new IllegalArgumentException("Node does not exist");
            this.ta = ta;
            this.ta.ensureStart();
            iter = n.getRelationships(MyRelations.WAY).iterator();
        }

        public boolean hasNext() {
            boolean ret = iter.hasNext();
            if (ret)
                return true;

            // uh this is ugly ...
            ta.ensureEnd();
            return false;
        }

        public EdgeWithFlags next() {
            Relationship r = iter.next();
            int id = (Integer) r.getEndNode().getProperty(ID);
            double dist = (Double) r.getProperty(DISTANCE);
            return new EdgeWithFlags(id, dist, (byte) 3);
        }

        public void remove() {
            throw new UnsupportedOperationException("Not supported yet.");
        }
    }

    public MyIteratorable<EdgeWithFlags> getEdges(int ghId) {
        return new MyNeoIterable(ta, getNode(ghId));
    }

    public MyIteratorable<EdgeWithFlags> getIncoming(int ghId) {
        return new MyNeoIterable(ta, getNode(ghId));
    }

    public MyIteratorable<EdgeWithFlags> getOutgoing(int ghId) {
        return new MyNeoIterable(ta, getNode(ghId));
    }

    protected Node getNode(int ghId) {
        return idIndex.get(ID, ghId).getSingle();
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
}
