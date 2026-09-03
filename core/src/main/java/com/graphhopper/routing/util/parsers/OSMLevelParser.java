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
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.storage.IntsRef;

/**
 * https://wiki.openstreetmap.org/wiki/Key:level
 */
public class OSMLevelParser implements TagParser {
    private final DecimalEncodedValue levelEnc;

    public OSMLevelParser(DecimalEncodedValue levelEnc) {
        this.levelEnc = levelEnc;
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way, IntsRef relationFlags) {
        float level = 0;

        if (way.hasTag("level")) {
            String levels = way.getTag("level");
            String[] levelsTok = levels.split(";|-");

            // TODO
            if (levelsTok.length == 1) {
                try {
                    level = Float.parseFloat(levelsTok[0]);
                } catch (NumberFormatException ex) {
                    // ignore if no number
                }
            } else if (levelsTok.length == 2) {
                try {
                    float first = Float.parseFloat(levelsTok[0]);
                    float second = Float.parseFloat(levelsTok[1]);
                    level = (first + second)/2;
                } catch (NumberFormatException ex) {
                    // ignore if no number
                }
            }
        }
        levelEnc.setDecimal(false, edgeId, edgeIntAccess, level);
    }
}
