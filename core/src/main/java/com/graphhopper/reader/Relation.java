package com.graphhopper.reader;

import java.util.ArrayList;
import java.util.List;

import com.graphhopper.reader.OSMRelation.Member;



public interface Relation extends RoutingElement {
	ArrayList<? extends RelationMember> getMembers();
}
