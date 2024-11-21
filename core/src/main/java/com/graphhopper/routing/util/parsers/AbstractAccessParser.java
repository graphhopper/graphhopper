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
package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderNode;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.PMap;

import java.util.*;

public abstract class AbstractAccessParser implements TagParser {
    static final Collection<String> ONEWAYS = Arrays.asList("yes", "true", "1", "-1");
    static final Collection<String> INTENDED = Arrays.asList("yes", "designated", "official", "permissive");

    // order is important
    protected final List<String> RESTRICTION_KEY;
    protected final Set<String> RESTRICTION_VALUES = Set.of("no", "restricted", "military", "emergency");

    // http://wiki.openstreetmap.org/wiki/Mapfeatures#Barrier
    protected final Set<String> barriers = new HashSet<>(5);
    protected final BooleanEncodedValue accessEnc;

    protected AbstractAccessParser(BooleanEncodedValue accessEnc, List<String> restrictionKeys) {
        this.accessEnc = accessEnc;
        this.RESTRICTION_KEY = Collections.unmodifiableList(restrictionKeys);
    }

    protected void check(PMap properties) {
        if (properties.has("block_private") || properties.has("block_fords"))
            throw new IllegalArgumentException("block_private and block_fords are no longer supported. " +
                    "Use a custom model as described in #TODO");
    }

    protected void handleBarrierEdge(int edgeId, EdgeIntAccess edgeIntAccess, Map<String, Object> nodeTags) {
        // for now we just create a dummy reader node, because our encoders do not make use of the coordinates anyway
        ReaderNode readerNode = new ReaderNode(0, 0, 0, nodeTags);
        // block access for barriers
        if (isBarrier(readerNode)) {
            BooleanEncodedValue accessEnc = getAccessEnc();
            accessEnc.setBool(false, edgeId, edgeIntAccess, false);
            accessEnc.setBool(true, edgeId, edgeIntAccess, false);
        }
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way, IntsRef relationFlags) {
        handleWayTags(edgeId, edgeIntAccess, way);
    }

    public abstract void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way);

    /**
     * @return true if the given OSM node blocks access for the specified restrictions, false otherwise
     */
    public boolean isBarrier(ReaderNode node) {
        // note that this method will be only called for certain nodes as defined by OSMReader!
        String firstValue = node.getFirstValue(RESTRICTION_KEY);

        if (RESTRICTION_VALUES.contains(firstValue))
            return true;
        else if (node.hasTag("locked", "yes") && !INTENDED.contains(firstValue))
            return true;
        else if (INTENDED.contains(firstValue))
            return false;
        return node.hasTag("barrier", barriers);
    }

    public final BooleanEncodedValue getAccessEnc() {
        return accessEnc;
    }

    public final String getName() {
        return accessEnc.getName();
    }

    @Override
    public String toString() {
        return getName();
    }
}
