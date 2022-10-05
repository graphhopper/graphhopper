package com.graphhopper.reader.osm;

import static java.util.Collections.emptyMap;

import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.carrotsearch.hppc.LongArrayList;
import com.carrotsearch.hppc.cursors.LongCursor;
import com.graphhopper.reader.NodeRestriction;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.reader.WayRestriction;
import com.graphhopper.util.PointList;

public class OSMTurnRestrictionBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(OSMTurnRestrictionBuilder.class);
    public long nextArtificialOSMWayId = -Long.MAX_VALUE;
    
    private OSMNodeData nodeData;
    private OSMTurnRestrictionData restrictionData;
    private WayHandler wayHandler;
    
    public OSMTurnRestrictionBuilder(OSMNodeData nodeData, OSMTurnRestrictionData restrictionData, WayHandler wayHandler) {
    	this.nodeData = nodeData;
    	this.restrictionData = restrictionData;
    	this.wayHandler = wayHandler;
    }
    
    public void buildRestrictions() {
        for (WayRestriction restriction : restrictionData.wayRestrictions) {
            // for now we only work with single via-way restrictions 
            if (restriction.getWays().size() == 3) {
            	if (!allWaysHaveTwoTowerNodes(restriction, nodeData)) {
            		LOGGER.info("failed: " + restriction.getId() + " - too much Tower Nodes");
            		fallback(restriction);
            		continue;
            	}
                restriction.buildRestriction(restrictionData.osmWayMap);
                if (!restriction.isValid()) {
                    LOGGER.info("failed: " + restriction.getId() + " - invalid Restriction");
                    fallback(restriction);
                    continue;
                }
                try {
                    NodeRestriction r = restriction.getRestrictions().get(0);
                    NodeRestriction r2 = restriction.getRestrictions().get(1);
    
                    createArtificialViaNode(r.getVia(), nodeData);
    
                    // manipulate the first edge
                    // 1.
                    int from = nodeData.idToTowerNode(nodeData.getId(restriction.getStartNode()));
                    int to = nodeData.idToTowerNode(nodeData.getId(restrictionData.artificialViaNodes.get(r.getVia())));
                    ReaderWay way = restrictionData.osmWayMap.get(r.getFrom());
                    long newOsmId = nextArtificialOSMWayId++;
                    restrictionData.osmWayIdSet.add(newOsmId);
                    ReaderWay artificial_way = new ReaderWay(newOsmId, way.getTags(), way.getNodes());
                    LongArrayList nodes = way.getNodes();
                    PointList pointList = new PointList(nodes.size(), nodeData.is3D());
                    for (LongCursor point : nodes) {
                        nodeData.addCoordinatesToPointList(nodeData.getId(point.value), pointList);
                    }
                    
                    wayHandler.addEdge(from, to, pointList, artificial_way, emptyMap());
    
                    // 2.
                    int from2 = nodeData.idToTowerNode(nodeData.getId(restrictionData.artificialViaNodes.get(r.getVia())));
                    int to2 = nodeData.idToTowerNode(nodeData.getId(r.getVia()));
                    PointList pointList2 = new PointList(2, nodeData.is3D());
                    long newOsmId2 = nextArtificialOSMWayId++;
                    restrictionData.osmWayIdSet.add(newOsmId2);
                    LongArrayList longalist = new LongArrayList();
                    longalist.add(restrictionData.artificialViaNodes.get(r.getVia()));
                    longalist.add(r.getVia());
                    ReaderWay artificial_way2 = new ReaderWay(newOsmId2, way.getTags(), longalist);
                    nodeData.addCoordinatesToPointList(nodeData.towerNodeToId(from2), pointList2);
                    nodeData.addCoordinatesToPointList(nodeData.towerNodeToId(to2), pointList2);
    
                    wayHandler.addEdge(from2, to2, pointList2, artificial_way2, emptyMap());
    
                    // second edge
                    int from3 = nodeData.idToTowerNode(nodeData.getId(r.getVia()));
                    int to3 = nodeData.idToTowerNode(nodeData.getId(r2.getVia()));
                    ReaderWay way3 = restrictionData.osmWayMap.get(r.getTo());
                    long newOsmId3 = nextArtificialOSMWayId++;
                    restrictionData.osmWayIdSet.add(newOsmId3);
                    ReaderWay artificial_way3 = new ReaderWay(newOsmId3, way3.getTags(), way3.getNodes());
                    LongArrayList nodes3 = way3.getNodes();
                    PointList pointList3 = new PointList(nodes3.size(), nodeData.is3D());
                    for (LongCursor point : nodes3) {
                        nodeData.addCoordinatesToPointList(nodeData.getId(point.value), pointList3);
                    }
    
                    wayHandler.addEdge(from3, to3, pointList3, artificial_way3, emptyMap());
    
                    ArrayList<NodeRestriction> restrictions = new ArrayList<>();
                    restrictions.add(new NodeRestriction(newOsmId2, r.getVia(), r.getTo()));
                    restrictions.add(new NodeRestriction(newOsmId3, r2.getVia(), r2.getTo()));
                    restrictions.add(new NodeRestriction(newOsmId2, restrictionData.artificialViaNodes.get(r.getVia()), newOsmId3));
                    restrictionData.artificialNodeRestrictions.put(restriction.getId(), restrictions);
                } catch (Exception e) {
                    
                }
                LOGGER.info("success: " + restriction.getId());
            }
        }
    }

    private boolean allWaysHaveTwoTowerNodes(WayRestriction restriction, OSMNodeData nodeData) {
    	boolean allWaysHaveTwoTowerNodes = true;
    	for (Long w : restriction.getWays()) {
    		ReaderWay way = restrictionData.osmWayMap.get(w);
    		if (way == null) 
    			break;
    		if (getTowerNodeCount(way, nodeData) != 2)
    			allWaysHaveTwoTowerNodes = false;
    			break;
    	}
    	return allWaysHaveTwoTowerNodes;
	}
    
    private int getTowerNodeCount(ReaderWay way, OSMNodeData nodeData) {
    	int towerCount = 0;
		for (LongCursor node : way.getNodes()) {
			int id = nodeData.getId(node.value);
			if (OSMNodeData.isTowerNode(id))
				towerCount++;
		}
		return towerCount;
    }
    
    protected void createArtificialViaNode(Long via, OSMNodeData nodeData) {
        SegmentNode artificalNode = nodeData.addCopyOfNodeAsTowerNode(new SegmentNode(via, nodeData.getId(via)));
        restrictionData.artificialViaNodes.put(via, artificalNode.osmNodeId);
    }
    
    protected void fallback(WayRestriction restriction) {
    	// handle the ignored start way if restriction is invalid
        long startId = restriction.getWays().get(0);
        if (restrictionData.osmWayIdsToIgnore.contains(startId)){
        	restrictionData.osmWayIdsToIgnore.remove(startId);
            ReaderWay startWay = restrictionData.osmWayMap.get(startId);
            if (startWay != null) {
                wayHandler.handleWay(startWay);
            }
        }
    }
    
}
