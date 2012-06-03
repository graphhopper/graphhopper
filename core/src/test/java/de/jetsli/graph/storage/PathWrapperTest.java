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
package de.jetsli.graph.storage;

import de.jetsli.graph.dijkstra.DijkstraPath;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich, info@jetsli.de
 */
public class PathWrapperTest {

    @Test public void testExtract() {
        //
        // from-tree   to-tree
        //
        // a-b--c      d-e-f
        //  \ \         /  /
        //  ....       ....
        //
        // keep in mind:
        // c == d
        //                
        LinkedDistEntry entryC = new LinkedDistEntry(-1, 1.5f);
        LinkedDistEntry entryB = entryC.prevEntry = new LinkedDistEntry(-1, 0.5f);
        LinkedDistEntry entryA = entryB.prevEntry = new LinkedDistEntry(-1, 0);

        LinkedDistEntry entryD = new LinkedDistEntry(-1, 2.7f);
        LinkedDistEntry entryE = entryD.prevEntry = new LinkedDistEntry(-1, 2.5f);
        LinkedDistEntry entryF = entryE.prevEntry = new LinkedDistEntry(-1, 0);

        PathWrapper wrapper = new PathWrapper();
        wrapper.entryFrom = entryC;
        wrapper.entryTo = entryD;

        DijkstraPath path = wrapper.extract();        
        assertEquals(4.2, path.distance(), 1e-5);
        assertEquals(5, path.locations());
    }
}
