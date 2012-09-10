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

import de.jetsli.graph.util.Helper;
import java.io.File;
import org.junit.Before;

/**
 *
 * @author Peter Karich
 */
public class DiscGraphTest extends AbstractGraphTester {

    String FILE = "/tmp/discgraph";

    @Override
    Graph createGraph(int size) {
        return new DiscGraph(FILE, 100);
    }

    @Before
    public void setUp() {
        Helper.deleteDir(new File(FILE));
    }

    @Override
    public void testDeleteNodeForUnidir() {
        // TODO
    }

    @Override
    public void testDeleteNode() {
        // TODO
    }

    @Override
    public void testSimpleDelete() {
        // TODO
    }

    @Override
    public void testSimpleDelete2() {
        // TODO
    }

    @Override
    public void testClone() {
        // hmmh a bit ugly
        Helper.deleteDir(new File(FILE + "-clone"));
        super.testClone();
    }
}
