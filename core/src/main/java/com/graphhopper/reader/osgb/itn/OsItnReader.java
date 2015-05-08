package com.graphhopper.reader.osgb.itn;

import static com.graphhopper.util.Helper.nf;
import gnu.trove.list.TLongList;
import gnu.trove.list.array.TLongArrayList;
import gnu.trove.map.TDoubleLongMap;
import gnu.trove.map.TDoubleObjectMap;
import gnu.trove.map.TIntLongMap;
import gnu.trove.map.TLongIntMap;
import gnu.trove.map.TLongLongMap;
import gnu.trove.map.TLongObjectMap;
import gnu.trove.map.hash.TDoubleLongHashMap;
import gnu.trove.map.hash.TDoubleObjectHashMap;
import gnu.trove.map.hash.TIntLongHashMap;
import gnu.trove.map.hash.TLongIntHashMap;
import gnu.trove.map.hash.TLongLongHashMap;
import gnu.trove.map.hash.TLongObjectHashMap;
import gnu.trove.procedure.TLongProcedure;
import gnu.trove.set.TLongSet;
import gnu.trove.set.hash.TLongHashSet;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.xml.stream.XMLStreamException;

import org.opengis.geometry.MismatchedDimensionException;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.operation.TransformException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.graphhopper.GraphHopper;
import com.graphhopper.coll.GHLongIntBTree;
import com.graphhopper.coll.LongIntMap;
import com.graphhopper.reader.DataReader;
import com.graphhopper.reader.ITurnCostTableEntry;
import com.graphhopper.reader.Node;
import com.graphhopper.reader.OSMElement;
import com.graphhopper.reader.OSMRelation;
import com.graphhopper.reader.OSMTurnRelation;
import com.graphhopper.reader.OSMTurnRelation.Type;
import com.graphhopper.reader.PillarInfo;
import com.graphhopper.reader.Relation;
import com.graphhopper.reader.RelationMember;
import com.graphhopper.reader.RoutingElement;
import com.graphhopper.reader.Way;
import com.graphhopper.reader.dem.ElevationProvider;
import com.graphhopper.reader.osgb.AbstractOsReader;
import com.graphhopper.reader.osgb.hn.OsHnReader;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.GraphExtension;
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.TurnCostExtension;
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

public class OsItnReader extends AbstractOsReader<Long> {

    private static final String TURN_FROM_TO_VIA_FORMAT = "Turn from:{} to:{} via:{}";
    private static final String PRINT_INFO_FORMAT = "finished {}  processing. nodes:{}, osmIdMap.size:{}, osmIdMap:{}MB, nodeFlagsMap.size:{}, relFlagsMap.size:{} {}";
    private static final String OS_ITN_READER_PREPARE_HIGHWAY_NODE_PILLAR_TOWER_FORMAT = "OsItnReader.prepareHighwayNode(PILLAR->TOWER):{}";
    private static final String OS_ITN_READER_PREPARE_HIGHWAY_NODE_EMPTY_PILLAR_FORMAT = "OsItnReader.prepareHighwayNode(EMPTY->PILLAR):{}";
    private static final String ADDING_NODE_AS_FORMAT = "Adding Node:{} as {}";
    private static final String NOW_PARSING_RELATIONS_FORMAT = "{}, now parsing relations";
    private static final String NOW_PARSING_WAYS_FORMAT = "{}, now parsing ways";
    private static final String PROCESSING_LOCS_FORMAT = "{}, locs: {} ({}) {}";
    private static final String PREPROCESS_OSM_ID_MAP_MB_FORMAT = "{} (preprocess), osmIdMap: {}  ({}MB) {}";
    private static final String NO_MATCHING_EDGES_FOR_FORMAT = "No Matching Edges for {} : {}";
    private static final String RELATIONMEMBERREF_FORMAT = "RELATIONMEMBERREF: {}";
    private static final String CONVERTING_PILLAR_TO_PILLAR_FORMAT = "Converting Pillar {} to pillar? {}";
    private static final String STORE_OSM_WAY_ID_FOR_FORMAT = "StoreOSMWayID: {} for {}";
    private static final String APPLYING_RELATION_FORMAT = "APPLYING relation: {}:{}";
    private static final String ADDING_WAY_RELATION_TO_FORMAT = "Adding WAY RELATION TO {}";
    private static final String PREPARE_ONE_WAY_FORMAT = "PREPARE ONE WAY: {}";
    private static final String MISSING_FROM_MAP_FORMAT = "MISSING FROM MAP: {}";
    private static final String ADDING_RELATION_TO_WAYS_FORMAT = "Adding Relation to WAYS: {}";
    private static final String WAYID_FIRST_LAST_FORMAT = "WAYID: {} first: {} last: ";
    private static final String RELFLAGS_FORMAT = "RELFLAGS: {} : {}";
    private static final String EDGE_ID_COORDS_TO_NODE_FLAGS_MAP_PUT_FORMAT = "edgeIdCoordsToNodeFlagsMap put: {} {} {} : {}";
    private static final String OS_ITN_READER_PRE_PROCESS_FORMAT = "OsItnReader.preProcess( {} )";
    private static final String WAY_ADDS_EDGES_FORMAT = "Way {} adds edges: {}";
    private static final String WAY_ADDS_BARRIER_EDGES_FORMAT = "Way {} adds barrier edges: {}";
    private static final String WAY_FORMAT = "WAY: {} : {}";
    private static final String NODEITEMID_FORMAT = "NODEITEMID: {}";
    private static final String PROCESS_FORMAT = "PROCESS: {}";
    private static final String STORAGE_NODES_FORMAT = "storage nodes: {}";
    private static final String CREATING_GRAPH_FOUND_NODES_PILLAR_TOWER_FORMAT = "creating graph. Found nodes (pillar+tower): {} , {}";
    private static final String EDGE_ID_TO_OSMIDMAP_FORMAT = "edgeIdTOOsmidmap: {}";

    public class ProcessVisitor {
        public void process(ProcessData processData, OsItnInputFile in) throws XMLStreamException, MismatchedDimensionException, FactoryException, TransformException {
        }
    }

    public class ProcessData {
        long wayStart = -1;
        long relationStart = -1;
        long counter = 1;

    }

    protected static final int EMPTY = -1;
    // pillar node is >= 3
    protected static final int PILLAR_NODE = 1;
    // tower node is <= -3
    protected static final int TOWER_NODE = -2;
    private static final Logger logger = LoggerFactory.getLogger(OsItnReader.class);
    private static final Logger errors_logger = LoggerFactory.getLogger("ingestionerrors");

    private static final int MAX_GRADE_SEPARATION = 4;
    private long locations;
    private long skippedLocations;
    private final NodeAccess nodeAccess;
    protected long zeroCounter = 0;

    private long successfulStartNoEntries = 0;
    private long successfulEndNoEntries = 0;
    private long failedStartNoEntries = 0;
    private long failedEndNoEntries = 0;

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
    private final LongIntMap osmNodeIdToInternalNodeMap;
    private TLongLongHashMap osmNodeIdToNodeFlagsMap;
    private TLongLongHashMap osmWayIdToRouteWeightMap;

    private TLongIntHashMap osmWayIdToAttributeBitMaskMap;
    private static final int ATTRIBUTE_BIT_GATE = 1;
    private static final int ATTRIBUTE_BIT_LEVEL_CROSSING = 2;
    private static final int ATTRIBUTE_BIT_FORD = 4;
    // stores osm way ids used by relations to identify which edge ids needs to
    // be mapped later
    private TLongHashSet osmIdStoreRequiredSet = new TLongHashSet();
    private TIntLongMap edgeIdToOsmIdMap;
    private final TLongList barrierNodeIds = new TLongArrayList();
    protected PillarInfo pillarInfo;
    private final DistanceCalc distCalc = new DistanceCalcEarth();
    private final DistanceCalc3D distCalc3D = new DistanceCalc3D();
    private boolean doSimplify = true;
    private int nextTowerId = 0;
    private int nextPillarId = 0;
    // negative but increasing to avoid clash with custom created OSM files
    private long newUniqueOsmId = -Long.MAX_VALUE;
    private ElevationProvider eleProvider = ElevationProvider.NOOP;
    private final boolean exitOnlyPillarNodeException = true;

    private TLongObjectMap<ItnNodePair> edgeIdToNodeMap;
    private TLongSet prohibitedWayIds;

    private TLongObjectMap<String> edgeNameMap;
    private TLongObjectMap<String> edgeRoadTypeMap;
    private TLongObjectMap<String> edgeEnvironmentMap;
    private TLongObjectMap<String> edgeRoadDirectionMap;

    private TLongObjectMap<TDoubleObjectMap<TDoubleLongMap>> edgeIdToXToYToNodeFlagsMap;

    // With this set to true additional tower nodes will be added after the
    // start node and before the final node
    // of a way. This is to overcome an issue when you are routing short
    // distances and turn restrictions wouldn't
    // be recognised.
    private boolean addAdditionalTowerNodes;

    private CmdArgs commandLineArguments;

    public OsItnReader(GraphStorage storage) {
        this(storage, null);
    }

    public OsItnReader(GraphStorage storage, CmdArgs commandLineArguments) {
        super(storage);
        this.commandLineArguments = commandLineArguments;
        String addAdditionalTowerNodesString = graphStorage.getProperties().get("add.additional.tower.nodes");
        if (addAdditionalTowerNodesString != null && addAdditionalTowerNodesString.length() > 0) {
            // Only parse this if it has been explicitly set otherwise set to
            // true
            addAdditionalTowerNodes = Boolean.parseBoolean(addAdditionalTowerNodesString);
        } else {
            addAdditionalTowerNodes = false;
        }

        this.nodeAccess = graphStorage.getNodeAccess();

        osmNodeIdToInternalNodeMap = new GHLongIntBTree(200);
        osmNodeIdToNodeFlagsMap = new TLongLongHashMap(200, .5f, 0, 0);
        osmWayIdToRouteWeightMap = new TLongLongHashMap(200, .5f, 0, 0);
        pillarInfo = new PillarInfo(nodeAccess.is3D(), graphStorage.getDirectory());
    }


    protected void preProcessSingleFile(File itnFile) throws XMLStreamException, IOException, MismatchedDimensionException, FactoryException, TransformException {
        OsItnInputFile in = null;
        try {
            logger.error(PREPROCESS_FORMAT, itnFile.getName());
            in = new OsItnInputFile(itnFile);
            in.setWorkerThreads(workerThreads).open();
            preProcessSingleFile(in);
        } finally {
            Helper.close(in);
        }
    }

    private void preProcessSingleFile(OsItnInputFile in) throws XMLStreamException, MismatchedDimensionException, FactoryException, TransformException {
        logger.error("==== preProcessSingleFile");
        long tmpWayCounter = 1;
        long tmpRelationCounter = 1;
        RoutingElement item;
        // Limit the number of xml nodes we process to speed up ingestion
        in.setAbstractFactory(new OsItnPreProcessRoutingElementFactory());
        while ((item = in.getNext()) != null) {
            logger.debug(OS_ITN_READER_PRE_PROCESS_FORMAT, item.getType());
            if (item.isType(OSMElement.WAY)) {
                final OSITNWay way = (OSITNWay) item;
                boolean valid = filterWay(way);
                if (valid) {
                    TLongList wayNodes = way.getNodes();
                    int s = wayNodes.size();
                    for (int index = 0; index < s; index++) {
                        prepareHighwayNode(wayNodes.get(index));
                    }

                    if (++tmpWayCounter % 500000 == 0) {
                        logger.info(PREPROCESS_OSM_ID_MAP_MB_FORMAT, nf(tmpWayCounter), nf(getNodeMap().getSize()), getNodeMap().getMemoryUsage(), Helper.getMemInfo());
                    }
                }
            }
            if (item.isType(OSMElement.RELATION)) {
                final Relation relation = (Relation) item;
                // logger.warn("RELATION :" + item.getClass() + " TYPE:" +
                // item.getTag(OSITNElement.TAG_KEY_TYPE) + " meta?" +
                // relation.isMetaRelation());
                if (!relation.isMetaRelation() && relation.hasTag(OSITNElement.TAG_KEY_TYPE, "route"))
                    prepareWaysWithRelationInfo(relation);

                if (relation.hasTag(OSITNElement.TAG_KEY_TYPE, OSITNElement.TAG_VALUE_TYPE_RESTRICTION))
                    prepareRestrictionRelation(relation);

                if (relation.hasTag(OSITNElement.TAG_KEY_TYPE, OSITNElement.TAG_VALUE_TYPE_NOENTRY)) {
                    prepareNoEntryRelation(relation);
                }

                // If this way is prohibited then we want to make a note of it
                // so we don't include it in later route generation
                if (relation.hasTag(OSITNElement.TAG_KEY_TYPE, OSITNElement.TAG_VALUE_TYPE_ACCESS_LIMITED) || relation.hasTag(OSITNElement.TAG_KEY_TYPE, OSITNElement.TAG_VALUE_TYPE_ACCESS_PROHIBITED)) {
                    prepareAccessProhibitedRelation(relation);
                }

                if (relation.hasTag("name"))
                    prepareNameRelation(relation);

                if (relation.hasTag("highway"))
                    prepareRoadTypeRelation(relation);

                if (relation.hasTag(OSITNElement.TAG_KEY_ONEWAY_ORIENTATION)) {
                    prepareRoadDirectionRelation(relation);
                }

                if (relation.hasTag(OSITNElement.TAG_KEY_CLASSIFICATION)) {
                    convertRelationTagsToWayAttributeBits(relation);
                }

                if (++tmpRelationCounter % 50000 == 0) {
                    logger.info(nf(tmpRelationCounter) + " (preprocess), osmWayMap:" + nf(getOsmWayIdToRouteWeightMap().size()) + " " + Helper.getMemInfo());
                }

            }
        }
    }

    private void prepareRestrictionRelation(Relation relation) {
        OSITNTurnRelation turnRelation = createTurnRelation(relation);
        if (turnRelation != null) {
            getOsmIdStoreRequiredSet().add(turnRelation.getOsmIdFrom());
            getOsmIdStoreRequiredSet().add(turnRelation.getOsmIdTo());
        }
    }

    /**
     * Currently identical handling for access prohibited and access limited to
     *
     * @param relation
     */
    private void prepareAccessProhibitedRelation(Relation relation) {
        if (!encodingManager.isVehicleQualifierTypeExcluded(relation) && !encodingManager.isVehicleQualifierTypeIncluded(relation)) {
            ArrayList<? extends RelationMember> members = relation.getMembers();
            // There will be only one
            for (RelationMember relationMember : members) {
                long wayId = relationMember.ref();
                getProhibitedWayIds().add(wayId);
            }
        }
    }

    private void prepareNameRelation(Relation relation) {
        String name = relation.getTag("name");
        TLongObjectMap<String> edgeIdToNameMap = getEdgeNameMap();
        ArrayList<? extends RelationMember> members = relation.getMembers();
        for (RelationMember relationMember : members) {
            long wayId = relationMember.ref();
            String namePrefix = getWayName(wayId);
            if (null != namePrefix && !namePrefix.contains(name)) {
                namePrefix = namePrefix + " (" + name + ")";
                edgeIdToNameMap.put(wayId, namePrefix);
            } else {
                edgeIdToNameMap.put(wayId, name);
            }
        }
    }

    private void prepareRoadTypeRelation(Relation relation) {
        String highway = relation.getTag("highway");
        TLongObjectMap<String> edgeIdToRoadTypeMap = getEdgeRoadTypeMap();
        ArrayList<? extends RelationMember> members = relation.getMembers();
        for (RelationMember relationMember : members) {
            long wayId = relationMember.ref();
            edgeIdToRoadTypeMap.put(wayId, highway);
        }
    }

    private void prepareRoadDirectionRelation(Relation relation) {
        // Check if this vehicle has an exception meaning we shouldn't handle
        // one way
        if (!encodingManager.isVehicleQualifierTypeExcluded(relation) && !encodingManager.isVehicleQualifierTypeIncluded(relation)) {
            // This will be "-1" the first time this is called
            String orientationIndicator = relation.getTag(OSITNElement.TAG_KEY_ONEWAY_ORIENTATION);
            TLongObjectMap<String> edgeIdToRoadDirectionMap = getEdgeRoadDirectionMap();
            ArrayList<? extends RelationMember> members = relation.getMembers();
            for (RelationMember relationMember : members) {
                long wayId = relationMember.ref();
                edgeIdToRoadDirectionMap.put(wayId, orientationIndicator);
            }
        }
    }

    private void convertRelationTagsToWayAttributeBits(Relation relation) {
        String classification = relation.getTag(OSITNElement.TAG_KEY_CLASSIFICATION);
        TLongIntMap osmWayIdToAttributeBitMaskMap = getOsmWayIdToAttributeBitMaskMap();
        int bitToSet = 0;
        switch (classification) {
        case OSITNElement.TAG_VALUE_CLASSIFICATION_FORD:
            bitToSet = ATTRIBUTE_BIT_FORD;
            break;
        case OSITNElement.TAG_VALUE_CLASSIFICATION_GATE:
            bitToSet = ATTRIBUTE_BIT_GATE;
            break;
        case OSITNElement.TAG_VALUE_CLASSIFICATION_LEVEL_CROSSING:
            bitToSet = ATTRIBUTE_BIT_LEVEL_CROSSING;
            break;
        }
        ArrayList<? extends RelationMember> members = relation.getMembers();
        // There will be only one for a RoadLinkInformation classification
        for (RelationMember relationMember : members) {
            long wayId = relationMember.ref();
            int wayAttributeBitMask = osmWayIdToAttributeBitMaskMap.get(wayId) | bitToSet;
            osmWayIdToAttributeBitMaskMap.put(wayId, wayAttributeBitMask);
        }
    }

    /**
     * Handle "No Entry" instructions. Here we are going to store
     *
     * @param relation
     */
    private void prepareNoEntryRelation(Relation relation) {
        // Check if this vehicle has an exception meaning we shouldn't handle no
        // entry
        if (!encodingManager.isVehicleQualifierTypeExcluded(relation) && !encodingManager.isVehicleQualifierTypeIncluded(relation)) {
            long flags = 1l; // (+) orientation
            String orientationIndicator = relation.getTag(OSITNElement.TAG_KEY_NOENTRY_ORIENTATION);
            if ("-1".equals(orientationIndicator)) {
                flags = 0l; // (-) orientation
            }
            TLongObjectMap<TDoubleObjectMap<TDoubleLongMap>> edgeIdToXToYToNodeFlagsMap = getEdgeIdToXToYToNodeFlagsMap();

            ArrayList<? extends RelationMember> members = relation.getMembers();
            // There will be only one which is the directedLink that this No
            // Entry relation sits on
            for (RelationMember relationMember : members) {
                long wayId = relationMember.ref();
                String coords = ((OSITNRelation) relation).getCoordinates();
                String[] coordParts = coords.split(",");
                double xCoord = Double.parseDouble(coordParts[0]);
                double yCoord = Double.parseDouble(coordParts[1]);
                TDoubleObjectMap<TDoubleLongMap> xCoordMap = edgeIdToXToYToNodeFlagsMap.get(wayId);
                if (xCoordMap == null) {
                    xCoordMap = new TDoubleObjectHashMap<TDoubleLongMap>();
                    edgeIdToXToYToNodeFlagsMap.put(wayId, xCoordMap);
                }
                TDoubleLongMap yCoordMap = xCoordMap.get(xCoord);
                if (yCoordMap == null) {
                    yCoordMap = new TDoubleLongHashMap();
                    xCoordMap.put(xCoord, yCoordMap);
                }
                // now put the flag in there
                logger.info(EDGE_ID_COORDS_TO_NODE_FLAGS_MAP_PUT_FORMAT, wayId, xCoord, yCoord, flags);

                yCoordMap.put(yCoord, flags);
            }
        }
    }

    private TLongSet getOsmIdStoreRequiredSet() {
        return osmIdStoreRequiredSet;
    }

    private TIntLongMap getEdgeIdToOsmidMap() {
        logger.info(EDGE_ID_TO_OSMIDMAP_FORMAT, edgeIdToOsmIdMap);
        if (edgeIdToOsmIdMap == null)
            edgeIdToOsmIdMap = new TIntLongHashMap(getOsmIdStoreRequiredSet().size());

        return edgeIdToOsmIdMap;
    }

    private TLongObjectMap<ItnNodePair> getNodeEdgeMap() {
        if (edgeIdToNodeMap == null)
            edgeIdToNodeMap = new TLongObjectHashMap<ItnNodePair>(getOsmIdStoreRequiredSet().size());

        return edgeIdToNodeMap;
    }

    private TLongSet getProhibitedWayIds() {
        if (prohibitedWayIds == null)
            prohibitedWayIds = new TLongHashSet();

        return prohibitedWayIds;
    }

    private TLongObjectMap<String> getEdgeNameMap() {
        if (edgeNameMap == null)
            edgeNameMap = new TLongObjectHashMap<String>(getOsmIdStoreRequiredSet().size());

        return edgeNameMap;
    }

    private TLongObjectMap<String> getEdgeRoadTypeMap() {
        if (edgeRoadTypeMap == null)
            edgeRoadTypeMap = new TLongObjectHashMap<String>(getOsmIdStoreRequiredSet().size());

        return edgeRoadTypeMap;
    }

    private TLongObjectMap<String> getEdgeEnvironmentMap() {
        if (edgeEnvironmentMap == null)
            edgeEnvironmentMap = new TLongObjectHashMap<String>(getOsmIdStoreRequiredSet().size());

        return edgeEnvironmentMap;
    }

    private TLongObjectMap<String> getEdgeRoadDirectionMap() {
        if (edgeRoadDirectionMap == null)
            edgeRoadDirectionMap = new TLongObjectHashMap<String>(getOsmIdStoreRequiredSet().size());

        return edgeRoadDirectionMap;
    }

    private TLongIntMap getOsmWayIdToAttributeBitMaskMap() {
        if (osmWayIdToAttributeBitMaskMap == null)
            osmWayIdToAttributeBitMaskMap = new TLongIntHashMap();

        return osmWayIdToAttributeBitMaskMap;
    }

    private TLongObjectMap<TDoubleObjectMap<TDoubleLongMap>> getEdgeIdToXToYToNodeFlagsMap() {
        if (edgeIdToXToYToNodeFlagsMap == null)
            edgeIdToXToYToNodeFlagsMap = new TLongObjectHashMap<TDoubleObjectMap<TDoubleLongMap>>();

        return edgeIdToXToYToNodeFlagsMap;
    }

    // TLongObjectMap<TObjectLongHashMap<String>>
    // edgeIdMapToCoordsToNodeFlagsMap
    /**
     * Filter ways but do not analyze properties wayNodes will be filled with
     * participating node ids.
     * <p/>
     *
     * @return true the current xml entry is a way entry and has nodes
     */
    boolean filterWay(OSITNWay way) {
        // ignore broken geometry
        if (way.getNodes().size() < 2)
            return false;

        // ignore multipolygon geometry
        if (!way.hasTags())
            return false;

        return encodingManager.acceptWay(way) > 0;
    }

    /**
     * Creates the edges and nodes files from the specified osm file.
     */
    @Override
    protected void writeOsm2Graph(File osmFile) {
        int tmp = (int) Math.max(getNodeMap().getSize() / 50, 100);
        logger.error(CREATING_GRAPH_FOUND_NODES_PILLAR_TOWER_FORMAT, nf(getNodeMap().getSize()), Helper.getMemInfo());
        graphStorage.create(tmp);

        ProcessData processData = new ProcessData();
        try {
            ProcessVisitor processVisitor = new ProcessVisitor() {
                @Override
                public void process(ProcessData processData, OsItnInputFile in) throws XMLStreamException, MismatchedDimensionException, FactoryException, TransformException {
                    logger.error("PROCESS STAGE 1");
                    processStageOne(processData, in);
                }
            };
            logger.error("decorateItnDataWithHighwaysNetworkData");
            decorateItnDataWithHighwaysNetworkData();
            logger.error("PROCESS NODES");
            writeOsm2GraphFromDirOrFile(osmFile, processData, processVisitor);
            processVisitor = new ProcessVisitor() {
                @Override
                public void process(ProcessData processData, OsItnInputFile in) throws XMLStreamException, MismatchedDimensionException, FactoryException, TransformException {
                    logger.error("PROCESS STAGE 2");
                    processStageTwo(processData, in);
                }
            };
            logger.error("PROCESS WAY");
            writeOsm2GraphFromDirOrFile(osmFile, processData, processVisitor);
            processVisitor = new ProcessVisitor() {
                @Override
                public void process(ProcessData processData, OsItnInputFile in) throws XMLStreamException, MismatchedDimensionException, FactoryException, TransformException {
                    logger.error("PROCESS STAGE 3");
                    processStageThree(processData, in);
                }
            };
            logger.error("PROCESS RELATION");
            writeOsm2GraphFromDirOrFile(osmFile, processData, processVisitor);

        } catch (Exception ex) {
            throw new RuntimeException("Couldn't process file " + osmFile, ex);
        }

        finishedReading();
        if (graphStorage.getNodes() == 0)
            throw new IllegalStateException("osm must not be empty. read " + processData.counter + " lines and " + locations + " locations");
    }

    private void writeOsm2GraphFromDirOrFile(File osmFile, ProcessData processData, ProcessVisitor processVisitor) throws XMLStreamException, IOException, MismatchedDimensionException, FactoryException, TransformException {
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

    private void writeOsm2GraphFromSingleFile(File osmFile, ProcessData processData, ProcessVisitor processVisitor) throws XMLStreamException, IOException, MismatchedDimensionException, FactoryException, TransformException {
        OsItnInputFile in = null;
        try {
            logger.error(PROCESS_FORMAT, osmFile.getName());
            in = new OsItnInputFile(osmFile);
            in.setWorkerThreads(workerThreads).open();
            processVisitor.process(processData, in);
            logger.info(STORAGE_NODES_FORMAT, graphStorage.getNodes());
        } finally {
            Helper.close(in);
        }

    }

    private void processStageOne(ProcessData processData, OsItnInputFile in) throws XMLStreamException, MismatchedDimensionException, FactoryException, TransformException {
        logger.error("==== processStageOne");
        RoutingElement item;
        LongIntMap nodeFilter = getNodeMap();
        // Limit the number of xml nodes we process to speed up ingestion
        in.setAbstractFactory(new OsItnProcessStageOneRoutingElementFactory());
        while ((item = in.getNext()) != null) {
            switch (item.getType()) {
            case OSMElement.NODE:
                OSITNNode node = (OSITNNode) item;
                long id = node.getId();
                logger.info(NODEITEMID_FORMAT, id);
                if (nodeFilter.get(id) != -1) {
                    OSITNNode nodeItem = (OSITNNode) item;

                    processNode(nodeItem);

                    String strId = String.valueOf(id);
                    addGradeNodesIfRequired(nodeItem, strId, nodeFilter);
                } else {
                    // We have failed to find a node for the simple id. We need
                    // to check if we have any grade separated nodes
                    // This can occur when the 0'th grade is not a supported
                    // road type, for example Private Road - Restricted Access
                    // but the 1st grade is a valid road type. This situation
                    // was found around the M27/M3 slip road where Roman Road
                    // crosses the motorway
                    OSITNNode nodeItem = (OSITNNode) item;
                    String strId = String.valueOf(id);
                    addGradeNodesIfRequired(nodeItem, strId, nodeFilter);
                }
                break;
            }
            if (++processData.counter % 5000000 == 0) {
                logger.info(PROCESSING_LOCS_FORMAT, nf(processData.counter), nf(locations), skippedLocations, Helper.getMemInfo());
            }
        }
    }

    private void decorateItnDataWithHighwaysNetworkData() {
        if (commandLineArguments != null) {
            String hnPath = commandLineArguments.get("hn.data", null);
            String hnGraphLocation = commandLineArguments.get("hn.graph.location", null);
            if (hnPath != null && hnGraphLocation!= null) {
                logger.error("=================================================>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
                logger.error("==== decorateItnDataWithHighwaysNetworkData from " + hnPath);
                FlagEncoder carEncoder = new CarFlagEncoder(5, 5, 3);
                EncodingManager encodingManager = new EncodingManager(carEncoder);
                GraphHopper hnGraphHopper = new GraphHopper(){
                    @Override
                    protected void postProcessing()
                    {
                        System.out.println("DON'T DO postProcessing()");
                    }
                    @Override
                    protected void flush()
                    {
                        //                fullyLoaded = true;
                    }

                    @Override
                    protected DataReader createReader(GraphStorage tmpGraph) {
                        DataReader reader = new OsHnReader(tmpGraph, getEdgeEnvironmentMap() );
                        return initReader(reader);
                    }

                }.setOSMFile(hnPath).setGraphHopperLocation(hnGraphLocation).setEncodingManager(encodingManager).setCHEnable(false).setAsHnReader();
                hnGraphHopper.importOrLoad();
                //                OsHnReader hnReader = new OsHnReader(hnGraphHopper.getGraph());
                logger.error("<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<=================================================");

            }
        }
    }

    private void processStageTwo(ProcessData processData, OsItnInputFile in) throws XMLStreamException, MismatchedDimensionException, FactoryException, TransformException {
        logger.error("==== processStageTwo");
        RoutingElement item;
        LongIntMap nodeFilter = getNodeMap();
        // Limit the number of xml nodes we process to speed up ingestion
        in.setAbstractFactory(new OsItnProcessStageTwoRoutingElementFactory());
        while ((item = in.getNext()) != null) {
            switch (item.getType()) {
            case OSMElement.WAY:
                OSITNWay way = (OSITNWay) item;
                logger.info(WAY_FORMAT, way.getId(), processData.wayStart);
                if (processData.wayStart < 0) {
                    logger.info(NOW_PARSING_WAYS_FORMAT, nf(processData.counter));
                    processData.wayStart = processData.counter;
                }
                // wayNodes will only contain the mid nodes and not the start or
                // end nodes.
                List<OSITNNode> wayNodes = prepareWaysNodes(way, nodeFilter);
                processWay(way, wayNodes);
                way.clearStoredCoords();
                break;
            }
            if (++processData.counter % 5000000 == 0) {
                logger.info(PROCESSING_LOCS_FORMAT, nf(processData.counter), nf(locations), skippedLocations, Helper.getMemInfo());
            }
        }
    }

    private List<OSITNNode> prepareWaysNodes(RoutingElement item, LongIntMap nodeFilter) throws MismatchedDimensionException, FactoryException, TransformException {
        List<OSITNNode> evaluateWayNodes = ((OSITNWay) item).evaluateWayNodes(getEdgeIdToXToYToNodeFlagsMap());
        for (OSITNNode ositnNode : evaluateWayNodes) {
            nodeFilter.put(ositnNode.getId(), PILLAR_NODE);
            processNode(ositnNode);
        }
        logger.info(WE_HAVE_EVALUATED_WAY_NODES_FORMAT, evaluateWayNodes.size());
        return evaluateWayNodes;
    }

    private void processStageThree(ProcessData processData, OsItnInputFile in) throws XMLStreamException, MismatchedDimensionException, FactoryException, TransformException {
        logger.error("==== processStageThree");
        RoutingElement item;
        // Limit the number of xml nodes we process to speed up ingestion
        in.setAbstractFactory(new OsItnProcessStageThreeRoutingElementFactory());
        while ((item = in.getNext()) != null) {
            switch (item.getType()) {
            case OSMElement.RELATION:
                if (processData.relationStart < 0) {
                    logger.info(NOW_PARSING_RELATIONS_FORMAT, nf(processData.counter));
                    processData.relationStart = processData.counter;
                }
                processRelation((Relation) item);
                break;
            }
            if (++processData.counter % 5000000 == 0) {
                logger.info(PROCESSING_LOCS_FORMAT, nf(processData.counter), nf(locations), skippedLocations, Helper.getMemInfo());
            }
        }
    }

    private void addGradeNodesIfRequired(OSITNNode item, String idStr, LongIntMap nodeFilter) {
        String curId;
        for (int i = 1; i <= MAX_GRADE_SEPARATION; i++) {
            curId = i + idStr;
            long parseInt = Long.parseLong(curId);
            if (nodeFilter.get(parseInt) != -1) {
                OSITNNode gradeNode = item.gradeClone(parseInt);
                processNode(gradeNode);
            }
        }
    }

    /**
     * Process properties, encode flags and create edges for the way.
     */
    private void processWay(OSITNWay way, List<OSITNNode> wayNodes) {

        if (way.getNodes().size() < 2) {
            return;
        }

        // ignore multipolygon geometry
        if (!way.hasTags()) {
            return;
        }

        long wayOsmId = way.getId();

        // Handle attributeBits
        int attributeBits = getOsmWayIdToAttributeBitMaskMap().get(way.getId());
        if (attributeBits != getOsmWayIdToAttributeBitMaskMap().getNoEntryValue()) {
            // We have at least one bit set so create tags for the set
            // attributes
            if ((attributeBits & ATTRIBUTE_BIT_FORD) != 0) {
                way.setTag(OSITNElement.TAG_VALUE_CLASSIFICATION_FORD, "yes");
            }
            if ((attributeBits & ATTRIBUTE_BIT_GATE) != 0) {
                way.setTag("barrier", OSITNElement.TAG_VALUE_CLASSIFICATION_GATE);
            }
            if ((attributeBits & ATTRIBUTE_BIT_LEVEL_CROSSING) != 0) {
                way.setTag(OSITNElement.TAG_KEY_CLASSIFICATION, OSITNElement.TAG_VALUE_CLASSIFICATION_LEVEL_CROSSING);
            }
        }

        long includeWay = encodingManager.acceptWay(way);
        if (includeWay == 0) {
            return;
        }

        // Check if we are prohibited from ever traversing this way
        if (getProhibitedWayIds().remove(wayOsmId)) {
            return;
        }

        long relationFlags = getOsmWayIdToRouteWeightMap().get(way.getId());
        logger.info(RELFLAGS_FORMAT, way.getId(), relationFlags);
        String wayName = getWayName(way.getId());
        if (null != wayName) {
            way.setTag("name", wayName);
        }
        String wayType = getWayRoadType(way.getId());
        if (null != wayType && !way.hasTag("highway")) {
            way.setTag("highway", wayType);
        }
        String wayEnvironment = getWayEnvironment(way.getId());
        if (null != wayEnvironment && !way.hasTag("environment")) {

            String nature = way.getTag("nature");
            if (!Helper.isEmpty(nature))
            {
                wayEnvironment += ":"+nature;
            }

            System.out.println(">>>>>>>>>>>>>>>> Way " + wayOsmId + " is in environment " + wayEnvironment );
            way.setTag("environment", wayEnvironment);
        }

        String wayDirection = getWayRoadDirection(way.getId());
        // If the way is ONEWAY then set the direction
        if (null != wayDirection && !way.hasTag(OSITNElement.TAG_KEY_ONEWAY_ORIENTATION)) {
            way.setTag(OSITNElement.TAG_KEY_ONEWAY_ORIENTATION, wayDirection);
        }
        // TODO move this after we have created the edge and know the
        // coordinates => encodingManager.applyWayTags
        // estimate length of the track e.g. for ferry speed calculation
        TLongList osmNodeIds = way.getNodes();
        if (osmNodeIds.size() > 1) {
            long firstItnNode = osmNodeIds.get(0);
            int first = getNodeMap().get(firstItnNode);
            long lastItnNode = osmNodeIds.get(osmNodeIds.size() - 1);
            int last = getNodeMap().get(lastItnNode);

            logger.info(WAYID_FIRST_LAST_FORMAT, wayOsmId, firstItnNode, lastItnNode);
            getNodeEdgeMap().put(wayOsmId, new ItnNodePair(firstItnNode, lastItnNode));
            double firstLat = getTmpLatitude(first), firstLon = getTmpLongitude(first);
            double lastLat = getTmpLatitude(last), lastLon = getTmpLongitude(last);
            if (!Double.isNaN(firstLat) && !Double.isNaN(firstLon) && !Double.isNaN(lastLat) && !Double.isNaN(lastLon)) {
                double estimatedDist = distCalc.calcDist(firstLat, firstLon, lastLat, lastLon);
                way.setTag("estimated_distance", estimatedDist);
                way.setTag("estimated_center", new GHPoint((firstLat + lastLat) / 2, (firstLon + lastLon) / 2));
            }
        }

        long wayFlags = encodingManager.handleWayTags(way, includeWay, relationFlags);
        if (wayFlags == 0) {
            return;
        }
        // logger.warn(ADDING_RELATION_TO_WAYS_FORMAT, wayFlags);

        // Check if we need to add additional TOWER nodes at the start and end
        // locations to deal
        // with a routing algorithm bug which prevents turn restrictions from
        // working when you start or finish on the
        // final edge of a way
        if (addAdditionalTowerNodes) {
            osmNodeIds = createStartTowerNodeAndEdge(osmNodeIds, way, wayNodes, wayFlags, wayOsmId);
        }
        // Process No Entry and then Barriers, and finally add the remaining way
        processNoEntry(way, wayNodes, osmNodeIds, wayFlags, wayOsmId);

    }

    private TLongList createStartTowerNodeAndEdge(TLongList osmNodeIds, OSITNWay way, List<OSITNNode> wayNodes, long wayFlags, long wayOsmId) {
        // if (osmNodeIds.size()>2) {
        List<EdgeIteratorState> startCreatedEdges = new ArrayList<EdgeIteratorState>();

        // Get the node id of the first pillar node/way node

        long nodeId = osmNodeIds.get(0);

        // Check if we have a pillar node at the start. If so we need to convert
        // to a tower.
        int graphIndex = getNodeMap().get(nodeId);
        if (graphIndex < TOWER_NODE) {
            OSITNNode newNode = addBarrierNode(nodeId, true);
            // logger.error("Add Node at start of way from " + nodeId +
            // " to " + osmNodeIds.get(osmNodeIds.size()-1) + " lat lon is " +
            // newNode.getLat() + " " + newNode.getLon());
            long newNodeId = newNode.getId();
            int nodeType = getNodeMap().get(newNodeId);

            // add way up to barrier shadow node
            long transfer[] = osmNodeIds.toArray(0, 2); // From 0 for length
            // 2
            transfer[transfer.length - 1] = newNodeId;
            TLongList partIds = new TLongArrayList(transfer);
            Collection<EdgeIteratorState> newWays = addOSMWay(partIds, wayFlags, wayOsmId);
            // logger.warn(WAY_ADDS_EDGES_FORMAT, wayOsmId, newWays.size());
            startCreatedEdges.addAll(newWays);

            // Set the 0th node id to be our new node id
            osmNodeIds.set(0, newNodeId);

            for (EdgeIteratorState edge : startCreatedEdges) {
                encodingManager.applyWayTags(way, edge);
            }
        }
        return osmNodeIds;
    }

    /**
     * This method processes the list of NodeIds and checks if any nodes have a
     * NoEntry Tag. If it does then it adds a shadow node and an extra way as a
     * OneWay. Once it has run out of NoEntry nodes to process it passes the
     * remainder on to processBarriers to check for barriers and construct the
     * remaining way
     *
     * @param way
     * @param wayNodes
     *            OSITNNode objects for the way nodes, ie not including the
     *            start and end nodes
     * @param osmNodeIds
     *            Node Ids of all Nodes, including the start and end nodes
     * @param wayFlags
     * @param wayOsmId
     * @return
     */
    private List<EdgeIteratorState> processNoEntry(OSITNWay way, List<OSITNNode> wayNodes, TLongList osmNodeIds, long wayFlags, long wayOsmId) {
        List<EdgeIteratorState> createdEdges = new ArrayList<EdgeIteratorState>();
        int lastNoEntry = -1;
        List<EdgeIteratorState> noEntryCreatedEdges = new ArrayList<EdgeIteratorState>();
        boolean modifiedWithNoEntry = false;
        // Process Start Coordinate
        String startDirection = checkForNoEntryDirection(wayOsmId, way.getStartCoord());
        if (startDirection != null) {
            modifiedWithNoEntry = true;
            lastNoEntry = 1; // This will set the index used for way nodes
            // create shadow node copy for zero length edge
            long nodeId = osmNodeIds.get(1); // Get the second node id

            int graphIndex = getNodeMap().get(nodeId);
            if (graphIndex != EMPTY) {

                long nodeFlags = getNodeFlagsMap().get(nodeId);
                OSITNNode newNode = addBarrierNode(nodeId);
                long newNodeId = newNode.getId();
                // add way up to barrier shadow node
                long transfer[] = osmNodeIds.toArray(0, 2); // From 0 for length
                // 2
                transfer[transfer.length - 1] = newNodeId;
                TLongList partIds = new TLongArrayList(transfer);
                Collection<EdgeIteratorState> newWays = addOSMWay(partIds, wayFlags, wayOsmId);
                // logger.warn(WAY_ADDS_EDGES_FORMAT, wayOsmId, newWays.size());
                noEntryCreatedEdges.addAll(newWays);

                // create zero length edge for barrier to the next node
                Collection<EdgeIteratorState> newBarriers = addBarrierEdge(newNodeId, nodeId, wayFlags, nodeFlags, wayOsmId);
                // logger.warn(WAY_ADDS_BARRIER_EDGES_FORMAT, wayOsmId,
                // newBarriers.size());
                noEntryCreatedEdges.addAll(newBarriers);
                // Update the orientation of our little one way
                for (EdgeIteratorState edgeIteratorState : newWays) {
                    boolean forwards = startDirection.equals("true");

                    long flags = encodingManager.flagsDefault(forwards, !forwards);
                    // Set the flags on our new edge.
                    edgeIteratorState.setFlags(flags);
                }
                successfulStartNoEntries++;
            } else {
                failedStartNoEntries++;
                errors_logger.error("MISSING NODE: osmNodeIdToInternalNodeMap returned -1 for nodeId " + nodeId + " on way " + way.getId() + " for START Node " + osmNodeIds.toString() + " (" + successfulStartNoEntries + " succeeded, " + failedStartNoEntries + " failed)");
            }
        }
        // Process Way Nodes
        final int size = osmNodeIds.size();
        for (int i = 1, j = 0; j < wayNodes.size(); i++, j++) {
            OSITNNode ositnNode = wayNodes.get(j);
            String direction = checkForNoEntryDirection(wayOsmId, way.getWayCoords()[j]);
            // If direction is null then there is no No Entry defined for this
            // way node
            if (direction != null) {
                modifiedWithNoEntry = true;
                long nodeId = ositnNode.getId();
                long nodeFlags = getNodeFlagsMap().get(nodeId);

                // create shadow node copy for zero length edge
                OSITNNode newNode = addBarrierNode(nodeId);
                long newNodeId = newNode.getId();
                // Always > 0 as we start at index 1
                if (i > 0) {
                    // start at beginning of array if there was no previous
                    // barrier. This was only set to -1 so if we never get here
                    // we know it will still be -1 below
                    if (lastNoEntry < 0)
                        lastNoEntry = 0;
                    // add way up to barrier shadow node
                    long transfer[] = osmNodeIds.toArray(lastNoEntry, i - lastNoEntry + 1);
                    transfer[transfer.length - 1] = newNodeId;
                    TLongList partIds = new TLongArrayList(transfer);
                    Collection<EdgeIteratorState> newWays = addOSMWay(partIds, wayFlags, wayOsmId);
                    // logger.warn(WAY_ADDS_EDGES_FORMAT, wayOsmId,
                    // newWays.size());
                    noEntryCreatedEdges.addAll(newWays);

                    // create zero length edge for barrier to the next node
                    Collection<EdgeIteratorState> newBarriers = addBarrierEdge(newNodeId, nodeId, wayFlags, nodeFlags, wayOsmId);
                    // logger.warn(WAY_ADDS_BARRIER_EDGES_FORMAT, wayOsmId,
                    // newBarriers.size());
                    noEntryCreatedEdges.addAll(newBarriers);
                    // Update the orientation of our little one way
                    for (EdgeIteratorState edgeIteratorState : newBarriers) {
                        boolean forwards = direction.equals("true");

                        long flags = encodingManager.flagsDefault(forwards, !forwards);
                        // Set the flags on our new edge.
                        edgeIteratorState.setFlags(flags);
                    }
                } else {
                    // TODO Currently this code is never called. I believe that
                    // when the no entry is placed on either
                    // TODO end of way we will have issues
                    // run edge from real first node to shadow node
                    Collection<EdgeIteratorState> newBarriers = addBarrierEdge(nodeId, newNodeId, wayFlags, nodeFlags, wayOsmId);
                    // logger.warn(WAY_ADDS_BARRIER_EDGES_FORMAT, wayOsmId,
                    // newBarriers.size());
                    noEntryCreatedEdges.addAll(newBarriers);
                    // exchange first node for created barrier node
                    osmNodeIds.set(0, newNodeId);
                }
                // remember barrier for processing the way behind it
                lastNoEntry = j + 1;

            }
        }
        // Process the last coordinate
        boolean processedEntireWay = false;
        TLongList nodeIdsToCreateWaysFor = null;
        String endDirection = checkForNoEntryDirection(wayOsmId, way.getEndCoord());
        if (endDirection != null) {
            // Get the last node id
            long nodeId = osmNodeIds.get(osmNodeIds.size() - 1);
            // Check if there is a graphIndex for this node. Not sure why there
            // wouldn't be one
            int graphIndex = getNodeMap().get(nodeId);
            if (graphIndex != EMPTY) {
                // First thing to do is add ways up to the last node
                {
                    if (lastNoEntry < 0)
                        lastNoEntry = 0;
                    long transfer[] = osmNodeIds.toArray(lastNoEntry, size - lastNoEntry - 1);
                    nodeIdsToCreateWaysFor = new TLongArrayList(transfer);
                    Collection<EdgeIteratorState> newEdges = addOSMWay(nodeIdsToCreateWaysFor, wayFlags, wayOsmId);
                    // logger.warn(WAY_ADDS_EDGES_FORMAT, wayOsmId,
                    // newEdges.size());
                    createdEdges.addAll(newEdges);
                }

                // We are processing all the way to the end of the line so don't
                // process down to barriers.
                // This is going to obscure barrier behaviour I believe.
                processedEntireWay = true;
                long nodeFlags = getNodeFlagsMap().get(nodeId);
                // create shadow node copy for zero length edge
                OSITNNode newNode = addBarrierNode(nodeId);
                long newNodeId = newNode.getId();

                // Set this to be a TOWER node explicitly to overcome a
                // limitation in the GraphHopper code for TurnRestrictions
                // getNodeMap().put(newNodeId, TOWER_NODE);

                // add way up to barrier shadow node
                long transfer[] = osmNodeIds.toArray(osmNodeIds.size() - 2, 2); // From
                // 0
                // for
                // length
                // 2
                transfer[transfer.length - 1] = newNodeId;
                TLongList partIds = new TLongArrayList(transfer);
                Collection<EdgeIteratorState> newWays = addOSMWay(partIds, wayFlags, wayOsmId);
                // logger.warn(WAY_ADDS_EDGES_FORMAT, wayOsmId, newWays.size());
                noEntryCreatedEdges.addAll(newWays);

                // create zero length edge for barrier to the next node
                Collection<EdgeIteratorState> newBarriers = addBarrierEdge(newNodeId, nodeId, wayFlags, nodeFlags, wayOsmId);
                // logger.warn(WAY_ADDS_BARRIER_EDGES_FORMAT, wayOsmId,
                // newBarriers.size());
                noEntryCreatedEdges.addAll(newBarriers);
                // Update the orientation of our little one way
                for (EdgeIteratorState edgeIteratorState : newBarriers) {
                    boolean forwards = endDirection.equals("-1");
                    long flags = encodingManager.flagsDefault(forwards, !forwards);
                    // Set the flags on our new edge.
                    edgeIteratorState.setFlags(flags);
                }
                successfulEndNoEntries++;
            } else {
                failedEndNoEntries++;
                // TODO Figure out why there are some end nodes that don't have
                // internal node ids
                errors_logger.error("MISSING NODE: osmNodeIdToInternalNodeMap returned -1 for nodeId " + nodeId + " on way " + way.getId() + " for END Node " + osmNodeIds.toString() + " (" + successfulEndNoEntries + " succeeded, " + failedEndNoEntries + " failed)");
            }
        }

        // If we have processed a No Entry on the last node we have processed
        // the whole way do don't go down to barriers
        if (!processedEntireWay) {
            // just add remainder of way to graph if barrier was not the last
            // node
            if (modifiedWithNoEntry) {
                if (lastNoEntry < size - 1) {
                    long transfer[] = osmNodeIds.toArray(lastNoEntry, size - lastNoEntry);
                    nodeIdsToCreateWaysFor = new TLongArrayList(transfer);
                }
            } else {
                // no barriers - simply add the whole way
                nodeIdsToCreateWaysFor = osmNodeIds;
            }
            if (nodeIdsToCreateWaysFor != null) {
                // Now process Barriers for the remaining route. This will
                // create
                // the remaining ways for after the end of the barriers
                processBarriers(way, nodeIdsToCreateWaysFor, wayFlags, wayOsmId);
            }
        }
        // TODO Can we move this code out into processWay?
        if (modifiedWithNoEntry || processedEntireWay) {
            for (EdgeIteratorState edge : noEntryCreatedEdges) {
                encodingManager.applyWayTags(way, edge);
            }
        }
        return createdEdges;
    }

    /**
     *
     * @param wayId
     * @param wayCoord
     * @return "true" for (+), "-1" for (-), null for not set
     */
    private String checkForNoEntryDirection(long wayId, String wayCoord) {
        // Look for direction flags in edgeIdToXToYToNodeFlagsMap for the wayId,
        // x, y combination
        long key = wayId;
        TDoubleObjectMap<TDoubleLongMap> xToYToNodeFlagsMap = getEdgeIdToXToYToNodeFlagsMap().get(key);
        if (xToYToNodeFlagsMap != null) {
            String[] coordParts = wayCoord.split(",");
            double xCoord = Double.parseDouble(coordParts[0]);
            double yCoord = Double.parseDouble(coordParts[1]);
            TDoubleLongMap yToNodeFlagsMap = xToYToNodeFlagsMap.get(xCoord);
            if (yToNodeFlagsMap != null) {
                if (yToNodeFlagsMap.containsKey(yCoord)) {
                    long direction = yToNodeFlagsMap.remove(yCoord);
                    // Tidy Up so we reduce memory usage as the ingestion
                    // progresses
                    if (yToNodeFlagsMap.size() == 0) {
                        // Remove empty yCoord map from xCoord Map
                        xToYToNodeFlagsMap.remove(xCoord);
                        if (xToYToNodeFlagsMap.size() == 0) {
                            // We have no more x coords for this key so this
                            // way has been handled
                            edgeIdToXToYToNodeFlagsMap.remove(key);
                        }
                    }
                    // wayNode.setTag(TAG_KEY_NOENTRY_ORIENTATION, "true");
                    if (direction > 0l) {
                        // (+)
                        return "true";
                    } else {
                        // (-)
                        return "-1";
                    }
                }
            }
        }
        return null;
    }

    /**
     * This method takes the supplied list of osmNodeIds and checks for
     * barriers. If it find them it adds a shadow node with zero length at the
     * same location and adds ways such that they are connected. The remaining
     * way once it has processed all barriers is just created as a series of
     * ways between the remaining nodes
     *
     * @param way
     *            Not really used much. Could be refactored so this parameter is
     *            not required.
     * @param osmNodeIds
     *            Ids of the nodes to check for barriers
     * @param wayFlags
     * @param wayOsmId
     * @return
     */
    private List<EdgeIteratorState> processBarriers(Way way, TLongList osmNodeIds, long wayFlags, long wayOsmId) {
        List<EdgeIteratorState> createdEdges = new ArrayList<EdgeIteratorState>();
        // look for barriers along the way
        final int size = osmNodeIds.size();
        int lastBarrier = -1;
        for (int i = 0; i < size; i++) {
            long nodeId = osmNodeIds.get(i);
            // This will return the same flags for ALL points along a road link.
            // Is this correct? This means that if a link has 50 points and one
            // is a barrier, they will all be barriers???
            long nodeFlags = getNodeFlagsMap().get(nodeId);
            // barrier was spotted and way is otherwise passable for that
            // mode
            // of travel
            if (nodeFlags > 0) {
                if ((nodeFlags & wayFlags) > 0) {
                    // remove barrier to avoid duplicates
                    getNodeFlagsMap().put(nodeId, 0);

                    // create shadow node copy for zero length edge
                    OSITNNode newNode = addBarrierNode(nodeId);
                    long newNodeId = newNode.getId();
                    if (i > 0) {
                        // start at beginning of array if there was no
                        // previous
                        // barrier
                        if (lastBarrier < 0)
                            lastBarrier = 0;

                        // add way up to barrier shadow node
                        long transfer[] = osmNodeIds.toArray(lastBarrier, i - lastBarrier + 1);
                        transfer[transfer.length - 1] = newNodeId;
                        TLongList partIds = new TLongArrayList(transfer);
                        Collection<EdgeIteratorState> newWays = addOSMWay(partIds, wayFlags, wayOsmId);
                        // logger.warn(WAY_ADDS_EDGES_FORMAT, wayOsmId,
                        // newWays.size());
                        createdEdges.addAll(newWays);

                        // create zero length edge for barrier
                        Collection<EdgeIteratorState> newBarriers = addBarrierEdge(newNodeId, nodeId, wayFlags, nodeFlags, wayOsmId);
                        // logger.warn(WAY_ADDS_BARRIER_EDGES_FORMAT, wayOsmId,
                        // newBarriers.size());
                        createdEdges.addAll(newBarriers);
                    } else {
                        // run edge from real first node to shadow node
                        Collection<EdgeIteratorState> newBarriers = addBarrierEdge(nodeId, newNodeId, wayFlags, nodeFlags, wayOsmId);
                        // logger.warn(WAY_ADDS_BARRIER_EDGES_FORMAT, wayOsmId,
                        // newBarriers.size());
                        createdEdges.addAll(newBarriers);

                        // exchange first node for created barrier node
                        osmNodeIds.set(0, newNodeId);
                    }

                    // remember barrier for processing the way behind it
                    lastBarrier = i;
                }
            }
        }

        // just add remainder of way to graph if barrier was not the last
        // node
        TLongList nodeIdsToCreateWaysFor = null;
        if (lastBarrier >= 0) {
            if (lastBarrier < size - 1) {
                long transfer[] = osmNodeIds.toArray(lastBarrier, size - lastBarrier);
                nodeIdsToCreateWaysFor = new TLongArrayList(transfer);
            }
        } else {
            // no barriers - simply add the whole way
            nodeIdsToCreateWaysFor = osmNodeIds;
        }

        if (nodeIdsToCreateWaysFor != null) {
            long lastNodeId = nodeIdsToCreateWaysFor.get(nodeIdsToCreateWaysFor.size() - 1);
            ;
            long newNodeId = -1;

            int graphIndex = getNodeMap().get(lastNodeId);// -4 for wayOsmId
            // 4000000025288017

            // An index < TOWER_NODE means it is a tower node.
            boolean doInsertAdditionalTowerNodes = addAdditionalTowerNodes && (graphIndex < TOWER_NODE);

            // logger.error("doInsertAdditionalTowerNodes is " +
            // doInsertAdditionalTowerNodes + " for lastNodeId "+ lastNodeId );

            // add end tower here
            if (doInsertAdditionalTowerNodes) {
                OSITNNode newNode = addBarrierNode(lastNodeId, true);
                // logger.error("Add End shadow node between " +
                // nodeIdsToCreateWaysFor.get(0) + " and " + lastNodeId +
                // " lat lon is " + newNode.getLat() + " " + newNode.getLon());
                newNodeId = newNode.getId();

                nodeIdsToCreateWaysFor.set(nodeIdsToCreateWaysFor.size() - 1, newNodeId);
            }

            Collection<EdgeIteratorState> newEdges = addOSMWay(nodeIdsToCreateWaysFor, wayFlags, wayOsmId);

            createdEdges.addAll(newEdges);
            if (doInsertAdditionalTowerNodes) {
                long transfer[] = { newNodeId, lastNodeId };
                TLongList partIds = new TLongArrayList(transfer);
                newEdges = addOSMWay(partIds, wayFlags, wayOsmId);
                createdEdges.addAll(newEdges);
            }
        }
        // TODO Can we move this code out into processWay?
        for (EdgeIteratorState edge : createdEdges) {
            encodingManager.applyWayTags(way, edge);
        }
        return createdEdges;
    }

    private String getWayName(long id) {
        return getEdgeNameMap().remove(id);
    }

    private String getWayRoadType(long id) {
        return getEdgeRoadTypeMap().remove(id);
    }
    private String getWayEnvironment(long id) {
        return getEdgeEnvironmentMap().remove(id);
    }

    private String getWayRoadDirection(long id) {
        return getEdgeRoadDirectionMap().remove(id);
    }

    /**
     * Called at Stage 3. This processes Mandatory Turn and No Turn
     *
     * @param relation
     * @throws XMLStreamException
     */
    public void processRelation(Relation relation) throws XMLStreamException {
        if (relation.hasTag(OSITNElement.TAG_KEY_TYPE, OSITNElement.TAG_VALUE_TYPE_RESTRICTION)) {
            OSITNTurnRelation turnRelation = createTurnRelation(relation);
            if (turnRelation != null) {
                long fromId = turnRelation.getOsmIdFrom();
                long toId = turnRelation.getOsmIdTo();
                // These were not originally added here. This could be used to
                // clean up getEdgeIdToOsmidMap() as it will now contain all
                // edgeIdToOsmId mappings
                getOsmIdStoreRequiredSet().add(fromId);
                getOsmIdStoreRequiredSet().add(toId);

                logger.info(TURN_FROM_TO_VIA_FORMAT, turnRelation.getOsmIdFrom(), turnRelation.getOsmIdTo(), turnRelation.getVia());
                GraphExtension extendedStorage = graphStorage.getExtension();
                if (extendedStorage instanceof TurnCostExtension) {
                    TurnCostExtension tcs = (TurnCostExtension) extendedStorage;
                    Collection<ITurnCostTableEntry> entries = encodingManager.analyzeTurnRelation(turnRelation, this);
                    for (ITurnCostTableEntry entry : entries) {
                        tcs.addTurnInfo(entry.getEdgeFrom(), entry.getVia(), entry.getEdgeTo(), entry.getFlags());
                    }
                }
            }
        }
        // else if (relation.hasTag(OSITNElement.TAG_KEY_TYPE,
        // OSITNElement.TAG_VALUE_TYPE_NOENTRY)) {
        // IMPLEMENT THIS
        // }
    }

    @Override
    public Long getOsmIdOfInternalEdge(int edgeId) {
        return getEdgeIdToOsmidMap().get(edgeId);
    }

    public long getInternalIdOfOsmEdge(int edgeId) {
        return getEdgeIdToOsmidMap().get(edgeId);
    }

    @Override
    public int getInternalNodeIdOfOsmNode(Long nodeOsmId) {
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

    private void processNode(OSITNNode node) {
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

    boolean addNode(OSITNNode node) {
        int nodeType = getNodeMap().get(node.getId());
        if (nodeType == EMPTY) {
            // logger.warn(MISSING_FROM_MAP_FORMAT, node.getId());
            return false;
        }
        logger.debug(ADDING_NODE_AS_FORMAT, node.getId(), nodeType);
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

    private double getElevation(Node node) {
        return eleProvider.getEle(node.getLat(), node.getLon());
    }

    void prepareWaysWithRelationInfo(Relation relation) {
        // is there at least one tag interesting for the registered encoders?
        long handleRelationTags = encodingManager.handleRelationTags(relation, 0);
        // logger.warn(PREPARE_ONE_WAY_FORMAT, handleRelationTags);
        if (handleRelationTags == 0) {
            return;
        }

        int size = relation.getMembers().size();
        for (int index = 0; index < size; index++) {
            RelationMember member = relation.getMembers().get(index);
            if (member.type() != OSMRelation.Member.WAY)
                continue;
            long osmId = member.ref();
            // logger.warn(ADDING_WAY_RELATION_TO_FORMAT, osmId);
            long oldRelationFlags = getOsmWayIdToRouteWeightMap().get(osmId);

            // Check if our new relation data is better compared to the the
            // last one
            long newRelationFlags = encodingManager.handleRelationTags(relation, oldRelationFlags);
            // logger.warn(APPLYING_RELATION_FORMAT, oldRelationFlags,
            // newRelationFlags);
            if (oldRelationFlags != newRelationFlags) {
                getOsmWayIdToRouteWeightMap().put(osmId, newRelationFlags);
            }
        }
    }

    void prepareHighwayNode(long osmId) {
        int tmpIndex = getNodeMap().get(osmId);
        if (tmpIndex == EMPTY) {
            // osmId is used exactly once
            logger.info(OS_ITN_READER_PREPARE_HIGHWAY_NODE_EMPTY_PILLAR_FORMAT, osmId);
            getNodeMap().put(osmId, PILLAR_NODE);
        } else if (tmpIndex > EMPTY) {
            // mark node as tower node as it occured at least twice times
            logger.info(OS_ITN_READER_PREPARE_HIGHWAY_NODE_PILLAR_TOWER_FORMAT, osmId);
            getNodeMap().put(osmId, TOWER_NODE);
        } else {
            // tmpIndex is already negative (already tower node)
        }
    }

    private int addTowerNode(long osmId, double lat, double lon, double ele) {
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
    Collection<EdgeIteratorState> addOSMWay(TLongList osmNodeIds, long flags, long wayOsmId) {
        PointList pointList = new PointList(osmNodeIds.size(), nodeAccess.is3D());
        List<EdgeIteratorState> newEdges = new ArrayList<EdgeIteratorState>(5);
        int firstNode = -1;
        int lastIndex = osmNodeIds.size() - 1;
        int lastInBoundsPillarNode = -1;
        try {
            for (int i = 0; i < osmNodeIds.size(); i++) {
                long osmId = osmNodeIds.get(i);
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

    EdgeIteratorState addEdge(int fromIndex, int toIndex, PointList pointList, long flags, long wayOsmId) {
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
        logger.info("Add edge flags:" + flags);
        EdgeIteratorState iter = graphStorage.edge(fromIndex, toIndex).setDistance(towerNodeDistance).setFlags(flags);
        if (nodes > 2) {
            if (doSimplify)
                simplifyAlgo.simplify(pillarNodes);

            iter.setWayGeometry(pillarNodes);
        }
        storeOSMWayID(iter.getEdge(), wayOsmId);
        return iter;
    }

    /**
     * FROM OSMReader: Stores only osmWayIds which are required for relations
     * This copy stores all mappings because getOsmIdStoreRequiredSet() isn't
     * populated until processStage3 and this call is made in processStage2 so
     * we would not have any entries
     */
    private void storeOSMWayID(int edgeId, long osmWayID) {
        logger.info(STORE_OSM_WAY_ID_FOR_FORMAT, osmWayID, edgeId);
        // getOsmIdStoreRequiredSet() isn't populated until processStage3 and
        // this call is made in processStage2 so we should not check
        // if (getOsmIdStoreRequiredSet().contains(osmWayID)) {
        getEdgeIdToOsmidMap().put(edgeId, osmWayID);
        // }
    }

    /**
     * @return converted tower node
     */
    private int handlePillarNode(int tmpNode, long osmId, PointList pointList, boolean convertToTowerNode) {
        logger.info(CONVERTING_PILLAR_TO_PILLAR_FORMAT, osmId, convertToTowerNode);
        tmpNode = tmpNode - 3;
        double lat = pillarInfo.getLatitude(tmpNode);
        double lon = pillarInfo.getLongitude(tmpNode);
        double ele = pillarInfo.getElevation(tmpNode);
        if (lat == Double.MAX_VALUE || lon == Double.MAX_VALUE)
            throw new RuntimeException("Conversion pillarNode to towerNode already happended!? " + "osmId:" + osmId + " pillarIndex:" + tmpNode);

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
        eleProvider.release();
        // osmNodeIdToInternalNodeMap = null;
        osmNodeIdToNodeFlagsMap = null;
        osmWayIdToRouteWeightMap = null;
        osmIdStoreRequiredSet = null;
        edgeIdToOsmIdMap = null;
        edgeIdToNodeMap = null;
    }

    /**
     * Create a copy of the barrier node
     */
    private OSITNNode addBarrierNode(long nodeId) {
        return addBarrierNode(nodeId, false);
    }

    private OSITNNode addBarrierNode(long nodeId, boolean forceAsTower) {
        OSITNNode newNode = null;
        int graphIndex = getNodeMap().get(nodeId);

        if (graphIndex < TOWER_NODE || forceAsTower) {
            graphIndex = -graphIndex - 3;
            // logger.error("Create Tower node for nodeId " + nodeId +
            // " graphIndex is " + graphIndex);

            newNode = new OSITNNode(createNewNodeId(), nodeAccess, graphIndex);
        } else {
            graphIndex = graphIndex - 3;
            try {
                newNode = new OSITNNode(createNewNodeId(), pillarInfo, graphIndex);
            } catch (ArrayIndexOutOfBoundsException e) {
                e.printStackTrace();
            }
        }

        final long id = newNode.getId();
        if (forceAsTower) {
            getNodeMap().put(id, TOWER_NODE);
        } else {
            prepareHighwayNode(id);
        }
        addNode(newNode);
        return newNode;
    }

    private long createNewNodeId() {
        return newUniqueOsmId++;
    }

    /**
     * Add a zero length edge with reduced routing options to the graph.
     */
    private Collection<EdgeIteratorState> addBarrierEdge(long fromId, long toId, long flags, long nodeFlags, long wayOsmId) {
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
     * <p>
     *
     * @return the OSM turn relation, <code>null</code>, if unsupported turn
     *         relation
     */
    OSITNTurnRelation createTurnRelation(Relation relation) {
        OSMTurnRelation.Type type = OSITNTurnRelation.getRestrictionType(relation.getTag(OSITNElement.TAG_KEY_RESTRICTION));

        // Handle No Turn and Mandatory Turn Exceptions. This is done by
        // selectively ignoring restrictions based on excluded/included vehicle
        // types
        // as defined in the AbstractFlagEncoder and populated in its child
        // classes CarFlagEncoder and BikeFlagEncoder
        // if (Mandatory Turn (Type.ONLY) except buses=true) remove the
        // restriction
        // if (Mandatory Turn (Type.ONLY) except buses=false) leave as is
        // if (no turn (Type.NOT) except buses=true) leave as is
        // if (no turn (Type.NOT) except buses=false) remove the restriction
        if (type == Type.NOT || type == Type.ONLY) {
            // There is a no entry or mandatory turn
            if (encodingManager.isVehicleQualifierTypeExcluded(relation) || encodingManager.isVehicleQualifierTypeIncluded(relation)) {
                // The current encoder vehicle is excluded from this restriction
                // so remove it OR (except buses=false)
                // The current encoder vehicle is included in the exception so
                // remove it.
                type = Type.UNSUPPORTED;
            }
        }

        if (type != OSMTurnRelation.Type.UNSUPPORTED) {
            long fromWayID = -1;
            long viaNodeID = -1;
            long toWayID = -1;

            for (RelationMember member : relation.getMembers()) {
                long ref = member.ref();
                if (logger.isInfoEnabled())
                    logger.info(RELATIONMEMBERREF_FORMAT, ref);
                if (OSMElement.WAY == member.type()) {
                    if ("from".equals(member.role())) {
                        fromWayID = ref;
                    } else if ("to".equals(member.role())) {
                        toWayID = ref;
                    }
                } else if (OSMElement.NODE == member.type() && "via".equals(member.role())) {
                    viaNodeID = ref;
                }
            }

            if (type != OSMTurnRelation.Type.UNSUPPORTED && fromWayID >= 0 && toWayID >= 0) {
                long foundViaNode = findViaNode(fromWayID, toWayID);
                if (-1 < foundViaNode) {
                    OSITNTurnRelation osmTurnRelation = new OSITNTurnRelation(fromWayID, foundViaNode, toWayID, type);
                    return osmTurnRelation;
                }
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
    protected LongIntMap getNodeMap() {
        return osmNodeIdToInternalNodeMap;
    }

    protected TLongLongMap getNodeFlagsMap() {
        return osmNodeIdToNodeFlagsMap;
    }

    private TLongLongHashMap getOsmWayIdToRouteWeightMap() {
        return osmWayIdToRouteWeightMap;
    }

    /**
     * Specify the type of the path calculation (car, bike, ...).
     */
    @Override
    public AbstractOsReader<Long> setEncodingManager(EncodingManager acceptWay) {
        this.encodingManager = acceptWay;
        return this;
    }

    @Override
    public AbstractOsReader<Long> setWayPointMaxDistance(double maxDist) {
        doSimplify = maxDist > 0;
        simplifyAlgo.setMaxDistance(maxDist);
        return this;
    }

    @Override
    public AbstractOsReader<Long> setWorkerThreads(int numOfWorkers) {
        this.workerThreads = numOfWorkers;
        return this;
    }

    private void printInfo(String str) {
        logger.info(PRINT_INFO_FORMAT, str, graphStorage.getNodes(), getNodeMap().getSize(), getNodeMap().getMemoryUsage(), getNodeFlagsMap().size(), getOsmWayIdToRouteWeightMap().size(), Helper.getMemInfo());
    }

    private long findViaNode(long fromOsm, long toOsm) {
        TLongObjectMap<ItnNodePair> nodeEdgeMap = getNodeEdgeMap();
        ItnNodePair itnNodePairFrom = nodeEdgeMap.get(fromOsm);
        ItnNodePair itnNodePairTo = nodeEdgeMap.get(toOsm);

        if (null != itnNodePairFrom && null != itnNodePairTo) {
            if (itnNodePairFrom.last == itnNodePairTo.first) {
                return itnNodePairFrom.last;
            }
            if (itnNodePairFrom.first == itnNodePairTo.first) {
                return itnNodePairFrom.first;
            }
            if (itnNodePairFrom.first == itnNodePairTo.last) {
                return itnNodePairFrom.first;
            }
            if (itnNodePairFrom.last == itnNodePairTo.last) {
                return itnNodePairFrom.last;
            }
            TLongProcedure outputMap = new TLongProcedure() {

                @Override
                public boolean execute(long arg0) {
                    return true;
                }
            };
            nodeEdgeMap.forEachKey(outputMap);
            logger.error(NO_MATCHING_EDGES_FOR_FORMAT, fromOsm, toOsm);
            // throw new IllegalArgumentException("No Matching Edges for " +
            // fromOsm + ":" + toOsm);
        }
        return -1;
    }
}
