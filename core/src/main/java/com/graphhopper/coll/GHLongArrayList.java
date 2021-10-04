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

import com.carrotsearch.hppc.LongArrayList;

/**
 * @author Andrzej Oles
 */
public class GHLongArrayList extends LongArrayList {
    public GHLongArrayList() {
        super(10);
    }

    public GHLongArrayList(int capacity) {
        super(capacity);
    }

    public GHLongArrayList(GHLongArrayList list) {
        super(list);
    }

    public final GHLongArrayList reverse() {
        final long[] buffer = this.buffer;
        long tmp;
        for (int start = 0, end = size() - 1; start < end; start++, end--) {
            // swap the values
            tmp = buffer[start];
            buffer[start] = buffer[end];
            buffer[end] = tmp;
        }
        return this;
    }
}
