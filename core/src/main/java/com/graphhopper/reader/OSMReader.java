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
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.DataAccess;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.util.*;
import static com.graphhopper.util.Helper.*;
import java.io.*;
import javax.xml.stream.XMLStreamException;

import gnu.trove.list.TLongList;
import gnu.trove.list.array.TLongArrayList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class parses an OSM xml file and creates a graph from it. See run.sh on how to use it from
 * command line.
 * <p/>
 * @author Peter Karich
 */
public class OSMReader
{
    protected static final int EMPTY = -1;
    // pillar node is >= 3
    protected static final int PILLAR_NODE = 1;
    // tower node is <= -3
    protected static final int TOWER_NODE = -2;
    private static Logger logger = LoggerFactory.getLogger(OSMReader.class);
    private long locations;
    private long skippedLocations;
    private GraphStorage graphStorage;
    private EncodingManager encodingManager = null;
    private int workerThreads = -1;
    private LongIntMap osmNodeIdToBarrierMap;
    private boolean enableInstructions = true;
    protected final Directory dir;
    protected long zeroCounter = 0;
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

    public OSMReader( GraphStorage storage, long expectedCap )
    {
        this.graphStorage = storage;
        osmNodeIdToBarrierMap = new GHLongIntBTree(200);
        this.expectedNodes = expectedCap;
        osmNodeIdToIndexMap = new GHLongIntBTree(200);
        dir = graphStorage.getDirectory();
        pillarLats = dir.find("tmpLatitudes");
        pillarLons = dir.find("tmpLongitudes");

        pillarLats.create(Math.max(expectedCap, 100));
        pillarLons.create(Math.max(expectedCap, 100));
    }

    public void doOSM2Graph( File osmFile ) throws IOException
    {
        if (encodingManager == null)
            throw new IllegalStateException("Encoding manager not set.");

        StopWatch sw1 = new StopWatch().start();
        preProcess(osmFile);
        sw1.stop();

        StopWatch sw2 = new StopWatch().start();
        writeOsm2Graph(osmFile);
        sw2.stop();

        logger.info("time(pass1): " + (int) sw1.getSeconds() + " pass2: " + (int) sw2.getSeconds()
                + " total:" + ((int) (sw1.getSeconds() + sw2.getSeconds())));
    }

    /**
     * Preprocessing of OSM file to select nodes which are used for highways. This allows a more
     * compact graph data structure.
     */
    public void preProcess( File osmFile )
    {
        OSMInputFile in = null;
        try
        {
            in = new OSMInputFile(osmFile).setWorkerThreads(workerThreads).open();

            long tmpCounter = 1;

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

                        if (++tmpCounter % 500000 == 0)
                        {
                            logger.info(nf(tmpCounter) + " (preprocess), osmIdMap:"
                                    + nf(getNodeMap().getSize()) + " (" + getNodeMap().getMemoryUsage() + "MB) "
                                    + Helper.getMemInfo());
                        }
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

    /**
     * Filter ways but do not analyze properties wayNodes will be filled with participating node
     * ids.
     * <p/>
     * @return true the current xml entry is a way entry and has nodes
     */
    boolean filterWay( OSMWay item ) throws XMLStreamException
    {

        // ignore broken geometry
        if (item.getNodes().size() < 2)
        {
            return false;
        }
        // ignore multipolygon geometry
        if (!item.hasTags())
        {
            return false;
        }

        return encodingManager.accept(item) > 0;
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
                }
                if (++counter % 5000000 == 0)
                {
                    logger.info(nf(counter) + ", locs:" + nf(locations)
                            + " (" + skippedLocations + ") " + Helper.getMemInfo());
                }
            }

            // logger.info("storage nodes:" + storage.nodes() + " vs. graph nodes:" + storage.getGraph().nodes());
        } catch (Exception ex)
        {
            throw new RuntimeException("Couldn't process file " + osmFile, ex);
        } finally
        {
            Helper.close(in);
        }

        finishedReading();
        if (graphStorage.getNodes() == 0)
            throw new IllegalStateException("osm must not be empty. read " + counter + " lines and " + locations + " locations");
    }

    /**
     * Process properties, encode flags and create edges for the way
     * <p/>
     * @param way
     * @throws XMLStreamException
     */
    public void processWay( OSMWay way ) throws XMLStreamException
    {
        if (way.getNodes().size() < 2)
            return;

        // ignore multipolygon geometry
        if (!way.hasTags())
            return;

        int includeWay = encodingManager.accept(way);
        if (includeWay == 0)
            return;

        int flags = encodingManager.handleWayTags(includeWay, way);
        if (flags == 0)
            return;

        TLongList osmNodeIds = way.getNodes();
        List<EdgeIteratorState> createdEdges = new ArrayList<EdgeIteratorState>();
        // look for barriers along the way
        final int size = osmNodeIds.size();
        int lastBarrier = -1;
        for (int i = 0; i < size; i++)
        {
            final long nodeId = osmNodeIds.get(i);
            int barrierFlags = osmNodeIdToBarrierMap.get(nodeId);
            // barrier was spotted and way is otherwise passable for that mode of travel
            if (barrierFlags > 0 && (barrierFlags & flags) > 0)
            {
                // remove barrier to avoid duplicates
                osmNodeIdToBarrierMap.put(nodeId, 0);

                // create shadow node copy for zero length edge
                long newNodeId = addBarrierNode(nodeId);

                if (i > 0)
                {
                    // start at beginning of array if there was no previous barrier
                    if (lastBarrier < 0)
                    {
                        lastBarrier = 0;
                    }
                    // add way up to barrier shadow node
                    long transfer[] = osmNodeIds.toArray(lastBarrier, i - lastBarrier + 1);
                    transfer[transfer.length - 1] = newNodeId;
                    TLongList partIds = new TLongArrayList(transfer);
                    createdEdges.addAll(addOSMWay(partIds, flags));

                    // create zero length edge for barrier
                    createdEdges.addAll(addBarrierEdge(newNodeId, nodeId, flags, barrierFlags));
                } else
                {
                    // run edge from real first node to shadow node
                    createdEdges.addAll(addBarrierEdge(nodeId, newNodeId, flags, barrierFlags));

                    // exchange first node for created barrier node
                    osmNodeIds.set(0, newNodeId);
                }
                // remember barrier for processing the way behind it
                lastBarrier = i;
            }
        }

        // just add remainder of way to graph if barrier was not the last node
        if (lastBarrier >= 0)
        {
            if (lastBarrier < size - 1)
            {
                long transfer[] = osmNodeIds.toArray(lastBarrier, size - lastBarrier);
                TLongList partNodeIds = new TLongArrayList(transfer);
                createdEdges.addAll(addOSMWay(partNodeIds, flags));
            }
        } else
        {
            // no barriers - simply add the whole way
            createdEdges.addAll(addOSMWay(way.getNodes(), flags));
        }
        if (enableInstructions)
        {
            // String wayInfo = encodingManager.getWayInfo(way);
            // http://wiki.openstreetmap.org/wiki/Key:name
            String name = fixWayName(way.getTag("name"));
            // http://wiki.openstreetmap.org/wiki/Key:ref
            String refName = fixWayName(way.getTag("ref"));
            if (!Helper.isEmpty(refName))
            {
                if (Helper.isEmpty(name))
                {
                    name = refName;
                } else
                {
                    name += ", " + refName;
                }
            }

            for (EdgeIteratorState iter : createdEdges)
            {
                iter.setName(name);
            }
        }
    }

    static String fixWayName( String str )
    {
        if (str == null)
            return "";
        return str.replaceAll(";[ ]*", ", ");
    }

    private void processNode( OSMNode node ) throws XMLStreamException
    {
        if (isInBounds(node))
        {
            addNode(node);

            // analyze node tags for barriers
            if (node.hasTags())
            {
                final int barrierFlags = encodingManager.analyzeNode(node);
                if (barrierFlags != 0)
                {
                    osmNodeIdToBarrierMap.put(node.getId(), barrierFlags);
                }
            }

            locations++;
        } else
        {
            skippedLocations++;
        }
    }

    public boolean addNode( OSMNode node )
    {
        int nodeType = getNodeMap().get(node.getId());
        if (nodeType == EMPTY)
            return false;

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
            getNodeMap().put(node.getId(), pillarId + 3);
            pillarId++;
        }
        return true;
    }

    public void prepareHighwayNode( long osmId )
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

    protected int addTowerNode( long osmId, double lat, double lon )
    {
        graphStorage.setNode(towerId, lat, lon);
        int id = -(towerId + 3);
        getNodeMap().put(osmId, id);
        towerId++;
        return id;
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
                            newEdges.add(addEdge(firstNode, tmpNode, pointList, flags));
                            pointList.clear();
                            pointList.add(graphStorage.getLatitude(tmpNode), graphStorage.getLongitude(tmpNode));
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
                    pointList.add(graphStorage.getLatitude(tmpNode), graphStorage.getLongitude(tmpNode));
                    if (firstNode >= 0)
                    {
                        newEdges.add(addEdge(firstNode, tmpNode, pointList, flags));
                        pointList.clear();
                        pointList.add(graphStorage.getLatitude(tmpNode), graphStorage.getLongitude(tmpNode));
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

        EdgeIteratorState iter = graphStorage.edge(fromIndex, toIndex, towerNodeDistance, flags);
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

    void finishedReading()
    {
        // todo: is this necessary before removing it?
        getNodeMap().optimize();
        printInfo("way");
        dir.remove(pillarLats);
        dir.remove(pillarLons);
        pillarLons = null;
        pillarLats = null;
        osmNodeIdToIndexMap = null;
    }

    /**
     * Create a copy of the barrier node
     */
    public long addBarrierNode( long nodeId )
    {
        // create node
        OSMNode newNode;
        int graphIndex = getNodeMap().get(nodeId);
        if (graphIndex < TOWER_NODE)
        {
            graphIndex = -graphIndex - 3;
            newNode = new OSMNode(createNewNodeId(),
                    graphStorage.getLatitude(graphIndex), graphStorage.getLongitude(graphIndex));
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

    /**
     * Filter method, override in subclass
     */
    protected boolean isInBounds( OSMNode node )
    {
        return true;
    }

    private LongIntMap getNodeMap()
    {
        return osmNodeIdToIndexMap;
    }

    /**
     * Specify the type of the path calculation (car, bike, ...).
     */
    public OSMReader setEncodingManager( EncodingManager acceptWay )
    {
        this.encodingManager = acceptWay;
        return this;
    }

    public OSMReader setEnableInstructions( boolean enableInstructions )
    {
        this.enableInstructions = enableInstructions;
        return this;
    }

    public OSMReader setWayPointMaxDistance( double maxDist )
    {
        dpAlgo.setMaxDistance(maxDist);
        return this;
    }

    public OSMReader setWorkerThreads( int numOfWorkers )
    {
        this.workerThreads = numOfWorkers;
        return this;
    }

    private void printInfo( String str )
    {
        LoggerFactory.getLogger(getClass()).info("finished " + str + " processing."
                + " nodes: " + graphStorage.getNodes() + ", osmIdMap.size:" + getNodeMap().getSize()
                + ", osmIdMap:" + getNodeMap().getMemoryUsage() + "MB"
                + ", osmIdMap.toString:" + getNodeMap() + " "
                + Helper.getMemInfo());
    }

    @Override
    public String toString()
    {
        return getClass().getSimpleName();
    }
}
