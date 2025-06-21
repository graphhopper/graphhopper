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

import java.util.*;

public abstract class AbstractAccessParser implements TagParser {
    static final Collection<String> ONEWAYS = Arrays.asList("yes", "true", "1", "-1");
    static final Collection<String> INTENDED = Arrays.asList("yes", "designated", "official", "permissive");

    // order is important
    protected final List<String> restrictionKeys;
    protected final Set<String> restrictedValues = new HashSet<>(5);

    protected final Set<String> intendedValues = new HashSet<>(INTENDED);
    // http://wiki.openstreetmap.org/wiki/Mapfeatures#Barrier
    protected final Set<String> barriers = new HashSet<>(5);
    protected final BooleanEncodedValue accessEnc;
    private boolean blockFords = true;

    protected AbstractAccessParser(BooleanEncodedValue accessEnc, List<String> restrictionKeys) {
        this.accessEnc = accessEnc;
        this.restrictionKeys = restrictionKeys;

        intendedValues.add("destination");

        restrictedValues.add("no");
        restrictedValues.add("restricted");
        restrictedValues.add("military");
        restrictedValues.add("emergency");
        restrictedValues.add("private");
        restrictedValues.add("permit");
    }

    public boolean isBlockFords() {
        return blockFords;
    }

    protected void blockFords(boolean blockFords) {
        this.blockFords = blockFords;
    }

    protected void blockPrivate(boolean blockPrivate) {
        if (!blockPrivate) {
            if (!restrictedValues.remove("private"))
                throw new IllegalStateException("no 'private' found in restrictedValues");
            if (!restrictedValues.remove("permit"))
                throw new IllegalStateException("no 'permit' found in restrictedValues");
            intendedValues.add("private");
            intendedValues.add("permit");
        }
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
        String firstValue = node.getFirstValue(restrictionKeys);

        if (restrictedValues.contains(firstValue))
            return true;
        else if (node.hasTag("locked", "yes") && !intendedValues.contains(firstValue))
            return true;
        else if (intendedValues.contains(firstValue))
            return false;
        else if (node.hasTag("barrier", barriers))
            return true;
        else
            return blockFords && node.hasTag("ford", "yes");
    }

    public final BooleanEncodedValue getAccessEnc() {
        return accessEnc;
    }

    public final List<String> getRestrictionKeys() {
        return restrictionKeys;
    }

    public final String getName() {
        return accessEnc.getName();
    }

    @Override
    public String toString() {
        return getName();
    }
}
