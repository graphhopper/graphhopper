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

import com.graphhopper.reader.ReaderRelation;
import com.graphhopper.routing.ev.EncodedValue;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.storage.IntsRef;

import java.util.List;

/**
 * This interface serves the purpose of creating relation flags (max. 64 bits) from ReaderRelation in handleRelationTags
 * and then allows converting the relation flags into the edge flags. A direct conversion of ReaderRelation into edge
 * flags is not yet possible yet due to storage limitation of the 'supervisor' OSMReader. See #1775.
 */
public interface RelationTagParser extends TagParser {

    void createRelationEncodedValues(EncodedValueLookup lookup, List<EncodedValue> registerNewEncodedValue);

    /**
     * Analyze the tags of a relation and create the routing flags for the second read step.
     * In the pre-parsing step this method will be called to determine the useful relation tags.
     */
    IntsRef handleRelationTags(IntsRef relFlags, ReaderRelation relation);
}
