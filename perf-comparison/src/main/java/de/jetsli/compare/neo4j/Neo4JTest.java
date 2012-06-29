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

import de.jetsli.graph.reader.OSMReader;
import de.jetsli.graph.storage.Storage;
import de.jetsli.graph.storage.Graph;
import de.jetsli.graph.util.CmdArgs;
import de.jetsli.graph.util.Helper;
import java.io.FileNotFoundException;

/**
 * @author Peter Karich
 */
public class Neo4JTest {

    public static void main(String[] args) throws Exception {
        new Neo4JTest().start(Helper.readCmdArgs(args));
    }

    public void start(CmdArgs readCmdArgs) throws FileNotFoundException {
        OSMReader reader = new OSMReader(readCmdArgs.get("neo4jlocation", null),
                readCmdArgs.getInt("size", 5000000)) {

            @Override
            protected Storage createStorage(String storageLocation, int size) {
                return new Neo4JStorage(storageLocation, size);
            }
        };
        Graph g = OSMReader.osm2Graph(reader, readCmdArgs);
    }
}
