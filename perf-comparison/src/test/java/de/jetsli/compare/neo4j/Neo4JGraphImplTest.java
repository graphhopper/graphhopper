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

import de.jetsli.graph.storage.Graph;
import org.junit.After;

/**
 *
 * @author Peter Karich
 */
public class Neo4JGraphImplTest extends AbstractGraphTester {

    private Neo4JGraphImpl g;
    
    @Override
    Graph createGraph(int size) {
        g = new Neo4JGraphImpl(null).setBulkSize(1);
        g.init(true);
        return g;
    }
    
    @After
    public void tearDown() {
        g.close();
    }
}
