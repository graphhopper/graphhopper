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

import com.graphhopper.reader.ReaderNode;
import com.graphhopper.reader.ReaderRelation;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager08;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.util.EdgeIteratorState;

import java.util.*;

/**
 * This class approaches the storage of edge properties via orchestrating multiple EncodedValue-objects like maxspeed and
 * highway type that can be accessed in a Weighting and will be feeded in the Import (via TagsParser).
 */
public class EncodingManager extends EncodingManager08 {

    /**
     * for backward compatibility we have to specify an encoder name ("vehicle")
     */
    public static final String ENCODER_NAME = "weighting";
    private final Map<String, TagParser> parsers = new HashMap<>();
    private final Collection<ReaderWayFilter> filters = new HashSet<>();
    private final TagsParser parser;
    private final int extendedDataSize;
    private final FlagEncoder mockEncoder;

    /**
     * @param extendedDataSize in bytes
     */
    private EncodingManager(TagsParser parser, int extendedDataSize) {
        // we have to add a fake encoder that uses 0 bits for backward compatibility with EncodingManager08
        super(new CarFlagEncoder(0, 1, 0) {
            @Override
            public int defineWayBits(int index, int shift) {
                return shift;
            }

            @Override
            public String toString() {
                return ENCODER_NAME;
            }
        });
        this.mockEncoder = fetchEdgeEncoders().get(0);

        this.parser = parser;
        this.extendedDataSize = Math.min(1, extendedDataSize / 4) * 4;
    }

    public static class Builder {
        private boolean buildCalled;
        private final EncodingManager em;

        public Builder(TagsParser parser, int extendedDataSize) {
            this.em = new EncodingManager(parser, extendedDataSize);
        }

        public Builder add(TagParser parser) {
            check();
            TagParser old = em.parsers.get(parser.getName());
            if (old != null)
                throw new IllegalArgumentException("Already existing parser " + parser.getName() + ": " + old);

            em.parsers.put(parser.getName(), parser);
            em.filters.add(parser.getReadWayFilter());
            return this;
        }

        public EncodingManager build() {
            check();

            buildCalled = true;
            EncodedValue.InitializerConfig initializer = new EncodedValue.InitializerConfig();
            for (TagParser tp : em.parsers.values()) {
                tp.getEncodedValue().init(initializer);
            }
            return em;
        }

        private void check() {
            if (buildCalled)
                throw new IllegalStateException("EncodingManager.Builder.build() already called");
        }
    }

    public long acceptWay(ReaderWay way) {
        // TODO is this correct? if one way is rejected from one EncodedValue then it won't be parsed at all
        for (ReaderWayFilter filter : filters) {
            if (!filter.accept(way))
                return 0;
        }
        return 1;
    }

    @Override
    public long handleWayTags(ReaderWay way, long includeWay, long relationFlags) {
        // for backward compatibility return flags=1111...
        // applyWayTags does the property storage
        return ~0L;
    }

    @Override
    public void applyWayTags(ReaderWay way, EdgeIteratorState edge) {
        parser.parse(way, edge, parsers.values());
    }

    /**
     * Size of arbitrary storage per edge and in bytes
     */
    public int getExtendedDataSize() {
        return extendedDataSize;
    }

    public StringEncodedValue getEncodedValueString(String key) {
        return getEncodedValue(key, StringEncodedValue.class);
    }

    public BooleanEncodedValue getBooleanEncodedValue(String key) {
        return getEncodedValue(key, BooleanEncodedValue.class);
    }

    public IntEncodedValue getIntEncodedValue(String key) {
        return getEncodedValue(key, IntEncodedValue.class);
    }

    public DecimalEncodedValue getDecimalEncodedValue(String key) {
        return getEncodedValue(key, DecimalEncodedValue.class);
    }

    public <T extends EncodedValue> T getEncodedValue(String key, Class<T> clazz) {
        TagParser prop = parsers.get(key);
        if (prop == null)
            throw new IllegalArgumentException("Cannot find parser " + key + " for encoded value in existing collection: " + parsers);
        return (T) prop.getEncodedValue();
    }

    @Override
    public boolean supports(String encoder) {
        return encoder.equals(ENCODER_NAME);
    }

    @Override
    public FlagEncoder getEncoder(String name) {
        return mockEncoder;
    }

    @Override
    public long handleRelationTags(ReaderRelation relation, long oldRelationFlags) {
        // TODO
        return oldRelationFlags;
    }

    @Override
    public String toString() {
        String str = "";
        for (TagParser p : parsers.values()) {
            if (str.length() > 0)
                str += ", ";
            str += p.getName();
        }

        return str.toString();
    }

    @Override
    public String toDetailsString() {
        String str = "";
        for (TagParser p : parsers.values()) {
            if (str.length() > 0)
                str += ", ";
            str += p.getName();
        }

        return str.toString();
    }

    public long flagsDefault(boolean forward, boolean backward) {
        // TODO deprecate usage of flags
        return 0;
    }

    public int getBytesForFlags() {
        return 4;
    }

    /**
     * Reverse flags, to do so all encoders are called.
     */
    @Override
    public long reverseFlags(long flags) {
        return flags;
    }

    /**
     * Analyze tags on osm node. Store node tags (barriers etc) for later usage while parsing way.
     */
    @Override
    public long handleNodeTags(ReaderNode node) {
        // TODO not implemented
        return 0;
    }

    @Override
    public EncodingManager08 setEnableInstructions(boolean enableInstructions) {
        // TODO not implemented
        return this;
    }

    @Override
    public EncodingManager08 setPreferredLanguage(String preferredLanguage) {
        // TODO not implemented
        return this;
    }

    @Override
    public List<FlagEncoder> fetchEdgeEncoders() {
        return super.fetchEdgeEncoders();
    }

    @Override
    public boolean needsTurnCostsSupport() {
        // TODO encoded values per node
        return false;
    }
}
