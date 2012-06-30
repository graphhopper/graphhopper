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

import de.jetsli.graph.storage.DefaultStorage;

/**
 * Neo4j is great and uses very few RAM when importing. It is also fast (2 mio nodes / min)
 *
 * But it (wrongly?) uses lucene necessray for node lookup which makes it slow (500k nodes / min)
 *
 * @author Peter Karich, info@jetsli.de
 */
public class Neo4JStorage extends DefaultStorage {

    public Neo4JStorage() {
        this(null, 5000000);
    }

    public Neo4JStorage(String storeDir, int size) {
        super(size);
        g = new Neo4JGraphImpl(storeDir);
    }

    @Override
    public boolean loadExisting() {
        return init(false);
    }

    @Override
    public void createNew() {
        init(true);
    }

    public boolean init(boolean forceCreate) {
        return getNeoGraph().init(forceCreate);
    }
    
    private Neo4JGraphImpl getNeoGraph() {
        return (Neo4JGraphImpl)g;
    }

    @Override
    public void close() {
        getNeoGraph().close();
        super.close();
    }

    @Override
    public void flush() {
        getNeoGraph().flush();
    }
}
