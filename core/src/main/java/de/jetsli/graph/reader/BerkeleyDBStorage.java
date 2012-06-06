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

import com.sleepycat.bind.tuple.IntegerBinding;
import com.sleepycat.bind.tuple.TupleOutput;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.Environment;
import java.io.File;

/**
 * A ... hmmh ... cannot get it working too lazy now :/ Also we should prefer C over java edition
 * ...
 *
 * The nice thing is that Berkeley DB seems to use:
 * http://blog.aggregateknowledge.com/tag/linear-hashing/
 *
 * (Extended) Linear Hashing: http://pedia.directededge.com/article/Linear_hash#cite_note-0
 * http://pages.cpsc.ucalgary.ca/~verwaal/335/W2004/handouts/litwin.linear-hashing.pdf
 *
 *
 * @author Peter Karich, info@jetsli.de
 */
public class BerkeleyDBStorage implements Storage {

    private DatabaseConfig dbConfig = new DatabaseConfig();
    private File location;
    private Database db;

    public BerkeleyDBStorage() {
        dbConfig.setTemporary(true);
    }

    public BerkeleyDBStorage(String location) {
        this.location = new File(location);
    }

    @Override
    public BerkeleyDBStorage init(boolean forceCreate) throws Exception {
        // Type(DatabaseType.HASH);
        dbConfig.setAllowCreate(true);
        dbConfig.setReadOnly(false);
        dbConfig.setTransactional(false);

        // dbConfig.setSortedDuplicates(false);
        Environment env = new Environment(location, null);
        db = env.openDatabase(null, "mydatabase", dbConfig);
        return this;
    }
    byte[] integ = new byte[4];
    byte[] floats = new byte[8];
    IntegerBinding iBinding = new IntegerBinding();

    @Override
    public boolean addNode(int osmId, double lat, double lon) {
        //PackedInteger.writeInt(integ, 0, osmId);   
        DatabaseEntry de = new DatabaseEntry();
        iBinding.objectToEntry(osmId, de);

        TupleOutput t = new TupleOutput();
        t.writeDouble(lat);
        t.writeDouble(lon);
        db.put(null, de, new DatabaseEntry(t.getBufferBytes()));
        return true;
    }

    @Override
    public boolean addEdge(int nodeIdFrom, int nodeIdTo, boolean reverse, CalcDistance callback) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void close() throws Exception {
        // db.sync();
        db.close();
    }

    @Override
    public void stats() {        
    }

    @Override
    public void flush() {        
    }

    @Override
    public int getNodes() {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
