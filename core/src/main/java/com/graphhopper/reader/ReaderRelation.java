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
package com.graphhopper.reader;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a relation received from the reader.
 * <p>
 *
 * @author Nop
 */
public class ReaderRelation extends ReaderElement {
    protected final List<Member> members = new ArrayList<Member>(5);

    public ReaderRelation(long id) {
        super(id, RELATION);
    }

    @Override
    public String toString() {
        return "Relation (" + getId() + ", " + members.size() + " members)";
    }

    public List<Member> getMembers() {
        return members;
    }

    public boolean isMetaRelation() {
        for (Member member : members) {
            if (member.getType() == RELATION) {
                return true;
            }
        }
        return false;
    }

    public boolean isMixedRelation() {
        boolean hasRel = false;
        boolean hasOther = false;

        for (Member member : members) {
            if (member.getType() == RELATION)
                hasRel = true;
            else
                hasOther = true;

            if (hasRel && hasOther)
                return true;
        }
        return false;
    }

    public void removeRelations() {
        for (int i = members.size() - 1; i >= 0; i--) {
            if (members.get(i).getType() == RELATION)
                members.remove(i);
        }
    }

    public void add(Member member) {
        members.add(member);
    }

    /**
     * Container class for relation members
     */
    public static class Member {
        public static final int NODE = 0;
        public static final int WAY = 1;
        public static final int RELATION = 2;
        private final int type;
        private final long ref;
        private final String role;

        public Member(Member input) {
            type = input.type;
            ref = input.ref;
            role = input.role;
        }

        public Member(int type, long ref, String role) {
            this.type = type;
            this.ref = ref;
            this.role = role;
        }

        @Override
        public String toString() {
            return "Member " + type + ":" + ref;
        }

        public int getType() {
            return type;
        }

        /**
         * member reference which is an OSM ID
         */
        public long getRef() {
            return ref;
        }

        public String getRole() {
            return role;
        }
    }
}
