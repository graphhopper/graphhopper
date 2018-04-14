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

package com.michaz;

import com.graphhopper.reader.ReaderRelation;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.util.AbstractFlagEncoder;

public class OriginalDirectionFlagEncoder extends AbstractFlagEncoder {


    private long originalDirectionBitmask;

    public OriginalDirectionFlagEncoder() {
        super(0, 0, 0);
    }

    @Override
    public int defineWayBits(int index, int shift) {
        shift = super.defineWayBits(index, shift);
        this.originalDirectionBitmask = 1L << shift;
        return shift + 1;
    }

    @Override
    public long handleRelationTags(ReaderRelation readerRelation, long l) {
        return l;
    }

    @Override
    public long acceptWay(ReaderWay readerWay) {
        return 0;
    }

    @Override
    public long handleWayTags(ReaderWay readerWay, long l, long l1) {
        return 0;
    }

    @Override
    public int getVersion() {
        return 0;
    }

    public long reverseFlags(long flags) {
        return super.reverseFlags(flags) ^ this.originalDirectionBitmask;
    }

    public boolean isOriginalDirection(long flags) {
        return (flags & this.originalDirectionBitmask) != 0L;
    }

    public long setOriginalDirection(long flags, boolean originalDirection) {
        return originalDirection ? flags | originalDirectionBitmask : flags & ~originalDirectionBitmask;
    }

    public String toString() {
        return "original-direction";
    }

}
