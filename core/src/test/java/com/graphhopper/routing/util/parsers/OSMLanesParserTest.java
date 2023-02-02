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
import com.graphhopper.routing.ev.*;
import com.graphhopper.storage.IntsRef;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OSMLanesParserTest {
    private final IntEncodedValue lanesEnc = Lanes.create();
    private OSMLanesParser parser;
    private IntsRef relFlags;

    @BeforeEach
    void setup() {
        lanesEnc.init(new EncodedValue.InitializerConfig());
        parser = new OSMLanesParser(lanesEnc);
        relFlags = new IntsRef(2);
    }

    @Test
    void basic() {
        ReaderWay readerWay = new ReaderWay(1);
        IntAccess intAccess = new ArrayIntAccess(1);
        int edgeId = 0;
        readerWay.setTag("lanes", "4");
        parser.handleWayTags(edgeId, intAccess, readerWay, relFlags);
        Assertions.assertEquals(4, lanesEnc.getInt(false, edgeId, intAccess));
    }

    @Test
    void notTagged() {
        ReaderWay readerWay = new ReaderWay(1);
        IntAccess intAccess = new ArrayIntAccess(1);
        int edgeId = 0;
        parser.handleWayTags(edgeId, intAccess, readerWay, relFlags);
        Assertions.assertEquals(1, lanesEnc.getInt(false, edgeId, intAccess));
    }

}