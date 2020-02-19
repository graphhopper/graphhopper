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

package com.graphhopper.tardur;

import com.graphhopper.GraphHopper;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.profiles.EncodedValue;
import com.graphhopper.routing.profiles.EncodedValueLookup;
import com.graphhopper.routing.profiles.SimpleBooleanEncodedValue;
import com.graphhopper.routing.profiles.UnsignedIntEncodedValue;
import com.graphhopper.routing.util.parsers.TagParser;
import com.graphhopper.search.StringIndex;
import com.graphhopper.storage.IntsRef;

import java.util.HashMap;
import java.util.List;

public class OSMConditionalRestrictionParser implements TagParser {

    private final GraphHopper graphHopper;
    private SimpleBooleanEncodedValue conditional;
    private UnsignedIntEncodedValue tagPointer;

    public OSMConditionalRestrictionParser(GraphHopper graphHopper) {
        this.graphHopper = graphHopper;
    }

    @Override
    public void createEncodedValues(EncodedValueLookup lookup, List<EncodedValue> registerNewEncodedValue) {
        conditional = new SimpleBooleanEncodedValue("conditional");
        tagPointer = new UnsignedIntEncodedValue("tagpointer", 31, false);
        registerNewEncodedValue.add(conditional);
        registerNewEncodedValue.add(tagPointer);
    }

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay way, boolean ferry, IntsRef relationFlags) {
        List<TimeDependentRestrictionsDAO.ConditionalTagData> timeDependentAccessConditions = TimeDependentRestrictionsDAO.getTimeDependentAccessConditions(way);
        if (!timeDependentAccessConditions.isEmpty()) {
            conditional.setBool(false, edgeFlags, true);
            HashMap<String, String> tags = new HashMap<>();
            for (TimeDependentRestrictionsDAO.ConditionalTagData timeDependentAccessCondition : timeDependentAccessConditions) {
                tags.put(timeDependentAccessCondition.tag.key, timeDependentAccessCondition.tag.value);
            }
            int offset = (int) graphHopper.getGraphHopperStorage().getTagStore().add(tags);
            if (offset < 0)
                throw new IllegalStateException("Too many tags are stored, currently limited to int offset");
            tagPointer.setInt(false, edgeFlags, offset);
        }
        return edgeFlags;
    }
}
