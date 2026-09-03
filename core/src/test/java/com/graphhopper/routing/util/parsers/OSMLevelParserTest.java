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

class OSMLevelParserTest {
    private final DecimalEncodedValue levelEnc = Level.create();
    private OSMLevelParser parser;
    private IntsRef relFlags;

    @BeforeEach
    void setup() {
        levelEnc.init(new EncodedValue.InitializerConfig());
        parser = new OSMLevelParser(levelEnc);
        relFlags = new IntsRef(2);
    }

    @Test
    void basic() {
        ReaderWay readerWay = new ReaderWay(1);
        EdgeIntAccess edgeIntAccess = new ArrayEdgeIntAccess(1);
        int edgeId = 0;
        readerWay.setTag("level", "2");
        parser.handleWayTags(edgeId, edgeIntAccess, readerWay, relFlags);
        Assertions.assertEquals(2.0, levelEnc.getDecimal(false, edgeId, edgeIntAccess));
    }

    @Test
    void staircaseUp() {
        ReaderWay readerWay = new ReaderWay(1);
        EdgeIntAccess edgeIntAccess = new ArrayEdgeIntAccess(1);
        int edgeId = 0;
        readerWay.setTag("level", "0;1");
        parser.handleWayTags(edgeId, edgeIntAccess, readerWay, relFlags);
        Assertions.assertEquals(0.5, levelEnc.getDecimal(false, edgeId, edgeIntAccess));
    }

    @Test
    void staircaseDown() {
        ReaderWay readerWay = new ReaderWay(1);
        EdgeIntAccess edgeIntAccess = new ArrayEdgeIntAccess(1);
        int edgeId = 0;
        readerWay.setTag("level", "3;2");
        parser.handleWayTags(edgeId, edgeIntAccess, readerWay, relFlags);
        Assertions.assertEquals(2.5, levelEnc.getDecimal(false, edgeId, edgeIntAccess));
    }

    @Test
    void floatLevel() {
        ReaderWay readerWay = new ReaderWay(1);
        EdgeIntAccess edgeIntAccess = new ArrayEdgeIntAccess(1);
        int edgeId = 0;
        
        readerWay.setTag("level", "1.5");
        parser.handleWayTags(edgeId, edgeIntAccess, readerWay, relFlags);
        Assertions.assertEquals(1.5, levelEnc.getDecimal(false, edgeId, edgeIntAccess));
    }

    @Test
    void negativeFloatLevel() {
        ReaderWay readerWay = new ReaderWay(1);
        EdgeIntAccess edgeIntAccess = new ArrayEdgeIntAccess(1);
        int edgeId = 0;
                
        readerWay.setTag("level", "-1.5");
        parser.handleWayTags(edgeId, edgeIntAccess, readerWay, relFlags);
        Assertions.assertEquals(-1.5, levelEnc.getDecimal(false, edgeId, edgeIntAccess));
    }

    @Test
    void belowMinLevel() {
        ReaderWay readerWay = new ReaderWay(1);
        EdgeIntAccess edgeIntAccess = new ArrayEdgeIntAccess(1);
        int edgeId = 0;

        readerWay.setTag("level", "-100");
        parser.handleWayTags(edgeId, edgeIntAccess, readerWay, relFlags);
        Assertions.assertEquals(-40.0, levelEnc.getDecimal(false, edgeId, edgeIntAccess));
    }

    @Test
    void aboveMaxLevel() {
        ReaderWay readerWay = new ReaderWay(1);
        EdgeIntAccess edgeIntAccess = new ArrayEdgeIntAccess(1);
        int edgeId = 0;
        
        readerWay.setTag("level", "300");
        parser.handleWayTags(edgeId, edgeIntAccess, readerWay, relFlags);
        Assertions.assertEquals(164.7, levelEnc.getDecimal(false, edgeId, edgeIntAccess));
    }
