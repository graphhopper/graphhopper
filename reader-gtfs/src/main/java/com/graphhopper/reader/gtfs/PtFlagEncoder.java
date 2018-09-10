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
import com.graphhopper.routing.util.AbstractFlagEncoder;
import com.graphhopper.routing.util.EncodedDoubleValue;
import com.graphhopper.routing.util.EncodedValue;

public class PtFlagEncoder extends AbstractFlagEncoder {

	private EncodedValue time;
	private EncodedValue transfers;
	private EncodedValue validityId;
	private EncodedValue type;

	public PtFlagEncoder() {
		super(0, 1, 0);
	}

	@Override
	public int defineWayBits(int index, int shift) {
		shift = super.defineWayBits(index, shift);

		// I have to set super.speedEncoder even though
		// super already knows speedBits and speedFactor because they are constructor parameters.
		speedEncoder = new EncodedDoubleValue("Speed", shift, speedBits, speedFactor, 0, 0);
		shift += speedEncoder.getBits();

		time = new EncodedValue("time", shift, 17, 1.0, 0, 24*60*60);
		shift += time.getBits();
		transfers = new EncodedValue("transfers", shift, 1, 1.0, 0, 1);
		shift += transfers.getBits();
		validityId = new EncodedValue("validityId", shift, 20, 1.0, 0, 1048575);
		shift += validityId.getBits();
		GtfsStorage.EdgeType[] edgeTypes = GtfsStorage.EdgeType.values();
		type = new EncodedValue("type", shift, 4, 1.0, GtfsStorage.EdgeType.HIGHWAY.ordinal(), edgeTypes[edgeTypes.length-1].ordinal());
		shift += type.getBits();
		return shift;
	}

	@Override
	public long handleRelationTags(ReaderRelation relation, long oldRelationFlags) {
		return oldRelationFlags;
	}

	@Override
	public long acceptWay(ReaderWay way) {
		return 0;
	}

	@Override
	public long handleWayTags(ReaderWay way, long allowed, long relationFlags) {
		return 0;
	}

	long getTime(long flags) {
        return time.getValue(flags);
    }

    long setTime(long flags, long time) {
        return this.time.setValue(flags, time);
    }

    int getTransfers(long flags) {
		return (int) transfers.getValue(flags);
	}

	long setTransfers(long flags, int transfers) {
		return this.transfers.setValue(flags, transfers);
	}

	int getValidityId(long flags) {
		return (int) validityId.getValue(flags);
	}

	long setValidityId(long flags, int validityId) {
		return this.validityId.setValue(flags, validityId);
	}

	GtfsStorage.EdgeType getEdgeType(long flags) {
		return GtfsStorage.EdgeType.values()[(int) type.getValue(flags)];
	}

	long setEdgeType(long flags, GtfsStorage.EdgeType edgeType) {
		return type.setValue(flags, edgeType.ordinal());
	}

	public String toString() {
		return "pt";
	}

	@Override
	public int getVersion() {
		return 0;
	}
}
