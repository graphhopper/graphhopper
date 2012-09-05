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
package de.jetsli.compare.misc;

import de.jetsli.compare.neo4j.Neo4JStorage;
import de.jetsli.graph.reader.OSMReader;
import de.jetsli.graph.routing.util.RoutingAlgorithmIntegrationTests;
import de.jetsli.graph.storage.Storage;
import de.jetsli.graph.storage.Graph;
import de.jetsli.graph.util.CmdArgs;
import de.jetsli.graph.util.Helper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Peter Karich
 */
public class StartWithExternalStorage {

    public static void main(String[] args) throws Exception {
        new StartWithExternalStorage().start(Helper.readCmdArgs(args));
    }
    private Logger logger = LoggerFactory.getLogger(getClass());

    public void start(CmdArgs args) throws Exception {
        int initSize = args.getInt("size", 5000000);
        // TODO subnetworks are not deleted and so for 5 routing queries all nodes are traversed
        // (but could be even good to warm caches ;))
        final Storage s = new Neo4JStorage(args.get("storage", "neo4j.db"), initSize);
//        final Storage s = new TinkerStorage(readCmdArgs.get("storage", "tinker.db"), initSize);
        OSMReader reader = new OSMReader(null, initSize) {
            @Override protected Storage createStorage(String storageLocation, int size) {
                return s;
            }
        };
        Graph g = OSMReader.osm2Graph(reader, args);
        logger.info("finished with locations:" + g.getNodes() + " now warm up ...");
        // warm up caches:
        RoutingAlgorithmIntegrationTests tester = new RoutingAlgorithmIntegrationTests(g);
        String algo = args.get("algo", "dijkstra");
        tester.runShortestPathPerf(50, algo);

        logger.info(".. and go!");
        tester.runShortestPathPerf(200, algo);
    }
}
