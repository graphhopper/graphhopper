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
import com.graphhopper.routing.profiles.EncodedValue;
import com.graphhopper.routing.profiles.IntEncodedValue;
import com.graphhopper.routing.util.AbstractFlagEncoder;
import com.graphhopper.routing.util.FootFlagEncoder;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.EdgeIteratorState;

import java.util.ArrayList;
import java.util.List;

public class PtFlagEncoder extends AbstractFlagEncoder {

    private final FootFlagEncoder footFlagEncoder;
    private IntEncodedValue timeEnc;
    private IntEncodedValue transfersEnc;
    private IntEncodedValue validityIdEnc;
    private IntEncodedValue typeEnc;

    public PtFlagEncoder() {
        super(0, 1, 0);

        // TODO is this true?
        // Use the foot flag encoder only as a delegate to filter by OSM tags, not to encode flags
        footFlagEncoder = new FootFlagEncoder();
        // Do this as otherwise 'acceptWay' returns 0 even though it wants to accept. Basically, I have to tell it what 'true' means.
        footFlagEncoder.defineRelationBits(1, 0);
    }

    @Override
    public void createEncodedValues(List<EncodedValue> list, String prefix, int index) {
        // initialization of internal FootFlagEncoder
        // TODO is the bit position important to be identical to PtFlagEncoder bits? E.g. for the access bits?
        footFlagEncoder.setEncodedValueLookup(encodedValueLookup);
        List<EncodedValue> tmpList = new ArrayList<>();
        footFlagEncoder.createEncodedValues(tmpList, "foot.", index);
        EncodedValue.InitializerConfig config = new EncodedValue.InitializerConfig();
        for (EncodedValue ev : tmpList) {
            ev.init(config);
        }

        // do we really need 2 bits for pt.access?
        super.createEncodedValues(list, prefix, index);

        list.add(validityIdEnc = new IntEncodedValue(prefix + "validity_id", 20, 0, false));
        list.add(transfersEnc = new IntEncodedValue(prefix + "transfers", 1, 0, false));
        list.add(typeEnc = new IntEncodedValue(prefix + "type", 4, GtfsStorage.EdgeType.HIGHWAY.ordinal(), false));
        list.add(timeEnc = new IntEncodedValue(prefix + "time", 17, 0, false));
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

    public IntEncodedValue getTimeEnc() {
        return timeEnc;
    }

    public IntEncodedValue getTransfersEnc() {
        return transfersEnc;
    }

    public IntEncodedValue getValidityIdEnc() {
        return validityIdEnc;
    }

    GtfsStorage.EdgeType getEdgeType(EdgeIteratorState edge) {
        return GtfsStorage.EdgeType.values()[edge.get(typeEnc)];
    }

    void setEdgeType(EdgeIteratorState edge, GtfsStorage.EdgeType edgeType) {
        edge.set(typeEnc, edgeType.ordinal());
    }

    public String toString() {
        return "pt";
    }

    @Override
    public int getVersion() {
        return 1;
    }
}
