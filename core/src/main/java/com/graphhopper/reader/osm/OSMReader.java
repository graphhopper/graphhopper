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
import com.graphhopper.coll.LongIntMap;
import com.graphhopper.coll.*;
import com.graphhopper.reader.*;
import com.graphhopper.reader.dem.EdgeSampling;
import com.graphhopper.reader.dem.ElevationProvider;
import com.graphhopper.reader.dem.GraphElevationSmoothing;
import com.graphhopper.routing.OSMReaderConfig;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.Country;
import com.graphhopper.routing.util.AreaIndex;
import com.graphhopper.routing.util.CustomArea;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.countryrules.CountryRule;
import com.graphhopper.routing.util.countryrules.CountryRuleFactory;
import com.graphhopper.routing.util.parsers.TurnCostParser;
import com.graphhopper.storage.*;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.GHPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLStreamException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static com.graphhopper.util.Helper.nf;
import static java.util.Collections.emptyList;

/**
 * This class parses an OSM xml or pbf file and creates a graph from it. It does so in a two phase
 * parsing processes in order to reduce memory usage compared to a single parsing processing.
 * <p>
 * 1. a) Reads ways from OSM file and stores all associated node ids in {@link #osmNodeIdToInternalNodeMap}. If a
 * node occurs once it is a pillar node and if more it is a tower node, otherwise
 * {@link #osmNodeIdToInternalNodeMap} returns EMPTY.
 * <p>
 * 1. b) Reads relations from OSM file. In case that the relation is a route relation, it stores
 * specific relation attributes required for routing into {@link #osmWayIdToRelationFlagsMap} for all the ways
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
public class OSMReader implements TurnCostParser.ExternalInternalMap {
    private static final Logger LOGGER = LoggerFactory.getLogger(OSMReader.class);
    private static final int JUNCTION_NODE = -2;
    private static final int EMPTY_NODE = -1;
    private static final int END_NODE = 0;
    private static final int INTERMEDIATE_NODE = 1;
    // connection nodes are those where (only) two OSM ways are connected at their ends, so they are still no junctions
    private static final int CONNECTION_NODE = 2;

    private final GraphHopperStorage ghStorage;
    private final OSMReaderConfig config;
    private final Graph graph;
    private final NodeAccess nodeAccess;
    private final LongIndexedContainer barrierNodeIds = new LongArrayList();
    private final DistanceCalc distCalc = DistanceCalcEarth.DIST_EARTH;
    private final DouglasPeucker simplifyAlgo = new DouglasPeucker();
    private CountryRuleFactory countryRuleFactory = null;
    protected long zeroCounter = 0;
    protected PillarInfo pillarInfo;
    private long locations;
    private long ignoredBarrierNodes;
    private final EncodingManager encodingManager;
    // Choosing the best Map<Long, Integer> is hard. We need a memory efficient and fast solution for big data sets!
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
    private GHLongLongHashMap osmWayIdToRelationFlagsMap;
    // stores osm way ids used by relations to identify which edge ids needs to be mapped later
    private GHLongHashSet osmWayIdSet = new GHLongHashSet();
    private IntLongMap edgeIdToOsmWayIdMap;
    private final boolean doSimplify;
    private int nextTowerId = 0;
    private int nextPillarId = 0;
    // negative but increasing to avoid clash with custom created OSM files
    private long newUniqueOsmId = -Long.MAX_VALUE;
    private AreaIndex<CustomArea> areaIndex;
    private ElevationProvider eleProvider = ElevationProvider.NOOP;
    private File osmFile;
    private Date osmDataDate;
    private final IntsRef tempRelFlags;
    private final TurnCostStorage tcs;

    public OSMReader(GraphHopperStorage ghStorage, OSMReaderConfig config) {
        this.ghStorage = ghStorage;
        this.config = config;
        this.graph = ghStorage;
        this.nodeAccess = graph.getNodeAccess();
        this.encodingManager = ghStorage.getEncodingManager();

        doSimplify = config.getMaxWayPointDistance() > 0;
        simplifyAlgo.setMaxDistance(config.getMaxWayPointDistance());
        simplifyAlgo.setElevationMaxDistance(config.getElevationMaxWayPointDistance());

        osmNodeIdToInternalNodeMap = new GHLongIntBTree(200);
        osmNodeIdToNodeFlagsMap = new GHLongLongHashMap(200, .5f);
        osmWayIdToRelationFlagsMap = new GHLongLongHashMap(200, .5f);
        pillarInfo = new PillarInfo(nodeAccess.is3D(), ghStorage.getDirectory());
        tempRelFlags = encodingManager.createRelationFlags();
        if (tempRelFlags.length != 2)
            throw new IllegalArgumentException("Cannot use relation flags with != 2 integers");

        tcs = graph.getTurnCostStorage();
    }

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
        writeOsmToGraph(osmFile);
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
        LOGGER.info("Starting to process OSM file: '" + osmFile + "'");
        try (OSMInput in = openOsmInputFile(osmFile)) {
            long tmpWayCounter = 1;
            long tmpRelationCounter = 1;
            ReaderElement item;
            while ((item = in.getNext()) != null) {
                if (item.isType(ReaderElement.WAY)) {
                    final ReaderWay way = (ReaderWay) item;
                    if (filterWay(way)) {
                        LongIndexedContainer wayNodes = way.getNodes();
                        int s = wayNodes.size();
                        for (int index = 0; index < s; index++) {
                            final boolean isEnd = index == 0 || index == s - 1;
                            final long osmId = wayNodes.get(index);
                            int curr = getNodeMap().get(osmId);
                            if (curr == EMPTY_NODE)
                                getNodeMap().put(osmId, isEnd ? END_NODE : INTERMEDIATE_NODE);
                            else
                                getNodeMap().put(osmId, curr == END_NODE && isEnd ? CONNECTION_NODE : JUNCTION_NODE);
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

                    if (relation.hasTag("type", "restriction")) {
                        prepareRestrictionRelation(relation);
                    }

                    if (++tmpRelationCounter % 100_000 == 0) {
                        LOGGER.info(nf(tmpRelationCounter) + " (preprocess), osmWayMap:" + nf(getRelFlagsMapSize())
                                + ", " + Helper.getMemInfo());
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
        List<OSMTurnRelation> turnRelations = createTurnRelations(relation);
        for (OSMTurnRelation turnRelation : turnRelations) {
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
    private void writeOsmToGraph(File osmFile) {
        int tmp = (int) Math.max(getNodeMap().getSize() / 50, 100);
        LOGGER.info("creating graph. Found nodes (pillar+tower):" + nf(getNodeMap().getSize()) + ", " + Helper.getMemInfo());
        ghStorage.create(tmp);

        long wayStart = -1;
        long relationStart = -1;
        long counter = 1;
        try (OSMInput in = openOsmInputFile(osmFile)) {
            ReaderElement item;
            while ((item = in.getNext()) != null) {
                switch (item.getType()) {
                    case ReaderElement.NODE:
                        processNode((ReaderNode) item);
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
                    LOGGER.info(nf(counter) + ", locs:" + nf(locations) + ", " + Helper.getMemInfo());
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
        return new OSMInputFile(osmFile).setWorkerThreads(config.getWorkerThreads()).open();
    }

    /**
     * Process properties, encode flags and create edges for the way.
     */
    protected void processWay(ReaderWay way) {
        if (way.getNodes().size() < 2)
            return;

        // ignore multipolygon geometry
        if (!way.hasTags())
            return;

        EncodingManager.AcceptWay acceptWay = new EncodingManager.AcceptWay();
        if (!encodingManager.acceptWay(way, acceptWay))
            return;

        setArtificialWayTags(way);

        IntsRef relationFlags = getRelFlagsMap(way.getId());
        IntsRef edgeFlags = encodingManager.handleWayTags(way, acceptWay, relationFlags);
        if (edgeFlags.isEmpty())
            return;

        LongArrayList osmNodeIds = way.getNodes();
        // look for barriers along the way
        final int size = osmNodeIds.size();
        int lastBarrier = -1;
        for (int i = 0; i < size; i++) {
            long nodeId = osmNodeIds.get(i);
            long nodeFlags = getNodeFlagsMap().get(nodeId);
            // barrier was spotted and the way is passable for that mode of travel
            if (nodeFlags > 0) {
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
                    addOSMWay(partNodeIds, edgeFlags, way);

                    // create zero length edge for barrier
                    addBarrierEdge(newNodeId, nodeId, edgeFlags, nodeFlags, way);
                } else {
                    // run edge from real first node to shadow node
                    addBarrierEdge(nodeId, newNodeId, edgeFlags, nodeFlags, way);

                    // exchange first node for created barrier node
                    osmNodeIds.set(0, newNodeId);
                }
                // remember barrier for processing the way behind it
                lastBarrier = i;

                // ignore this barrier node from now. for example a barrier can be connecting two ways (appear in both
                // ways) and we only want to add a barrier edge once (but we want to add one).
                getNodeFlagsMap().put(nodeId, 0);
            }
        }

        // just add remainder of way to graph if barrier was not the last node
        if (lastBarrier >= 0) {
            if (lastBarrier < size - 1) {
                LongArrayList partNodeIds = new LongArrayList();
                partNodeIds.add(osmNodeIds.buffer, lastBarrier, size - lastBarrier);
                addOSMWay(partNodeIds, edgeFlags, way);
            }
        } else {
            // no barriers - simply add the whole way
            addOSMWay(way.getNodes(), edgeFlags, way);
        }
    }

    private void setArtificialWayTags(ReaderWay way) {
        // TODO move this after we have created the edge and know the coordinates => encodingManager.applyWayTags
        LongArrayList osmNodeIds = way.getNodes();
        // Estimate length of ways containing a route tag e.g. for ferry speed calculation
        int first = getNodeMap().get(osmNodeIds.get(0));
        int last = getNodeMap().get(osmNodeIds.get(osmNodeIds.size() - 1));
        double firstLat = getTmpLatitude(first), firstLon = getTmpLongitude(first);
        double lastLat = getTmpLatitude(last), lastLon = getTmpLongitude(last);
        GHPoint estimatedCenter = null;
        if (!Double.isNaN(firstLat) && !Double.isNaN(firstLon) && !Double.isNaN(lastLat) && !Double.isNaN(lastLon)) {
            double estimatedDist = distCalc.calcDist(firstLat, firstLon, lastLat, lastLon);
            // Add artificial tag for the estimated distance and center
            way.setTag("estimated_distance", estimatedDist);
            estimatedCenter = new GHPoint((firstLat + lastLat) / 2, (firstLon + lastLon) / 2);
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

        List<CustomArea> customAreas = estimatedCenter == null || areaIndex == null
                ? emptyList()
                : areaIndex.query(estimatedCenter.lat, estimatedCenter.lon);
        // special handling for countries: since they are built-in with GraphHopper they are always fed to the encodingmanager
        Country country = Country.MISSING;
        for (CustomArea customArea : customAreas) {
            Object countryCode = customArea.getProperties().get("ISO3166-1:alpha3");
            if (countryCode == null)
                continue;
            if (country != Country.MISSING)
                LOGGER.warn("Multiple countries found for way {}: {}, {}", way.getId(), country, countryCode);
            country = Country.valueOf(countryCode.toString());
        }
        way.setTag("country", country);

        if (countryRuleFactory != null) {
            CountryRule countryRule = countryRuleFactory.getCountryRule(country);
            if (countryRule != null)
                way.setTag("country_rule", countryRule);
        }

        // also add all custom areas as artificial tag
        way.setTag("custom_areas", customAreas);
    }

    protected void processRelation(ReaderRelation relation) {
        if (tcs != null && relation.hasTag("type", "restriction"))
            storeTurnRelation(createTurnRelations(relation));
    }

    void storeTurnRelation(List<OSMTurnRelation> turnRelations) {
        for (OSMTurnRelation turnRelation : turnRelations) {
            int viaNode = getInternalNodeIdOfOsmNode(turnRelation.getViaOsmNodeId());
            // street with restriction was not included (access or tag limits etc)
            if (viaNode >= 0)
                encodingManager.handleTurnRelationTags(turnRelation, this, graph);
        }
    }

    /**
     * @return OSM way ID from specified edgeId. Only previously stored OSM-way-IDs are returned in
     * order to reduce memory overhead.
     */
    @Override
    public long getOsmIdOfInternalEdge(int edgeId) {
        return getEdgeIdToOsmWayIdMap().get(edgeId);
    }

    @Override
    public int getInternalNodeIdOfOsmNode(long nodeOsmId) {
        int id = getNodeMap().get(nodeOsmId);
        if (isTowerNode(id))
            return -id - 3;

        return -1;
    }

    // TODO remove this ugly stuff via better preprocessing phase! E.g. putting every tags etc into a helper file!
    double getTmpLatitude(int id) {
        if (id == EMPTY_NODE)
            return Double.NaN;
        if (isTowerNode(id)) {
            id = -id - 3;
            return nodeAccess.getLat(id);
        } else if (isPillarNode(id)) {
            // pillar node
            id = id - 3;
            return pillarInfo.getLat(id);
        } else
            // e.g. if id is not handled from preprocessing (e.g. was ignored via isInBounds)
            return Double.NaN;
    }

    double getTmpLongitude(int id) {
        if (id == EMPTY_NODE)
            return Double.NaN;
        if (isTowerNode(id)) {
            id = -id - 3;
            return nodeAccess.getLon(id);
        } else if (isPillarNode(id)) {
            id = id - 3;
            return pillarInfo.getLon(id);
        } else
            // e.g. if id is not handled from preprocessing (e.g. was ignored via isInBounds)
            return Double.NaN;
    }

    protected void processNode(ReaderNode node) {
        int nodeType = getNodeMap().get(node.getId());
        if (nodeType == EMPTY_NODE) {
            return;
        } else if (nodeType == JUNCTION_NODE || nodeType == CONNECTION_NODE) {
            addTowerNode(node.getId(), node.getLat(), node.getLon(), eleProvider.getEle(node));
        } else if (nodeType == INTERMEDIATE_NODE || nodeType == END_NODE) {
            addPillarNode(node.getId(), node.getLat(), node.getLon(), eleProvider.getEle(node));
        }

        // analyze node tags for barriers
        if (node.hasTags()) {
            long nodeFlags = encodingManager.handleNodeTags(node);
            if (nodeFlags != 0)
                if (nodeType == JUNCTION_NODE) {
                    LOGGER.debug("OSM node {} at {},{} is a barrier node at a junction, the barrier will be ignored", node.getId(), Helper.round(node.getLat(), 7), Helper.round(node.getLon(), 7));
                    ignoredBarrierNodes++;
                } else
                    getNodeFlagsMap().put(node.getId(), nodeFlags);
        }

        locations++;
    }

    private void addPillarNode(long osmId, double lat, double lon, double ele) {
        pillarInfo.setNode(nextPillarId, lat, lon, ele);
        getNodeMap().put(osmId, nextPillarId + 3);
        nextPillarId++;
    }

    void prepareWaysWithRelationInfo(ReaderRelation osmRelation) {
        for (ReaderRelation.Member member : osmRelation.getMembers()) {
            if (member.getType() != ReaderRelation.Member.WAY)
                continue;

            long osmId = member.getRef();
            IntsRef oldRelationFlags = getRelFlagsMap(osmId);

            // Check if our new relation data is better compared to the last one
            IntsRef newRelationFlags = encodingManager.handleRelationTags(osmRelation, oldRelationFlags);
            putRelFlagsMap(osmId, newRelationFlags);
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
    void addOSMWay(final LongIndexedContainer osmNodeIds, final IntsRef flags, final ReaderWay way) {
        final PointList pointList = new PointList(osmNodeIds.size(), nodeAccess.is3D());
        int firstNode = -1;
        int lastInBoundsPillarNode = -1;
        try {
            // #2221: ways might include nodes at the beginning or end that do not exist -> skip them
            int firstExisting = -1;
            int lastExisting = -1;
            for (int i = 0; i < osmNodeIds.size(); ++i) {
                final int tmpNode = getNodeMap().get(osmNodeIds.get(i));
                if (isNodeId(tmpNode)) {
                    firstExisting = i;
                    break;
                }
            }
            for (int i = osmNodeIds.size() - 1; i >= 0; --i) {
                final int tmpNode = getNodeMap().get(osmNodeIds.get(i));
                if (isNodeId(tmpNode)) {
                    lastExisting = i;
                    break;
                }
            }
            if (firstExisting < 0) {
                assert lastExisting < 0;
                return;
            }
            for (int i = firstExisting; i <= lastExisting; i++) {
                final long osmNodeId = osmNodeIds.get(i);
                int tmpNode = getNodeMap().get(osmNodeId);
                if (tmpNode == EMPTY_NODE)
                    continue;

                // skip osmIds with no associated pillar or tower id (e.g. !OSMReader.isBounds)
                if (tmpNode == JUNCTION_NODE || tmpNode == CONNECTION_NODE)
                    continue;

                if (tmpNode == INTERMEDIATE_NODE || tmpNode == END_NODE) {
                    // In some cases no node information is saved for the specified osmId.
                    // i.e. a way references a <node> which does not exist in the current file.
                    // => if the node before was a pillar node then convert into to tower node (as it is also end-standing).
                    if (!pointList.isEmpty() && isPillarNode(lastInBoundsPillarNode)) {
                        // transform the pillar node to a tower node
                        tmpNode = lastInBoundsPillarNode;
                        tmpNode = handlePillarNode(tmpNode, osmNodeId, null, true);
                        tmpNode = -tmpNode - 3;
                        if (pointList.size() > 1 && firstNode >= 0) {
                            // TOWER node
                            addEdge(firstNode, tmpNode, pointList, flags, way);
                            pointList.clear();
                            pointList.add(nodeAccess, tmpNode);
                        }
                        firstNode = tmpNode;
                        lastInBoundsPillarNode = -1;
                    }
                    continue;
                }

                if (!isNodeId(tmpNode))
                    throw new AssertionError("Mapped index not in correct bounds " + tmpNode + ", " + osmNodeId);

                if (isPillarNode(tmpNode)) {
                    // PILLAR node, but convert to towerNode if end-standing
                    boolean convertToTowerNode = i == firstExisting || i == lastExisting;
                    if (!convertToTowerNode)
                        lastInBoundsPillarNode = tmpNode;
                    tmpNode = handlePillarNode(tmpNode, osmNodeId, pointList, convertToTowerNode);
                }

                if (isTowerNode(tmpNode)) {
                    tmpNode = -tmpNode - 3;

                    if (firstNode >= 0 && firstNode == tmpNode) {
                        // loop detected. See #1525 and #1533. Insert last OSM ID as tower node. Do this for all loops so that users can manipulate loops later arbitrarily.
                        long lastOsmNodeId = osmNodeIds.get(i - 1);
                        int lastGHNodeId = getNodeMap().get(lastOsmNodeId);
                        if (isTowerNode(lastGHNodeId)) {
                            LOGGER.warn("Pillar node " + lastOsmNodeId + " is already a tower node and used in loop, see #1533. " +
                                    "Fix mapping for way " + way.getId() + ", nodes:" + osmNodeIds);
                            break;
                        }

                        int newEndNode = -handlePillarNode(lastGHNodeId, lastOsmNodeId, pointList, true) - 3;
                        addEdge(firstNode, newEndNode, pointList, flags, way);
                        pointList.clear();
                        pointList.add(nodeAccess, newEndNode);
                        firstNode = newEndNode;
                    }

                    pointList.add(nodeAccess, tmpNode);
                    if (firstNode >= 0) {
                        addEdge(firstNode, tmpNode, pointList, flags, way);
                        pointList.clear();
                        pointList.add(nodeAccess, tmpNode);
                    }
                    firstNode = tmpNode;
                }
            }
        } catch (RuntimeException ex) {
            LOGGER.error("Couldn't properly add edge with osm ids:" + osmNodeIds, ex);
            throw ex;
        }
    }

    void addEdge(int fromIndex, int toIndex, PointList pointList, IntsRef flags, ReaderWay way) {
        // sanity checks
        if (fromIndex < 0 || toIndex < 0)
            throw new AssertionError("to or from index is invalid for this edge " + fromIndex + "->" + toIndex + ", points:" + pointList);
        if (pointList.getDimension() != nodeAccess.getDimension())
            throw new AssertionError("Dimension does not match for pointList vs. nodeAccess " + pointList.getDimension() + " <-> " + nodeAccess.getDimension());

        // Smooth the elevation before calculating the distance because the distance will be incorrect if calculated afterwards
        if (config.isSmoothElevation())
            GraphElevationSmoothing.smoothElevation(pointList);

        // sample points along long edges
        if (config.getLongEdgeSamplingDistance() < Double.MAX_VALUE && pointList.is3D())
            pointList = EdgeSampling.sample(pointList, config.getLongEdgeSamplingDistance(), distCalc, eleProvider);

        if (doSimplify && pointList.size() > 2)
            simplifyAlgo.simplify(pointList);

        double towerNodeDistance = distCalc.calcDistance(pointList);

        if (towerNodeDistance < 0.001) {
            // As investigation shows often two paths should have crossed via one identical point 
            // but end up in two very close points.
            zeroCounter++;
            towerNodeDistance = 0.001;
        }

        double maxDistance = (Integer.MAX_VALUE - 1) / 1000d;
        if (Double.isNaN(towerNodeDistance)) {
            LOGGER.warn("Bug in OSM or GraphHopper. Illegal tower node distance " + towerNodeDistance + " reset to 1m, osm way " + way.getId());
            towerNodeDistance = 1;
        }

        if (Double.isInfinite(towerNodeDistance) || towerNodeDistance > maxDistance) {
            // Too large is very rare and often the wrong tagging. See #435 
            // so we can avoid the complexity of splitting the way for now (new towernodes would be required, splitting up geometry etc)
            LOGGER.warn("Bug in OSM or GraphHopper. Too big tower node distance " + towerNodeDistance + " reset to large value, osm way " + way.getId());
            towerNodeDistance = maxDistance;
        }

        EdgeIteratorState iter = graph.edge(fromIndex, toIndex).setDistance(towerNodeDistance).setFlags(flags);

        // If the entire way is just the first and last point, do not waste space storing an empty way geometry
        if (pointList.size() > 2) {
            // the geometry consists only of pillar nodes, but we check that the first and last points of the pointList
            // are equal to the tower node coordinates
            checkCoordinates(fromIndex, pointList.get(0));
            checkCoordinates(toIndex, pointList.get(pointList.size() - 1));
            iter.setWayGeometry(pointList.shallowCopy(1, pointList.size() - 1, false));
        }
        encodingManager.applyWayTags(way, iter);

        checkDistance(iter);
        storeOsmWayID(iter.getEdge(), way.getId());
    }

    private void checkCoordinates(int nodeIndex, GHPoint point) {
        final double tolerance = 1.e-6;
        if (Math.abs(nodeAccess.getLat(nodeIndex) - point.getLat()) > tolerance || Math.abs(nodeAccess.getLon(nodeIndex) - point.getLon()) > tolerance)
            throw new IllegalStateException("Suspicious coordinates for node " + nodeIndex + ": (" + nodeAccess.getLat(nodeIndex) + "," + nodeAccess.getLon(nodeIndex) + ") vs. (" + point + ")");
    }

    private void checkDistance(EdgeIteratorState edge) {
        final double tolerance = 1;
        final double edgeDistance = edge.getDistance();
        final double geometryDistance = distCalc.calcDistance(edge.fetchWayGeometry(FetchMode.ALL));
        if (edgeDistance > 2_000_000)
            LOGGER.warn("Very long edge detected: " + edge + " dist: " + edgeDistance);
        else if (Math.abs(edgeDistance - geometryDistance) > tolerance)
            throw new IllegalStateException("Suspicious distance for edge: " + edge + " " + edgeDistance + " vs. " + geometryDistance
                    + ", difference: " + (edgeDistance - geometryDistance));
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
        double lat = pillarInfo.getLat(tmpNode);
        double lon = pillarInfo.getLon(tmpNode);
        double ele = pillarInfo.getEle(tmpNode);
        if (lat == Double.MAX_VALUE || lon == Double.MAX_VALUE)
            throw new RuntimeException("Conversion pillarNode to towerNode already happened!? "
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
        encodingManager.releaseParsers();
        eleProvider.release();
        osmNodeIdToInternalNodeMap = null;
        osmNodeIdToNodeFlagsMap = null;
        osmWayIdToRelationFlagsMap = null;
        osmWayIdSet = null;
        edgeIdToOsmWayIdMap = null;
    }

    /**
     * Create a copy of the barrier node
     */
    long addBarrierNode(long nodeId) {
        ReaderNode newNode;
        int graphIndex = getNodeMap().get(nodeId);
        if (isTowerNode(graphIndex)) {
            graphIndex = -graphIndex - 3;
            newNode = new ReaderNode(createArtificialOSMNodeId(), nodeAccess.getLat(graphIndex), nodeAccess.getLon(graphIndex));
        } else if (isPillarNode(graphIndex)) {
            graphIndex = graphIndex - 3;
            newNode = new ReaderNode(createArtificialOSMNodeId(), pillarInfo.getLat(graphIndex), pillarInfo.getLon(graphIndex));
        } else {
            throw new IllegalStateException("Cannot add barrier nodes for osm node ids that do not appear in ways or nodes");
        }

        final long osmId = newNode.getId();
        if (getNodeMap().get(osmId) != -1)
            throw new IllegalStateException("Artificial osm node id already exists: " + osmId);
        getNodeMap().put(osmId, INTERMEDIATE_NODE);
        addPillarNode(osmId, newNode.getLat(), newNode.getLon(), eleProvider.getEle(newNode));
        return osmId;
    }

    private long createArtificialOSMNodeId() {
        return newUniqueOsmId++;
    }

    /**
     * Add a zero length edge with reduced routing options to the graph.
     */
    void addBarrierEdge(long fromId, long toId, IntsRef inEdgeFlags, long nodeFlags, ReaderWay way) {
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
        addOSMWay(barrierNodeIds, edgeFlags, way);
    }

    /**
     * Creates turn relations out of an unspecified OSM relation
     */
    List<OSMTurnRelation> createTurnRelations(ReaderRelation relation) {
        List<OSMTurnRelation> osmTurnRelations = new ArrayList<>();
        String vehicleTypeRestricted = "";
        List<String> vehicleTypesExcept = new ArrayList<>();
        if (relation.hasTag("except")) {
            String tagExcept = relation.getTag("except");
            if (!Helper.isEmpty(tagExcept)) {
                List<String> vehicleTypes = new ArrayList<>(Arrays.asList(tagExcept.split(";")));
                for (String vehicleType : vehicleTypes)
                    vehicleTypesExcept.add(vehicleType.trim());
            }
        }
        if (relation.hasTag("restriction")) {
            OSMTurnRelation osmTurnRelation = createTurnRelation(relation, relation.getTag("restriction"), vehicleTypeRestricted, vehicleTypesExcept);
            if (osmTurnRelation != null) {
                osmTurnRelations.add(osmTurnRelation);
            }
            return osmTurnRelations;
        }
        if (relation.hasTagWithKeyPrefix("restriction:")) {
            List<String> vehicleTypesRestricted = relation.getKeysWithPrefix("restriction:");
            for (String vehicleType : vehicleTypesRestricted) {
                String restrictionType = relation.getTag(vehicleType);
                vehicleTypeRestricted = vehicleType.replace("restriction:", "").trim();
                OSMTurnRelation osmTurnRelation = createTurnRelation(relation, restrictionType, vehicleTypeRestricted, vehicleTypesExcept);
                if (osmTurnRelation != null) {
                    osmTurnRelations.add(osmTurnRelation);
                }
            }
        }
        return osmTurnRelations;
    }

    OSMTurnRelation createTurnRelation(ReaderRelation relation, String restrictionType, String vehicleTypeRestricted, List<String> vehicleTypesExcept) {
        OSMTurnRelation.Type type = OSMTurnRelation.Type.getRestrictionType(restrictionType);
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
                OSMTurnRelation osmTurnRelation = new OSMTurnRelation(fromWayID, viaNodeID, toWayID, type);
                osmTurnRelation.setVehicleTypeRestricted(vehicleTypeRestricted);
                osmTurnRelation.setVehicleTypesExcept(vehicleTypesExcept);
                return osmTurnRelation;
            }
        }
        return null;
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

    int getRelFlagsMapSize() {
        return osmWayIdToRelationFlagsMap.size();
    }

    IntsRef getRelFlagsMap(long osmId) {
        long relFlagsAsLong = osmWayIdToRelationFlagsMap.get(osmId);
        tempRelFlags.ints[0] = (int) relFlagsAsLong;
        tempRelFlags.ints[1] = (int) (relFlagsAsLong >> 32);
        return tempRelFlags;
    }

    void putRelFlagsMap(long osmId, IntsRef relFlags) {
        long relFlagsAsLong = ((long) relFlags.ints[1] << 32) | (relFlags.ints[0] & 0xFFFFFFFFL);
        osmWayIdToRelationFlagsMap.put(osmId, relFlagsAsLong);
    }

    public OSMReader setAreaIndex(AreaIndex<CustomArea> areaIndex) {
        this.areaIndex = areaIndex;
        return this;
    }

    public OSMReader setCountryRuleFactory(CountryRuleFactory countryRuleFactory) {
        this.countryRuleFactory = countryRuleFactory;
        return this;
    }

    public OSMReader setElevationProvider(ElevationProvider eleProvider) {
        if (eleProvider == null)
            throw new IllegalStateException("Use the NOOP elevation provider instead of null or don't call setElevationProvider");

        if (!nodeAccess.is3D() && ElevationProvider.NOOP != eleProvider)
            throw new IllegalStateException("Make sure you graph accepts 3D data");

        this.eleProvider = eleProvider;
        return this;
    }

    public OSMReader setFile(File osmFile) {
        this.osmFile = osmFile;
        return this;
    }

    private void printInfo(String str) {
        LOGGER.info("finished " + str + " processing." + " nodes: " + graph.getNodes()
                + ", ignored barrier nodes at junctions: " + nf(ignoredBarrierNodes)
                + ", osmIdMap.size:" + getNodeMap().getSize() + ", osmIdMap:" + getNodeMap().getMemoryUsage() + "MB"
                + ", nodeFlagsMap.size:" + getNodeFlagsMap().size() + ", relFlagsMap.size:" + getRelFlagsMapSize()
                + ", zeroCounter:" + zeroCounter
                + " " + Helper.getMemInfo());
    }

    /**
     * @return the timestamp given in the OSM file header or null if not found
     */
    public Date getDataDate() {
        return osmDataDate;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

    private boolean isTowerNode(int id) {
        // tower nodes are indexed -3, -4, -5, ...
        return id < JUNCTION_NODE;
    }

    private boolean isPillarNode(int id) {
        // pillar nodes are indexed 3, 4, 5, ..
        return id > CONNECTION_NODE;
    }

    private boolean isNodeId(int id) {
        return id > CONNECTION_NODE || id < JUNCTION_NODE;
    }
}
