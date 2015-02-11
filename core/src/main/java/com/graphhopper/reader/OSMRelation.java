/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper licenses this file to you under the Apache License, 
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
package com.graphhopper.reader;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.util.ArrayList;

/**
 * Represents an OSM Relation
 * <p/>
 * @author Nop
 */
public class OSMRelation extends OSMElement
{
    protected final ArrayList<Member> members = new ArrayList<Member>(5);

    public static OSMRelation create( long id, XMLStreamReader parser ) throws XMLStreamException
    {
        OSMRelation rel = new OSMRelation(id);

        parser.nextTag();
        rel.readMembers(parser);
        rel.readTags(parser);
        return rel;
    }

    public OSMRelation( long id )
    {
        super(id, RELATION);
    }

    protected void readMembers( XMLStreamReader parser ) throws XMLStreamException
    {
        int event = parser.getEventType();
        while (event != XMLStreamConstants.END_DOCUMENT && parser.getLocalName().equalsIgnoreCase("member"))
        {
            if (event == XMLStreamConstants.START_ELEMENT)
            {
                // read member
                members.add(new Member(parser));
            }

            event = parser.nextTag();
        }
    }

    @Override
    public String toString()
    {
        return "Relation (" + getId() + ", " + members.size() + " members)";
    }

    public ArrayList<Member> getMembers()
    {
        return members;
    }

    public boolean isMetaRelation()
    {
        for (Member member : members)
        {
            if (member.type() == RELATION)
            {
                return true;
            }
        }
        return false;
    }

    public boolean isMixedRelation()
    {
        boolean hasRel = false;
        boolean hasOther = false;

        for (Member member : members)
        {
            if (member.type() == RELATION)
            {
                hasRel = true;
            } else
            {
                hasOther = true;
            }

            if (hasRel && hasOther)
            {
                return true;
            }
        }
        return false;
    }

    public void removeRelations()
    {
        for (int i = members.size() - 1; i >= 0; i--)
        {
            if (members.get(i).type() == RELATION)
            {
                members.remove(i);
            }
        }
    }

    public void add( Member member )
    {
        members.add(member);
    }

    /**
     * Container class for relation members
     */
    public static class Member
    {
        public static final int NODE = 0;
        public static final int WAY = 1;
        public static final int RELATION = 2;
        private static final String typeDecode = "nwr";
        private final int type;
        private final long ref;
        private final String role;

        public Member( XMLStreamReader parser )
        {
            String typeName = parser.getAttributeValue(null, "type");
            type = typeDecode.indexOf(typeName.charAt(0));
            ref = Long.parseLong(parser.getAttributeValue(null, "ref"));
            role = parser.getAttributeValue(null, "role");
        }

        public Member( Member input )
        {
            type = input.type;
            ref = input.ref;
            role = input.role;
        }

        public Member( int type, long ref, String role )
        {
            this.type = type;
            this.ref = ref;
            this.role = role;
        }

        public String toString()
        {
            return "Member " + type + ":" + ref;
        }

        public int type()
        {
            return type;
        }

        public String role()
        {
            return role;
        }

        public long ref()
        {
            return ref;
        }
    }
}
