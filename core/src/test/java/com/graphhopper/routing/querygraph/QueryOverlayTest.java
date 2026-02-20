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
package com.graphhopper.routing.querygraph;

import com.carrotsearch.hppc.LongArrayList;
import org.junit.jupiter.api.Test;

import java.util.*;

import static com.carrotsearch.hppc.LongArrayList.from;
import static org.junit.jupiter.api.Assertions.*;

public class QueryOverlayTest {

    @Test
    void adjustValues() {
        // no adjustment needed
        checkAdjustValues(from(3, 4, 3), 10, 1, from(3, 4, 3));
        // positive diff, add 1 to each
        checkAdjustValues(from(3, 3, 3), 12, 1, from(4, 4, 4));
        // negative diff, subtract 1 from each
        checkAdjustValues(from(5, 5, 5), 12, 1, from(4, 4, 4));
        // skip zero when subtracting
        checkAdjustValues(from(2, 0, 3), 3, 1, from(1, 0, 2));
        // target zero
        checkAdjustValues(from(1, 1, 1), 0, 1, from(0, 0, 0));
        // diff exceeds n, unchanged
        checkAdjustValues(from(1, 1, 1), 10, 1, from(1, 1, 1));
        // negative diff exceeds n, unchanged
        checkAdjustValues(from(4, 3, 3), 3, 1, from(4, 3, 3));
        // empty array
        checkAdjustValues(from(), 5, 1, from());
        // single value
        checkAdjustValues(from(5), 6, 1, from(6));
        // single value, diff exceeds n
        checkAdjustValues(from(5), 8, 1, from(5));
        // all zeros, positive target
        checkAdjustValues(from(0, 0, 0), 3, 1, from(1, 1, 1));
        // all zeros, target zero
        checkAdjustValues(from(0, 0, 0), 0, 1, from(0, 0, 0));
        // partial addition
        checkAdjustValues(from(3, 3, 4), 12, 1, from(4, 4, 4));
        // skip multiple zeros
        checkAdjustValues(from(0, 0, 4), 3, 1, from(0, 0, 3));
        // reduce to zero
        checkAdjustValues(from(1, 1), 1, 1, from(0, 1));

        // diff=+6 == 2*3, two full passes
        checkAdjustValues(from(3, 3, 4), 16, 2, from(5, 5, 6));
        // diff=7 > 2*3, unchanged
        checkAdjustValues(from(1, 1, 1), 10, 2, from(1, 1, 1));
        // diff=4, would exceed max=1 but fits max=2
        checkAdjustValues(from(1, 1, 1), 7, 2, from(3, 2, 2));
        // subtract evenly
        checkAdjustValues(from(4, 4, 4), 6, 2, from(2, 2, 2));
        // subtract skips zeros across passes
        checkAdjustValues(from(0, 3, 2), 1, 2, from(0, 1, 0));
        // single element, diff=2 fits
        checkAdjustValues(from(5), 7, 2, from(7));
        // single element, diff=3 exceeds 2*1
        checkAdjustValues(from(5), 8, 2, from(5));
        // partial second pass
        checkAdjustValues(from(3, 3, 3), 13, 2, from(5, 4, 4));
    }

    private void checkAdjustValues(LongArrayList values, long target, int maxPerElement, LongArrayList expectedValues) {
        QueryOverlay.adjustValues(values, target, maxPerElement);
        assertEquals(expectedValues, values);
    }
}
