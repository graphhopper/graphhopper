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
import com.graphhopper.routing.util.AbstractFlagEncoder;
import com.graphhopper.storage.IntsRef;

import static com.graphhopper.routing.profiles.TagParserFactory.ACCEPT_IF_HIGHWAY;


public class CarMaxSpeedParser implements TagParser {
    private final DecimalEncodedValue ev;
    private ReaderWayFilter rwf = TagParserFactory.ACCEPT_IF_HIGHWAY;
    public CarMaxSpeedParser(){
        this.ev = new DecimalEncodedValue(TagParserFactory.CAR_MAX_SPEED, 7, 0, 2, false);
    }
    public CarMaxSpeedParser(DecimalEncodedValue ev){this.ev = ev;}
    public void parse(IntsRef ints, ReaderWay way) {
        assert rwf.accept(way);

        double val = AbstractFlagEncoder.parseSpeed(way.getTag("maxspeed"));
        if (val < 0)
            return;
        ev.setDecimal(false, ints, val);
    }

    public ReaderWayFilter getReadWayFilter() {
        return rwf;
    }

    public CarMaxSpeedParser setReadWayFilter(ReaderWayFilter rf){
        this.rwf = rf;
        return this;
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
