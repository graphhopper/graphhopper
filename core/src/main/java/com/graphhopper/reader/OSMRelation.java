/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor license 
 *  agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except 
 *  in compliance with the License. You may obtain a copy of the 
 *  License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.reader;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.util.ArrayList;

/**
 * Represents an OSM Relation
 *
 * @author Nop
 */
public class OSMRelation extends OSMElement {

    protected ArrayList<Member> members;

    public OSMRelation() {
        super(RELATION);
        members = new ArrayList<Member>();
    }

    public OSMRelation(long id, XMLStreamReader parser) throws XMLStreamException {
        super(RELATION, id, parser);
        members = new ArrayList<Member>();

        parser.nextTag();
        readMembers(parser);
        readTags(parser);
    }

    protected void readMembers(XMLStreamReader parser) throws XMLStreamException {
        int event = parser.getEventType();
        while (event != XMLStreamConstants.END_DOCUMENT && parser.getLocalName().equalsIgnoreCase("member")) {
            if (event == XMLStreamConstants.START_ELEMENT) {
                // read member
                members.add(new Member(parser));
            }

            event = parser.nextTag();
        }
    }

    public String toString() {
        return "Relation (" + id + ", " + members.size() + " members)";
    }

    public ArrayList<Member> getMembers() {
        return members;
    }

    public void copyMembers(OSMRelation input) {
        members = new ArrayList<Member>();
        for (int i = 0; i < input.members.size(); i++) {
            members.add(new Member(input.members.get(i)));
        }
    }

    public boolean isMetaRelation() {
        for (Member member : members) {
            if (member.type().equals("relation"))
                return true;
        }
        return false;
    }

    public boolean isMixedRelation() {
        boolean hasRel = false;
        boolean hasOther = false;

        for (Member member : members) {
            if (member.type().equals("relation"))
                hasRel = true;
            else
                hasOther = true;

            if (hasRel && hasOther)
                return true;
        }
        return false;
    }
    private static String[] memberType = new String[]{
        "node", "way", "relation"
    };

    public void removeRelations() {
        for (int i = members.size() - 1; i >= 0; i--) {
            if (members.get(i).type().equals("relation")) {
                members.remove(i);
            }
        }
    }

    public void add(Member member) {
        members.add(member);
    }

    /**
     * Container class for relation members
     */
    public static class Member {

        private String type;
        private String ref;
        private String role;

        public Member(XMLStreamReader parser) {
            type = parser.getAttributeValue(null, "type");
            ref = parser.getAttributeValue(null, "ref");
            role = parser.getAttributeValue(null, "role");
        }

        public Member(Member input) {
            type = input.type;
            ref = input.ref;
            role = input.role;
        }

        public String toString() {
            return "Member " + type + ":" + ref;
        }

        public String type() {
            return type;
        }

        public String role() {
            return role;
        }

        public String ref() {
            return ref;
        }
    }
}