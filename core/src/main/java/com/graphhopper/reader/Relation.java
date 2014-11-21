package com.graphhopper.reader;

import java.util.ArrayList;



public interface Relation extends RoutingElement {
	ArrayList<? extends RelationMember> getMembers();

	boolean isMetaRelation();
}
