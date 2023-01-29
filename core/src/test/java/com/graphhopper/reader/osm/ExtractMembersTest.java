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
import com.graphhopper.reader.ReaderRelation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.graphhopper.reader.ReaderElement.Type.NODE;
import static com.graphhopper.reader.ReaderElement.Type.WAY;
import static org.junit.jupiter.api.Assertions.*;

class ExtractMembersTest {
    private ReaderRelation relation;

    @BeforeEach
    void setup() {
        relation = new ReaderRelation(0);
        relation.setTag("type", "restriction");
    }

    @Test
    void simpleViaNode() throws OSMRestrictionException {
        relation.add(new ReaderRelation.Member(WAY, 1, "from"));
        relation.add(new ReaderRelation.Member(NODE, 2, "via"));
        relation.add(new ReaderRelation.Member(WAY, 3, "to"));
        RestrictionMembers restrictionMembers = RestrictionConverter.extractMembers(relation);
        assertEquals(LongArrayList.from(1), restrictionMembers.getFromWays());
        assertEquals(2, restrictionMembers.getViaOSMNode());
        assertEquals(LongArrayList.from(3), restrictionMembers.getToWays());
        assertNull(restrictionMembers.getViaWays());
    }

    @Test
    void noVia() {
        relation.add(new ReaderRelation.Member(WAY, 1, "from"));
        relation.add(new ReaderRelation.Member(WAY, 2, "to"));
        OSMRestrictionException e = assertThrows(OSMRestrictionException.class, () -> RestrictionConverter.extractMembers(relation));
        assertTrue(e.getMessage().contains("has no member with role 'via'"), e.getMessage());
    }

    @Test
    void multipleViaNodes() {
        relation.add(new ReaderRelation.Member(WAY, 1, "from"));
        relation.add(new ReaderRelation.Member(NODE, 2, "via"));
        relation.add(new ReaderRelation.Member(NODE, 3, "via"));
        relation.add(new ReaderRelation.Member(WAY, 4, "to"));
        OSMRestrictionException e = assertThrows(OSMRestrictionException.class, () -> RestrictionConverter.extractMembers(relation));
        assertTrue(e.getMessage().contains("has multiple members with role 'via' and type 'node'"), e.getMessage());
    }

    @Test
    void multipleFromButNotNoEntry() {
        relation.add(new ReaderRelation.Member(WAY, 1, "from"));
        relation.add(new ReaderRelation.Member(WAY, 2, "from"));
        relation.add(new ReaderRelation.Member(NODE, 3, "via"));
        relation.add(new ReaderRelation.Member(WAY, 4, "to"));
        OSMRestrictionException e = assertThrows(OSMRestrictionException.class, () -> RestrictionConverter.extractMembers(relation));
        assertTrue(e.getMessage().contains("has multiple members with role 'from' even though it is not a 'no_entry' restriction"), e.getMessage());
    }

    @Test
    void noEntry() throws OSMRestrictionException {
        relation.setTag("restriction", "no_entry");
        relation.add(new ReaderRelation.Member(WAY, 1, "from"));
        relation.add(new ReaderRelation.Member(WAY, 2, "from"));
        relation.add(new ReaderRelation.Member(NODE, 3, "via"));
        relation.add(new ReaderRelation.Member(WAY, 4, "to"));
        RestrictionMembers res = RestrictionConverter.extractMembers(relation);
        assertEquals(LongArrayList.from(1, 2), res.getFromWays());
        assertEquals(3, res.getViaOSMNode());
        assertEquals(LongArrayList.from(4), res.getToWays());
    }

    @Test
    void multipleToButNoNoExit() {
        relation.setTag("restriction", "no_left_turn");
        relation.add(new ReaderRelation.Member(WAY, 1, "from"));
        relation.add(new ReaderRelation.Member(NODE, 2, "via"));
        relation.add(new ReaderRelation.Member(WAY, 3, "to"));
        relation.add(new ReaderRelation.Member(WAY, 4, "to"));
        OSMRestrictionException e = assertThrows(OSMRestrictionException.class, () -> RestrictionConverter.extractMembers(relation));
        assertTrue(e.getMessage().contains("has multiple members with role 'to' even though it is not a 'no_exit' restriction"), e.getMessage());
    }

    @Test
    void noExit() throws OSMRestrictionException {
        relation.setTag("restriction", "no_exit");
        relation.add(new ReaderRelation.Member(WAY, 1, "from"));
        relation.add(new ReaderRelation.Member(NODE, 2, "via"));
        relation.add(new ReaderRelation.Member(WAY, 3, "to"));
        relation.add(new ReaderRelation.Member(WAY, 4, "to"));
        RestrictionMembers res = RestrictionConverter.extractMembers(relation);
        assertEquals(LongArrayList.from(1), res.getFromWays());
        assertEquals(2, res.getViaOSMNode());
        assertEquals(LongArrayList.from(3, 4), res.getToWays());
    }

}