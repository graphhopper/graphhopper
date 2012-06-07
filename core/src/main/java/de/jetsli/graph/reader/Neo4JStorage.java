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
package de.jetsli.graph.reader;

import de.jetsli.graph.storage.DistEntry;
import de.jetsli.graph.util.Helper;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.index.Index;
import org.neo4j.kernel.EmbeddedGraphDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Neo4j is great and uses very few RAM when importing. It is also fast (2 mio nodes / min)
 *
 * But it (wrongly?) uses lucene necessray for node lookup which makes it slow (500k nodes / min)
 *
 * @author Peter Karich, info@jetsli.de
 */
public class Neo4JStorage implements Storage {

    private static final String ID = "_id";
    private static final String DISTANCE = "distance";
    private static final Logger logger = LoggerFactory.getLogger(Neo4JStorage.class);

    @Override
    public void setHasEdges(int osmId) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean hasEdges(int osmId) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean loadExisting() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void createNew() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    enum MyRelations implements RelationshipType {

        WAY
    }
    private boolean temporary;
    private GraphDatabaseService graphDb;
    private Index<Node> locIndex;
    private String storeDir;

    public Neo4JStorage() {
        temporary = true;
        this.storeDir = "/tmp/neo4j." + new Random().nextLong() + ".db";
    }

    public Neo4JStorage(String storeDir) {
        temporary = false;
        this.storeDir = storeDir;
    }

    public Neo4JStorage init(boolean forceCreate) throws Exception {
        graphDb = new EmbeddedGraphDatabase(storeDir);
        locIndex = graphDb.index().forNodes("locations");

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

        return this;
    }
    int index;
    Transaction ta;

    @Override
    public boolean addNode(int osmId, double lat, double lon) {
        ensureTA();

        Node n = graphDb.createNode();
        // id necessary when grabbing relation info
        n.setProperty(ID, osmId);
        n.setProperty("lat", lat);
        n.setProperty("lon", lon);

        // id also necessary when doing look up
//        locIndex.add(n, ID, osmId);
        return true;
    }

    @Override
    public boolean addEdge(int nodeIdFrom, int nodeIdTo, boolean reverse, CalcDistance callback) {
        ensureTA();
        Node from = locIndex.get(ID, nodeIdFrom).getSingle();
        Node to = locIndex.get(ID, nodeIdTo).getSingle();
        Relationship r = from.createRelationshipTo(to, MyRelations.WAY);
        r.setProperty(DISTANCE, callback.calcDistKm(
                (Double) from.getProperty("lat"), (Double) from.getProperty("lon"),
                (Double) to.getProperty("lat"), (Double) to.getProperty("lon")));
        return true;
    }

    public List<DistEntry> getOutgoing(int node) {
        Node n = locIndex.get(ID, node).getSingle();
        ArrayList<DistEntry> list = new ArrayList<DistEntry>(2);
        for (Relationship rs : n.getRelationships(MyRelations.WAY)) {
            list.add(new DistEntry((Integer) rs.getEndNode().getProperty(ID), (Double) rs.getProperty(DISTANCE)));
        }

        return list;
    }

    @Override
    public void close() throws Exception {
        graphDb.shutdown();
        if (temporary)
            Helper.deleteDir(new File(storeDir));
    }

    private void ensureTA() {
        if (index++ % 20000 == 0) {
            if (ta != null) {
                ta.success();
                ta.finish();
            }

            ta = graphDb.beginTx();
        }
    }

    @Override public void stats() {
    }

    @Override public void flush() {
    }

    @Override public int getNodes() {
        throw new RuntimeException("not implemented");
    }
}
