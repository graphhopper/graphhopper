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

package com.graphhopper.reader.osm;

import com.carrotsearch.hppc.LongArrayList;

public class RestrictionMembers {
    private final boolean isViaWay;
    private final long viaOSMNode;
    private final LongArrayList fromWays;
    private final LongArrayList viaWays;
    private final LongArrayList toWays;

    public static RestrictionMembers viaNode(long viaOSMNode, LongArrayList fromWays, LongArrayList toWays) {
        return new RestrictionMembers(false, viaOSMNode, fromWays, null, toWays);
    }

    public static RestrictionMembers viaWay(LongArrayList fromWays, LongArrayList viaWays, LongArrayList toWays) {
        return new RestrictionMembers(true, -1, fromWays, viaWays, toWays);
    }

    private RestrictionMembers(boolean isViaWay, long viaOSMNode, LongArrayList fromWays, LongArrayList viaWays, LongArrayList toWays) {
        this.isViaWay = isViaWay;
        this.viaOSMNode = viaOSMNode;
        this.fromWays = fromWays;
        this.viaWays = viaWays;
        this.toWays = toWays;
    }

    public boolean isViaWay() {
        return isViaWay;
    }

    public long getViaOSMNode() {
        return viaOSMNode;
    }

    public LongArrayList getFromWays() {
        return fromWays;
    }

    public LongArrayList getViaWays() {
        return viaWays;
    }

    public LongArrayList getToWays() {
        return toWays;
    }

    public LongArrayList getAllWays() {
        LongArrayList result = new LongArrayList(fromWays.size() + toWays.size() + (isViaWay ? viaWays.size() : 0));
        result.addAll(fromWays);
        if (isViaWay) result.addAll(viaWays);
        result.addAll(toWays);
        return result;
    }
}
