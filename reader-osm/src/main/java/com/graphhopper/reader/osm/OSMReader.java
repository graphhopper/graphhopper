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
package com.graphhopper.reader.osm;

import com.carrotsearch.hppc.*;
import com.graphhopper.coll.*;
import com.graphhopper.coll.LongIntMap;
import com.graphhopper.reader.*;
import com.graphhopper.reader.dem.ElevationProvider;
import com.graphhopper.reader.dem.GraphElevationSmoothing;
import com.graphhopper.reader.osm.OSMTurnRelation.TurnCostTableEntry;
import com.graphhopper.routing.profiles.BooleanEncodedValue;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.weighting.TurnWeighting;
import com.graphhopper.storage.*;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.GHPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLStreamException;
import java.io.File;
import java.io.IOException;
import java.util.*;

import static com.graphhopper.util.Helper.nf;

/**
 * This class parses an OSM xml or pbf file and creates a graph from it. It does so in a two phase
 * parsing processes in order to reduce memory usage compared to a single parsing processing.
 * <p>
 * 1. a) Reads ways from OSM file and stores all associated node ids in {@link #osmNodeIdToInternalNodeMap}. If a
 * node occurs once it is a pillar node and if more it is a tower node, otherwise
 * {@link #osmNodeIdToInternalNodeMap} returns EMPTY.
 * <p>
 * 1. b) Reads relations from OSM file. In case that the relation is a route relation, it stores
 * specific relation attributes required for routing into {@link #osmWayIdToRouteWeightMap} for all the ways
 * of the relation.
 * <p>
 * 2.a) Reads nodes from OSM file and stores lat+lon information either into the intermediate
 * data structure for the pillar nodes (pillarLats/pillarLons) or, if a tower node, directly into the
 * graphStorage via setLatitude/setLongitude. It can also happen that a pillar node needs to be
 * transformed into a tower node e.g. via barriers or different speed values for one way.
 * <p>
 * 2.b) Reads ways from OSM file and creates edges while calculating the speed etc from the OSM tags.
 * When creating an edge the pillar node information from the intermediate data structure will be
 * stored in the way geometry of that edge.
 * <p>
 *
 * @author Peter Karich
 */
public class OSMReader implements DataReader {
    protected static final int EMPTY_NODE = -1;
    // pillar node is >= 3
    protected static final int PILLAR_NODE = 1;
    // tower node is <= -3
    protected static final int TOWER_NODE = -2;
    private static final Logger LOGGER = LoggerFactory.getLogger(OSMReader.class);
    private final GraphStorage ghStorage;
    private final Graph graph;
    private final NodeAccess nodeAccess;
    private final LongIndexedContainer barrierNodeIds = new LongArrayList();
    private final DistanceCalc distCalc = Helper.DIST_EARTH;
    private final DistanceCalc3D distCalc3D = Helper.DIST_3D;
    private final DouglasPeucker simplifyAlgo = new DouglasPeucker();
    private boolean smoothElevation = false;
    private final boolean exitOnlyPillarNodeException = true;
    private final Map<FlagEncoder, EdgeExplorer> outExplorerMap = new HashMap<>();
    private final Map<FlagEncoder, EdgeExplorer> inExplorerMap = new HashMap<>();
    protected long zeroCounter = 0;
    protected PillarInfo pillarInfo;
    private long locations;
    private long skippedLocations;
    private final EncodingManager encodingManager;
    private int workerThreads = 2;
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
    private GHLongLongHashMap osmNodeIdToNodeFlagsMap;
    private GHLongLongHashMap osmWayIdToRouteWeightMap;
    // stores osm way ids used by relations to identify which edge ids needs to be mapped later
    private GHLongHashSet osmWayIdSet = new GHLongHashSet();
    private IntLongMap edgeIdToOsmWayIdMap;
    private boolean doSimplify = true;
    private int nextTowerId = 0;
    private int nextPillarId = 0;
    // negative but increasing to avoid clash with custom created OSM files
    private long newUniqueOsmId = -Long.MAX_VALUE;
    private ElevationProvider eleProvider = ElevationProvider.NOOP;
    private File osmFile;
    private Date osmDataDate;
    private boolean createStorage = true;

    public OSMReader(GraphHopperStorage ghStorage) {
        this.ghStorage = ghStorage;
        this.graph = ghStorage;
        this.nodeAccess = graph.getNodeAccess();
        this.encodingManager = ghStorage.getEncodingManager();

        osmNodeIdToInternalNodeMap = new GHLongIntBTree(200);
        osmNodeIdToNodeFlagsMap = new GHLongLongHashMap(200, .5f);
        osmWayIdToRouteWeightMap = new GHLongLongHashMap(200, .5f);
        pillarInfo = new PillarInfo(nodeAccess.is3D(), ghStorage.getDirectory());
    }

    @Override
    public void readGraph() throws IOException {
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

        LOGGER.info("time pass1:" + (int) sw1.getSeconds() + "s, "
                + "pass2:" + (int) sw2.getSeconds() + "s, "
                + "total:" + (int) (sw1.getSeconds() + sw2.getSeconds()) + "s");
    }

    /**
     * Preprocessing of OSM file to select nodes which are used for highways. This allows a more
     * compact graph data structure.
     */
    void preProcess(File osmFile) {
        try (OSMInput in = openOsmInputFile(osmFile)) {
            long tmpWayCounter = 1;
            long tmpRelationCounter = 1;
            ReaderElement item;
            while ((item = in.getNext()) != null) {
                if (item.isType(ReaderElement.WAY)) {
                    final ReaderWay way = (ReaderWay) item;
                    boolean valid = filterWay(way);
                    if (valid) {
                        LongIndexedContainer wayNodes = way.getNodes();
                        int s = wayNodes.size();
                        for (int index = 0; index < s; index++) {
                            prepareHighwayNode(wayNodes.get(index));
                        }

                        if (++tmpWayCounter % 10_000_000 == 0) {
                            LOGGER.info(nf(tmpWayCounter) + " (preprocess), osmIdMap:" + nf(getNodeMap().getSize()) + " ("
                                    + getNodeMap().getMemoryUsage() + "MB) " + Helper.getMemInfo());
                        }
                    }
                } else if (item.isType(ReaderElement.RELATION)) {
                    final ReaderRelation relation = (ReaderRelation) item;
                    if (!relation.isMetaRelation() && relation.hasTag("type", "route"))
                        prepareWaysWithRelationInfo(relation);

                    if (relation.hasTag("type", "restriction"))
                        prepareRestrictionRelation(relation);

                    if (++tmpRelationCounter % 100_000 == 0) {
                        LOGGER.info(nf(tmpRelationCounter) + " (preprocess), osmWayMap:" + nf(getRelFlagsMap().size())
                                + " " + Helper.getMemInfo());
                    }
                } else if (item.isType(ReaderElement.FILEHEADER)) {
                    final OSMFileHeader fileHeader = (OSMFileHeader) item;
                    osmDataDate = Helper.createFormatter().parse(fileHeader.getTag("timestamp"));
                }

            }
        } catch (Exception ex) {
            throw new RuntimeException("Problem while parsing file", ex);
        }
    }

    private void prepareRestrictionRelation(ReaderRelation relation) {
        OSMTurnRelation turnRelation = createTurnRelation(relation);
        if (turnRelation != null) {
            getOsmWayIdSet().add(turnRelation.getOsmIdFrom());
            getOsmWayIdSet().add(turnRelation.getOsmIdTo());
        }
    }

    /**
     * @return all required osmWayIds to process e.g. relations.
     */
    private LongSet getOsmWayIdSet() {
        return osmWayIdSet;
    }

    private IntLongMap getEdgeIdToOsmWayIdMap() {
        if (edgeIdToOsmWayIdMap == null)
            edgeIdToOsmWayIdMap = new GHIntLongHashMap(getOsmWayIdSet().size(), 0.5f);

        return edgeIdToOsmWayIdMap;
    }

    /**
     * Filter ways but do not analyze properties wayNodes will be filled with participating node ids.
     *
     * @return true the current xml entry is a way entry and has nodes
     */
    boolean filterWay(ReaderWay item) {
        // ignore broken geometry
        if (item.getNodes().size() < 2)
            return false;

        // ignore multipolygon geometry
        if (!item.hasTags())
            return false;

        return encodingManager.acceptWay(item, new EncodingManager.AcceptWay());
    }

    /**
     * Creates the graph with edges and nodes from the specified osm file.
     */
    private void writeOsm2Graph(File osmFile) {
        int tmp = (int) Math.max(getNodeMap().getSize() / 50, 100);
        LOGGER.info("creating graph. Found nodes (pillar+tower):" + nf(getNodeMap().getSize()) + ", " + Helper.getMemInfo());
        if (createStorage)
            ghStorage.create(tmp);

        long wayStart = -1;
        long relationStart = -1;
        long counter = 1;
        try (OSMInput in = openOsmInputFile(osmFile)) {
            LongIntMap nodeFilter = getNodeMap();

            ReaderElement item;
            while ((item = in.getNext()) != null) {
                switch (item.getType()) {
                    case ReaderElement.NODE:
                        if (nodeFilter.get(item.getId()) != EMPTY_NODE) {
                            processNode((ReaderNode) item);
                        }
                        break;

                    case ReaderElement.WAY:
                        if (wayStart < 0) {
                            LOGGER.info(nf(counter) + ", now parsing ways");
                            wayStart = counter;
                        }
                        processWay((ReaderWay) item);
                        break;
                    case ReaderElement.RELATION:
                        if (relationStart < 0) {
                            LOGGER.info(nf(counter) + ", now parsing relations");
                            relationStart = counter;
                        }
                        processRelation((ReaderRelation) item);
                        break;
                    case ReaderElement.FILEHEADER:
                        break;
                    default:
                        throw new IllegalStateException("Unknown type " + item.getType());
                }
                if (++counter % 200_000_000 == 0) {
                    LOGGER.info(nf(counter) + ", locs:" + nf(locations) + " (" + skippedLocations + ") " + Helper.getMemInfo());
                }
            }

            if (in.getUnprocessedElements() > 0)
                throw new IllegalStateException("Still unprocessed elements in reader queue " + in.getUnprocessedElements());

            // logger.info("storage nodes:" + storage.nodes() + " vs. graph nodes:" + storage.getGraph().nodes());
        } catch (Exception ex) {
            throw new RuntimeException("Couldn't process file " + osmFile + ", error: " + ex.getMessage(), ex);
        }

        finishedReading();
        if (graph.getNodes() == 0)
            throw new RuntimeException("Graph after reading OSM must not be empty. Read " + counter + " items and " + locations + " locations");
    }

    protected OSMInput openOsmInputFile(File osmFile) throws XMLStreamException, IOException {
        return new OSMInputFile(osmFile).setWorkerThreads(workerThreads).open();
    }

    /**
     * Process properties, encode flags and create edges for the way.
     */
    void processWay(ReaderWay way) {
        if (way.getNodes().size() < 2)
            return;

        // ignore multipolygon geometry
        if (!way.hasTags())
            return;

        long wayOsmId = way.getId();

        EncodingManager.AcceptWay acceptWay = new EncodingManager.AcceptWay();
        if (!encodingManager.acceptWay(way, acceptWay))
            return;

        long relationFlags = getRelFlagsMap().get(way.getId());

        // TODO move this after we have created the edge and know the coordinates => encodingManager.applyWayTags
        LongArrayList osmNodeIds = way.getNodes();
        // Estimate length of ways containing a route tag e.g. for ferry speed calculation
        if (osmNodeIds.size() > 1) {
            int first = getNodeMap().get(osmNodeIds.get(0));
            int last = getNodeMap().get(osmNodeIds.get(osmNodeIds.size() - 1));
            double firstLat = getTmpLatitude(first), firstLon = getTmpLongitude(first);
            double lastLat = getTmpLatitude(last), lastLon = getTmpLongitude(last);
            if (!Double.isNaN(firstLat) && !Double.isNaN(firstLon) && !Double.isNaN(lastLat) && !Double.isNaN(lastLon)) {
                double estimatedDist = distCalc.calcDist(firstLat, firstLon, lastLat, lastLon);
                // Add artificial tag for the estimated distance and center
                way.setTag("estimated_distance", estimatedDist);
                way.setTag("estimated_center", new GHPoint((firstLat + lastLat) / 2, (firstLon + lastLon) / 2));
            }
        }

        if (way.getTag("duration") != null) {
            try {
                long dur = OSMReaderUtility.parseDuration(way.getTag("duration"));
                // Provide the duration value in seconds in an artificial graphhopper specific tag:
                way.setTag("duration:seconds", Long.toString(dur));
            } catch (Exception ex) {
                LOGGER.warn("Parsing error in way with OSMID=" + way.getId() + " : " + ex.getMessage());
            }
        }

        IntsRef edgeFlags = encodingManager.handleWayTags(way, acceptWay, relationFlags);
        if (edgeFlags.isEmpty())
            return;

        List<EdgeIteratorState> createdEdges = new ArrayList<>();
        // look for barriers along the way
        final int size = osmNodeIds.size();
        int lastBarrier = -1;
        for (int i = 0; i < size; i++) {
            long nodeId = osmNodeIds.get(i);
            long nodeFlags = getNodeFlagsMap().get(nodeId);
            // barrier was spotted and the way is passable for that mode of travel
            if (nodeFlags > 0) {
                if (isOnePassable(encodingManager.getAccessEncFromNodeFlags(nodeFlags), edgeFlags)) {
                    // remove barrier to avoid duplicates
                    getNodeFlagsMap().put(nodeId, 0);

                    // create shadow node copy for zero length edge
                    long newNodeId = addBarrierNode(nodeId);
                    if (i > 0) {
                        // start at beginning of array if there was no previous barrier
                        if (lastBarrier < 0)
                            lastBarrier = 0;

                        // add way up to barrier shadow node                        
                        int length = i - lastBarrier + 1;
                        LongArrayList partNodeIds = new LongArrayList();
                        partNodeIds.add(osmNodeIds.buffer, lastBarrier, length);
                        partNodeIds.set(length - 1, newNodeId);
                        createdEdges.addAll(addOSMWay(partNodeIds, edgeFlags, wayOsmId));

                        // create zero length edge for barrier
                        createdEdges.addAll(addBarrierEdge(newNodeId, nodeId, edgeFlags, nodeFlags, wayOsmId));
                    } else {
                        // run edge from real first node to shadow node
                        createdEdges.addAll(addBarrierEdge(nodeId, newNodeId, edgeFlags, nodeFlags, wayOsmId));

                        // exchange first node for created barrier node
                        osmNodeIds.set(0, newNodeId);
                    }
                    // remember barrier for processing the way behind it
                    lastBarrier = i;
                }
            }
        }

        // just add remainder of way to graph if barrier was not the last node
        if (lastBarrier >= 0) {
            if (lastBarrier < size - 1) {
                LongArrayList partNodeIds = new LongArrayList();
                partNodeIds.add(osmNodeIds.buffer, lastBarrier, size - lastBarrier);
                createdEdges.addAll(addOSMWay(partNodeIds, edgeFlags, wayOsmId));
            }
        } else {
            // no barriers - simply add the whole way
            createdEdges.addAll(addOSMWay(way.getNodes(), edgeFlags, wayOsmId));
        }

        for (EdgeIteratorState edge : createdEdges) {
            encodingManager.applyWayTags(way, edge);
        }
    }

    public void processRelation(ReaderRelation relation) {
        if (relation.hasTag("type", "restriction")) {
            OSMTurnRelation turnRelation = createTurnRelation(relation);
            if (turnRelation != null) {
                GraphExtension extendedStorage = graph.getExtension();
                if (extendedStorage instanceof TurnCostExtension) {
                    TurnCostExtension tcs = (TurnCostExtension) extendedStorage;
                    Collection<TurnCostTableEntry> entries = analyzeTurnRelation(turnRelation);
                    for (TurnCostTableEntry entry : entries) {
                        tcs.addTurnInfo(entry.edgeFrom, entry.nodeVia, entry.edgeTo, entry.flags);
                    }
                }
            }
        }
    }

    public Collection<TurnCostTableEntry> analyzeTurnRelation(OSMTurnRelation turnRelation) {
        Map<Long, TurnCostTableEntry> entries = new LinkedHashMap<>();

        for (FlagEncoder encoder : encodingManager.fetchEdgeEncoders()) {
            for (TurnCostTableEntry entry : analyzeTurnRelation(encoder, turnRelation)) {
                TurnCostTableEntry oldEntry = entries.get(entry.getItemId());
                if (oldEntry != null) {
                    // merging different encoders
                    oldEntry.flags |= entry.flags;
                } else {
                    entries.put(entry.getItemId(), entry);
                }
            }
        }

        return entries.values();
    }

    public Collection<TurnCostTableEntry> analyzeTurnRelation(FlagEncoder encoder, OSMTurnRelation turnRelation) {
        if (!encoder.supports(TurnWeighting.class))
            return Collections.emptyList();

        EdgeExplorer edgeOutExplorer = outExplorerMap.get(encoder);
        EdgeExplorer edgeInExplorer = inExplorerMap.get(encoder);

        if (edgeOutExplorer == null || edgeInExplorer == null) {
            edgeOutExplorer = graph.createEdgeExplorer(DefaultEdgeFilter.outEdges(encoder));
            outExplorerMap.put(encoder, edgeOutExplorer);

            edgeInExplorer = graph.createEdgeExplorer(DefaultEdgeFilter.inEdges(encoder));
            inExplorerMap.put(encoder, edgeInExplorer);
        }
        return turnRelation.getRestrictionAsEntries(encoder, edgeOutExplorer, edgeInExplorer, this);
    }

    /**
     * @return OSM way ID from specified edgeId. Only previously stored OSM-way-IDs are returned in
     * order to reduce memory overhead.
     */
    public long getOsmIdOfInternalEdge(int edgeId) {
        return getEdgeIdToOsmWayIdMap().get(edgeId);
    }

    public int getInternalNodeIdOfOsmNode(long nodeOsmId) {
        int id = getNodeMap().get(nodeOsmId);
        if (id < TOWER_NODE)
            return -id - 3;

        return EMPTY_NODE;
    }

    // TODO remove this ugly stuff via better preparsing phase! E.g. putting every tags etc into a helper file!
    double getTmpLatitude(int id) {
        if (id == EMPTY_NODE)
            return Double.NaN;
        if (id < TOWER_NODE) {
            // tower node
            id = -id - 3;
            return nodeAccess.getLatitude(id);
        } else if (id > -TOWER_NODE) {
            // pillar node
            id = id - 3;
            return pillarInfo.getLatitude(id);
        } else
            // e.g. if id is not handled from preparse (e.g. was ignored via isInBounds)
            return Double.NaN;
    }

    double getTmpLongitude(int id) {
        if (id == EMPTY_NODE)
            return Double.NaN;
        if (id < TOWER_NODE) {
            // tower node
            id = -id - 3;
            return nodeAccess.getLongitude(id);
        } else if (id > -TOWER_NODE) {
            // pillar node
            id = id - 3;
            return pillarInfo.getLon(id);
        } else
            // e.g. if id is not handled from preparse (e.g. was ignored via isInBounds)
            return Double.NaN;
    }

    private void processNode(ReaderNode node) {
        if (isInBounds(node)) {
            addNode(node);

            // analyze node tags for barriers
            if (node.hasTags()) {
                long nodeFlags = encodingManager.handleNodeTags(node);
                if (nodeFlags != 0)
                    getNodeFlagsMap().put(node.getId(), nodeFlags);
            }

            locations++;
        } else {
            skippedLocations++;
        }
    }

    boolean addNode(ReaderNode node) {
        int nodeType = getNodeMap().get(node.getId());
        if (nodeType == EMPTY_NODE)
            return false;

        double lat = node.getLat();
        double lon = node.getLon();
        double ele = getElevation(node);
        if (nodeType == TOWER_NODE) {
            addTowerNode(node.getId(), lat, lon, ele);
        } else if (nodeType == PILLAR_NODE) {
            pillarInfo.setNode(nextPillarId, lat, lon, ele);
            getNodeMap().put(node.getId(), nextPillarId + 3);
            nextPillarId++;
        }
        return true;
    }

    /**
     * The nodeFlags store the encoders to check for accessibility in edgeFlags. E.g. if nodeFlags==3, then the
     * accessibility of the first two encoders will be check in edgeFlags
     */
    private static boolean isOnePassable(List<BooleanEncodedValue> checkEncoders, IntsRef edgeFlags) {
        for (BooleanEncodedValue accessEnc : checkEncoders) {
            if (accessEnc.getBool(false, edgeFlags) || accessEnc.getBool(true, edgeFlags))
                return true;
        }
        return false;
    }

    protected double getElevation(ReaderNode node) {
        return eleProvider.getEle(node.getLat(), node.getLon());
    }

    void prepareWaysWithRelationInfo(ReaderRelation osmRelation) {
        // is there at least one tag interesting for the registed encoders?
        if (encodingManager.handleRelationTags(0, osmRelation) == 0)
            return;

        for (ReaderRelation.Member member : osmRelation.getMembers()) {
            if (member.getType() != ReaderRelation.Member.WAY)
                continue;

            long osmId = member.getRef();
            long oldRelationFlags = getRelFlagsMap().get(osmId);

            // Check if our new relation data is better comparated to the the last one
            long newRelationFlags = encodingManager.handleRelationTags(oldRelationFlags, osmRelation);
            if (oldRelationFlags != newRelationFlags)
                getRelFlagsMap().put(osmId, newRelationFlags);
        }
    }

    void prepareHighwayNode(long osmId) {
        int tmpGHNodeId = getNodeMap().get(osmId);
        if (tmpGHNodeId == EMPTY_NODE) {
            // osmId is used exactly once
            getNodeMap().put(osmId, PILLAR_NODE);
        } else if (tmpGHNodeId > EMPTY_NODE) {
            // mark node as tower node as it occured at least twice times
            getNodeMap().put(osmId, TOWER_NODE);
        } else {
            // tmpIndex is already negative (already tower node)
        }
    }

    int addTowerNode(long osmId, double lat, double lon, double ele) {
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
    Collection<EdgeIteratorState> addOSMWay(final LongIndexedContainer osmNodeIds, final IntsRef flags, final long wayOsmId) {
        PointList pointList = new PointList(osmNodeIds.size(), nodeAccess.is3D());
        List<EdgeIteratorState> newEdges = new ArrayList<>(5);
        int firstNode = -1;
        int lastIndex = osmNodeIds.size() - 1;
        int lastInBoundsPillarNode = -1;
        try {
            for (int i = 0; i < osmNodeIds.size(); i++) {
                long osmNodeId = osmNodeIds.get(i);
                int tmpNode = getNodeMap().get(osmNodeId);
                if (tmpNode == EMPTY_NODE)
                    continue;

                // skip osmIds with no associated pillar or tower id (e.g. !OSMReader.isBounds)
                if (tmpNode == TOWER_NODE)
                    continue;

                if (tmpNode == PILLAR_NODE) {
                    // In some cases no node information is saved for the specified osmId.
                    // ie. a way references a <node> which does not exist in the current file.
                    // => if the node before was a pillar node then convert into to tower node (as it is also end-standing).
                    if (!pointList.isEmpty() && lastInBoundsPillarNode > -TOWER_NODE) {
                        // transform the pillar node to a tower node
                        tmpNode = lastInBoundsPillarNode;
                        tmpNode = handlePillarNode(tmpNode, osmNodeId, null, true);
                        tmpNode = -tmpNode - 3;
                        if (pointList.getSize() > 1 && firstNode >= 0) {
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
                    throw new AssertionError("Mapped index not in correct bounds " + tmpNode + ", " + osmNodeId);

                if (tmpNode > -TOWER_NODE) {
                    boolean convertToTowerNode = i == 0 || i == lastIndex;
                    if (!convertToTowerNode) {
                        lastInBoundsPillarNode = tmpNode;
                    }

                    // PILLAR node, but convert to towerNode if end-standing
                    tmpNode = handlePillarNode(tmpNode, osmNodeId, pointList, convertToTowerNode);
                }

                if (tmpNode < TOWER_NODE) {
                    // TOWER node
                    tmpNode = -tmpNode - 3;

                    if (firstNode >= 0 && firstNode == tmpNode) {
                        // loop detected. See #1525 and #1533. Insert last OSM ID as tower node. Do this for all loops so that users can manipulate loops later arbitrarily.
                        long lastOsmNodeId = osmNodeIds.get(i - 1);
                        int lastGHNodeId = getNodeMap().get(lastOsmNodeId);
                        if (lastGHNodeId < TOWER_NODE) {
                            LOGGER.warn("Pillar node " + lastOsmNodeId + " is already a tower node and used in loop, see #1533. " +
                                    "Fix mapping for way " + wayOsmId + ", nodes:" + osmNodeIds);
                            break;
                        }

                        int newEndNode = -handlePillarNode(lastGHNodeId, lastOsmNodeId, pointList, true) - 3;
                        newEdges.add(addEdge(firstNode, newEndNode, pointList, flags, wayOsmId));
                        pointList.clear();
                        pointList.add(nodeAccess, newEndNode);
                        firstNode = newEndNode;
                    }

                    pointList.add(nodeAccess, tmpNode);
                    if (firstNode >= 0) {
                        newEdges.add(addEdge(firstNode, tmpNode, pointList, flags, wayOsmId));
                        pointList.clear();
                        pointList.add(nodeAccess, tmpNode);
                    }
                    firstNode = tmpNode;
                }
            }
        } catch (RuntimeException ex) {
            LOGGER.error("Couldn't properly add edge with osm ids:" + osmNodeIds, ex);
            if (exitOnlyPillarNodeException)
                throw ex;
        }
        return newEdges;
    }

    EdgeIteratorState addEdge(int fromIndex, int toIndex, PointList pointList, IntsRef flags, long wayOsmId) {
        // sanity checks
        if (fromIndex < 0 || toIndex < 0)
            throw new AssertionError("to or from index is invalid for this edge " + fromIndex + "->" + toIndex + ", points:" + pointList);
        if (pointList.getDimension() != nodeAccess.getDimension())
            throw new AssertionError("Dimension does not match for pointList vs. nodeAccess " + pointList.getDimension() + " <-> " + nodeAccess.getDimension());

        // Smooth the elevation before calculating the distance because the distance will be incorrect if calculated afterwards
        if (this.smoothElevation)
            pointList = GraphElevationSmoothing.smoothElevation(pointList);

        double towerNodeDistance = 0;
        double prevLat = pointList.getLatitude(0);
        double prevLon = pointList.getLongitude(0);
        double prevEle = pointList.is3D() ? pointList.getElevation(0) : Double.NaN;
        double lat, lon, ele = Double.NaN;
        PointList pillarNodes = new PointList(pointList.getSize() - 2, nodeAccess.is3D());
        int nodes = pointList.getSize();
        for (int i = 1; i < nodes; i++) {
            // we could save some lines if we would use pointList.calcDistance(distCalc);
            lat = pointList.getLatitude(i);
            lon = pointList.getLongitude(i);
            if (pointList.is3D()) {
                ele = pointList.getElevation(i);
                if (!distCalc.isCrossBoundary(lon, prevLon))
                    towerNodeDistance += distCalc3D.calcDist(prevLat, prevLon, prevEle, lat, lon, ele);
                prevEle = ele;
            } else if (!distCalc.isCrossBoundary(lon, prevLon))
                towerNodeDistance += distCalc.calcDist(prevLat, prevLon, lat, lon);

            prevLat = lat;
            prevLon = lon;
            if (nodes > 2 && i < nodes - 1) {
                if (pillarNodes.is3D())
                    pillarNodes.add(lat, lon, ele);
                else
                    pillarNodes.add(lat, lon);
            }
        }
        if (towerNodeDistance < 0.0001) {
            // As investigation shows often two paths should have crossed via one identical point 
            // but end up in two very close points.
            zeroCounter++;
            towerNodeDistance = 0.0001;
        }

        double maxDistance = (Integer.MAX_VALUE - 1) / 1000d;
        if (Double.isNaN(towerNodeDistance)) {
            LOGGER.warn("Bug in OSM or GraphHopper. Illegal tower node distance " + towerNodeDistance + " reset to 1m, osm way " + wayOsmId);
            towerNodeDistance = 1;
        }

        if (Double.isInfinite(towerNodeDistance) || towerNodeDistance > maxDistance) {
            // Too large is very rare and often the wrong tagging. See #435 
            // so we can avoid the complexity of splitting the way for now (new towernodes would be required, splitting up geometry etc)
            LOGGER.warn("Bug in OSM or GraphHopper. Too big tower node distance " + towerNodeDistance + " reset to large value, osm way " + wayOsmId);
            towerNodeDistance = maxDistance;
        }

        EdgeIteratorState iter = graph.edge(fromIndex, toIndex).setDistance(towerNodeDistance).setFlags(flags);

        if (nodes > 2) {
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
    protected void storeOsmWayID(int edgeId, long osmWayId) {
        if (getOsmWayIdSet().contains(osmWayId)) {
            getEdgeIdToOsmWayIdMap().put(edgeId, osmWayId);
        }
    }

    /**
     * @return converted tower node
     */
    private int handlePillarNode(int tmpNode, long osmId, PointList pointList, boolean convertToTowerNode) {
        tmpNode = tmpNode - 3;
        double lat = pillarInfo.getLatitude(tmpNode);
        double lon = pillarInfo.getLongitude(tmpNode);
        double ele = pillarInfo.getElevation(tmpNode);
        if (lat == Double.MAX_VALUE || lon == Double.MAX_VALUE)
            throw new RuntimeException("Conversion pillarNode to towerNode already happended!? "
                    + "osmId:" + osmId + " pillarIndex:" + tmpNode);

        if (convertToTowerNode) {
            // convert pillarNode type to towerNode, make pillar values invalid
            pillarInfo.setNode(tmpNode, Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE);
            tmpNode = addTowerNode(osmId, lat, lon, ele);
        } else if (pointList.is3D())
            pointList.add(lat, lon, ele);
        else
            pointList.add(lat, lon);

        return tmpNode;
    }

    protected void finishedReading() {
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
    long addBarrierNode(long nodeId) {
        ReaderNode newNode;
        int graphIndex = getNodeMap().get(nodeId);
        if (graphIndex < TOWER_NODE) {
            graphIndex = -graphIndex - 3;
            newNode = new ReaderNode(createNewNodeId(), nodeAccess, graphIndex);
        } else {
            graphIndex = graphIndex - 3;
            newNode = new ReaderNode(createNewNodeId(), pillarInfo, graphIndex);
        }

        final long id = newNode.getId();
        prepareHighwayNode(id);
        addNode(newNode);
        return id;
    }

    private long createNewNodeId() {
        return newUniqueOsmId++;
    }

    /**
     * Add a zero length edge with reduced routing options to the graph.
     */
    Collection<EdgeIteratorState> addBarrierEdge(long fromId, long toId, IntsRef inEdgeFlags, long nodeFlags, long wayOsmId) {
        IntsRef edgeFlags = IntsRef.deepCopyOf(inEdgeFlags);
        // clear blocked directions from flags
        for (BooleanEncodedValue accessEnc : encodingManager.getAccessEncFromNodeFlags(nodeFlags)) {
            accessEnc.setBool(false, edgeFlags, false);
            accessEnc.setBool(true, edgeFlags, false);
        }
        // add edge
        barrierNodeIds.clear();
        barrierNodeIds.add(fromId);
        barrierNodeIds.add(toId);
        return addOSMWay(barrierNodeIds, edgeFlags, wayOsmId);
    }

    /**
     * Creates an OSM turn relation out of an unspecified OSM relation
     * <p>
     *
     * @return the OSM turn relation, <code>null</code>, if unsupported turn relation
     */
    OSMTurnRelation createTurnRelation(ReaderRelation relation) {
        OSMTurnRelation.Type type = OSMTurnRelation.Type.getRestrictionType(relation.getTag("restriction"));
        if (type != OSMTurnRelation.Type.UNSUPPORTED) {
            long fromWayID = -1;
            long viaNodeID = -1;
            long toWayID = -1;

            for (ReaderRelation.Member member : relation.getMembers()) {
                if (ReaderElement.WAY == member.getType()) {
                    if ("from".equals(member.getRole())) {
                        fromWayID = member.getRef();
                    } else if ("to".equals(member.getRole())) {
                        toWayID = member.getRef();
                    }
                } else if (ReaderElement.NODE == member.getType() && "via".equals(member.getRole())) {
                    viaNodeID = member.getRef();
                }
            }
            if (fromWayID >= 0 && toWayID >= 0 && viaNodeID >= 0) {
                return new OSMTurnRelation(fromWayID, viaNodeID, toWayID, type);
            }
        }
        return null;
    }

    /**
     * Filter method, override in subclass
     */
    boolean isInBounds(ReaderNode node) {
        return true;
    }

    /**
     * Maps OSM IDs (long) to internal node IDs (int)
     */
    protected LongIntMap getNodeMap() {
        return osmNodeIdToInternalNodeMap;
    }

    protected LongLongMap getNodeFlagsMap() {
        return osmNodeIdToNodeFlagsMap;
    }

    GHLongLongHashMap getRelFlagsMap() {
        return osmWayIdToRouteWeightMap;
    }

    @Override
    public OSMReader setWayPointMaxDistance(double maxDist) {
        doSimplify = maxDist > 0;
        simplifyAlgo.setMaxDistance(maxDist);
        return this;
    }

    @Override
    public DataReader setSmoothElevation(boolean smoothElevation) {
        this.smoothElevation = smoothElevation;
        return this;
    }

    @Override
    public OSMReader setWorkerThreads(int numOfWorkers) {
        this.workerThreads = numOfWorkers;
        return this;
    }

    @Override
    public OSMReader setElevationProvider(ElevationProvider eleProvider) {
        if (eleProvider == null)
            throw new IllegalStateException("Use the NOOP elevation provider instead of null or don't call setElevationProvider");

        if (!nodeAccess.is3D() && ElevationProvider.NOOP != eleProvider)
            throw new IllegalStateException("Make sure you graph accepts 3D data");

        this.eleProvider = eleProvider;
        return this;
    }

    @Override
    public DataReader setFile(File osmFile) {
        this.osmFile = osmFile;
        return this;
    }

    private void printInfo(String str) {
        LOGGER.info("finished " + str + " processing." + " nodes: " + graph.getNodes()
                + ", osmIdMap.size:" + getNodeMap().getSize() + ", osmIdMap:" + getNodeMap().getMemoryUsage() + "MB"
                + ", nodeFlagsMap.size:" + getNodeFlagsMap().size() + ", relFlagsMap.size:" + getRelFlagsMap().size()
                + ", zeroCounter:" + zeroCounter
                + " " + Helper.getMemInfo());
    }

    @Override
    public Date getDataDate() {
        return osmDataDate;
    }

    /**
     * Per default the storage used in this OSMReader is uninitialized and created i.e. createStorage is true. Specify
     * false if you call the create method outside of OSMReader.
     */
    public void setCreateStorage(boolean createStorage) {
        this.createStorage = createStorage;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }
}
