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
    protected OsmNodeTranslation osmNodeTranslation;
    protected boolean is3D = false;
    private final TLongList barrierNodeIDs = new TLongArrayList();
    protected DataAccess pillarLats;
    protected DataAccess pillarLons;
    private DistanceCalc distCalc = new DistanceCalc();
    private DouglasPeucker dpAlgo = new DouglasPeucker();
    private int towerId = 0;
    private int pillarId = 0;
    // negative but increasing to avoid clash with custom created OSM files
    private long newUniqueOSMId = -Long.MAX_VALUE;

    public OSMReaderHelper( GraphStorage g, long expectedCap )
    {
        this.g = g;
        this.expectedNodes = expectedCap;
        osmNodeTranslation = new OsmNodeTranslation2D();

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
        int nodeType = osmNodeTranslation.getIndex(node.getId());
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
            osmNodeTranslation.putIndex(node.getId(), pillarId + 3);
            pillarId++;
        }
        return true;
    }

    String getInfo()
    {
        return "Found " + zeroCounter + " zero distances.";
    }

    /**
     * Count usage to determine type during preprocessing
     * @param osmId
     */
    public void prepareHighwayNode( long osmId )
    {
        int tmpIndex = osmNodeTranslation.getIndex(osmId);
        if (tmpIndex == OSMReaderHelper.EMPTY)
        {
            // osmId is used exactly once
            osmNodeTranslation.putIndex(osmId, OSMReaderHelper.PILLAR_NODE);
        } else if (tmpIndex > OSMReaderHelper.EMPTY)
        {
            // mark node as tower node as it occured at least twice times
            osmNodeTranslation.putIndex(osmId, OSMReaderHelper.TOWER_NODE);
        } else
        {
            // tmpIndex is already negative (already tower node)
        }
    }

    public void preprocessingFinished()
    {
        osmNodeTranslation.optimize();
    }

    protected int addTowerNode( long osmId, double lat, double lon )
    {
        g.setNode(towerId, lat, lon);
        int id = -(towerId + 3);
        osmNodeTranslation.putIndex(osmId, id);
        towerId++;
        return id;
    }

    public long getFoundNodes()
    {
        return osmNodeTranslation.getSize();
    }

    /**
     * This method creates from an OSM way (via the osm ids) one or more edges in the graph.
     */
    public Collection<EdgeIterator> addOSMWay( TLongList osmNodeIds, int flags, GeometryAccess geometryAccess )
    {
        PointList pointList = new PointList(osmNodeIds.size(), is3D);
        List<EdgeIterator> newEdges = new ArrayList<EdgeIterator>(5);
        int firstNode = -1;
        int lastIndex = osmNodeIds.size() - 1;
        int lastInBoundsPillarNode = -1;
        long lastInBoundsOsmId = 0;
        int[] coords = new int[3];
        try
        {
            for (int i = 0; i < osmNodeIds.size(); i++)
            {
                long osmId = osmNodeIds.get(i);
                int tmpNode = osmNodeTranslation.getIndex(osmId);
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
                        geometryAccess.getNode(lastInBoundsOsmId, coords);
                        tmpNode = handlePillarNode(tmpNode, osmId, null, true, coords);
                        tmpNode = -tmpNode - 3;
                        if (pointList.getSize() > 1 && firstNode >= 0)
                        {
                            // TOWER node
                            newEdges.add(addEdge(firstNode, tmpNode, pointList, flags));
                            pointList.clear();
                            // todo: is this correct? Just connecting in spite of missing nodes will create invalid shortcuts.
//                            pointList.add(g.getLatitude(tmpNode), g.getLongitude(tmpNode), geometryAccess.getElevation(lastInBoundsOsmId));
                            pointList.add(coords);
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

                // handle normal pillar nodes
                if (tmpNode > -TOWER_NODE)
                {
                    boolean convertToTowerNode = i == 0 || i == lastIndex;
                    if (!convertToTowerNode)
                    {
                        lastInBoundsPillarNode = tmpNode;
                        lastInBoundsOsmId = osmId;
                    }

                    // PILLAR node, but convert to towerNode if end-standing
                    geometryAccess.getNode( osmId, coords );
                    tmpNode = handlePillarNode(tmpNode, osmId, pointList, convertToTowerNode, coords);
                }

                // handle normal tower nodes
                if (tmpNode < TOWER_NODE)
                {
                    // TOWER node
                    tmpNode = -tmpNode - 3;
                    geometryAccess.getNode( osmId, coords );
                    pointList.add( coords );
                    //pointList.add(g.getLatitude(tmpNode), g.getLongitude(tmpNode), geometryAccess.getElevation( osmId ));
                    if (firstNode >= 0)
                    {
                        newEdges.add(addEdge(firstNode, tmpNode, pointList, flags));
                        pointList.clear();
                        pointList.add(coords);
//                        pointList.add(g.getLatitude(tmpNode), g.getLongitude(tmpNode));
                    }
                    firstNode = tmpNode;
                }
            }
        } catch (RuntimeException ex)
        {
            logger.error("Couldn't properly add edge with osm ids:" + osmNodeIds, ex);
        }
        return newEdges;
    }

    EdgeIterator addEdge( int fromIndex, int toIndex, PointList pointList, int flags )
    {
        if (fromIndex < 0 || toIndex < 0)
        {
            throw new AssertionError("to or from index is invalid for this edge "
                    + fromIndex + "->" + toIndex + ", points:" + pointList);
        }

        double towerNodeDistance = 0;
        double prevLat = pointList.getLatitude(0);
        double prevLon = pointList.getLongitude(0);
        double prevEle = pointList.getElevation(0);
        double lat;
        double lon;
        double ele;
        PointList pillarNodes = new PointList(pointList.getSize() - 2, is3D);
        int nodes = pointList.getSize();
        for (int i = 1; i < nodes; i++)
        {
            lat = pointList.getLatitude(i);
            lon = pointList.getLongitude(i);
            ele = pointList.getElevation(i);
            double edgeDistance = distCalc.calcDist(prevLat, prevLon, lat, lon);
            // longer ways due to elevations. Use pythagoras ignoring spherical geometry.
            if( is3D )
                edgeDistance = distCalc.calcOrthogonal( edgeDistance, ele-prevEle );
            towerNodeDistance += edgeDistance;
            prevLat = lat;
            prevLon = lon;
            prevEle = ele;
            if (nodes > 2 && i < nodes - 1)
            {
                pillarNodes.add(lat, lon, ele);
            }
        }
        if (towerNodeDistance == 0)
        {
            // As investigation shows often two paths should have crossed via one identical point 
            // but end up in two very close points.
            zeroCounter++;
            towerNodeDistance = 0.0001;
        }

        EdgeIterator iter = g.edge(fromIndex, toIndex, towerNodeDistance, flags);
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
                                  boolean convertToTowerNode, int[] coords )
    {
        tmpNode = tmpNode - 3;
        if (convertToTowerNode)
        {
            // convert pillarNode type to towerNode, make pillar values invalid
            pillarLons.setInt(tmpNode * 4, Integer.MAX_VALUE);
            pillarLats.setInt(tmpNode * 4, Integer.MAX_VALUE);
            tmpNode = addTowerNode(osmId, Helper.intToDegree(coords[0]), Helper.intToDegree(coords[1]));
        } else
        {
            pointList.add(coords);
        }

        return tmpNode;
    }

    private void printInfo( String str )
    {
        LoggerFactory.getLogger(getClass()).info("finished " + str + " processing."
                + " nodes: " + g.getNodes() + ", osmIdMap.size:" + osmNodeTranslation.getSize()
                + ", osmIdMap:" + osmNodeTranslation.getMemoryUsage() + "MB"
                + ", osmIdMap.toString:" + osmNodeTranslation + " "
                + Helper.getMemInfo());
    }

    void finishedReading()
    {
        printInfo("way");
        dir.remove(pillarLats);
        dir.remove(pillarLons);
        pillarLons = null;
        pillarLats = null;
        osmNodeTranslation = null;
    }

    @Override
    public String toString()
    {
        return getClass().getSimpleName();
    }

    public OsmNodeTranslation getNodeMap()
    {
        return osmNodeTranslation;
    }

    /**
     * Create a copy of the barrier node
     */
    public long addBarrierNode( long nodeId, GeometryAccess geometryAccess )
    {
        // create node
        int node[] = new int[3];
        geometryAccess.getNode( nodeId, node );

        OSMNode newNode = new OSMNode(createNewNodeId(),
                    Helper.intToDegree(node[0]), Helper.intToDegree(node[1]));

        // add the new node
        final long id = newNode.getId();
        osmNodeTranslation.copyNode(nodeId, id, OSMReaderHelper.TOWER_NODE);
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
    public Collection<EdgeIterator> addBarrierEdge( long fromId, long toId, int flags, int barrierFlags,
                                                    GeometryAccess geometryAccess )
    {
        // clear barred directions from routing flags
        flags &= ~barrierFlags;
        // add edge
        barrierNodeIDs.clear();
        barrierNodeIDs.add(fromId);
        barrierNodeIDs.add(toId);
        return addOSMWay(barrierNodeIDs, flags, geometryAccess );
    }

    public int getNodeIndex( long osmId )
    {
        return osmNodeTranslation.getIndex(osmId);
    }

    public void getLocation( int index, int[] latLon )
    {
        index *= 4;
        latLon[0] = pillarLats.getInt(index);
        latLon[1] = pillarLons.getInt(index);

        if (latLon[0] == Integer.MAX_VALUE || latLon[1] == Integer.MAX_VALUE)
        {
            throw new RuntimeException("Pillar entry has been removed pillarIndex:" + index/4 );
        }
    }

    public void activateElevations()
    {
        is3D = true;
        osmNodeTranslation = new OsmNodeTranslation3D();
    }

}
