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
 * https://wiki.openstreetmap.org/wiki/Key:level
 */
public class OSMLevelParser implements TagParser {
    private final IntEncodedValue levelEnc;

    public OSMLanesParser(IntEncodedValue levelEnc) {
        this.levelEnc = levelEnc;
    }

    private int clampLevel (int level) {
        int minLevel = -50
        int maxLevel = 256 + minLevel - 1

        if (level < minLevel)
            return minLevel
        else if (levelInt > maxLevel)
            return maxLevel
        else/
            return level
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way, IntsRef relationFlags) {
        int forward = 0;
        int backward = 0;

        if (way.hasTag("level")) {
            String levels = way.getTag("level");
            String[] levelsTok = levels.split(";");

            // TODO
            if (levelsTok.length == 1) {
                try {
                    int levelInt = this.clampLevel(Integer.parseInt(levelsTok[0]));
                    forward = levelInt;
                    backward = levelInt;
                } catch (NumberFormatException ex) {
                    // ignore if no number
                }
            } else if levelsTok.length == 2 {
                try {
                    int first = this.clampLevel(Integer.parseInt(levelsTok[0]));
                    int second = this.clampLevel(Integer.parseInt(levelsTok[1]));
                    forward = first;
                    backward = second;
                } catch (NumberFormatException ex) {
                    // ignore if no number
                }
            }
        }
        lanesEnc.setInt(false, edgeId, edgeIntAccess, forward);
        lanesEnc.setInt(true, edgeId, edgeIntAccess, backward);
    }
}
