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
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.ev.IntEncodedValue;
import com.graphhopper.storage.IntsRef;

/**
 * Parses the mountain biking difficulty.
 * Note that the tag mtb:scale=0 corresponds to mtb_rating=1 because mtb_rating=0 is the default,
 * i.e. mtb_rating goes until 7.
 *
 * @see <a href="https://wiki.openstreetmap.org/wiki/Key:mtb:scale">Key:mtb:scale</a> for details on OSM mountain biking difficulties.
 * @see <a href="http://www.singletrail-skala.de/">Single Trail Scale</a>
 */
public class OSMMtbRatingParser implements TagParser {
    private final IntEncodedValue mtbRatingEnc;

    public OSMMtbRatingParser(IntEncodedValue mtbRatingEnc) {
        this.mtbRatingEnc = mtbRatingEnc;
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay readerWay, IntsRef relationFlags) {
        String scale = readerWay.getTag("mtb:scale");
        int rating = 0;
        if (scale != null) {
            if (scale.length() == 2 && (scale.charAt(1) == '+' || scale.charAt(1) == '-'))
                scale = scale.substring(0, 1);
            try {
                int scaleAsInt = Integer.parseInt(scale);
                rating = scaleAsInt + 1;
            } catch (Exception ex) {
            }
        }
        if (rating > 0 && rating < 8)
            mtbRatingEnc.setInt(false, edgeId, edgeIntAccess, rating);
    }
}
