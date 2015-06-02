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

import static com.graphhopper.util.Helper.nf;

import gnu.trove.list.TLongList;
import gnu.trove.list.array.TLongArrayList;
import gnu.trove.map.TIntLongMap;
import gnu.trove.map.TLongLongMap;
import gnu.trove.map.hash.TIntLongHashMap;
import gnu.trove.map.hash.TLongLongHashMap;
import gnu.trove.set.TLongSet;
import gnu.trove.set.hash.TLongHashSet;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.xml.stream.XMLStreamException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.graphhopper.coll.GHLongIntBTree;
import com.graphhopper.coll.LongIntMap;
import com.graphhopper.reader.OSMTurnRelation.TurnCostTableEntry;
import com.graphhopper.reader.dem.ElevationProvider;
import com.graphhopper.routing.util.*;
import com.graphhopper.storage.*;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.GHPoint;
import gnu.trove.map.TLongObjectMap;
import gnu.trove.map.hash.TLongObjectHashMap;

import java.util.*;

/**
 * This class parses an OSM xml or pbf file and creates a graph from it. It does so in a two phase
 * parsing processes in order to reduce memory usage compared to a single parsing processing.
 * <p/>
 * 1. a) Reads ways from OSM file and stores all associated node ids in osmNodeIdToIndexMap. If a
 * node occurs once it is a pillar node and if more it is a tower node, otherwise
 * osmNodeIdToIndexMap returns EMPTY.
 * <p/>
 * 1. b) Reads relations from OSM file. In case that the relation is a route relation, it stores
 * specific relation attributes required for routing into osmWayIdToRouteWeigthMap for all the ways
 * of the relation.
 * <p/>
 * 2.a) Reads nodes from OSM file and stores lat+lon information either into the intermediate
 * datastructure for the pillar nodes (pillarLats/pillarLons) or, if a tower node, directly into the
 * graphStorage via setLatitude/setLongitude. It can also happen that a pillar node needs to be
 * transformed into a tower node e.g. via barriers or different speed values for one way.
 * <p/>
 * 2.b) Reads ways OSM file and creates edges while calculating the speed etc from the OSM tags.
 * When creating an edge the pillar node information from the intermediate datastructure will be
 * stored in the way geometry of that edge.
 * <p/>
 * @author Peter Karich
 */
public class OSMReader implements DataReader
{
    protected static final int EMPTY = -1;
    // pillar node is >= 3
    protected static final int PILLAR_NODE = 1;
    // tower node is <= -3
    protected static final int TOWER_NODE = -2;
    private static final Logger logger = LoggerFactory.getLogger(OSMReader.class);
    private long locations;
    private long skippedLocations;
    private final GraphStorage graphStorage;
    private final NodeAccess nodeAccess;
    private EncodingManager encodingManager = null;
    private int workerThreads = -1;
    protected long zeroCounter = 0;
    // Using the correct Map<Long, Integer> is hard. We need a memory efficient and fast solution for big data sets!
    //
    // very slow: new SparseLongLongArray
    // only append and update possible (no unordered storage like with this doubleParse): new OSMIDMap
    // same here: not applicable as ways introduces the nodes in 'wrong' order: new OSMIDSegmentedMap
    // memory overhead due to open addressing and full rehash:
    //        nodeOsmIdToIndexMap = new BigLongIntMap(expectedNodes, EMPTY);
    // smaller memory overhead for bigger data sets because of avoiding a "rehash"
    // remember how many times a node was used to identify tower nodes
    private LongIntMap osmNodeIdToInternalNodeMap;
    private TLongLongHashMap osmNodeIdToNodeFlagsMap;
    private TLongLongHashMap osmWayIdToRouteWeightMap;
    // stores osm way ids used by relations to identify which edge ids needs to be mapped later
    private TLongHashSet osmWayIdSet = new TLongHashSet();
    private TIntLongMap edgeIdToOsmWayIdMap;
    private final TLongList barrierNodeIds = new TLongArrayList();
    protected PillarInfo pillarInfo;
    private final DistanceCalc distCalc = Helper.DIST_EARTH;
    private final DistanceCalc3D distCalc3D = Helper.DIST_3D;
    private final DouglasPeucker simplifyAlgo = new DouglasPeucker();
    private boolean doSimplify = true;
    private int nextTowerId = 0;
    private int nextPillarId = 0;
    // negative but increasing to avoid clash with custom created OSM files
    private long newUniqueOsmId = -Long.MAX_VALUE;
    private ElevationProvider eleProvider = ElevationProvider.NOOP;
    private boolean exitOnlyPillarNodeException = true;
    private File osmFile;
    private Map<FlagEncoder, EdgeExplorer> outExplorerMap = new HashMap<FlagEncoder, EdgeExplorer>();
    private Map<FlagEncoder, EdgeExplorer> inExplorerMap = new HashMap<FlagEncoder, EdgeExplorer>();

    public OSMReader( GraphStorage storage )
    {
        this.graphStorage = storage;
        this.nodeAccess = graphStorage.getNodeAccess();

        osmNodeIdToInternalNodeMap = new GHLongIntBTree(200);
        osmNodeIdToNodeFlagsMap = new TLongLongHashMap(200, .5f, 0, 0);
        osmWayIdToRouteWeightMap = new TLongLongHashMap(200, .5f, 0, 0);
        pillarInfo = new PillarInfo(nodeAccess.is3D(), graphStorage.getDirectory());
    }

    @Override
    public void readGraph() throws IOException
    {
        if (encodingManager == null)
            throw new IllegalStateException("Encoding manager was not set.");

        if (osmFile == null)
            throw new IllegalStateException("No OSM file specified");

        if (!osmFile.exists())
            throw new IllegalStateException("Your specified OSM file does not exist:" + osmFile.getAbsolutePath());

        StopWatch sw1 = new StopWatch().start();
        preProcess(osmFile);
        sw1.stop();

        StopWatch sw2 = new StopWatch().start();
        writeOsm2Graph(osmFile);
        sw2.stop();

        logger.info("time(pass1): " + (int) sw1.getSeconds() + " pass2: " + (int) sw2.getSeconds() + " total:"
                + ((int) (sw1.getSeconds() + sw2.getSeconds())));
    }

    /**
     * Preprocessing of OSM file to select nodes which are used for highways. This allows a more
     * compact graph data structure.
     */
    void preProcess( File osmFile )
    {
        OSMInputFile in = null;
        try
        {
            in = new OSMInputFile(osmFile).setWorkerThreads(workerThreads).open();

            long tmpWayCounter = 1;
            long tmpRelationCounter = 1;
            OSMElement item;
            while ((item = in.getNext()) != null)
            {
                if (item.isType(OSMElement.WAY))
                {
                    final OSMWay way = (OSMWay) item;
                    boolean valid = filterWay(way);
                    if (valid)
                    {
                        TLongList wayNodes = way.getNodes();
                        int s = wayNodes.size();
                        for (int index = 0; index < s; index++)
                        {
                            prepareHighwayNode(wayNodes.get(index));
                        }

                        if (++tmpWayCounter % 5000000 == 0)
                        {
                            logger.info(nf(tmpWayCounter) + " (preprocess), osmIdMap:" + nf(getNodeMap().getSize()) + " ("
                                    + getNodeMap().getMemoryUsage() + "MB) " + Helper.getMemInfo());
                        }
                    }
                }
                if (item.isType(OSMElement.RELATION))
                {
                    final OSMRelation relation = (OSMRelation) item;
                    if (!relation.isMetaRelation() && relation.hasTag("type", "route"))
                        prepareWaysWithRelationInfo(relation);

                    if (relation.hasTag("type", "restriction"))
                        prepareRestrictionRelation(relation);

                    if (++tmpRelationCounter % 50000 == 0)
                    {
                        logger.info(nf(tmpRelationCounter) + " (preprocess), osmWayMap:" + nf(getRelFlagsMap().size())
                                + " " + Helper.getMemInfo());
                    }

                }
            }
        } catch (Exception ex)
        {
            throw new RuntimeException("Problem while parsing file", ex);
        } finally
        {
            Helper.close(in);
        }
    }

    private void prepareRestrictionRelation( OSMRelation relation )
    {
        OSMTurnRelation turnRelation = createTurnRelation(relation);
        if (turnRelation != null)
        {
            getOsmWayIdSet().add(turnRelation.getOsmIdFrom());
            getOsmWayIdSet().add(turnRelation.getOsmIdTo());
        }
    }

    /**
     * @return all required osmWayIds to process e.g. relations.
     */
    private TLongSet getOsmWayIdSet()
    {
        return osmWayIdSet;
    }

    private TIntLongMap getEdgeIdToOsmWayIdMap()
    {
        if (edgeIdToOsmWayIdMap == null)
            edgeIdToOsmWayIdMap = new TIntLongHashMap(getOsmWayIdSet().size(), 0.5f, -1, -1);

        return edgeIdToOsmWayIdMap;
    }

    /**
     * Filter ways but do not analyze properties wayNodes will be filled with participating node
     * ids.
     * <p/>
     * @return true the current xml entry is a way entry and has nodes
     */
    boolean filterWay( OSMWay item )
    {
        // ignore broken geometry
        if (item.getNodes().size() < 2)
            return false;

        // ignore multipolygon geometry
        if (!item.hasTags())
            return false;

        return encodingManager.acceptWay(item) > 0;
    }

    /**
     * Creates the edges and nodes files from the specified osm file.
     */
    private void writeOsm2Graph( File osmFile )
    {
        int tmp = (int) Math.max(getNodeMap().getSize() / 50, 100);
        logger.info("creating graph. Found nodes (pillar+tower):" + nf(getNodeMap().getSize()) + ", " + Helper.getMemInfo());
        graphStorage.create(tmp);
        long wayStart = -1;
        long relationStart = -1;
        long counter = 1;
        OSMInputFile in = null;
        try
        {
            in = new OSMInputFile(osmFile).setWorkerThreads(workerThreads).open();
            LongIntMap nodeFilter = getNodeMap();

            OSMElement item;
            while ((item = in.getNext()) != null)
            {
                switch (item.getType())
                {
                    case OSMElement.NODE:
                        if (nodeFilter.get(item.getId()) != -1)
                        {
                            processNode((OSMNode) item);
                        }
                        break;

                    case OSMElement.WAY:
                        if (wayStart < 0)
                        {
                            logger.info(nf(counter) + ", now parsing ways");
                            wayStart = counter;
                        }
                        processWay((OSMWay) item);
                        break;
                    case OSMElement.RELATION:
                        if (relationStart < 0)
                        {
                            logger.info(nf(counter) + ", now parsing relations");
                            relationStart = counter;
                        }
                        processRelation((OSMRelation) item);
                        break;
                }
                if (++counter % 100000000 == 0)
                {
                    logger.info(nf(counter) + ", locs:" + nf(locations) + " (" + skippedLocations + ") " + Helper.getMemInfo());
                }
            }

            // logger.info("storage nodes:" + storage.nodes() + " vs. graph nodes:" + storage.getGraph().nodes());
        } catch (Exception ex)
        {
            throw new RuntimeException("Couldn't process file " + osmFile + ", error: " + ex.getMessage(), ex);
        } finally
        {
            Helper.close(in);
        }

        finishedReading();
        if (graphStorage.getNodes() == 0)
            throw new IllegalStateException("osm must not be empty. read " + counter + " lines and " + locations + " locations");
    }

    /**
     * Process properties, encode flags and create edges for the way.
     */
    void processWay( OSMWay way )
    {
        if (way.getNodes().size() < 2)
            return;

        // ignore multipolygon geometry
        if (!way.hasTags())
            return;

        long wayOsmId = way.getId();

        long includeWay = encodingManager.acceptWay(way);
        if (includeWay == 0)
            return;

        long relationFlags = getRelFlagsMap().get(way.getId());

        // TODO move this after we have created the edge and know the coordinates => encodingManager.applyWayTags
        // estimate length of the track e.g. for ferry speed calculation
        TLongList osmNodeIds = way.getNodes();
        if (osmNodeIds.size() > 1)
        {
            int first = getNodeMap().get(osmNodeIds.get(0));
            int last = getNodeMap().get(osmNodeIds.get(osmNodeIds.size() - 1));
            double firstLat = getTmpLatitude(first), firstLon = getTmpLongitude(first);
            double lastLat = getTmpLatitude(last), lastLon = getTmpLongitude(last);
            if (!Double.isNaN(firstLat) && !Double.isNaN(firstLon) && !Double.isNaN(lastLat) && !Double.isNaN(lastLon))
            {
                double estimatedDist = distCalc.calcDist(firstLat, firstLon, lastLat, lastLon);
                way.setTag("estimated_distance", estimatedDist);
                way.setTag("estimated_center", new GHPoint((firstLat + lastLat) / 2, (firstLon + lastLon) / 2));
            }
        }

        long wayFlags = encodingManager.handleWayTags(way, includeWay, relationFlags);
        if (wayFlags == 0)
            return;

        List<EdgeIteratorState> createdEdges = new ArrayList<EdgeIteratorState>();
        // look for barriers along the way
        final int size = osmNodeIds.size();
        int lastBarrier = -1;
        for (int i = 0; i < size; i++)
        {
            long nodeId = osmNodeIds.get(i);
            long nodeFlags = getNodeFlagsMap().get(nodeId);
            // barrier was spotted and way is otherwise passable for that mode of travel
            if (nodeFlags > 0)
            {
                if ((nodeFlags & wayFlags) > 0)
                {
                    // remove barrier to avoid duplicates
                    getNodeFlagsMap().put(nodeId, 0);

                    // create shadow node copy for zero length edge
                    long newNodeId = addBarrierNode(nodeId);
                    if (i > 0)
                    {
                        // start at beginning of array if there was no previous barrier
                        if (lastBarrier < 0)
                            lastBarrier = 0;

                        // add way up to barrier shadow node
                        long transfer[] = osmNodeIds.toArray(lastBarrier, i - lastBarrier + 1);
                        transfer[transfer.length - 1] = newNodeId;
                        TLongList partIds = new TLongArrayList(transfer);
                        createdEdges.addAll(addOSMWay(partIds, wayFlags, wayOsmId));

                        // create zero length edge for barrier
                        createdEdges.addAll(addBarrierEdge(newNodeId, nodeId, wayFlags, nodeFlags, wayOsmId));
                    } else
                    {
                        // run edge from real first node to shadow node
                        createdEdges.addAll(addBarrierEdge(nodeId, newNodeId, wayFlags, nodeFlags, wayOsmId));

                        // exchange first node for created barrier node
                        osmNodeIds.set(0, newNodeId);
                    }
                    // remember barrier for processing the way behind it
                    lastBarrier = i;
                }
            }
        }

        // just add remainder of way to graph if barrier was not the last node
        if (lastBarrier >= 0)
        {
            if (lastBarrier < size - 1)
            {
                long transfer[] = osmNodeIds.toArray(lastBarrier, size - lastBarrier);
                TLongList partNodeIds = new TLongArrayList(transfer);
                createdEdges.addAll(addOSMWay(partNodeIds, wayFlags, wayOsmId));
            }
        } else
        {
            // no barriers - simply add the whole way
            createdEdges.addAll(addOSMWay(way.getNodes(), wayFlags, wayOsmId));
        }

        for (EdgeIteratorState edge : createdEdges)
        {
            encodingManager.applyWayTags(way, edge);
        }
    }

    public void processRelation( OSMRelation relation ) throws XMLStreamException
    {
        if (relation.hasTag("type", "restriction"))
        {
            OSMTurnRelation turnRelation = createTurnRelation(relation);
            if (turnRelation != null)
            {
                GraphExtension extendedStorage = graphStorage.getExtension();
                if (extendedStorage instanceof TurnCostExtension)
                {
                    TurnCostExtension tcs = (TurnCostExtension) extendedStorage;
                    Collection<TurnCostTableEntry> entries = analyzeTurnRelation(turnRelation);
                    for (TurnCostTableEntry entry : entries)
                    {
                        tcs.addTurnInfo(entry.edgeFrom, entry.nodeVia, entry.edgeTo, entry.flags);
                    }
                }
            }
        }
    }

    public Collection<TurnCostTableEntry> analyzeTurnRelation( OSMTurnRelation turnRelation )
    {
        TLongObjectMap<TurnCostTableEntry> entries = new TLongObjectHashMap<OSMTurnRelation.TurnCostTableEntry>();

        for (FlagEncoder encoder : encodingManager.fetchEdgeEncoders())
        {
            for (TurnCostTableEntry entry : analyzeTurnRelation(encoder, turnRelation))
            {
                TurnCostTableEntry oldEntry = entries.get(entry.getItemId());
                if (oldEntry != null)
                {
                    // merging different encoders
                    oldEntry.flags |= entry.flags;
                } else
                {
                    entries.put(entry.getItemId(), entry);
                }
            }
        }

        return entries.valueCollection();
    }

    public Collection<TurnCostTableEntry> analyzeTurnRelation( FlagEncoder encoder, OSMTurnRelation turnRelation )
    {
        if (!encoder.supports(TurnWeighting.class))
            return Collections.emptyList();

        EdgeExplorer edgeOutExplorer = outExplorerMap.get(encoder);
        EdgeExplorer edgeInExplorer = inExplorerMap.get(encoder);

        if (edgeOutExplorer == null || edgeInExplorer == null)
        {
            edgeOutExplorer = getGraphStorage().createEdgeExplorer(new DefaultEdgeFilter(encoder, false, true));
            outExplorerMap.put(encoder, edgeOutExplorer);

            edgeInExplorer = getGraphStorage().createEdgeExplorer(new DefaultEdgeFilter(encoder, true, false));
            inExplorerMap.put(encoder, edgeInExplorer);
        }
        return turnRelation.getRestrictionAsEntries(encoder, edgeOutExplorer, edgeInExplorer, this);
    }

    /**
     * @return OSM way ID from specified edgeId. Only previously stored OSM-way-IDs are returned in
     * order to reduce memory overhead.
     */
    public long getOsmIdOfInternalEdge( int edgeId )
    {
        return getEdgeIdToOsmWayIdMap().get(edgeId);
    }

    public int getInternalNodeIdOfOsmNode( long nodeOsmId )
    {
        int id = getNodeMap().get(nodeOsmId);
        if (id < TOWER_NODE)
            return -id - 3;

        return EMPTY;
    }

    // TODO remove this ugly stuff via better preparsing phase! E.g. putting every tags etc into a helper file!
    double getTmpLatitude( int id )
    {
        if (id == EMPTY)
            return Double.NaN;
        if (id < TOWER_NODE)
        {
            // tower node
            id = -id - 3;
            return nodeAccess.getLatitude(id);
        } else if (id > -TOWER_NODE)
        {
            // pillar node
            id = id - 3;
            return pillarInfo.getLatitude(id);
        } else
            // e.g. if id is not handled from preparse (e.g. was ignored via isInBounds)
            return Double.NaN;
    }

    double getTmpLongitude( int id )
    {
        if (id == EMPTY)
            return Double.NaN;
        if (id < TOWER_NODE)
        {
            // tower node
            id = -id - 3;
            return nodeAccess.getLongitude(id);
        } else if (id > -TOWER_NODE)
        {
            // pillar node
            id = id - 3;
            return pillarInfo.getLon(id);
        } else
            // e.g. if id is not handled from preparse (e.g. was ignored via isInBounds)
            return Double.NaN;
    }

    private void processNode( OSMNode node )
    {
        if (isInBounds(node))
        {
            addNode(node);

            // analyze node tags for barriers
            if (node.hasTags())
            {
                long nodeFlags = encodingManager.handleNodeTags(node);
                if (nodeFlags != 0)
                    getNodeFlagsMap().put(node.getId(), nodeFlags);
            }

            locations++;
        } else
        {
            skippedLocations++;
        }
    }

    boolean addNode( OSMNode node )
    {
        int nodeType = getNodeMap().get(node.getId());
        if (nodeType == EMPTY)
            return false;

        double lat = node.getLat();
        double lon = node.getLon();
        double ele = getElevation(node);
        if (nodeType == TOWER_NODE)
        {
            addTowerNode(node.getId(), lat, lon, ele);
        } else if (nodeType == PILLAR_NODE)
        {
            pillarInfo.setNode(nextPillarId, lat, lon, ele);
            getNodeMap().put(node.getId(), nextPillarId + 3);
            nextPillarId++;
        }
        return true;
    }

    protected double getElevation( OSMNode node )
    {
        return eleProvider.getEle(node.getLat(), node.getLon());
    }

    void prepareWaysWithRelationInfo( OSMRelation osmRelation )
    {
        // is there at least one tag interesting for the registed encoders?
        if (encodingManager.handleRelationTags(osmRelation, 0) == 0)
            return;

        int size = osmRelation.getMembers().size();
        for (int index = 0; index < size; index++)
        {
            OSMRelation.Member member = osmRelation.getMembers().get(index);
            if (member.type() != OSMRelation.Member.WAY)
                continue;

            long osmId = member.ref();
            long oldRelationFlags = getRelFlagsMap().get(osmId);

            // Check if our new relation data is better comparated to the the last one
            long newRelationFlags = encodingManager.handleRelationTags(osmRelation, oldRelationFlags);
            if (oldRelationFlags != newRelationFlags)
                getRelFlagsMap().put(osmId, newRelationFlags);
        }
    }

    void prepareHighwayNode( long osmId )
    {
        int tmpIndex = getNodeMap().get(osmId);
        if (tmpIndex == EMPTY)
        {
            // osmId is used exactly once
            getNodeMap().put(osmId, PILLAR_NODE);
        } else if (tmpIndex > EMPTY)
        {
            // mark node as tower node as it occured at least twice times
            getNodeMap().put(osmId, TOWER_NODE);
        } else
        {
            // tmpIndex is already negative (already tower node)
        }
    }

    int addTowerNode( long osmId, double lat, double lon, double ele )
    {
        if (nodeAccess.is3D())
            nodeAccess.setNode(nextTowerId, lat, lon, ele);
        else
            nodeAccess.setNode(nextTowerId, lat, lon);

        int id = -(nextTowerId + 3);
        getNodeMap().put(osmId, id);
        nextTowerId++;
        return id;
    }

    /**
     * This method creates from an OSM way (via the osm ids) one or more edges in the graph.
     */
    Collection<EdgeIteratorState> addOSMWay( final TLongList osmNodeIds, final long flags, final long wayOsmId )
    {
        PointList pointList = new PointList(osmNodeIds.size(), nodeAccess.is3D());
        List<EdgeIteratorState> newEdges = new ArrayList<EdgeIteratorState>(5);
        int firstNode = -1;
        int lastIndex = osmNodeIds.size() - 1;
        int lastInBoundsPillarNode = -1;
        try
        {
            for (int i = 0; i < osmNodeIds.size(); i++)
            {
                long osmId = osmNodeIds.get(i);
                int tmpNode = getNodeMap().get(osmId);
                if (tmpNode == EMPTY)
                    continue;

                // skip osmIds with no associated pillar or tower id (e.g. !OSMReader.isBounds)
                if (tmpNode == TOWER_NODE)
                    continue;

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
                            newEdges.add(addEdge(firstNode, tmpNode, pointList, flags, wayOsmId));
                            pointList.clear();
                            pointList.add(nodeAccess, tmpNode);
                        }
                        firstNode = tmpNode;
                        lastInBoundsPillarNode = -1;
                    }
                    continue;
                }

                if (tmpNode <= -TOWER_NODE && tmpNode >= TOWER_NODE)
                    throw new AssertionError("Mapped index not in correct bounds " + tmpNode + ", " + osmId);

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
                    pointList.add(nodeAccess, tmpNode);
                    if (firstNode >= 0)
                    {
                        newEdges.add(addEdge(firstNode, tmpNode, pointList, flags, wayOsmId));
                        pointList.clear();
                        pointList.add(nodeAccess, tmpNode);
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

    EdgeIteratorState addEdge( int fromIndex, int toIndex, PointList pointList, long flags, long wayOsmId )
    {
        // sanity checks
        if (fromIndex < 0 || toIndex < 0)
            throw new AssertionError("to or from index is invalid for this edge " + fromIndex + "->" + toIndex + ", points:" + pointList);
        if (pointList.getDimension() != nodeAccess.getDimension())
            throw new AssertionError("Dimension does not match for pointList vs. nodeAccess " + pointList.getDimension() + " <-> " + nodeAccess.getDimension());

        double towerNodeDistance = 0;
        double prevLat = pointList.getLatitude(0);
        double prevLon = pointList.getLongitude(0);
        double prevEle = pointList.is3D() ? pointList.getElevation(0) : Double.NaN;
        double lat, lon, ele = Double.NaN;
        PointList pillarNodes = new PointList(pointList.getSize() - 2, nodeAccess.is3D());
        int nodes = pointList.getSize();
        for (int i = 1; i < nodes; i++)
        {
            // we could save some lines if we would use pointList.calcDistance(distCalc);
            lat = pointList.getLatitude(i);
            lon = pointList.getLongitude(i);
            if (pointList.is3D())
            {
                ele = pointList.getElevation(i);
                towerNodeDistance += distCalc3D.calcDist(prevLat, prevLon, prevEle, lat, lon, ele);
                prevEle = ele;
            } else
                towerNodeDistance += distCalc.calcDist(prevLat, prevLon, lat, lon);
            prevLat = lat;
            prevLon = lon;
            if (nodes > 2 && i < nodes - 1)
            {
                if (pillarNodes.is3D())
                    pillarNodes.add(lat, lon, ele);
                else
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

        EdgeIteratorState iter = graphStorage.edge(fromIndex, toIndex).setDistance(towerNodeDistance).setFlags(flags);
        if (nodes > 2)
        {
            if (doSimplify)
                simplifyAlgo.simplify(pillarNodes);

            iter.setWayGeometry(pillarNodes);
        }
        storeOsmWayID(iter.getEdge(), wayOsmId);
        return iter;
    }

    /**
     * Stores only osmWayIds which are required for relations
     */
    private void storeOsmWayID( int edgeId, long osmWayId )
    {
        if (getOsmWayIdSet().contains(osmWayId))
        {
            getEdgeIdToOsmWayIdMap().put(edgeId, osmWayId);
        }
    }

    /**
     * @return converted tower node
     */
    private int handlePillarNode( int tmpNode, long osmId, PointList pointList, boolean convertToTowerNode )
    {
        tmpNode = tmpNode - 3;
        double lat = pillarInfo.getLatitude(tmpNode);
        double lon = pillarInfo.getLongitude(tmpNode);
        double ele = pillarInfo.getElevation(tmpNode);
        if (lat == Double.MAX_VALUE || lon == Double.MAX_VALUE)
            throw new RuntimeException("Conversion pillarNode to towerNode already happended!? "
                    + "osmId:" + osmId + " pillarIndex:" + tmpNode);

        if (convertToTowerNode)
        {
            // convert pillarNode type to towerNode, make pillar values invalid
            pillarInfo.setNode(tmpNode, Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE);
            tmpNode = addTowerNode(osmId, lat, lon, ele);
        } else
        {
            if (pointList.is3D())
                pointList.add(lat, lon, ele);
            else
                pointList.add(lat, lon);
        }

        return (int) tmpNode;
    }

    protected void finishedReading()
    {
        printInfo("way");
        pillarInfo.clear();
        eleProvider.release();
        osmNodeIdToInternalNodeMap = null;
        osmNodeIdToNodeFlagsMap = null;
        osmWayIdToRouteWeightMap = null;
        osmWayIdSet = null;
        edgeIdToOsmWayIdMap = null;
    }

    /**
     * Create a copy of the barrier node
     */
    long addBarrierNode( long nodeId )
    {
        OSMNode newNode;
        int graphIndex = getNodeMap().get(nodeId);
        if (graphIndex < TOWER_NODE)
        {
            graphIndex = -graphIndex - 3;
            newNode = new OSMNode(createNewNodeId(), nodeAccess, graphIndex);
        } else
        {
            graphIndex = graphIndex - 3;
            newNode = new OSMNode(createNewNodeId(), pillarInfo, graphIndex);
        }

        final long id = newNode.getId();
        prepareHighwayNode(id);
        addNode(newNode);
        return id;
    }

    private long createNewNodeId()
    {
        return newUniqueOsmId++;
    }

    /**
     * Add a zero length edge with reduced routing options to the graph.
     */
    Collection<EdgeIteratorState> addBarrierEdge( long fromId, long toId, long flags, long nodeFlags, long wayOsmId )
    {
        // clear barred directions from routing flags
        flags &= ~nodeFlags;
        // add edge
        barrierNodeIds.clear();
        barrierNodeIds.add(fromId);
        barrierNodeIds.add(toId);
        return addOSMWay(barrierNodeIds, flags, wayOsmId);
    }

    /**
     * Creates an OSM turn relation out of an unspecified OSM relation
     * <p/>
     * @return the OSM turn relation, <code>null</code>, if unsupported turn relation
     */
    OSMTurnRelation createTurnRelation( OSMRelation relation )
    {
        OSMTurnRelation.Type type = OSMTurnRelation.Type.getRestrictionType(relation.getTag("restriction"));
        if (type != OSMTurnRelation.Type.UNSUPPORTED)
        {
            long fromWayID = -1;
            long viaNodeID = -1;
            long toWayID = -1;

            for (OSMRelation.Member member : relation.getMembers())
            {
                if (OSMElement.WAY == member.type())
                {
                    if ("from".equals(member.role()))
                    {
                        fromWayID = member.ref();
                    } else if ("to".equals(member.role()))
                    {
                        toWayID = member.ref();
                    }
                } else if (OSMElement.NODE == member.type() && "via".equals(member.role()))
                {
                    viaNodeID = member.ref();
                }
            }
            if (fromWayID >= 0 && toWayID >= 0 && viaNodeID >= 0)
            {
                return new OSMTurnRelation(fromWayID, viaNodeID, toWayID, type);
            }
        }
        return null;
    }

    /**
     * Filter method, override in subclass
     */
    boolean isInBounds( OSMNode node )
    {
        return true;
    }

    /**
     * Maps OSM IDs (long) to internal node IDs (int)
     */
    protected LongIntMap getNodeMap()
    {
        return osmNodeIdToInternalNodeMap;
    }

    protected TLongLongMap getNodeFlagsMap()
    {
        return osmNodeIdToNodeFlagsMap;
    }

    TLongLongHashMap getRelFlagsMap()
    {
        return osmWayIdToRouteWeightMap;
    }

    /**
     * Specify the type of the path calculation (car, bike, ...).
     */
    public OSMReader setEncodingManager( EncodingManager em )
    {
        this.encodingManager = em;
        return this;
    }

    public OSMReader setWayPointMaxDistance( double maxDist )
    {
        doSimplify = maxDist > 0;
        simplifyAlgo.setMaxDistance(maxDist);
        return this;
    }

    public OSMReader setWorkerThreads( int numOfWorkers )
    {
        this.workerThreads = numOfWorkers;
        return this;
    }

    public OSMReader setElevationProvider( ElevationProvider eleProvider )
    {
        if (eleProvider == null)
            throw new IllegalStateException("Use the NOOP elevation provider instead of null or don't call setElevationProvider");

        if (!nodeAccess.is3D() && ElevationProvider.NOOP != eleProvider)
            throw new IllegalStateException("Make sure you graph accepts 3D data");

        this.eleProvider = eleProvider;
        return this;
    }

    public OSMReader setOSMFile( File osmFile )
    {
        this.osmFile = osmFile;
        return this;
    }

    private void printInfo( String str )
    {
        logger.info("finished " + str + " processing." + " nodes: " + graphStorage.getNodes()
                + ", osmIdMap.size:" + getNodeMap().getSize() + ", osmIdMap:" + getNodeMap().getMemoryUsage() + "MB"
                + ", nodeFlagsMap.size:" + getNodeFlagsMap().size() + ", relFlagsMap.size:" + getRelFlagsMap().size()
                + ", zeroCounter:" + zeroCounter
                + " " + Helper.getMemInfo());
    }

    @Override
    public String toString()
    {
        return getClass().getSimpleName();
    }

    public GraphStorage getGraphStorage()
    {
        return graphStorage;
    }
}
