/**
 * Copyright (C) 2010 Peter Karich <info@jetsli.de>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.jetsli.graph.coll;

import static org.junit.Assert.*;
import java.util.List;
import java.util.Map.Entry;
import org.junit.Test;


/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class StringFreqMapTest {

    public StringFreqMapTest() {
    }

    @Test
    public void testGetSortedFreqLimit() {
        StringFreqMap map = new StringFreqMap();
        map.set("a", 2).set("c", 10).set("b", 20).set("d", 1).set("e", -1);
        List<Entry<String, Integer>> res = map.getSortedFreqLimit(0.1f);
        assertEquals(3, res.size());
        assertEquals("b", res.get(0).getKey());
        assertEquals("c", res.get(1).getKey());
        assertEquals("a", res.get(2).getKey());

        res = map.getSortedFreqLimit(0.05f);
        assertEquals(4, res.size());
        assertEquals("d", res.get(3).getKey());
    }

    @Test
    public void testAnd() {
        StringFreqMap map = new StringFreqMap();
        map.set("a", 2).set("c", 10).set("b", 20).set("d", 1).set("e", -1);
        StringFreqMap map2 = new StringFreqMap();
        map2.set("a", 2).set("f", 10);
        assertEquals(2, map2.andSize(map));
//        assertEquals("a", map2.and(map).iterator().next());
    }

    @Test
    public void testOr() {
        StringFreqMap map = new StringFreqMap();
        map.set("a", 2).set("c", 10).set("b", 20).set("d", 1).set("e", 2);
        StringFreqMap map2 = new StringFreqMap();
        map2.set("a", 2).set("f", 11);
        assertEquals(46, map2.orSize(map));
        assertTrue(map2.or(map).containsKey("f"));
    }

    @Test
    public void testInc() {
        StringFreqMap map = new StringFreqMap();
        map.set("a", 2).set("c", 10).set("b", 20).set("e", -1);
        StringFreqMap map2 = new StringFreqMap();
        map2.set("a", 2).set("e", 12).set("d", 5);

        assertEquals(5, map2.addOne2All(map).size());
        map2.containsKey("a");
        map2.containsKey("c");
        map2.containsValue(11);
        map2.containsValue(10);
        map2.containsValue(4);
    }
}
