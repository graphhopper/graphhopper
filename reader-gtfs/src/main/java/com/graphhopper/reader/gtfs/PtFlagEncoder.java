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
import com.graphhopper.routing.profiles.DecimalEncodedValue;
import com.graphhopper.routing.profiles.EncodedValue;
import com.graphhopper.routing.profiles.IntEncodedValue;
import com.graphhopper.routing.util.AbstractFlagEncoder;
import com.graphhopper.routing.util.FootFlagEncoder;
import com.graphhopper.storage.IntsRef;

import java.util.List;

public class PtFlagEncoder extends AbstractFlagEncoder {

    private final FootFlagEncoder footFlagEncoder;
    private IntEncodedValue time;
    private IntEncodedValue transfers;
    private IntEncodedValue validityId;
    private IntEncodedValue type;

    public PtFlagEncoder() {
        super(0, 1, 0);

        // Use the foot flag encoder only as a delegate to filter by OSM tags, not to encode flags.
        footFlagEncoder = new FootFlagEncoder();
        // Do this as otherwise 'acceptWay' returns 0 even though it wants to accept. Basically, I have to tell it what 'true' means.
        footFlagEncoder.defineRelationBits(1, 0);
    }

    @Override
    public void createEncodedValues(List<EncodedValue> list, String prefix, int index) {
        footFlagEncoder.createEncodedValues(list, "foot", index + 1);

        super.createEncodedValues(list, prefix, index);
        list.add(speedEncoder = new DecimalEncodedValue(prefix + "average_speed", speedBits, 0, speedFactor, false));
        list.add(time = new IntEncodedValue(prefix + "time", 17, 0, false));
        list.add(transfers = new IntEncodedValue(prefix + "transfers", 1, 0, false));
        list.add(validityId = new IntEncodedValue(prefix + "validity_id", 20, 0, false));
        list.add(type = new IntEncodedValue(prefix + "type", 4, GtfsStorage.EdgeType.HIGHWAY.ordinal(), false));
    }

    @Override
    public long handleRelationTags(long oldRelationFlags, ReaderRelation relation) {
        return footFlagEncoder.handleRelationTags(oldRelationFlags, relation);
    }

    @Override
    public long acceptWay(ReaderWay way) {
        return footFlagEncoder.acceptWay(way);
    }

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay way, long allowed, long relationFlags) {
        return footFlagEncoder.handleWayTags(edgeFlags, way, allowed, relationFlags);
    }

    long getTime(IntsRef flags) {
        return time.getInt(false, flags);
    }

    void setTime(IntsRef flags, long time) {
        this.time.setInt(false, flags, (int) time);
    }

    int getTransfers(IntsRef flags) {
        return transfers.getInt(false, flags);
    }

    void setTransfers(IntsRef flags, int transfers) {
        this.transfers.setInt(false, flags, transfers);
    }

    int getValidityId(IntsRef flags) {
        return validityId.getInt(false, flags);
    }

    void setValidityId(IntsRef flags, int validityId) {
        this.validityId.setInt(false, flags, validityId);
    }

    GtfsStorage.EdgeType getEdgeType(IntsRef flags) {
        return GtfsStorage.EdgeType.values()[type.getInt(false, flags)];
    }

    void setEdgeType(IntsRef flags, GtfsStorage.EdgeType edgeType) {
        type.setInt(false, flags, edgeType.ordinal());
    }

    public String toString() {
        return "pt";
    }

    @Override
    public int getVersion() {
        return 0;
    }
}
