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

import com.graphhopper.coll.GHLongIntBTree;
import com.graphhopper.coll.LongIntMap;
import com.graphhopper.storage.DataAccess;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.util.*;
import gnu.trove.list.TLongList;

import gnu.trove.list.array.TLongArrayList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Nop
 * @author Peter Karich
 */
public class OSMReaderHelper
{
    protected static final int EMPTY = -1;
    // pillar node is >= 3
    protected static final int PILLAR_NODE = 1;
    // tower node is <= -3
    protected static final int TOWER_NODE = -2;
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    protected final Directory dir;
    protected long zeroCounter = 0;
    protected final Graph g;
    protected final long expectedNodes;
    // Using the correct Map<Long, Integer> is hard. We need a memory efficient and fast solution for big data sets!
    //
    // very slow: new SparseLongLongArray
    // only append and update possible (no unordered storage like with this doubleParse): new OSMIDMap
    // same here: not applicable as ways introduces the nodes in 'wrong' order: new OSMIDSegmentedMap
    // memory overhead due to open addressing and full rehash:
    //        nodeOsmIdToIndexMap = new BigLongIntMap(expectedNodes, EMPTY);
    // smaller memory overhead for bigger data sets because of avoiding a "rehash"
    // remember how many times a node was used to identify tower nodes
    private LongIntMap osmNodeIdToIndexMap;
    private final TLongList barrierNodeIDs = new TLongArrayList();
    protected DataAccess pillarLats;
    protected DataAccess pillarLons;
    private DistanceCalc distCalc = new DistanceCalc();
    private DouglasPeucker dpAlgo = new DouglasPeucker();
    private int towerId = 0;
    private int pillarId = 0;
    // negative but increasing to avoid clash with custom created OSM files
    private long newUniqueOSMId = -Long.MAX_VALUE;
    private boolean exitOnlyPillarNodeException = true;

    public OSMReaderHelper( GraphStorage g, long expectedCap )
    {
        this.g = g;
        this.expectedNodes = expectedCap;
        osmNodeIdToIndexMap = new GHLongIntBTree(200);
        dir = g.getDirectory();
        pillarLats = dir.find("tmpLatitudes");
        pillarLons = dir.find("tmpLongitudes");

        pillarLats.create(Math.max(expectedCap, 100));
        pillarLons.create(Math.max(expectedCap, 100));
    }

    public OSMReaderHelper setWayPointMaxDistance( double maxDist )
    {
        dpAlgo.setMaxDistance(maxDist);
        return this;
    }

    public long getEdgeCount()
    {
        return g.getAllEdges().getMaxId();
    }

    public boolean addNode( OSMNode node )
    {
        int nodeType = osmNodeIdToIndexMap.get(node.getId());
        if (nodeType == EMPTY)
        {
            return false;
        }

        double lat = node.getLat();
        double lon = node.getLon();
        if (nodeType == TOWER_NODE)
        {
            addTowerNode(node.getId(), lat, lon);
        } else if (nodeType == PILLAR_NODE)
        {
            int tmp = pillarId * 4;
            pillarLats.ensureCapacity(tmp + 4);
            pillarLats.setInt(tmp, Helper.degreeToInt(lat));
            pillarLons.ensureCapacity(tmp + 4);
            pillarLons.setInt(tmp, Helper.degreeToInt(lon));
            osmNodeIdToIndexMap.put(node.getId(), pillarId + 3);
            pillarId++;
        }
        return true;
    }

    String getInfo()
    {
        return "Found " + zeroCounter + " zero distances.";
    }

    public void prepareHighwayNode( long osmId )
    {
        int tmpIndex = osmNodeIdToIndexMap.get(osmId);
        if (tmpIndex == OSMReaderHelper.EMPTY)
        {
            // osmId is used exactly once
            osmNodeIdToIndexMap.put(osmId, OSMReaderHelper.PILLAR_NODE);
        } else if (tmpIndex > OSMReaderHelper.EMPTY)
        {
            // mark node as tower node as it occured at least twice times
            osmNodeIdToIndexMap.put(osmId, OSMReaderHelper.TOWER_NODE);
        } else
        {
            // tmpIndex is already negative (already tower node)
        }
    }

    protected int addTowerNode( long osmId, double lat, double lon )
    {
        g.setNode(towerId, lat, lon);
        int id = -(towerId + 3);
        osmNodeIdToIndexMap.put(osmId, id);
        towerId++;
        return id;
    }

    public long getFoundNodes()
    {
        return osmNodeIdToIndexMap.getSize();
    }

    /**
     * This method creates from an OSM way (via the osm ids) one or more edges in the graph.
     */
    public Collection<EdgeIteratorState> addOSMWay( TLongList osmNodeIds, int flags )
    {
        PointList pointList = new PointList(osmNodeIds.size());
        List<EdgeIteratorState> newEdges = new ArrayList<EdgeIteratorState>(5);
        int firstNode = -1;
        int lastIndex = osmNodeIds.size() - 1;
        int lastInBoundsPillarNode = -1;
        try
        {
            for (int i = 0; i < osmNodeIds.size(); i++)
            {
                long osmId = osmNodeIds.get(i);
                int tmpNode = osmNodeIdToIndexMap.get(osmId);
                if (tmpNode == EMPTY)
                {
                    continue;
                }
                // skip osmIds with no associated pillar or tower id (e.g. !OSMReader.isBounds)
                if (tmpNode == TOWER_NODE)
                {
                    continue;
                }
                if (tmpNode == PILLAR_NODE)
                {
                    // In some cases no node information is saved for the specified osmId.
                    // ie. a way references a <node> which does not exist in the current file.
                    // => if the node before was a pillar node then convert into to tower node (as it is also end-standing).
                    if (!pointList.isEmpty() && lastInBoundsPillarNode > -TOWER_NODE)
                    {
                        // transform the pillar node to a tower node
                        tmpNode = lastInBoundsPillarNode;
                        tmpNode = handlePillarNode(tmpNode, osmId, null, true);
                        tmpNode = -tmpNode - 3;
                        if (pointList.getSize() > 1 && firstNode >= 0)
                        {
                            // TOWER node
                            newEdges.add(addEdge(firstNode, tmpNode, pointList, flags));
                            pointList.clear();
                            pointList.add(g.getLatitude(tmpNode), g.getLongitude(tmpNode));
                        }
                        firstNode = tmpNode;
                        lastInBoundsPillarNode = -1;
                    }
                    continue;
                }

                if (tmpNode <= -TOWER_NODE && tmpNode >= TOWER_NODE)
                {
                    throw new AssertionError("Mapped index not in correct bounds " + tmpNode + ", " + osmId);
                }

                if (tmpNode > -TOWER_NODE)
                {
                    boolean convertToTowerNode = i == 0 || i == lastIndex;
                    if (!convertToTowerNode)
                    {
                        lastInBoundsPillarNode = tmpNode;
                    }

                    // PILLAR node, but convert to towerNode if end-standing
                    tmpNode = handlePillarNode(tmpNode, osmId, pointList, convertToTowerNode);
                }

                if (tmpNode < TOWER_NODE)
                {
                    // TOWER node
                    tmpNode = -tmpNode - 3;
                    pointList.add(g.getLatitude(tmpNode), g.getLongitude(tmpNode));
                    if (firstNode >= 0)
                    {
                        newEdges.add(addEdge(firstNode, tmpNode, pointList, flags));
                        pointList.clear();
                        pointList.add(g.getLatitude(tmpNode), g.getLongitude(tmpNode));
                    }
                    firstNode = tmpNode;
                }
            }
        } catch (RuntimeException ex)
        {
            logger.error("Couldn't properly add edge with osm ids:" + osmNodeIds, ex);
            if (exitOnlyPillarNodeException)
                throw ex;
        }
        return newEdges;
    }

    EdgeIteratorState addEdge( int fromIndex, int toIndex, PointList pointList, int flags )
    {
        if (fromIndex < 0 || toIndex < 0)
        {
            throw new AssertionError("to or from index is invalid for this edge "
                    + fromIndex + "->" + toIndex + ", points:" + pointList);
        }

        double towerNodeDistance = 0;
        double prevLat = pointList.getLatitude(0);
        double prevLon = pointList.getLongitude(0);
        double lat;
        double lon;
        PointList pillarNodes = new PointList(pointList.getSize() - 2);
        int nodes = pointList.getSize();
        for (int i = 1; i < nodes; i++)
        {
            lat = pointList.getLatitude(i);
            lon = pointList.getLongitude(i);
            towerNodeDistance += distCalc.calcDist(prevLat, prevLon, lat, lon);
            prevLat = lat;
            prevLon = lon;
            if (nodes > 2 && i < nodes - 1)
            {
                pillarNodes.add(lat, lon);
            }
        }
        if (towerNodeDistance == 0)
        {
            // As investigation shows often two paths should have crossed via one identical point 
            // but end up in two very close points.
            zeroCounter++;
            towerNodeDistance = 0.0001;
        }

        EdgeIteratorState iter = g.edge(fromIndex, toIndex, towerNodeDistance, flags);
        if (nodes > 2)
        {
            dpAlgo.simplify(pillarNodes);
            iter.setWayGeometry(pillarNodes);
        }
        return iter;
    }

    /**
     * @return converted tower node
     */
    private int handlePillarNode( int tmpNode, long osmId, PointList pointList,
            boolean convertToTowerNode )
    {
        tmpNode = tmpNode - 3;
        int intlat = pillarLats.getInt(tmpNode * 4);
        int intlon = pillarLons.getInt(tmpNode * 4);
        if (intlat == Integer.MAX_VALUE || intlon == Integer.MAX_VALUE)
        {
            throw new RuntimeException("Conversion pillarNode to towerNode already happended!? "
                    + "osmId:" + osmId + " pillarIndex:" + tmpNode);
        }

        double tmpLat = Helper.intToDegree(intlat);
        double tmpLon = Helper.intToDegree(intlon);

        if (convertToTowerNode)
        {
            // convert pillarNode type to towerNode, make pillar values invalid
            pillarLons.setInt(tmpNode * 4, Integer.MAX_VALUE);
            pillarLats.setInt(tmpNode * 4, Integer.MAX_VALUE);
            tmpNode = addTowerNode(osmId, tmpLat, tmpLon);
        } else
        {
            pointList.add(tmpLat, tmpLon);
        }

        return tmpNode;
    }

    private void printInfo( String str )
    {
        LoggerFactory.getLogger(getClass()).info("finished " + str + " processing."
                + " nodes: " + g.getNodes() + ", osmIdMap.size:" + osmNodeIdToIndexMap.getSize()
                + ", osmIdMap:" + osmNodeIdToIndexMap.getMemoryUsage() + "MB"
                + ", osmIdMap.toString:" + osmNodeIdToIndexMap + " "
                + Helper.getMemInfo());
    }

    void finishedReading()
    {
        // todo: is this necessary before removing it?
        osmNodeIdToIndexMap.optimize();
        printInfo("way");
        dir.remove(pillarLats);
        dir.remove(pillarLons);
        pillarLons = null;
        pillarLats = null;
        osmNodeIdToIndexMap = null;
    }

    @Override
    public String toString()
    {
        return getClass().getSimpleName();
    }

    public LongIntMap getNodeMap()
    {
        return osmNodeIdToIndexMap;
    }

    /**
     * Create a copy of the barrier node
     */
    public long addBarrierNode( long nodeId )
    {
        // create node
        OSMNode newNode;
        int graphIndex = osmNodeIdToIndexMap.get(nodeId);
        if (graphIndex < TOWER_NODE)
        {
            graphIndex = -graphIndex - 3;
            newNode = new OSMNode(createNewNodeId(),
                    g.getLatitude(graphIndex), g.getLongitude(graphIndex));
        } else
        {
            graphIndex = graphIndex - 3;
            newNode = new OSMNode(createNewNodeId(),
                    Helper.intToDegree(pillarLats.getInt(graphIndex * 4)),
                    Helper.intToDegree(pillarLons.getInt(graphIndex * 4)));
        }

        final long id = newNode.getId();
        prepareHighwayNode(id);
        addNode(newNode);

        return id;
    }

    private long createNewNodeId()
    {
        return newUniqueOSMId++;
    }

    /**
     * Add a zero length edge with reduced routing options to the graph.
     */
    public Collection<EdgeIteratorState> addBarrierEdge( long fromId, long toId, int flags, int barrierFlags )
    {
        // clear barred directions from routing flags
        flags &= ~barrierFlags;
        // add edge
        barrierNodeIDs.clear();
        barrierNodeIDs.add(fromId);
        barrierNodeIDs.add(toId);
        return addOSMWay(barrierNodeIDs, flags);
    }
}
