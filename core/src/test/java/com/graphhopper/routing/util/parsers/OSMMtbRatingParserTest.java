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

package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.storage.BytesRef;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OSMMtbRatingParserTest {

    @Test
    void test() {
        // 0 means missing (or invalid)
        checkRating(0, null);
        // rating=scale+1
        checkRating(1, "0");
        checkRating(6, "5");
        checkRating(7, "6");
        // we ignore + and - for now
        checkRating(2, "1+");
        checkRating(3, "2-");
        // negative
        checkRating(0, "-1");
        // otherwise invalid
        checkRating(0, "S1");
        // too large value
        checkRating(0, "8");
        // also 'too large' even though it probably means "S1/2" etc. -> still ignored
        checkRating(0, "12");
        checkRating(0, "23");
        // ... similar here
        checkRating(0, "1-2");
        checkRating(0, "0;1");
        checkRating(0, "1;2");
    }

    private void checkRating(int expectedRating, String scaleString) {
        IntEncodedValue ev = MtbRating.create();
        ev.init(new EncodedValue.InitializerConfig());
        OSMMtbRatingParser parser = new OSMMtbRatingParser(ev);
        EdgeBytesAccess edgeAccess = new EdgeBytesAccessArray(4);
        int edgeId = 0;
        ReaderWay way = new ReaderWay(0);
        if (scaleString != null)
            way.setTag("mtb:scale", scaleString);
        parser.handleWayTags(edgeId, edgeAccess, way, new BytesRef(8));
        assertEquals(expectedRating, ev.getInt(false, edgeId, edgeAccess), "unexpected rating for mtb:scale=" + scaleString);
    }

}
