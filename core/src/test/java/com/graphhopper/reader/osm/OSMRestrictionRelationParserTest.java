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

import com.graphhopper.reader.ReaderElement;
import com.graphhopper.reader.ReaderRelation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.graphhopper.reader.osm.OSMRestrictionRelationParser.createTurnRestrictions;
import static com.graphhopper.reader.osm.OSMTurnRestriction.RestrictionType.NOT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OSMRestrictionRelationParserTest {
    @Test
    public void turnRestrictionsRestrictedToCertainVehicles() {
        {
            ReaderRelation rel = createRestrictionRelation();
            List<String> warnings = new ArrayList<>();
            assertTrue(createTurnRestrictions(rel, warnings::add).isEmpty());
            assertEquals(1, warnings.size());
            assertTrue(warnings.get(0).contains("Restriction relation 0 neither has a 'restriction' nor 'restriction:' tags"), warnings.get(0));
        }
        {
            ReaderRelation rel = createRestrictionRelation();
            rel.setTag("restriction:bicycle", "no_right_turn");
            rel.setTag("except", "bus");
            List<String> warnings = new ArrayList<>();
            assertTrue(createTurnRestrictions(rel, warnings::add).isEmpty());
            assertEquals(1, warnings.size());
            assertTrue(warnings.get(0).contains("Restriction relation 0 has an 'except', but no 'restriction' or 'restriction:conditional' tag"), warnings.get(0));
        }
        {
            ReaderRelation rel = createRestrictionRelation();
            rel.setTag("restriction:hgv:conditional", "no_right_turn @ (weight > 3.5)");
            rel.setTag("except", "bus");
            List<String> warnings = new ArrayList<>();
            assertTrue(createTurnRestrictions(rel, warnings::add).isEmpty());
            assertEquals(1, warnings.size());
            assertTrue(warnings.get(0).contains("Restriction relation 0 has an 'except', but no 'restriction' or 'restriction:conditional' tag"), warnings.get(0));
        }
        {
            ReaderRelation rel = createRestrictionRelation();
            rel.setTag("restriction:conditional", "no_right_turn @ (weight > 3.5)");
            rel.setTag("except", "bus");
            List<String> warnings = new ArrayList<>();
            // we do not handle conditional restrictions yet, but no warning for except+restriction:conditional, because that would make sense
            assertTrue(createTurnRestrictions(rel, warnings::add).isEmpty());
            assertEquals(0, warnings.size());
        }
        {
            // restriction and restriction:vehicle
            ReaderRelation rel = createRestrictionRelation();
            rel.setTag("restriction", "no_left_turn");
            rel.setTag("restriction:bus", "no_left_turn");
            List<String> warnings = new ArrayList<>();
            assertTrue(createTurnRestrictions(rel, warnings::add).isEmpty());
            assertEquals(1, warnings.size());
            assertTrue(warnings.get(0).contains("Restriction relation 0 has a 'restriction' tag, but also 'restriction:' tags"), warnings.get(0));
        }
        {
            ReaderRelation rel = createRestrictionRelation();
            // this could happen for real, or at least it wouldn't be nonsensical. we ignore give_way so far, but do not
            // ignore or warn about the entire relation
            rel.setTag("restriction:bicycle", "give_way");
            rel.setTag("restriction", "no_left_turn");
            List<String> warnings = new ArrayList<>();
            List<OSMTurnRestriction> turnRestrictions = createTurnRestrictions(rel, warnings::add);
            assertEquals(1, turnRestrictions.size());
            assertTrue(warnings.isEmpty());
            assertEquals("", turnRestrictions.get(0).getVehicleTypeRestricted());
            assertTrue(turnRestrictions.get(0).getVehicleTypesExcept().isEmpty());
        }
        {
            ReaderRelation rel = createRestrictionRelation();
            // So far we are ignoring conditional restrictions, even though for example weight restrictions could
            // be interesting. But shouldn't they simply be mapped using restriction:hgv?
            rel.setTag("restriction:conditional", "no_left_turn @ (weight > 3.5)");
            List<String> warnings = new ArrayList<>();
            assertTrue(createTurnRestrictions(rel, warnings::add).isEmpty());
            assertEquals(0, warnings.size());
        }
        {
            ReaderRelation rel = createRestrictionRelation();
            rel.setTag("restriction", "no_left_turn");
            rel.setTag("except", "bus");
            List<String> warnings = new ArrayList<>();
            List<OSMTurnRestriction> turnRestrictions = createTurnRestrictions(rel, warnings::add);
            assertEquals(1, turnRestrictions.size());
            assertEquals("", turnRestrictions.get(0).getVehicleTypeRestricted());
            assertEquals(1, turnRestrictions.get(0).getVehicleTypesExcept().size());
            assertEquals("bus", turnRestrictions.get(0).getVehicleTypesExcept().get(0));
            assertEquals(0, warnings.size());
        }
        {
            ReaderRelation rel = createRestrictionRelation();
            rel.setTag("restriction:motorcar", "no_left_turn");
            List<String> warnings = new ArrayList<>();
            List<OSMTurnRestriction> turnRestrictions = createTurnRestrictions(rel, warnings::add);
            assertEquals(1, turnRestrictions.size());
            assertEquals("motorcar", turnRestrictions.get(0).getVehicleTypeRestricted());
            assertTrue(turnRestrictions.get(0).getVehicleTypesExcept().isEmpty());
            assertEquals(0, warnings.size());
        }
        {
            ReaderRelation rel = createRestrictionRelation();
            rel.setTag("restriction:motorcar", "no_left_turn");
            rel.setTag("restriction:bus", "no_left_turn");
            List<String> warnings = new ArrayList<>();
            List<OSMTurnRestriction> turnRestrictions = createTurnRestrictions(rel, warnings::add);
            assertEquals(2, turnRestrictions.size());
            assertEquals("motorcar", turnRestrictions.get(0).getVehicleTypeRestricted());
            assertEquals("bus", turnRestrictions.get(1).getVehicleTypeRestricted());
            assertTrue(turnRestrictions.get(0).getVehicleTypesExcept().isEmpty());
            assertEquals(0, warnings.size());
        }
    }

    private static ReaderRelation createRestrictionRelation() {
        ReaderRelation rel = new ReaderRelation(0);
        rel.setTag("type", "restriction");
        rel.add(new ReaderRelation.Member(ReaderElement.Type.WAY, 1, "from"));
        rel.add(new ReaderRelation.Member(ReaderElement.Type.NODE, 2, "via"));
        rel.add(new ReaderRelation.Member(ReaderElement.Type.WAY, 3, "to"));
        return rel;
    }

    @Test
    public void turnRestrictionsWithoutVehicleConstraints() {
        {
            ReaderRelation rel = createRestrictionWithoutMembers("no_left_turn");
            rel.add(new ReaderRelation.Member(ReaderElement.Type.WAY, 1, "from"));
            rel.add(new ReaderRelation.Member(ReaderElement.Type.NODE, 2, "via"));
            rel.add(new ReaderRelation.Member(ReaderElement.Type.WAY, 3, "to"));
            List<String> warnings = new ArrayList<>();
            List<OSMTurnRestriction> turnRestrictions = createTurnRestrictions(rel, warnings::add);
            assertEquals(0, warnings.size());
            assertEquals(1, turnRestrictions.size());
            assertEquals(1, turnRestrictions.get(0).getOsmIdFrom());
            assertEquals(2, turnRestrictions.get(0).getViaOsmNodeId());
            assertEquals(3, turnRestrictions.get(0).getOsmIdTo());
            assertEquals(NOT, turnRestrictions.get(0).getRestriction());
            assertEquals(OSMTurnRestriction.ViaType.NODE, turnRestrictions.get(0).getViaType());
        }
        {
            ReaderRelation rel = createRestrictionWithoutMembers("no_left_turn");
            rel.add(new ReaderRelation.Member(ReaderElement.Type.WAY, 1, "from"));
            rel.add(new ReaderRelation.Member(ReaderElement.Type.WAY, 2, "from"));
            rel.add(new ReaderRelation.Member(ReaderElement.Type.NODE, 3, "via"));
            rel.add(new ReaderRelation.Member(ReaderElement.Type.WAY, 4, "to"));
            List<String> warnings = new ArrayList<>();
            List<OSMTurnRestriction> turnRestrictions = createTurnRestrictions(rel, warnings::add);
            assertEquals(0, turnRestrictions.size());
            assertEquals(1, warnings.size());
            assertTrue(warnings.get(0).contains("Restriction relation 0 has multiple members with role 'from' even though it is not a 'no_entry' restriction"), warnings.get(0));
        }
        {
            ReaderRelation rel = createRestrictionWithoutMembers("no_entry");
            rel.add(new ReaderRelation.Member(ReaderElement.Type.WAY, 1, "from"));
            rel.add(new ReaderRelation.Member(ReaderElement.Type.WAY, 2, "from"));
            rel.add(new ReaderRelation.Member(ReaderElement.Type.NODE, 3, "via"));
            rel.add(new ReaderRelation.Member(ReaderElement.Type.WAY, 4, "to"));
            List<String> warnings = new ArrayList<>();
            List<OSMTurnRestriction> turnRestrictions = createTurnRestrictions(rel, warnings::add);
            assertEquals(2, turnRestrictions.size());
            assertEquals(1, turnRestrictions.get(0).getOsmIdFrom());
            assertEquals(2, turnRestrictions.get(1).getOsmIdFrom());
            turnRestrictions.forEach(t -> assertEquals(4, t.getOsmIdTo()));
            turnRestrictions.forEach(t -> assertEquals(NOT, t.getRestriction()));
            assertEquals(0, warnings.size());
        }
        {
            ReaderRelation rel = createRestrictionWithoutMembers("no_left_turn");
            rel.add(new ReaderRelation.Member(ReaderElement.Type.WAY, 1, "from"));
            rel.add(new ReaderRelation.Member(ReaderElement.Type.NODE, 2, "via"));
            rel.add(new ReaderRelation.Member(ReaderElement.Type.WAY, 3, "to"));
            rel.add(new ReaderRelation.Member(ReaderElement.Type.WAY, 4, "to"));
            List<String> warnings = new ArrayList<>();
            List<OSMTurnRestriction> turnRestrictions = createTurnRestrictions(rel, warnings::add);
            assertEquals(0, turnRestrictions.size());
            assertEquals(1, warnings.size());
            assertTrue(warnings.get(0).contains("Restriction relation 0 has multiple members with role 'to' even though it is not a 'no_exit' restriction"), warnings.get(0));
        }
        {
            ReaderRelation rel = createRestrictionWithoutMembers("no_exit");
            rel.add(new ReaderRelation.Member(ReaderElement.Type.WAY, 1, "from"));
            rel.add(new ReaderRelation.Member(ReaderElement.Type.NODE, 2, "via"));
            rel.add(new ReaderRelation.Member(ReaderElement.Type.WAY, 3, "to"));
            rel.add(new ReaderRelation.Member(ReaderElement.Type.WAY, 4, "to"));
            List<String> warnings = new ArrayList<>();
            List<OSMTurnRestriction> turnRestrictions = createTurnRestrictions(rel, warnings::add);
            assertEquals(2, turnRestrictions.size());
            assertEquals(3, turnRestrictions.get(0).getOsmIdTo());
            assertEquals(4, turnRestrictions.get(1).getOsmIdTo());
            turnRestrictions.forEach(t -> assertEquals(1, t.getOsmIdFrom()));
            turnRestrictions.forEach(t -> assertEquals(NOT, t.getRestriction()));
            assertEquals(0, warnings.size());
        }
        {
            ReaderRelation rel = createRestrictionWithoutMembers("no_left_turn");
            rel.add(new ReaderRelation.Member(ReaderElement.Type.WAY, 1, "from"));
            rel.add(new ReaderRelation.Member(ReaderElement.Type.WAY, 2, "to"));
            List<String> warnings = new ArrayList<>();
            List<OSMTurnRestriction> turnRestrictions = createTurnRestrictions(rel, warnings::add);
            assertEquals(0, turnRestrictions.size());
            assertEquals(1, warnings.size());
            assertTrue(warnings.get(0).contains("Restriction relation 0 has no member with role 'via'"), warnings.get(0));
        }
    }

    @Test
    void turnRestrictionsViaType() {
        ReaderRelation rel = createRestrictionWithoutMembers("no_right_turn");
        rel.add(new ReaderRelation.Member(ReaderElement.Type.WAY, 1L, "from"));
        rel.add(new ReaderRelation.Member(ReaderElement.Type.WAY, 2L, "via"));
        rel.add(new ReaderRelation.Member(ReaderElement.Type.WAY, 3L, "to"));

        List<String> warnings = new ArrayList<>();
        List<OSMTurnRestriction> osmRel = createTurnRestrictions(rel, warnings::add);
        assertEquals(0, warnings.size());
        assertEquals(OSMTurnRestriction.ViaType.WAY, osmRel.get(0).getViaType());
        assertEquals(2, osmRel.get(0).getViaOsmNodeId());
    }

    private static ReaderRelation createRestrictionWithoutMembers(String restriction) {
        ReaderRelation rel = new ReaderRelation(0);
        rel.setTag("type", "restriction");
        rel.setTag("restriction", restriction);
        return rel;
    }

}