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
package com.graphhopper.reader.osgb;

import java.util.ArrayList;
import java.util.List;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import com.graphhopper.reader.OSMRelation;
import com.graphhopper.reader.OSMTurnRelation;
import com.graphhopper.reader.OSMTurnRelation.Type;
import com.graphhopper.reader.Relation;
import com.graphhopper.reader.RelationMember;

/**
 * Represents an OSM Relation
 * <p/>
 * 
 * @author Nop
 */
public class OSITNRelation extends OSITNElement implements Relation {

	private static final List<String> notInstructions;
	private static final List<String> onlyInstructions;
	protected final ArrayList<ITNMember> members = new ArrayList<ITNMember>(5);
	private Type relationType;

	static {
		notInstructions = new ArrayList<>();
		onlyInstructions = new ArrayList<>();

		notInstructions.add("No Turn");
	}

	public static OSITNRelation create(long id, XMLStreamReader parser)
			throws XMLStreamException {
		OSITNRelation rel = new OSITNRelation(id);

		parser.nextTag();
		rel.readTags(parser);
		return rel;
	}

	public OSITNRelation(long id) {
		super(id, RELATION);
	}

	@Override
	public String toString() {
		return "Relation (" + getId() + ", " + members.size() + " members)";
	}

	public ArrayList<ITNMember> getMembers() {
		return members;
	}

	public boolean isMetaRelation() {
		for (ITNMember member : members) {
			if (member.type() == RELATION) {
				return true;
			}
		}
		return false;
	}

	public boolean isMixedRelation() {
		boolean hasRel = false;
		boolean hasOther = false;

		for (ITNMember member : members) {
			if (member.type() == RELATION) {
				hasRel = true;
			} else {
				hasOther = true;
			}

			if (hasRel && hasOther) {
				return true;
			}
		}
		return false;
	}

	public void removeRelations() {
		for (int i = members.size() - 1; i >= 0; i--) {
			if (members.get(i).type() == RELATION) {
				members.remove(i);
			}
		}
	}

	public void add(ITNMember member) {
		members.add(member);
	}

	/**
	 * Container class for relation members
	 */
	public static class ITNMember implements RelationMember {
		public static final int NODE = 0;
		public static final int WAY = 1;
		public static final int RELATION = 2;
		private static final String typeDecode = "nwr";
		private int type;
		private long ref;
		private String role;

		public ITNMember(XMLStreamReader parser) {
			String typeName = parser.getAttributeValue(null, "type");
			type = typeDecode.indexOf(typeName.charAt(0));
			ref = Long.parseLong(parser.getAttributeValue(null, "ref"));
			role = parser.getAttributeValue(null, "role");
		}

		public ITNMember(ITNMember input) {
			type = input.type;
			ref = input.ref;
			role = input.role;
		}

		public ITNMember(int type, long ref, String role) {
			this.type = type;
			this.ref = ref;
			this.role = role;
		}

		public String toString() {
			return "Member " + type + ":" + ref;
		}

		public int type() {
			return type;
		}

		public String role() {
			return role;
		}

		public long ref() {
			return ref;
		}
	}

	@Override
	protected void parseCoords(String elementText) {
		// TODO Auto-generated method stub

	}

	@Override
	protected void parseNetworkMember(String elementText) {
		// TODO Auto-generated method stub

	}

	@Override
	protected void addDirectedNode(String nodeId, String grade, String orientation) {
		// TODO Auto-generated method stub

	}

	@Override
	protected void addDirectedLink(String nodeId, String orientation) {
		int size = members.size();
		if (size < 2) {  //TODO this will break multi way information but helps me simplify multi way instruction for now
			String idStr = nodeId.substring(5);
			ITNMember member = new ITNMember(WAY, Long.valueOf(idStr),
					0 == size ? "from" : "to");
			add(member);
		}
	}
}
