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
package com.graphhopper.reader

/**
 * Represents a relation received from the reader.
 *
 * @author Nop
 */
class ReaderRelation(id: Long) : ReaderElement(id, Type.RELATION, HashMap(2)) {
    private var members: MutableList<Member>? = null

    override fun toString(): String =
        "Relation (" + id + ", " + (members?.size ?: 0) + " members)"

    fun getMembers(): List<Member> = members ?: emptyList()

    fun add(member: Member) {
        val list = members ?: ArrayList<Member>(3).also { members = it }
        list.add(member)
    }

    /**
     * Container class for relation members
     */
    class Member(val type: Type, /** member reference which is an OSM ID */ val ref: Long, val role: String?) {
        override fun toString(): String = "Member $type:$ref"
    }
}
