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
import com.graphhopper.util.EdgeIteratorState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

/**
 * This class orchestrates the call of multiple TagParser its 'parse' method.
 */
public class TagsParserOSM implements TagsParser {

    private static class OSMSetter implements EdgeSetter {

        @Override
        public void set(EdgeIteratorState edgeState, EncodedValue encodedValue, Object value) {
            // TODO how can we avoid the if-instanceof stuff?
            // for this it is especially ugly that the order is important as e.g. because of
            // DecimalEncodedValue extends IntEncodedValue the decimal has to come before int, same for bit
            if (encodedValue instanceof StringEncodedValue) {
                edgeState.set((StringEncodedValue) encodedValue, (String) value);
            } else if (encodedValue instanceof DecimalEncodedValue) {
                edgeState.set((DecimalEncodedValue) encodedValue, ((Number) value).doubleValue());
            } else if (encodedValue instanceof BooleanEncodedValue) {
                edgeState.set((BooleanEncodedValue) encodedValue, (Boolean) value);
            } else if (encodedValue instanceof IntEncodedValue) {
                edgeState.set((IntEncodedValue) encodedValue, ((Number) value).intValue());
            } else {
                throw new IllegalArgumentException("encodedValue " + encodedValue.getClass() + " not supported: " + encodedValue);
            }
        }
    }

    private OSMSetter osmSetter;
    private static Logger LOGGER = LoggerFactory.getLogger(TagsParserOSM.class);

    public TagsParserOSM() {
        this.osmSetter = new OSMSetter();
    }

    @Override
    public void parse(ReaderWay way, EdgeIteratorState edgeState, Collection<TagParser> parsers) {
        // TODO modify raw data instead of calling edge?
        // IntsRef ints = edgeState.getData()

        try {
            for (TagParser tagParser : parsers) {
                // parsing should allow to call edgeState.set multiple times (e.g. for composed values) without reimplementing this set method
                tagParser.parse(osmSetter, way, edgeState);
            }
        } catch (Exception ex) {
            // TODO for now do not stop when there are errors
            LOGGER.error("Cannot parse way to modify edge " + edgeState.getEdge() + ". Way: " + way, ex);
        }
    }
}
