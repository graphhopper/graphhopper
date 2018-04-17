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
package com.graphhopper.routing.profiles.tagparsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.profiles.*;
import com.graphhopper.storage.IntsRef;

import java.util.Map;


public class FootAverageSpeedParser implements TagParser {
    private final DecimalEncodedValue ev;
    private ReaderWayFilter acceptKnownRoadClasses = TagParserFactory.SPEEDMAPFILTER;
    private Map<String, Double> speedMap = TagParserFactory.getSpeedMap();

    public FootAverageSpeedParser(){
        // TODO Are these the correct speedbit values?
        this.ev = new DecimalEncodedValue(TagParserFactory.FOOT_AVERAGE_SPEED, 5, 0, 5, false);
    }

    public FootAverageSpeedParser(DecimalEncodedValue ev){this.ev = ev;}

    public void parse(IntsRef ints, ReaderWay way) {
        String sacScale = way.getTag("sac_scale");
        if (sacScale != null) {
            if ("hiking".equals(sacScale))
                ev.setDecimal(false, ints, TagParserFactory.Foot.FOOT_MEAN_SPEED);
            else
                ev.setDecimal(false, ints, TagParserFactory.Foot.FOOT_SLOW_SPEED);
        } else {
            ev.setDecimal(false, ints, TagParserFactory.Foot.FOOT_MEAN_SPEED);
        }
    }

    public ReaderWayFilter getReadWayFilter() {
        return TagParserFactory.ACCEPT_IF_HIGHWAY;
    }

    public final EncodedValue getEncodedValue() {
        return ev;
    }

    public final String toString() {
        return ev.toString();
    }

    public final String getName() {
        return ev.getName();
    }


}
