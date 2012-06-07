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
import de.jetsli.graph.storage.Graph;
import de.jetsli.graph.util.Helper;
import de.jetsli.graph.util.MyIteratorable;
import java.io.File;
import java.util.Iterator;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich, info@jetsli.de
 */
public class OSMReaderRoutingTest {

    private String dir = "/tmp/OSMReaderTrialsTest";
    private OSMReaderRouting reader;

    protected OSMReaderRouting createOSM() {
        Helper.deleteDir(new File(dir));
        new File(dir).mkdirs();

        OSMReaderRouting osm = new OSMReaderRouting(dir + "/test-db", 1000);
        return reader = osm;
    }

    @After
    public void tearDown() {
        reader.close();
        Helper.deleteDir(new File(dir));
    }

    @Test public void testMain() {
        OSMReaderRouting o = createOSM();
        o.acceptHighwaysOnly(getClass().getResourceAsStream("test1.xml"));
        o.writeOsm2Binary(getClass().getResourceAsStream("test1.xml"));
        Graph graph = o.readGraph();
        assertEquals(4, graph.getLocations());
        assertEquals(1, MyIteratorable.count(graph.getOutgoing(0)));
        assertEquals(3, MyIteratorable.count(graph.getOutgoing(1)));
        assertEquals(1, MyIteratorable.count(graph.getOutgoing(2)));

        Iterator<DistEntry> iter = graph.getOutgoing(1).iterator();
        DistEntry locNextEntry = iter.next();
        assertEquals(0, locNextEntry.node);
        assertEquals(88.643, locNextEntry.distance, 1e-3);
        locNextEntry = iter.next();
        assertEquals(2, locNextEntry.node);
        assertEquals(93.146888, locNextEntry.distance, 1e-3);

        // get third added location => 2
        iter = graph.getOutgoing(2).iterator();
        locNextEntry = iter.next();
        assertEquals(1, locNextEntry.node);
        assertEquals(93.146888, locNextEntry.distance, 1e-3);
    }
}
