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
package de.jetsli.graph.routing;

import de.jetsli.graph.routing.Path;
import de.jetsli.graph.storage.Edge;
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
        Edge entryC = new Edge(-1, 1.5f);
        Edge entryB = entryC.prevEntry = new Edge(-1, 0.5f);
        Edge entryA = entryB.prevEntry = new Edge(-1, 0);

        Edge entryD = new Edge(-1, 2.7f);
        Edge entryE = entryD.prevEntry = new Edge(-1, 2.5f);
        Edge entryF = entryE.prevEntry = new Edge(-1, 0);

        PathWrapperRef wrapper = new PathWrapperRef();
        wrapper.edgeFrom = entryC;
        wrapper.edgeTo = entryD;

        Path path = wrapper.extract();        
        assertEquals(4.2, path.distance(), 1e-5);
        assertEquals(5, path.locations());
    }
}
