package com.graphhopper.reader.osgb.dpn;

import static com.graphhopper.util.Helper.nf;
import gnu.trove.map.TLongObjectMap;
import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.TObjectLongMap;
import gnu.trove.map.hash.TLongObjectHashMap;
import gnu.trove.map.hash.TObjectIntHashMap;
import gnu.trove.map.hash.TObjectLongHashMap;
import gnu.trove.set.hash.THashSet;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.xml.stream.XMLStreamException;

import org.opengis.geometry.MismatchedDimensionException;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.operation.TransformException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.graphhopper.reader.Node;
import com.graphhopper.reader.OSMElement;
import com.graphhopper.reader.OSMTurnRelation;
import com.graphhopper.reader.PillarInfo;
import com.graphhopper.reader.Relation;
import com.graphhopper.reader.RelationMember;
import com.graphhopper.reader.RoutingElement;
import com.graphhopper.reader.TurnRelation;
import com.graphhopper.reader.Way;
import com.graphhopper.reader.osgb.AbstractOsReader;
import com.graphhopper.reader.osgb.itn.OSITNTurnRelation;
import com.graphhopper.routing.util.EncoderDecorator;
import com.graphhopper.storage.AvoidanceAttributeExtension;
import com.graphhopper.storage.GraphExtension;
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.DistanceCalc3D;
import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.GHPoint;

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
/**
 * This class parses an OS ITN xml file and creates a graph from it. It does so
 * in a two phase parsing processes in order to reduce memory usage compared to
 * a single parsing processing.
 * <p/>
 * 1. a) Reads ways from OSM file and stores all associated node ids in
 * osmNodeIdToIndexMap. If a node occurs once it is a pillar node and if more it
 * is a tower node, otherwise osmNodeIdToIndexMap returns EMPTY.
 * <p>
 * 1. b) Reads relations from OSM file. In case that the relation is a route
 * relation, it stores specific relation attributes required for routing into
 * osmWayIdToRouteWeigthMap for all the ways of the relation.
 * <p/>
 * 2.a) Reads nodes from OSM file and stores lat+lon information either into the
 * intermediate datastructure for the pillar nodes (pillarLats/pillarLons) or,
 * if a tower node, directly into the graphStorage via setLatitude/setLongitude.
 * It can also happen that a pillar node needs to be transformed into a tower
 * node e.g. via barriers or different speed values for one way.
 * <p/>
 * 2.b) Reads ways OSM file and creates edges while calculating the speed etc
 * from the OSM tags. When creating an edge the pillar node information from the
 * intermediate datastructure will be stored in the way geometry of that edge.
 * <p/>
 *
 * @author Peter Karich
 */

public class OsDpnReader extends AbstractOsReader<String> {
    private InputStream is;

    protected static final int EMPTY = -1;
    // pillar node is >= 3
    protected static final int PILLAR_NODE = 1;
    // tower node is <= -3
    protected static final int TOWER_NODE = -2;
    private static final Logger logger = LoggerFactory.getLogger(OsDpnReader.class);
    private long locations;
    private long skippedLocations;
    protected long zeroCounter = 0;
    // Using the correct Map<Long, Integer> is hard. We need a memory efficient
    // and fast solution for big data sets!
    //
    // very slow: new SparseLongLongArray
    // only append and update possible (no unordered storage like with this
    // doubleParse): new OSMIDMap
    // same here: not applicable as ways introduces the nodes in 'wrong' order:
    // new OSMIDSegmentedMap
    // memory overhead due to open addressing and full rehash:
    // nodeOsmIdToIndexMap = new BigLongIntMap(expectedNodes, EMPTY);
    // smaller memory overhead for bigger data sets because of avoiding a
    // "rehash"
    // remember how many times a node was used to identify tower nodes
    private TObjectIntMap<String> osmNodeIdToInternalNodeMap;
    private TObjectLongMap<String> osmNodeIdToNodeFlagsMap;
    private TObjectLongHashMap<String> osmWayIdToRouteWeightMap;
    // stores osm way ids used by relations to identify which edge ids needs to
    // be mapped later
    private THashSet<String> osmIdStoreRequiredSet = new THashSet<String>();
    private TLongObjectMap<String> edgeIdToOsmIdMap;
    protected PillarInfo pillarInfo;
    private final DistanceCalc distCalc = new DistanceCalcEarth();
    private final DistanceCalc3D distCalc3D = new DistanceCalc3D();
    private int nextTowerId = 0;
    private int nextPillarId = 0;
    // negative but increasing to avoid clash with custom created OSM files
    private long newUniqueOsmId = -Long.MAX_VALUE;
    private final boolean exitOnlyPillarNodeException = true;
    private static final String PROCESS_FORMAT = "PROCESS: {}";
    private static final String STORAGE_NODES_FORMAT = "storage nodes: {}";

    public OsDpnReader(GraphStorage storage, CmdArgs commandLineArguments) {
        super(storage);
        // Not as clean as I would like. Might use Guice.
        OsDpnWay.THROW_EXCEPTION_ON_INVALID_HAZARD = commandLineArguments != null ? commandLineArguments.getBool(
                "fail.on.invalid.potentialHazard", false) : false;
        osmNodeIdToInternalNodeMap = new TObjectIntHashMap<String>(200, .5f, -1);
        osmNodeIdToNodeFlagsMap = new TObjectLongHashMap<String>(200, .5f, 0);
        osmWayIdToRouteWeightMap = new TObjectLongHashMap<String>(200, .5f, 0);
        pillarInfo = new PillarInfo(nodeAccess.is3D(), graphStorage.getDirectory());
    }

    public class ProcessVisitor {
        public void process(ProcessData processData, OsDpnInputFile in) throws XMLStreamException,
        MismatchedDimensionException, FactoryException, TransformException {
        }
    }

    public class ProcessData {
        long wayStart = -1;
        long relationStart = -1;
        long counter = 1;

    }

    @Override
    protected void preProcessSingleFile(File dpnFile) {
        OsDpnInputFile in = null;
        try {
            in = new OsDpnInputFile(dpnFile);
            in.setWorkerThreads(workerThreads).open();

            long tmpWayCounter = 1;
            long tmpRelationCounter = 1;
            RoutingElement item;
            while ((item = in.getNext()) != null) {
                logger.trace("OsDpnReader.preProcess( " + item.getType() + " )");
                if (item.isType(OSMElement.WAY)) {
                    final OsDpnWay way = (OsDpnWay) item;
                    boolean valid = filterWay(way);
                    logger.trace("Valid Way:" + valid);
                    if (valid) {
                        List<String> wayNodes = way.getNodes();
                        int s = wayNodes.size();
                        logger.trace("With Nodes:" + s);
                        for (int index = 0; index < s; index++) {
                            prepareHighwayNode(wayNodes.get(index));
                        }

                        if (++tmpWayCounter % 500000 == 0) {
                            logger.info(nf(tmpWayCounter) + " (preprocess), osmIdMap:" + nf(getNodeMap().size())
                                    + Helper.getMemInfo());
                        }
                    }
                }
            }
        } catch (Exception ex) {
            throw new RuntimeException("Problem while parsing file", ex);
        } finally {
            Helper.close(in);
        }
    }

    private THashSet<String> getOsmIdStoreRequiredSet() {
        return osmIdStoreRequiredSet;
    }

    private TLongObjectMap<String> getEdgeIdToOsmidMap() {
        if (edgeIdToOsmIdMap == null)
            edgeIdToOsmIdMap = new TLongObjectHashMap<String>(getOsmIdStoreRequiredSet().size());

        return edgeIdToOsmIdMap;
    }

    /**
     * Filter ways but do not analyze properties wayNodes will be filled with
     * participating node ids.
     * <p/>
     *
     * @return true the current xml entry is a way entry and has nodes
     */
    boolean filterWay(OsDpnWay way) {
        logger.info(way.getNodes().size() + ":" + way.hasTags());
        // ignore broken geometry
        if (way.getNodes().size() < 2)
            return false;

        // ignore multipolygon geometry
        // if (!way.hasTags())
        // return false;

        return encodingManager.acceptWay(way) > 0;
    }

    /**
     * Creates the edges and nodes files from the specified osm file.
     *
     * @throws TransformException
     * @throws FactoryException
     * @throws IOException
     * @throws XMLStreamException
     * @throws MismatchedDimensionException
     */
    @Override
    protected void writeOsm2Graph(File osmFile) throws MismatchedDimensionException, XMLStreamException, IOException,
    FactoryException, TransformException {
        int tmp = Math.max(getNodeMap().size() / 50, 100);
        graphStorage.create(tmp);
        ProcessData processData = new ProcessData();
        try {
            ProcessVisitor processVisitor = new ProcessVisitor() {
                @Override
                public void process(ProcessData processData, OsDpnInputFile in) throws XMLStreamException,
                MismatchedDimensionException, FactoryException, TransformException {
                    logger.info("PROCESS STAGE 1");
                    processStageOne(in);
                }
            };
            logger.info("PROCESS NODES");
            writeOsm2GraphFromDirOrFile(osmFile, processData, processVisitor);
            processVisitor = new ProcessVisitor() {
                @Override
                public void process(ProcessData processData, OsDpnInputFile in) throws XMLStreamException,
                MismatchedDimensionException, FactoryException, TransformException {
                    logger.info("PROCESS STAGE 2");
                    processStageTwo(processData, in);
                }
            };
            logger.info("PROCESS WAY");
            writeOsm2GraphFromDirOrFile(osmFile, processData, processVisitor);
            processVisitor = new ProcessVisitor() {
                @Override
                public void process(ProcessData processData, OsDpnInputFile in) throws XMLStreamException,
                MismatchedDimensionException, FactoryException, TransformException {
                    logger.info("PROCESS STAGE 3");
                    processStageThree(processData, in);
                }
            };
            logger.info("PROCESS RELATION");
            writeOsm2GraphFromDirOrFile(osmFile, processData, processVisitor);

        } catch (Exception ex) {
            throw new RuntimeException("Couldn't process file " + osmFile, ex);
        }

        finishedReading();

    }

    private void writeOsm2GraphFromDirOrFile(File osmFile, ProcessData processData, ProcessVisitor processVisitor)
            throws XMLStreamException, IOException, MismatchedDimensionException, FactoryException, TransformException {
        if (osmFile.isDirectory()) {
            String absolutePath = osmFile.getAbsolutePath();
            String[] list = osmFile.list();
            for (String file : list) {
                File nextFile = new File(absolutePath + File.separator + file);
                writeOsm2GraphFromDirOrFile(nextFile, processData, processVisitor);
            }
        } else {
            writeOsm2GraphFromSingleFile(osmFile, processData, processVisitor);
        }
    }

    private void writeOsm2GraphFromSingleFile(File osmFile, ProcessData processData, ProcessVisitor processVisitor)
            throws XMLStreamException, IOException, MismatchedDimensionException, FactoryException, TransformException {
        OsDpnInputFile in = null;
        try {
            logger.info(PROCESS_FORMAT, osmFile.getName());
            in = new OsDpnInputFile(osmFile);
            in.setWorkerThreads(workerThreads).open();
            processVisitor.process(processData, in);
            logger.info(STORAGE_NODES_FORMAT, graphStorage.getNodes());
        } finally {
            Helper.close(in);
        }
    }

    private List<OsDpnNode> prepareWaysNodes(RoutingElement item, TObjectIntMap<String> nodeFilter)
            throws MismatchedDimensionException, FactoryException, TransformException {
        List<OsDpnNode> evaluateWayNodes = ((OsDpnWay) item).evaluateWayNodes(null);
        for (OsDpnNode osdpnNode : evaluateWayNodes) {
            nodeFilter.put(osdpnNode.getId(), PILLAR_NODE);
            processNode(osdpnNode);
        }
        logger.info(WE_HAVE_EVALUATED_WAY_NODES_FORMAT, evaluateWayNodes.size());
        return evaluateWayNodes;
    }

    /**
     * Process properties, encode flags and create edges for the way.
     */
    void processWay(OsDpnWay way) {
        if (way.getNodes().size() < 2)
            return;

        // ignore multipolygon geometry
        if (!way.hasTags())
            return;

        String wayOsmId = way.getId();

        long includeWay = encodingManager.acceptWay(way);
        if (includeWay == 0)
            return;

        long relationFlags = getRelFlagsMap().get(way.getId());

        // TODO move this after we have created the edge and know the
        // coordinates => encodingManager.applyWayTags
        // estimate length of the track e.g. for ferry speed calculation
        List<String> osmNodeIds = way.getNodes();
        if (osmNodeIds.size() > 1) {
            int first = getNodeMap().get(osmNodeIds.get(0));
            int last = getNodeMap().get(osmNodeIds.get(osmNodeIds.size() - 1));
            double firstLat = getTmpLatitude(first), firstLon = getTmpLongitude(first);
            double lastLat = getTmpLatitude(last), lastLon = getTmpLongitude(last);
            if (!Double.isNaN(firstLat) && !Double.isNaN(firstLon) && !Double.isNaN(lastLat) && !Double.isNaN(lastLon)) {
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
        for (int i = 0; i < size; i++) {
            String nodeId = osmNodeIds.get(i);
            long nodeFlags = getNodeFlagsMap().get(nodeId);
            // barrier was spotted and way is otherwise passable for that mode
            // of travel
            if (nodeFlags > 0) {
                if ((nodeFlags & wayFlags) > 0) {
                    // remove barrier to avoid duplicates
                    getNodeFlagsMap().put(nodeId, 0);

                    // create shadow node copy for zero length edge
                    String newNodeId = addBarrierNode(nodeId);
                    if (i > 0) {
                        // start at beginning of array if there was no previous
                        // barrier
                        if (lastBarrier < 0)
                            lastBarrier = 0;

                        // add way up to barrier shadow node
                        String transfer[] = { "" };
                        transfer = osmNodeIds.subList(lastBarrier, i - lastBarrier + 1).toArray(transfer);
                        transfer[transfer.length - 1] = newNodeId;
                        createdEdges.addAll(addOSMWay(transfer, wayFlags, wayOsmId));

                        // create zero length edge for barrier
                        createdEdges.addAll(addBarrierEdge(newNodeId, nodeId, wayFlags, nodeFlags, wayOsmId));
                    } else {
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
        if (lastBarrier >= 0) {
            if (lastBarrier < size - 1) {
                String transfer[] = { "" };
                transfer = osmNodeIds.subList(lastBarrier, size - lastBarrier).toArray(transfer);
                createdEdges.addAll(addOSMWay(transfer, wayFlags, wayOsmId));
            }
        } else {
            // no barriers - simply add the whole way
            String transfer[] = { "" };
            transfer = way.getNodes().toArray(transfer);
            createdEdges.addAll(addOSMWay(transfer, wayFlags, wayOsmId));
        }

        long configureEdgeAvoidance = configureEdgeAvoidance(way);
        applyAvoidanceAttributes(way, createdEdges, configureEdgeAvoidance);
    }

	private void applyAvoidanceAttributes(OsDpnWay way, List<EdgeIteratorState> createdEdges,
			long configureEdgeAvoidance) {
		for (EdgeIteratorState edge : createdEdges) {
            encodingManager.applyWayTags(way, edge);
            if(0<configureEdgeAvoidance) {
            	AvoidanceAttributeExtension avoidanceExtension = (AvoidanceAttributeExtension)graphStorage.getExtension();
            	avoidanceExtension.addEdgeInfo(edge.getEdge(), edge.getAdjNode(), configureEdgeAvoidance);
            }
        }
	}

	private long configureEdgeAvoidance(Way way) {
		 GraphExtension extendedStorage = graphStorage.getExtension();
		 long handleWayTags=0;
         if (extendedStorage instanceof AvoidanceAttributeExtension)
         {
        	 List<EncoderDecorator> decorators = encodingManager.getDecorators();
        	 for (EncoderDecorator encoderDecorator : decorators) {
        		 handleWayTags += encoderDecorator.handleWayTags(way);
        	 }
         }
         return handleWayTags;
	}

    public void processRelation(Relation relation) throws XMLStreamException {
        // if (relation.hasTag("type", "restriction")) {
        // TurnRelation turnRelation = createTurnRelation(relation);
        // if (turnRelation != null) {
        // ExtendedStorage extendedStorage = ((GraphHopperStorage) graphStorage)
        // .getExtendedStorage();
        // if (extendedStorage instanceof TurnCostStorage) {
        // Collection<ITurnCostTableEntry> entries = encodingManager
        // .analyzeTurnRelation(turnRelation, this);
        // for (ITurnCostTableEntry entry : entries) {
        // ((TurnCostStorage) extendedStorage).setTurnCosts(
        // entry.nodeVia, entry.edgeFrom, entry.edgeTo,
        // (int) entry.flags);
        // }
        // }
        // }
        // }
    }

    @Override
    public String getOsmIdOfInternalEdge(int edgeId) {
        return getEdgeIdToOsmidMap().get(edgeId);
    }

    @Override
    public int getInternalNodeIdOfOsmNode(String nodeOsmId) {
        int id = getNodeMap().get(nodeOsmId);
        if (id < TOWER_NODE)
            return -id - 3;

        return EMPTY;
    }

    // TODO remove this ugly stuff via better preparsing phase! E.g. putting
    // every tags etc into a helper file!
    double getTmpLatitude(int id) {
        if (id == EMPTY)
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
            // e.g. if id is not handled from preparse (e.g. was ignored via
            // isInBounds)
            return Double.NaN;
    }

    double getTmpLongitude(int id) {
        if (id == EMPTY)
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
            // e.g. if id is not handled from preparse (e.g. was ignored via
            // isInBounds)
            return Double.NaN;
    }

    private void processNode(OsDpnNode node) {
        logger.trace("PROCESSING:" + node.getId());
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

    boolean addNode(OsDpnNode node) {
        int nodeType = getNodeMap().get(node.getId());
        if (nodeType == EMPTY)
            return false;

        double lat = node.getLat();
        double lon = node.getLon();
        double ele = getElevation(node);
        if (nodeType == TOWER_NODE) {
            addTowerNode(node.getId(), lat, lon, ele);
        } else if (nodeType == PILLAR_NODE) {
            logger.trace("OsDpnReader.addPillarNode(" + nextPillarId + ")");
            pillarInfo.setNode(nextPillarId, lat, lon, ele);
            getNodeMap().put(node.getId(), nextPillarId + 3);
            nextPillarId++;
        }
        return true;
    }

    private double getElevation(Node node) {
        if (null == elevationProvider) {
            String eleString = node.getTag("ele");
            return Double.valueOf(eleString);
        }
        return elevationProvider.getEle(node.getLat(), node.getLon());
    }

    /*
     * void prepareWaysWithRelationInfo(OSMRelation osmRelation) { // is there
     * at least one tag interesting for the registed encoders? if
     * (encodingManager.handleRelationTags(osmRelation, 0) == 0) return;
     *
     * int size = osmRelation.getMembers().size(); for (int index = 0; index <
     * size; index++) { OSMRelation.Member member =
     * osmRelation.getMembers().get(index); if (member.type() !=
     * OSMRelation.Member.WAY) continue;
     *
     * long osmId = member.ref(); long oldRelationFlags =
     * getRelFlagsMap().get(osmId);
     *
     * // Check if our new relation data is better comparated to the the // last
     * one long newRelationFlags = encodingManager.handleRelationTags(
     * osmRelation, oldRelationFlags); if (oldRelationFlags != newRelationFlags)
     * getRelFlagsMap().put(osmId, newRelationFlags); } }
     */

    void prepareHighwayNode(String idStr) {
        logger.info("Prepare HighwayNode:" + idStr);
        int tmpIndex = getNodeMap().get(idStr);
        if (tmpIndex == EMPTY) {
            // osmId is used exactly once
            logger.debug("OsDpnReader.prepareHighwayNode(EMPTY->PILLAR)");
            getNodeMap().put(idStr, PILLAR_NODE);
        } else if (tmpIndex > EMPTY) {
            // mark node as tower node as it occured at least twice times
            logger.debug("OsDpnReader.prepareHighwayNode(PILLAR->TOWER)");
            getNodeMap().put(idStr, TOWER_NODE);
        } else {
            // tmpIndex is already negative (already tower node)
        }
    }

    int addTowerNode(String osmId, double lat, double lon, double ele) {
        logger.trace("OsDpnReader.addTowerNode(" + osmId + ")");
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
     * This method creates from an OSM way (via the osm ids) one or more edges
     * in the graph.
     */
    Collection<EdgeIteratorState> addOSMWay(String[] osmNodeIds, long flags, String wayOsmId) {
        PointList pointList = new PointList(osmNodeIds.length, nodeAccess.is3D());
        List<EdgeIteratorState> newEdges = new ArrayList<EdgeIteratorState>(5);
        int firstNode = -1;
        int lastIndex = osmNodeIds.length - 1;
        int lastInBoundsPillarNode = -1;
        try {
            for (int i = 0; i < osmNodeIds.length; i++) {
                String osmId = osmNodeIds[i];
                int tmpNode = getNodeMap().get(osmId);
                if (tmpNode == EMPTY)
                    continue;

                // skip osmIds with no associated pillar or tower id (e.g.
                // !OSMReader.isBounds)
                if (tmpNode == TOWER_NODE)
                    continue;

                if (tmpNode == PILLAR_NODE) {
                    // In some cases no node information is saved for the
                    // specified osmId.
                    // ie. a way references a <node> which does not exist in the
                    // current file.
                    // => if the node before was a pillar node then convert into
                    // to tower node (as it is also end-standing).
                    if (!pointList.isEmpty() && lastInBoundsPillarNode > -TOWER_NODE) {
                        // transform the pillar node to a tower node
                        tmpNode = lastInBoundsPillarNode;
                        tmpNode = handlePillarNode(tmpNode, osmId, null, true);
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
                    throw new AssertionError("Mapped index not in correct bounds " + tmpNode + ", " + osmId);

                if (tmpNode > -TOWER_NODE) {
                    boolean convertToTowerNode = i == 0 || i == lastIndex;
                    if (!convertToTowerNode) {
                        lastInBoundsPillarNode = tmpNode;
                    }

                    // PILLAR node, but convert to towerNode if end-standing
                    tmpNode = handlePillarNode(tmpNode, osmId, pointList, convertToTowerNode);
                }

                if (tmpNode < TOWER_NODE) {
                    // TOWER node
                    tmpNode = -tmpNode - 3;
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
            logger.error("Couldn't properly add edge with osm ids:" + osmNodeIds, ex);
            if (exitOnlyPillarNodeException)
                throw ex;
        }
        return newEdges;
    }

    EdgeIteratorState addEdge(int fromIndex, int toIndex, PointList pointList, long flags, String wayOsmId) {
        // sanity checks
        if (fromIndex < 0 || toIndex < 0)
            throw new AssertionError("to or from index is invalid for this edge " + fromIndex + "->" + toIndex
                    + ", points:" + pointList);
        if (pointList.getDimension() != nodeAccess.getDimension())
            throw new AssertionError("Dimension does not match for pointList vs. nodeAccess "
                    + pointList.getDimension() + " <-> " + nodeAccess.getDimension());

        double towerNodeDistance = 0;
        double prevLat = pointList.getLatitude(0);
        double prevLon = pointList.getLongitude(0);
        double prevEle = pointList.is3D() ? pointList.getElevation(0) : Double.NaN;
        double lat, lon, ele = Double.NaN;
        PointList pillarNodes = new PointList(pointList.getSize() - 2, nodeAccess.is3D());
        int nodes = pointList.getSize();
        for (int i = 1; i < nodes; i++) {
            // we could save some lines if we would use
            // pointList.calcDistance(distCalc);
            lat = pointList.getLatitude(i);
            lon = pointList.getLongitude(i);
            if (pointList.is3D()) {
                ele = pointList.getElevation(i);
                towerNodeDistance += distCalc3D.calcDist(prevLat, prevLon, prevEle, lat, lon, ele);
                prevEle = ele;
            } else
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
        if (towerNodeDistance == 0) {
            // As investigation shows often two paths should have crossed via
            // one identical point
            // but end up in two very release points.
            zeroCounter++;
            towerNodeDistance = 0.0001;
        }

        EdgeIteratorState iter = graphStorage.edge(fromIndex, toIndex).setDistance(towerNodeDistance).setFlags(flags);
        if (nodes > 2) {
            if (doSimplify)
                simplifyAlgo.simplify(pillarNodes);

            iter.setWayGeometry(pillarNodes);
        }
        storeOSMWayID(iter.getEdge(), wayOsmId);
        return iter;
    }

    private void storeOSMWayID(int edgeId, String osmWayID) {
        if (getOsmIdStoreRequiredSet().contains(osmWayID)) {
            getEdgeIdToOsmidMap().put(edgeId, osmWayID);
        }
    }

    /**
     * @return converted tower node
     */
    private int handlePillarNode(int tmpNode, String osmId, PointList pointList, boolean convertToTowerNode) {
        logger.info("Converting Pillar " + osmId, " to pillar? " + convertToTowerNode);
        tmpNode = tmpNode - 3;
        double lat = pillarInfo.getLatitude(tmpNode);
        double lon = pillarInfo.getLongitude(tmpNode);
        double ele = pillarInfo.getElevation(tmpNode);
        if (lat == Double.MAX_VALUE || lon == Double.MAX_VALUE)
            throw new RuntimeException("Conversion pillarNode to towerNode already happended!? " + "osmId:" + osmId
                    + " pillarIndex:" + tmpNode);

        if (convertToTowerNode) {
            // convert pillarNode type to towerNode, make pillar values invalid
            pillarInfo.setNode(tmpNode, Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE);
            tmpNode = addTowerNode(osmId, lat, lon, ele);
        } else {
            if (pointList.is3D())
                pointList.add(lat, lon, ele);
            else
                pointList.add(lat, lon);
        }

        return tmpNode;
    }

    protected void finishedReading() {
        printInfo("way");
        pillarInfo.clear();
        elevationProvider.release();
        // osmNodeIdToInternalNodeMap = null;
        // osmNodeIdToNodeFlagsMap = null;
        osmWayIdToRouteWeightMap = null;
        osmIdStoreRequiredSet = null;
        edgeIdToOsmIdMap = null;
    }

    /**
     * Create a copy of the barrier node
     */
    String addBarrierNode(String nodeId) {
        OsDpnNode newNode;
        int graphIndex = getNodeMap().get(nodeId);
        if (graphIndex < TOWER_NODE) {
            graphIndex = -graphIndex - 3;
            newNode = new OsDpnNode(createNewNodeId(), nodeAccess, graphIndex);
        } else {
            graphIndex = graphIndex - 3;
            newNode = new OsDpnNode(createNewNodeId(), pillarInfo, graphIndex);
        }

        final String id = newNode.getId();
        prepareHighwayNode(id);
        addNode(newNode);
        return id;
    }

    private String createNewNodeId() {
        return String.valueOf(newUniqueOsmId++);
    }

    /**
     * Add a zero length edge with reduced routing options to the graph.
     */
    Collection<EdgeIteratorState> addBarrierEdge(String fromId, String toId, long flags, long nodeFlags, String wayOsmId) {
        // clear barred directions from routing flags
        flags &= ~nodeFlags;
        // add edge
        String barrierNodeIds[] = { fromId, toId };
        return addOSMWay(barrierNodeIds, flags, wayOsmId);
    }

    /**
     * Creates an OSM turn relation out of an unspecified OSM relation
     * <p>
     *
     * @return the OSM turn relation, <code>null</code>, if unsupported turn
     *         relation
     */
    TurnRelation createTurnRelation(Relation relation) {
        OSMTurnRelation.Type type = OSITNTurnRelation.getRestrictionType(relation.getTag("restriction"));
        if (type != OSMTurnRelation.Type.UNSUPPORTED) {
            long fromWayID = -1;
            long viaNodeID = -1;
            long toWayID = -1;

            for (RelationMember member : relation.getMembers()) {
                if (OSMElement.WAY == member.type()) {
                    if ("from".equals(member.role())) {
                        fromWayID = member.ref();
                    } else if ("to".equals(member.role())) {
                        toWayID = member.ref();
                    }
                } else if (OSMElement.NODE == member.type() && "via".equals(member.role())) {
                    viaNodeID = member.ref();
                }
            }
            if (type != OSMTurnRelation.Type.UNSUPPORTED && fromWayID >= 0 && toWayID >= 0 && viaNodeID >= 0) {
                return new OSMTurnRelation(fromWayID, viaNodeID, toWayID, type);
            }
        }
        return null;
    }

    /**
     * Filter method, override in subclass
     */
    boolean isInBounds(Node node) {
        return true;
    }

    /**
     * Maps OSM IDs (long) to internal node IDs (int)
     */
    protected TObjectIntMap<String> getNodeMap() {
        return osmNodeIdToInternalNodeMap;
    }

    protected TObjectLongMap<String> getNodeFlagsMap() {
        return osmNodeIdToNodeFlagsMap;
    }

    TObjectLongHashMap<String> getRelFlagsMap() {
        return osmWayIdToRouteWeightMap;
    }

    private void printInfo(String str) {
        LoggerFactory.getLogger(getClass()).info(
                "finished " + str + " processing." + " nodes: " + graphStorage.getNodes() + ", osmIdMap.size:"
                        + getNodeMap().size() + ", osmIdMap:" + ", nodeFlagsMap.size:" + getNodeFlagsMap().size()
                        + ", relFlagsMap.size:" + getRelFlagsMap().size() + " " + Helper.getMemInfo());
    }

    private void processStageOne(OsDpnInputFile in) throws XMLStreamException, MismatchedDimensionException,
    FactoryException, TransformException {
        RoutingElement item;
        while ((item = in.getNext()) != null) {
            switch (item.getType()) {
            case OSMElement.NODE:
                OsDpnNode dpnNode = (OsDpnNode) item;
                String id = dpnNode.getId();
                logger.info("NODEITEMID:" + id);
                if (getNodeMap().get(id) != -1) {
                    processNode(dpnNode);
                }
            }
        }
    }

    private void processStageTwo(ProcessData processData, OsDpnInputFile in) throws XMLStreamException,
    MismatchedDimensionException, FactoryException, TransformException {
        RoutingElement item;
        while ((item = in.getNext()) != null) {
            switch (item.getType()) {
            case OSMElement.WAY:
                OsDpnWay dpnWay = (OsDpnWay) item;
                logger.info("WAY:" + dpnWay.getId() + ":" + processData.wayStart);
                if (processData.wayStart < 0) {
                    logger.info(nf(processData.counter) + ", now parsing ways");
                    processData.wayStart = processData.counter;
                }
                prepareWaysNodes(dpnWay, getNodeMap());
                processWay(dpnWay);
                dpnWay.clearStoredCoords();
            }
        }
    }

    private void processStageThree(ProcessData processData, OsDpnInputFile in) throws XMLStreamException,
    MismatchedDimensionException, FactoryException, TransformException {
        RoutingElement item;
        if (processData.relationStart < 0) {
            logger.info(nf(processData.counter) + ", now parsing relations");
            processData.relationStart = processData.counter;
        }
        while ((item = in.getNext()) != null) {
            switch (item.getType()) {
            case OSMElement.RELATION:
                processRelation((Relation) item);
                if (++processData.counter % 5000000 == 0) {
                    logger.info(nf(processData.counter) + ", locs:" + nf(locations) + " (" + skippedLocations + ") "
                            + Helper.getMemInfo());
                }
            }
        }
    }
}
