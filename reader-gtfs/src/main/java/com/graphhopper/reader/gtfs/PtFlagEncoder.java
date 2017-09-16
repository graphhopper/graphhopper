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

package com.graphhopper.reader.gtfs;

import com.graphhopper.reader.ReaderRelation;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.profiles.BooleanEncodedValue;
import com.graphhopper.routing.profiles.DecimalEncodedValue;
import com.graphhopper.routing.profiles.IntEncodedValue;
import com.graphhopper.routing.profiles.TagParser;
import com.graphhopper.routing.util.AbstractFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FootFlagEncoder;
import com.graphhopper.storage.IntsRef;

import java.util.HashMap;
import java.util.Map;

public class PtFlagEncoder extends AbstractFlagEncoder {

    private final FootFlagEncoder footFlagEncoder;
    private IntEncodedValue time;
    private IntEncodedValue transfers;
    private IntEncodedValue validityId;
    private IntEncodedValue type;

    public PtFlagEncoder() {
        super(0, 1, 0);

        // TODO NOW replace this filtering via the TagParsers from the FootFlagEncoder?
        // I use the foot flag encoder only as a delegate to filter by OSM tags,
        // not to encode flags.
        footFlagEncoder = new FootFlagEncoder();

        // Still, I have to do this. Otherwise 'getAccess' returns 0 even though
        // it wants to accept. Basically, I have to tell it what 'true' means.
        // TODO NOW: still necessary? footFlagEncoder.defineWayBits(1, 0);
        footFlagEncoder.defineRelationBits(1, 0);
    }

    public Map<String, TagParser> createTagParsers(final String prefix) {
        // I have to set super.speedEncoder even though
        // super already knows speedBits and speedFactor because they are constructor parameters.
        averageSpeedEnc = new DecimalEncodedValue("Speed", speedBits, 0, speedFactor, false);
        accessEnc = new BooleanEncodedValue("access", true);
        time = new IntEncodedValue("time", 32, 0, false);
        // use BooleanEncodedValue?
        transfers = new IntEncodedValue("transfers", 1, 0, false);
        validityId = new IntEncodedValue("validityId", 20, 0, false);
        type = new IntEncodedValue("type", 6, GtfsStorage.EdgeType.HIGHWAY.ordinal(), false);
        return new HashMap<>();
    }

    @Override
    public long handleRelationTags(ReaderRelation relation, long oldRelationFlags) {
        return footFlagEncoder.handleRelationTags(relation, oldRelationFlags);
    }

    @Override
    public EncodingManager.Access getAccess(ReaderWay way) {
        return footFlagEncoder.getAccess(way);
    }

    @Override
    public IntsRef handleWayTags(IntsRef ints, ReaderWay way, EncodingManager.Access allowed, long relationFlags) {
        return footFlagEncoder.handleWayTags(ints, way, allowed, relationFlags);
    }

    long getTime(IntsRef flags) {
        return time.getInt(false, flags);
    }

    IntsRef setTime(IntsRef flags, long time) {
        this.time.setInt(false, flags, (int) time);
        return flags;
    }

    int getTransfers(IntsRef flags) {
        return transfers.getInt(false, flags);
    }

    IntsRef setTransfers(IntsRef flags, int transfers) {
        this.transfers.setInt(false, flags, transfers);
        return flags;
    }

    int getValidityId(IntsRef flags) {
        return validityId.getInt(false, flags);
    }

    IntsRef setValidityId(IntsRef flags, int validityId) {
        this.validityId.setInt(false, flags, validityId);
        return flags;
    }

    GtfsStorage.EdgeType getEdgeType(IntsRef flags) {
        return GtfsStorage.EdgeType.values()[type.getInt(false, flags)];
    }

    IntsRef setEdgeType(IntsRef flags, GtfsStorage.EdgeType edgeType) {
        type.setInt(false, flags, edgeType.ordinal());
        return flags;
    }

    public String toString() {
        return "pt";
    }

    @Override
    public int getVersion() {
        return 0;
    }
}
