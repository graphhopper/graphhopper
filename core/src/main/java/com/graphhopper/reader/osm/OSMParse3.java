package com.graphhopper.reader.osm;

import com.graphhopper.reader.ReaderNode;
import com.graphhopper.reader.ReaderRelation;
import com.graphhopper.reader.ReaderWay;

public class OSMParse3 implements OSMParseInterface {
	private NodeHandler nodeHandler;
	private WayHandler wayHandler;
	private RelationHandler relationHandler;
	
	public OSMParse3(NodeHandler nodeHandler, WayHandler wayHandler, RelationHandler relationHandler) {
		this.nodeHandler = nodeHandler;
		this.wayHandler = wayHandler;
		this.relationHandler = relationHandler;
	}
	
    @Override
    public void handleNode(ReaderNode node) {
    	nodeHandler.handleNode(node);
    }
	
    @Override
    public void handleWay(ReaderWay way) {
    	wayHandler.handleWay(way);
    }

    @Override
    public void handleRelation(ReaderRelation relation) {
    	relationHandler.handleRelation(relation);
    }
}
