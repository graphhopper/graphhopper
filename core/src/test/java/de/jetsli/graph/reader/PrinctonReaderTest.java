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

import de.jetsli.graph.storage.Graph;
import static de.jetsli.graph.util.MyIteratorable.*;
import de.jetsli.graph.storage.MemoryGraphSafe;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich, info@jetsli.de
 */
public class PrinctonReaderTest {

    @Test
    public void testRead() {
        Graph graph = new MemoryGraphSafe(100);
        new PrinctonReader(graph).setStream(PrinctonReader.class.getResourceAsStream("tinyEWD.txt")).read();
        assertEquals(8, graph.getNodes());
        assertEquals(2, count(graph.getOutgoing(0)));
        assertEquals(3, count(graph.getOutgoing(6)));
    }
    
    @Test
    public void testMediumRead() throws IOException {
        Graph graph = new MemoryGraphSafe(100);
        new PrinctonReader(graph).setStream(new GZIPInputStream(PrinctonReader.class.getResourceAsStream("mediumEWD.txt.gz"))).read();
        assertEquals(250, graph.getNodes());
        assertEquals(13, count(graph.getOutgoing(244)));
        assertEquals(11, count(graph.getOutgoing(16)));
    }
}
