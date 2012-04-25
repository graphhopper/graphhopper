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
package de.jetsli.graph.trees;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich, info@jetsli.de
 */
public class QuadTreeSimpleTest extends QuadTreeTester {

    @Override
    protected QuadTree createQuadTree(int items) {
        return new QuadTreeSimple().init(items);
    }

    @Test
    public void testNodePutNull() {
        try {
            createQuadTree(10).add(10, 10, null);
            assertTrue("an exception should be thrown on 'storing null' as we rely on this in datanode", false);
        } catch (Exception ex) {
        }
    }

    @Test
    public void testAddDuplicates() {
        QTDataNode<String> dn = new QTDataNode<String>(4);
        dn.add(1, "test1");
        assertEquals(1, dn.count());
        dn.add(1, "test5");
        dn.add(1, "test2");
        assertEquals(3, dn.count());
        QuadTree<Integer> qt = createQuadTree(100);
        qt.add(1, 2, 10);
    }

    @Test
    public void testNodeAdd() {
        QTDataNode<String> dn = new QTDataNode<String>(2);
        assertFalse(dn.add(1, "test1"));
        assertFalse(dn.add(5, "test5"));
        assertTrue(dn.add(2, "test2"));
    }

    @Test
    public void testNodeRemove() {
        QTDataNode<String> dn = new QTDataNode<String>(4);
        dn.add(1, "test1");
        dn.add(5, "test5");
        dn.add(2, "test2");
        dn.add(3, "test3");

        assertEquals(1, dn.remove(3));
        assertEquals(1, dn.remove(5));

        assertNull(dn.getValue(5));
        assertNull(dn.getValue(3));
        assertEquals("test1", dn.getValue(1));
        assertEquals("test2", dn.getValue(2));
    }
}
