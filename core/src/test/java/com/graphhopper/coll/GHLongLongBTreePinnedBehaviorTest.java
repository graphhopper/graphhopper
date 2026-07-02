/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.coll;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins behavior discovered during the Kotlin conversion (see KOTLIN_MIGRATION.md, rule 5b):
 * for an EVEN maxLeafEntries the constructor stores the given value (leaves split when reaching
 * it) while splitIndex/factor/initLeafSize are derived from the value incremented to odd. The
 * resulting tree shape for even values must not change silently.
 */
public class GHLongLongBTreePinnedBehaviorTest {

    @Test
    public void evenMaxLeafEntriesTreeShape() {
        GHLongLongBTree tree = new GHLongLongBTree(4, 8, -1);
        for (long i = 0; i < 100; i++) {
            tree.put(i * 3, i);
        }
        assertEquals(100, tree.getSize());
        // observed with the original java implementation: even maxLeafEntries=4 splits at 4
        // entries while splitIndex derives from 5 -> height 4 after 100 inserts
        assertEquals(4, tree.height());
        for (long i = 0; i < 100; i++) {
            assertEquals(i, tree.get(i * 3));
        }
    }
}
