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
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.Sidewalk;
import com.graphhopper.storage.IntsRef;

/**
 * Parses the sidewalk presence from sidewalk, sidewalk:left, sidewalk:right and sidewalk:both OSM tags.
 * The main sidewalk tag encodes direction as its value (left, right, both), while the directional
 * keys encode presence as their value (yes, no, separate).
 *
 * @see <a href="https://wiki.openstreetmap.org/wiki/Key:sidewalk">Key:sidewalk</a>
 */
public class OSMSidewalkParser implements TagParser {

    private final EnumEncodedValue<Sidewalk> sidewalkEnc;

    public OSMSidewalkParser(EnumEncodedValue<Sidewalk> sidewalkEnc) {
        this.sidewalkEnc = sidewalkEnc;
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay readerWay, IntsRef relationFlags) {
        Sidewalk bestFwd = Sidewalk.MISSING;
        Sidewalk bestRev = Sidewalk.MISSING;

        Sidewalk both = Sidewalk.find(readerWay.getTag("sidewalk:both"));
        if (both != Sidewalk.MISSING) {
            bestFwd = both;
            bestRev = both;
        }

        Sidewalk right = Sidewalk.find(readerWay.getTag("sidewalk:right"));
        if (right != Sidewalk.MISSING)
            bestFwd = right;

        Sidewalk left = Sidewalk.find(readerWay.getTag("sidewalk:left"));
        if (left != Sidewalk.MISSING)
            bestRev = left;

        // sidewalk, now the direction is encoded in the value
        String main = readerWay.getTag("sidewalk");
        if (main != null) {
            switch (main) {
                case "both", "yes":
                    if (bestFwd == Sidewalk.MISSING) bestFwd = Sidewalk.YES;
                    if (bestRev == Sidewalk.MISSING) bestRev = Sidewalk.YES;
                    break;
                case "right":
                    if (bestFwd == Sidewalk.MISSING) bestFwd = Sidewalk.YES;
                    break;
                case "left":
                    if (bestRev == Sidewalk.MISSING) bestRev = Sidewalk.YES;
                    break;
                case "no", "none":
                    if (bestFwd == Sidewalk.MISSING) bestFwd = Sidewalk.NO;
                    if (bestRev == Sidewalk.MISSING) bestRev = Sidewalk.NO;
                    break;
                case "separate":
                    if (bestFwd == Sidewalk.MISSING) bestFwd = Sidewalk.SEPARATE;
                    if (bestRev == Sidewalk.MISSING) bestRev = Sidewalk.SEPARATE;
                    break;
            }
        }

        sidewalkEnc.setEnum(false, edgeId, edgeIntAccess, bestFwd);
        sidewalkEnc.setEnum(true, edgeId, edgeIntAccess, bestRev);
    }
}
