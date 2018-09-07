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
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.EdgeIteratorState;

import java.util.List;

public class PtFlagEncoder extends AbstractFlagEncoder {

    private IntEncodedValue timeEnc;
    private IntEncodedValue transfersEnc;
    private IntEncodedValue validityIdEnc;
    private IntEncodedValue typeEnc;

    public PtFlagEncoder() {
        super(0, 1, 0);
    }

    @Override
    public void createEncodedValues(List<EncodedValue> list, String prefix, int index) {
        // do we really need 2 bits for pt.access?
        super.createEncodedValues(list, prefix, index);

        list.add(validityIdEnc = new IntEncodedValue(prefix + "validity_id", 20, 0, false));
        list.add(transfersEnc = new IntEncodedValue(prefix + "transfers", 1, 0, false));
        list.add(typeEnc = new IntEncodedValue(prefix + "type", 4, GtfsStorage.EdgeType.HIGHWAY.ordinal(), false));
        list.add(timeEnc = new IntEncodedValue(prefix + "time", 17, 0, false));
    }

    @Override
    public long handleRelationTags(long oldRelationFlags, ReaderRelation relation) {
        return oldRelationFlags;
    }

    @Override
    public long acceptWay(ReaderWay way) {
        return 0;
    }

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay way, long allowed, long relationFlags) {
        return edgeFlags;
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
