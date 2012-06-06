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
import java.util.Iterator;
import org.junit.Test;
import static org.junit.Assert.*;
import static de.jetsli.graph.util.MyIteratorable.*;
import de.jetsli.graph.storage.Graph;

/**
 *
 * @author Peter Karich, info@jetsli.de
 */
public abstract class AbstractOSMReaderTester {
    
    protected abstract OSMReader createOSM();
    
    @Test public void testMain() {        
        OSMReader o = createOSM();
        o.writeOsm2Binary(getClass().getResourceAsStream("test1.xml"));
        Graph graph = o.readGraph();        
        assertEquals(5, graph.getLocations());
        assertEquals(1, count(graph.getOutgoing(0)));
        assertEquals(3, count(graph.getOutgoing(1)));
        assertEquals(1, count(graph.getOutgoing(2)));

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
