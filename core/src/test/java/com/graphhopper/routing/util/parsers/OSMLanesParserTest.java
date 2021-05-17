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

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.Lanes;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.IntsRef;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OSMLanesParserTest {

    private OSMLanesParser parser;
    private EncodingManager em;

    @BeforeEach
    void setup() {
        parser = new OSMLanesParser();
        em = new EncodingManager.Builder().add(parser).build();
    }

    @Test
    void basic() {
        ReaderWay readerWay = new ReaderWay(1);
        IntsRef intsRef = em.createEdgeFlags();
        readerWay.setTag("lanes", "4");
        parser.handleWayTags(intsRef, readerWay, false, em.createRelationFlags());
        Assertions.assertEquals(4, em.getIntEncodedValue(Lanes.KEY).getInt(false, intsRef));
    }

    @Test
    void notTagged() {
        ReaderWay readerWay = new ReaderWay(1);
        IntsRef intsRef = em.createEdgeFlags();
        parser.handleWayTags(intsRef, readerWay, false, em.createRelationFlags());
        Assertions.assertEquals(1, em.getIntEncodedValue(Lanes.KEY).getInt(false, intsRef));
    }

}