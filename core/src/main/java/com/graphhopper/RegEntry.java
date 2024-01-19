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

package com.graphhopper;

import com.graphhopper.routing.ev.EncodedValue;
import com.graphhopper.routing.util.parsers.TagParserFactory;
import com.graphhopper.util.PMap;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

public interface RegEntry {
    static RegEntry create(Function<PMap, EncodedValue> evFactory, TagParserFactory tagParserFactory, String... reqdRegs) {
        return new RegEntry() {
            @Override
            public Function<PMap, EncodedValue> getEVFactory() {
                return evFactory;
            }

            @Override
            public TagParserFactory getTagParserFactory() {
                return tagParserFactory;
            }

            @Override
            public List<String> getReqdRegs() {
                return List.of(reqdRegs);
            }
        };
    }

    Function<PMap, EncodedValue> getEVFactory();

    TagParserFactory getTagParserFactory();

    default List<String> getReqdRegs() {
        return Collections.emptyList();
    }
}
