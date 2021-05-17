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

import static com.graphhopper.Junit4To5Assertions.assertEquals;

/**
 * @author Peter Karich
 */
public class GHTBitSetTest extends AbstractMyBitSetTest {
    @Override
    public GHBitSet createBitSet(int no) {
        return new GHTBitSet(no);
    }

    @Override
    public void testNext() {
        // not supported (yet) -> due to sorting
    }

    @Override
    public void testToString() {
        // unsorted output!
        GHBitSet bs = createBitSet(100);
        bs.add(12);
        bs.add(1);
        assertEquals("[1, 12]", bs.toString());
    }
}
