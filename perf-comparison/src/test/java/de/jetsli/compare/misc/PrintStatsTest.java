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

import de.jetsli.graph.geohash.SpatialHashtable;
import java.util.Random;
import org.junit.*;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public class PrintStatsTest {

    @Test
    public void testStatsNoError() {
        PrintStats.TmpHashtable tree = new PrintStats.TmpHashtable(10, 2);
        tree.init(10000);
        Random rand = new Random(12);
        for (int i = 0; i < 10000; i++) {
            tree.add(Math.abs(rand.nextDouble()), Math.abs(rand.nextDouble()), (long) i * 100);
        }
        tree.getEntries("e");
        tree.getOverflowEntries("o");
        tree.getOverflowOffset("oo");
    }
}
