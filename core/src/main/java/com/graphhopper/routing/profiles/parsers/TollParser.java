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
package com.graphhopper.routing.profiles.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.profiles.ObjectEncodedValue;
import com.graphhopper.routing.profiles.Toll;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.IntsRef;

public class TollParser extends AbstractTagParser<ObjectEncodedValue> {

    public TollParser() {
        super(EncodingManager.TOLL, Toll.create());
    }

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay way, long allowed, long relationFlags) {
        if (way.hasTag("toll", "yes"))
            encodedValue.setObject(false, edgeFlags, Toll.ALL);
        if (way.hasTag("toll:hgv", "yes"))
            encodedValue.setObject(false, edgeFlags, Toll.HGV);

        return edgeFlags;
    }
}
