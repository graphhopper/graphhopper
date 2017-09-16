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
package com.graphhopper.routing.profiles;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.storage.IntsRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

public class TagsParser {

    private static Logger LOGGER = LoggerFactory.getLogger(TagsParser.class);

    public void parse(Collection<TagParser> parsers, IntsRef ints, ReaderWay way) {
        try {
            for (TagParser tagParser : parsers) {
                if (tagParser.getReadWayFilter().accept(way))
                    // parsing should allow to call edgeState.set multiple times (e.g. for composed values) without reimplementing this set method
                    tagParser.parse(ints, way);
            }

        } catch (Exception ex) {
            // TODO for now do not stop when there are errors
            LOGGER.error("Cannot parse way to store edge properties. Way: " + way, ex);
        }
    }
}
