package com.graphhopper.reader.osgb;

import static com.graphhopper.util.Helper.nf;
import gnu.trove.list.TLongList;
import gnu.trove.list.array.TLongArrayList;
import gnu.trove.map.TIntLongMap;
import gnu.trove.map.TLongByteMap;
import gnu.trove.map.TLongLongMap;
import gnu.trove.map.TLongObjectMap;
import gnu.trove.map.hash.TIntLongHashMap;
import gnu.trove.map.hash.TLongByteHashMap;
import gnu.trove.map.hash.TLongLongHashMap;
import gnu.trove.map.hash.TLongObjectHashMap;
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
import com.graphhopper.reader.DataReader;
import com.graphhopper.reader.ITurnCostTableEntry;
import com.graphhopper.reader.Node;
import com.graphhopper.reader.OSMElement;
import com.graphhopper.reader.OSMNode;
import com.graphhopper.reader.OSMRelation;
import com.graphhopper.reader.OSMTurnRelation;
import com.graphhopper.reader.PillarInfo;
import com.graphhopper.reader.Relation;
import com.graphhopper.reader.RelationMember;
import com.graphhopper.reader.RoutingElement;
import com.graphhopper.reader.Way;
import com.graphhopper.reader.dem.ElevationProvider;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.ExtendedStorage;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.TurnCostStorage;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.DistanceCalc3D;
import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.DouglasPeucker;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PointList;
import com.graphhopper.util.StopWatch;
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

public class OsItnReader implements DataReader {

	public class ProcessVisitor {
		public void process(ProcessData processData, OsItnInputFile in) throws XMLStreamException {
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
	private static final Logger logger = LoggerFactory
			.getLogger(OsItnReader.class);

	private static final int MAX_GRADE_SEPARATION = 4;
	private long locations;
	private long skippedLocations;
	private final GraphStorage graphStorage;
	private final NodeAccess nodeAccess;
	private EncodingManager encodingManager = null;
	private int workerThreads = -1;
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
	private LongIntMap osmNodeIdToInternalNodeMap;
	private TLongLongHashMap osmNodeIdToNodeFlagsMap;
	private TLongLongHashMap osmWayIdToRouteWeightMap;
	// stores osm way ids used by relations to identify which edge ids needs to
	// be mapped later
	private TLongHashSet osmIdStoreRequiredSet = new TLongHashSet();
	private TIntLongMap edgeIdToOsmIdMap;
	private final TLongList barrierNodeIds = new TLongArrayList();
	protected PillarInfo pillarInfo;
	private final DistanceCalc distCalc = new DistanceCalcEarth();
	private final DistanceCalc3D distCalc3D = new DistanceCalc3D();
	private final DouglasPeucker simplifyAlgo = new DouglasPeucker();
	private boolean doSimplify = true;
	private int nextTowerId = 0;
	private int nextPillarId = 0;
	// negative but increasing to avoid clash with custom created OSM files
	private long newUniqueOsmId = -Long.MAX_VALUE;
	private ElevationProvider eleProvider = ElevationProvider.NOOP;
	private boolean exitOnlyPillarNodeException = true;
	private File routingFile;

	private TLongObjectMap<ItnNodePair> edgeIdToNodeMap;

	private TLongObjectMap<String> edgeNameMap;
	private TLongObjectMap<String> edgeRoadTypeMap;
	private TLongObjectMap<String> edgeRoadDirectionMap;

	public OsItnReader(GraphStorage storage) {
		this.graphStorage = storage;
		this.nodeAccess = graphStorage.getNodeAccess();

		osmNodeIdToInternalNodeMap = new GHLongIntBTree(200);
		osmNodeIdToNodeFlagsMap = new TLongLongHashMap(200, .5f, 0, 0);
		osmWayIdToRouteWeightMap = new TLongLongHashMap(200, .5f, 0, 0);
		pillarInfo = new PillarInfo(nodeAccess.is3D(),
				graphStorage.getDirectory());
	}

	@Override
	public void readGraph() throws IOException {
		if (encodingManager == null)
			throw new IllegalStateException("Encoding manager was not set.");

		if (routingFile == null)
			throw new IllegalStateException("No OS ITN file specified");

		if (!routingFile.exists())
			throw new IllegalStateException(
					"Your specified OS ITN file does not exist:"
							+ routingFile.getAbsolutePath());

		StopWatch sw1 = new StopWatch().start();
		preProcess(routingFile);
		sw1.stop();

		StopWatch sw2 = new StopWatch().start();
		writeOsm2Graph(routingFile);
		sw2.stop();

		logger.info("time(pass1): " + (int) sw1.getSeconds() + " pass2: "
				+ (int) sw2.getSeconds() + " total:"
				+ ((int) (sw1.getSeconds() + sw2.getSeconds())));
	}

	/**
	 * Preprocessing of OSM file to select nodes which are used for highways.
	 * This allows a more compact graph data structure.
	 */
	void preProcess(File osmFile) {
		try {
			preProcessDirOrFile(osmFile);
		} catch (Exception ex) {
			throw new RuntimeException("Problem while parsing file", ex);
		}
	}

	private void preProcessDirOrFile(File osmFile) throws XMLStreamException,
			IOException {
		if (osmFile.isDirectory()) {
			String absolutePath = osmFile.getAbsolutePath();
			String[] list = osmFile.list();
			for (String file : list) {
				File nextFile = new File(absolutePath + File.separator + file);
				preProcessDirOrFile(nextFile);
			}
		} else {
			preProcessSingleFile(osmFile);
		}
	}

	private void preProcessSingleFile(File osmFile) throws XMLStreamException,
			IOException {
		OsItnInputFile in = null;
		try {
			logger.error("preprocess:" + osmFile.getName());
			in = new OsItnInputFile(osmFile).setWorkerThreads(workerThreads)
					.open();
			preProcessSingleFile(in);
		} finally {
			Helper.close(in);
		}
	}

	private void preProcessSingleFile(OsItnInputFile in)
			throws XMLStreamException {
		long tmpWayCounter = 1;
		long tmpRelationCounter = 1;
		RoutingElement item;
		while ((item = in.getNext()) != null) {
			logger.info("OsItnReader.preProcess( " + item.getType() + " )");
			if (item.isType(OSMElement.WAY)) {
				final Way way = (Way) item;
				boolean valid = filterWay(way);
				if (valid) {
					TLongList wayNodes = way.getNodes();
					int s = wayNodes.size();
					for (int index = 0; index < s; index++) {
						prepareHighwayNode(wayNodes.get(index));
					}

					if (++tmpWayCounter % 500000 == 0) {
						logger.info(nf(tmpWayCounter)
								+ " (preprocess), osmIdMap:"
								+ nf(getNodeMap().getSize()) + " ("
								+ getNodeMap().getMemoryUsage() + "MB) "
								+ Helper.getMemInfo());
					}
				}
			}
			if (item.isType(OSMElement.RELATION)) {
				final Relation relation = (Relation) item;
				logger.warn("RELATION :" + item.getClass() +" TYPE:" + item.getTag("type") + " meta?"
						+ relation.isMetaRelation());
				if (!relation.isMetaRelation()
						&& relation.hasTag("type", "route"))
					prepareWaysWithRelationInfo(relation);

				if (relation.hasTag("type", "restriction"))
					prepareRestrictionRelation(relation);

				if (relation.hasTag("name"))
					prepareNameRelation(relation);
				
				if (relation.hasTag("highway"))
					prepareRoadTypeRelation(relation);
				
				if(relation.hasTag("oneway")) {
					prepareRoadDirectionRelation(relation);
				}

				if (++tmpRelationCounter % 50000 == 0) {
					logger.info(nf(tmpRelationCounter)
							+ " (preprocess), osmWayMap:"
							+ nf(getRelFlagsMap().size()) + " "
							+ Helper.getMemInfo());
				}

			}
		}
	}

	private void prepareRestrictionRelation(Relation relation) {
		OSITNTurnRelation turnRelation = createTurnRelation(relation);
		if (turnRelation != null) {
			getOsmIdStoreRequiredSet().add(
					((OSITNTurnRelation) turnRelation).getOsmIdFrom());
			getOsmIdStoreRequiredSet().add(
					((OSITNTurnRelation) turnRelation).getOsmIdTo());
		}
	}

	private void prepareNameRelation(Relation relation) {
		String name = relation.getTag("name");
		System.err.println("Rel Name:" + name);
		TLongObjectMap<String> edgeIdToNameMap = getEdgeNameMap();
		ArrayList<? extends RelationMember> members = relation.getMembers();
		for (RelationMember relationMember : members) {
			long wayId = relationMember.ref();
			String namePrefix = getWayName(wayId);
			System.err.println("ADDING:" + wayId);
			if(null!=namePrefix  && !namePrefix.contains(name)) {
				namePrefix = namePrefix + " (" + name +")";
				System.err.println("NAME:" + namePrefix);
				edgeIdToNameMap.put(wayId, namePrefix);
			} else {
				edgeIdToNameMap.put(wayId, name);
			}
		}
		System.err.println("Rel Name:" + name);
	}
	
	private void prepareRoadTypeRelation(Relation relation) {
		String highway = relation.getTag("highway");
		System.err.println("Rel highway:" + highway);
		TLongObjectMap<String> edgeIdToRoadTypeMap = getEdgeRoadTypeMap();
		ArrayList<? extends RelationMember> members = relation.getMembers();
		for (RelationMember relationMember : members) {
			long wayId = relationMember.ref();
			System.err.println("MEMBERS:" + wayId);
			edgeIdToRoadTypeMap.put(wayId, highway);
		}
	}
	
	private void prepareRoadDirectionRelation(Relation relation) {
		String highway = relation.getTag("oneway");
		System.err.println("Rel oneway:" + highway);
		TLongObjectMap<String> edgeIdToRoadDirectionMap = getEdgeRoadDirectionMap();
		ArrayList<? extends RelationMember> members = relation.getMembers();
		for (RelationMember relationMember : members) {
			long wayId = relationMember.ref();
			System.err.println("MEMBERS:" + wayId);
			edgeIdToRoadDirectionMap.put(wayId, highway);
		}
	}

	private TLongSet getOsmIdStoreRequiredSet() {
		return osmIdStoreRequiredSet;
	}

	private TIntLongMap getEdgeIdToOsmidMap() {
		logger.info("edgeIdTOOsmidmap:" + edgeIdToOsmIdMap);
		if (edgeIdToOsmIdMap == null)
			edgeIdToOsmIdMap = new TIntLongHashMap(getOsmIdStoreRequiredSet()
					.size());

		return edgeIdToOsmIdMap;
	}

	private TLongObjectMap<ItnNodePair> getNodeEdgeMap() {
		if (edgeIdToNodeMap == null)
			edgeIdToNodeMap = new TLongObjectHashMap(getOsmIdStoreRequiredSet()
					.size());

		return edgeIdToNodeMap;
	}

	private TLongObjectMap<String> getEdgeNameMap() {
		if (edgeNameMap == null)
			edgeNameMap = new TLongObjectHashMap(getOsmIdStoreRequiredSet()
					.size());

		return edgeNameMap;
	}
	
	private TLongObjectMap<String> getEdgeRoadTypeMap() {
		if (edgeRoadTypeMap == null)
			edgeRoadTypeMap = new TLongObjectHashMap(getOsmIdStoreRequiredSet()
					.size());

		return edgeRoadTypeMap;
	}
	
	private TLongObjectMap<String> getEdgeRoadDirectionMap() {
		if (edgeRoadDirectionMap == null)
			edgeRoadDirectionMap = new TLongObjectHashMap<String>(getOsmIdStoreRequiredSet()
					.size());

		return edgeRoadDirectionMap;
	}
	
	/**
	 * Filter ways but do not analyze properties wayNodes will be filled with
	 * participating node ids.
	 * <p/>
	 * 
	 * @return true the current xml entry is a way entry and has nodes
	 */
	boolean filterWay(Way way) {
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
	private void writeOsm2Graph(File osmFile) {
		int tmp = (int) Math.max(getNodeMap().getSize() / 50, 100);
		logger.error("creating graph. Found nodes (pillar+tower):"
				+ nf(getNodeMap().getSize()) + ", " + Helper.getMemInfo());
		graphStorage.create(tmp);

		ProcessData processData = new ProcessData();
		try {
			ProcessVisitor processVisitor = new ProcessVisitor() {
				@Override
				public void process(ProcessData processData, OsItnInputFile in) throws XMLStreamException {
					processStageOne(processData, in);
				}
			};
			logger.error("PROCESS NODES");
			writeOsm2GraphFromDirOrFile(osmFile, processData, processVisitor );
			processVisitor = new ProcessVisitor() {
				@Override
				public void process(ProcessData processData, OsItnInputFile in) throws XMLStreamException {
					processStageTwo(processData, in);
				}
			};
			logger.error("PROCESS WAY");
			writeOsm2GraphFromDirOrFile(osmFile, processData, processVisitor );
			processVisitor = new ProcessVisitor() {
				@Override
				public void process(ProcessData processData, OsItnInputFile in) throws XMLStreamException {
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
			throw new IllegalStateException("osm must not be empty. read "
					+ processData.counter + " lines and " + locations
					+ " locations");
	}

	private void writeOsm2GraphFromDirOrFile(File osmFile, ProcessData processData, ProcessVisitor processVisitor) throws XMLStreamException, IOException {
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

	private void writeOsm2GraphFromSingleFile(File osmFile, ProcessData processData, ProcessVisitor processVisitor) throws XMLStreamException, IOException {
		OsItnInputFile in=null;
		try {
			logger.error("PROCESS: "  + osmFile.getName());
			in = new OsItnInputFile(osmFile).setWorkerThreads(workerThreads)
					.open();
			processVisitor.process(processData, in);
			logger.info("storage nodes:" + graphStorage.getNodes());
		} finally {
			Helper.close(in);
		}
		
	}

	private void processStageOne(ProcessData processData, OsItnInputFile in)
			throws XMLStreamException {
		RoutingElement item;
		LongIntMap nodeFilter = getNodeMap();
		while ((item = in.getNext()) != null) {
			switch (item.getType()) {
			case OSMElement.NODE:
				long id = item.getId();
				logger.info("NODEITEMID:" + id);
				if (nodeFilter.get(id) != -1) {
					OSITNNode nodeItem = (OSITNNode) item;
					processNode(nodeItem);

					String strId = String.valueOf(id);
					addGradeNodesIfRequired(nodeItem, strId, nodeFilter);
				}
				break;

			}
			if (++processData.counter % 5000000 == 0) {
				logger.info(nf(processData.counter) + ", locs:" + nf(locations)
						+ " (" + skippedLocations + ") " + Helper.getMemInfo());
			}
		}
	}

	private void processStageTwo(ProcessData processData, OsItnInputFile in)
			throws XMLStreamException {
		RoutingElement item;
		LongIntMap nodeFilter = getNodeMap();
		while ((item = in.getNext()) != null) {
			switch (item.getType()) {
			case OSMElement.WAY:
				logger.info("WAY:" + item.getId() + ":" + processData.wayStart);
				if (processData.wayStart < 0) {
					logger.info(nf(processData.counter) + ", now parsing ways");
					processData.wayStart = processData.counter;
				}
				prepareWaysNodes(item, nodeFilter);
				processWay((Way) item);
				((OSITNWay)item).clearWayNodes();
				break;
			}
			if (++processData.counter % 5000000 == 0) {
				logger.info(nf(processData.counter) + ", locs:" + nf(locations)
						+ " (" + skippedLocations + ") " + Helper.getMemInfo());
			}
		}
	}

	private void prepareWaysNodes(RoutingElement item, LongIntMap nodeFilter) {
		List<OSITNNode> evaluateWayNodes = ((OSITNWay) item)
				.evaluateWayNodes();
		for (OSITNNode ositnNode : evaluateWayNodes) {
			nodeFilter.put(ositnNode.getId(), TOWER_NODE);
			processNode(ositnNode);
		}
	}

	private void processStageThree(ProcessData processData, OsItnInputFile in)
			throws XMLStreamException {
		RoutingElement item;
		while ((item = in.getNext()) != null) {
			switch (item.getType()) {
			case OSMElement.RELATION:
				if (processData.relationStart < 0) {
					logger.info(nf(processData.counter)
							+ ", now parsing relations");
					processData.relationStart = processData.counter;
				}
				processRelation((Relation) item);
				break;
			}
			if (++processData.counter % 5000000 == 0) {
				logger.info(nf(processData.counter) + ", locs:" + nf(locations)
						+ " (" + skippedLocations + ") " + Helper.getMemInfo());
			}
		}
	}

	private void addGradeNodesIfRequired(OSITNNode item, String idStr,
			LongIntMap nodeFilter) {
		String curId;
		for (int i = 1; i <= MAX_GRADE_SEPARATION; i++) {
			curId = i + idStr;
			long parseInt = Long.parseLong(curId);
			if (nodeFilter.get(parseInt) != -1) {
				OSITNNode gradeNode = item.gradeClone(parseInt);
				processNode((Node) gradeNode);
			}
		}
	}

	/**
	 * Process properties, encode flags and create edges for the way.
	 */
	void processWay(Way way) {
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
		logger.info("RELFLAGS:" + way.getId() + ":" + relationFlags);
		String wayName = getWayName(way.getId());
		if (null != wayName) {
			System.err.println("SETTING WAY NAME:" + wayName);
			way.setTag("name", wayName);
		}
		String wayType = getWayRoadType(way.getId());
		if (null != wayType && !way.hasTag("highway")) {
			way.setTag("highway", wayType);
		}
		
		String wayDirection = getWayRoadDirection(way.getId());
		if (null != wayDirection && !way.hasTag("oneway")) {
			way.setTag("oneway", wayDirection);
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

			logger.info("WAYID:" + wayOsmId + " first:" + firstItnNode
					+ " last:" + lastItnNode);
			getNodeEdgeMap().put(wayOsmId,
					new ItnNodePair(firstItnNode, lastItnNode));
			double firstLat = getTmpLatitude(first), firstLon = getTmpLongitude(first);
			double lastLat = getTmpLatitude(last), lastLon = getTmpLongitude(last);
			if (!Double.isNaN(firstLat) && !Double.isNaN(firstLon)
					&& !Double.isNaN(lastLat) && !Double.isNaN(lastLon)) {
				double estimatedDist = distCalc.calcDist(firstLat, firstLon,
						lastLat, lastLon);
				way.setTag("estimated_distance", estimatedDist);
				way.setTag("estimated_center", new GHPoint(
						(firstLat + lastLat) / 2, (firstLon + lastLon) / 2));
			}
		}

		long wayFlags = encodingManager.handleWayTags(way, includeWay,
				relationFlags);
		if (wayFlags == 0)
			return;
		logger.warn("Adding Relation to WAYS:" + wayFlags);
		List<EdgeIteratorState> createdEdges = new ArrayList<EdgeIteratorState>();
		// look for barriers along the way
		final int size = osmNodeIds.size();
		int lastBarrier = -1;
		for (int i = 0; i < size; i++) {
			long nodeId = osmNodeIds.get(i);
			long nodeFlags = getNodeFlagsMap().get(nodeId);
			// barrier was spotted and way is otherwise passable for that mode
			// of travel
			if (nodeFlags > 0) {
				if ((nodeFlags & wayFlags) > 0) {
					// remove barrier to avoid duplicates
					getNodeFlagsMap().put(nodeId, 0);

					// create shadow node copy for zero length edge
					long newNodeId = addBarrierNode(nodeId);
					if (i > 0) {
						// start at beginning of array if there was no previous
						// barrier
						if (lastBarrier < 0)
							lastBarrier = 0;

						// add way up to barrier shadow node
						long transfer[] = osmNodeIds.toArray(lastBarrier, i
								- lastBarrier + 1);
						transfer[transfer.length - 1] = newNodeId;
						TLongList partIds = new TLongArrayList(transfer);
						Collection<EdgeIteratorState> newWays = addOSMWay(
								partIds, wayFlags, wayOsmId);
						logger.warn("Way adds edges:" + wayOsmId
								+ newWays.size());
						createdEdges.addAll(newWays);

						// create zero length edge for barrier
						Collection<EdgeIteratorState> newBarriers = addBarrierEdge(
								newNodeId, nodeId, wayFlags, nodeFlags,
								wayOsmId);
						logger.warn("Way adds barrier edges:" + wayOsmId
								+ newBarriers.size());
						createdEdges.addAll(newBarriers);
					} else {
						// run edge from real first node to shadow node
						Collection<EdgeIteratorState> newBarriers = addBarrierEdge(
								nodeId, newNodeId, wayFlags, nodeFlags,
								wayOsmId);
						logger.warn("Way adds barrier edges:" + wayOsmId
								+ newBarriers.size());
						createdEdges.addAll(newBarriers);

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
				long transfer[] = osmNodeIds.toArray(lastBarrier, size
						- lastBarrier);
				TLongList partNodeIds = new TLongArrayList(transfer);
				Collection<EdgeIteratorState> newEdges = addOSMWay(partNodeIds,
						wayFlags, wayOsmId);
				logger.warn("Way adds edges:" + wayOsmId + newEdges.size());
				createdEdges.addAll(newEdges);
			}
		} else {
			// no barriers - simply add the whole way
			Collection<EdgeIteratorState> newEdges = addOSMWay(way.getNodes(),
					wayFlags, wayOsmId);
			logger.warn("Way adds edges:" + wayOsmId + ":" + newEdges.size());
			createdEdges.addAll(newEdges);
		}

		for (EdgeIteratorState edge : createdEdges) {
			encodingManager.applyWayTags(way, edge);
		}
	}

	private String getWayName(long id) {
		return getEdgeNameMap().remove(id);
	}
	
	private String getWayRoadType(long id) {
		return getEdgeRoadTypeMap().remove(id);
	}
	
	private String getWayRoadDirection(long id) {
		return getEdgeRoadDirectionMap().remove(id);
	}

	public void processRelation(Relation relation) throws XMLStreamException {
		if (relation.hasTag("type", "restriction")) {
			OSITNTurnRelation turnRelation = createTurnRelation(relation);
			if (turnRelation != null) {
				logger.info("Turn from:" + turnRelation.getOsmIdFrom() + " to:"
						+ turnRelation.getOsmIdTo() + " via:"
						+ turnRelation.getVia());
				ExtendedStorage extendedStorage = ((GraphHopperStorage) graphStorage)
						.getExtendedStorage();
				if (extendedStorage instanceof TurnCostStorage) {
					Collection<ITurnCostTableEntry> entries = encodingManager
							.analyzeTurnRelation(turnRelation, this);
					for (ITurnCostTableEntry entry : entries) {
						((TurnCostStorage) extendedStorage).setTurnCosts(
								entry.getVia(), entry.getEdgeFrom(),
								entry.getEdgeTo(), (int) entry.getFlags());
					}
				}
			}
		}
	}

	public long getOsmIdOfInternalEdge(int edgeId) {
		return getEdgeIdToOsmidMap().get(edgeId);
	}

	public long getInternalIdOfOsmEdge(int edgeId) {
		return getEdgeIdToOsmidMap().get(edgeId);
	}

	public int getInternalNodeIdOfOsmNode(long nodeOsmId) {
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

	private void processNode(Node node) {
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

	boolean addNode(Node node) {
		int nodeType = getNodeMap().get(node.getId());
		if (nodeType == EMPTY) {
			logger.warn("MISSING FROM MAP:" + node.getId());
			return false;
		}
		logger.warn("Adding Node:" + node.getId() + " as " + nodeType);
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
		// is there at least one tag interesting for the registed encoders?
		long handleRelationTags = encodingManager.handleRelationTags(relation,
				0);
		logger.warn("PREPARE ONE WAY:" + handleRelationTags);
		if (handleRelationTags == 0) {
			return;
		}	

		int size = relation.getMembers().size();
		for (int index = 0; index < size; index++) {
			RelationMember member = relation.getMembers().get(index);
			if (member.type() != OSMRelation.Member.WAY)
				continue;
			long osmId = member.ref();
			logger.warn("Adding WAY RELATION TO " + osmId);
			long oldRelationFlags = getRelFlagsMap().get(osmId);

			// Check if our new relation data is better comparated to the the
			// last one
			long newRelationFlags = encodingManager.handleRelationTags(
					relation, oldRelationFlags);
			logger.warn("APPLYING relation:" + oldRelationFlags + ":"
					+ newRelationFlags);
			if (oldRelationFlags != newRelationFlags) {
				getRelFlagsMap().put(osmId, newRelationFlags);
			}
		}
	}

	void prepareHighwayNode(long osmId) {
		int tmpIndex = getNodeMap().get(osmId);
		if (tmpIndex == EMPTY) {
			// osmId is used exactly once
			logger.info("OsItnReader.prepareHighwayNode(EMPTY->PILLAR):"
					+ osmId);
			getNodeMap().put(osmId, PILLAR_NODE);
		} else if (tmpIndex > EMPTY) {
			// mark node as tower node as it occured at least twice times
			logger.info("OsItnReader.prepareHighwayNode(PILLAR->TOWER):"
					+ osmId);
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
	 * This method creates from an OSM way (via the osm ids) one or more edges
	 * in the graph.
	 */
	Collection<EdgeIteratorState> addOSMWay(TLongList osmNodeIds, long flags,
			long wayOsmId) {
		PointList pointList = new PointList(osmNodeIds.size(),
				nodeAccess.is3D());
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
					if (!pointList.isEmpty()
							&& lastInBoundsPillarNode > -TOWER_NODE) {
						// transform the pillar node to a tower node
						tmpNode = lastInBoundsPillarNode;
						tmpNode = handlePillarNode(tmpNode, osmId, null, true);
						tmpNode = -tmpNode - 3;
						if (pointList.getSize() > 1 && firstNode >= 0) {
							// TOWER node
							newEdges.add(addEdge(firstNode, tmpNode, pointList,
									flags, wayOsmId));
							pointList.clear();
							pointList.add(nodeAccess, tmpNode);
						}
						firstNode = tmpNode;
						lastInBoundsPillarNode = -1;
					}
					continue;
				}

				if (tmpNode <= -TOWER_NODE && tmpNode >= TOWER_NODE)
					throw new AssertionError(
							"Mapped index not in correct bounds " + tmpNode
									+ ", " + osmId);

				if (tmpNode > -TOWER_NODE) {
					boolean convertToTowerNode = i == 0 || i == lastIndex;
					if (!convertToTowerNode) {
						lastInBoundsPillarNode = tmpNode;
					}

					// PILLAR node, but convert to towerNode if end-standing
					tmpNode = handlePillarNode(tmpNode, osmId, pointList,
							convertToTowerNode);
				}

				if (tmpNode < TOWER_NODE) {
					// TOWER node
					tmpNode = -tmpNode - 3;
					pointList.add(nodeAccess, tmpNode);
					if (firstNode >= 0) {
						newEdges.add(addEdge(firstNode, tmpNode, pointList,
								flags, wayOsmId));
						pointList.clear();
						pointList.add(nodeAccess, tmpNode);
					}
					firstNode = tmpNode;
				}
			}
		} catch (RuntimeException ex) {
			logger.error("Couldn't properly add edge with osm ids:"
					+ osmNodeIds, ex);
			if (exitOnlyPillarNodeException)
				throw ex;
		}
		return newEdges;
	}

	EdgeIteratorState addEdge(int fromIndex, int toIndex, PointList pointList,
			long flags, long wayOsmId) {
		// sanity checks
		if (fromIndex < 0 || toIndex < 0)
			throw new AssertionError(
					"to or from index is invalid for this edge " + fromIndex
							+ "->" + toIndex + ", points:" + pointList);
		if (pointList.getDimension() != nodeAccess.getDimension())
			throw new AssertionError(
					"Dimension does not match for pointList vs. nodeAccess "
							+ pointList.getDimension() + " <-> "
							+ nodeAccess.getDimension());

		double towerNodeDistance = 0;
		double prevLat = pointList.getLatitude(0);
		double prevLon = pointList.getLongitude(0);
		double prevEle = pointList.is3D() ? pointList.getElevation(0)
				: Double.NaN;
		double lat, lon, ele = Double.NaN;
		PointList pillarNodes = new PointList(pointList.getSize() - 2,
				nodeAccess.is3D());
		int nodes = pointList.getSize();
		for (int i = 1; i < nodes; i++) {
			// we could save some lines if we would use
			// pointList.calcDistance(distCalc);
			lat = pointList.getLatitude(i);
			lon = pointList.getLongitude(i);
			if (pointList.is3D()) {
				ele = pointList.getElevation(i);
				towerNodeDistance += distCalc3D.calcDist(prevLat, prevLon,
						prevEle, lat, lon, ele);
				prevEle = ele;
			} else
				towerNodeDistance += distCalc.calcDist(prevLat, prevLon, lat,
						lon);
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
		EdgeIteratorState iter = graphStorage.edge(fromIndex, toIndex)
				.setDistance(towerNodeDistance).setFlags(flags);
		if (nodes > 2) {
			if (doSimplify)
				simplifyAlgo.simplify(pillarNodes);

			iter.setWayGeometry(pillarNodes);
		}
		storeOSMWayID(iter.getEdge(), wayOsmId);
		return iter;
	}

	private void storeOSMWayID(int edgeId, long osmWayID) {
		logger.info("StoreOSMWayID: " + osmWayID + " for " + edgeId);
		if (getOsmIdStoreRequiredSet().contains(osmWayID)) {
			getEdgeIdToOsmidMap().put(edgeId, osmWayID);
		}
	}

	/**
	 * @return converted tower node
	 */
	private int handlePillarNode(int tmpNode, long osmId, PointList pointList,
			boolean convertToTowerNode) {
		logger.info("Converting Pillar " + osmId, " to pillar? "
				+ convertToTowerNode);
		tmpNode = tmpNode - 3;
		double lat = pillarInfo.getLatitude(tmpNode);
		double lon = pillarInfo.getLongitude(tmpNode);
		double ele = pillarInfo.getElevation(tmpNode);
		if (lat == Double.MAX_VALUE || lon == Double.MAX_VALUE)
			throw new RuntimeException(
					"Conversion pillarNode to towerNode already happended!? "
							+ "osmId:" + osmId + " pillarIndex:" + tmpNode);

		if (convertToTowerNode) {
			// convert pillarNode type to towerNode, make pillar values invalid
			pillarInfo.setNode(tmpNode, Double.MAX_VALUE, Double.MAX_VALUE,
					Double.MAX_VALUE);
			tmpNode = addTowerNode(osmId, lat, lon, ele);
		} else {
			if (pointList.is3D())
				pointList.add(lat, lon, ele);
			else
				pointList.add(lat, lon);
		}

		return (int) tmpNode;
	}

	protected void finishedReading() {
		printInfo("way");
		pillarInfo.clear();
		eleProvider.release();
		osmNodeIdToInternalNodeMap = null;
		osmNodeIdToNodeFlagsMap = null;
		osmWayIdToRouteWeightMap = null;
		osmIdStoreRequiredSet = null;
		edgeIdToOsmIdMap = null;
		edgeIdToNodeMap = null;
	}

	/**
	 * Create a copy of the barrier node
	 */
	long addBarrierNode(long nodeId) {
		OSMNode newNode;
		int graphIndex = getNodeMap().get(nodeId);
		if (graphIndex < TOWER_NODE) {
			graphIndex = -graphIndex - 3;
			newNode = new OSMNode(createNewNodeId(), nodeAccess, graphIndex);
		} else {
			graphIndex = graphIndex - 3;
			newNode = new OSMNode(createNewNodeId(), pillarInfo, graphIndex);
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
	Collection<EdgeIteratorState> addBarrierEdge(long fromId, long toId,
			long flags, long nodeFlags, long wayOsmId) {
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
		OSMTurnRelation.Type type = OSITNTurnRelation
				.getRestrictionType((String) relation.getTag("restriction"));
		if (type != OSMTurnRelation.Type.UNSUPPORTED) {
			long fromWayID = -1;
			long viaNodeID = -1;
			long toWayID = -1;

			for (RelationMember member : relation.getMembers()) {
				long ref = member.ref();
				logger.info("RELATIONMEMBERREF:" + ref);
				if (OSMElement.WAY == member.type()) {
					if ("from".equals(member.role())) {
						fromWayID = ref;
					} else if ("to".equals(member.role())) {
						toWayID = ref;
					}
				} else if (OSMElement.NODE == member.type()
						&& "via".equals(member.role())) {
					viaNodeID = ref;
				}
			}
			if (type != OSMTurnRelation.Type.UNSUPPORTED && fromWayID >= 0
					&& toWayID >= 0) {
				long foundViaNode = findViaNode(fromWayID, toWayID);
				OSITNTurnRelation osmTurnRelation = new OSITNTurnRelation(
						fromWayID, foundViaNode, toWayID, type);
				return osmTurnRelation;
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

	TLongLongHashMap getRelFlagsMap() {
		return osmWayIdToRouteWeightMap;
	}

	/**
	 * Specify the type of the path calculation (car, bike, ...).
	 */
	public OsItnReader setEncodingManager(EncodingManager acceptWay) {
		this.encodingManager = acceptWay;
		return this;
	}

	public OsItnReader setWayPointMaxDistance(double maxDist) {
		doSimplify = maxDist > 0;
		simplifyAlgo.setMaxDistance(maxDist);
		return this;
	}

	public OsItnReader setWorkerThreads(int numOfWorkers) {
		this.workerThreads = numOfWorkers;
		return this;
	}

	public OsItnReader setElevationProvider(ElevationProvider eleProvider) {
		if (eleProvider == null)
			throw new IllegalStateException(
					"Use the NOOP elevation provider instead of null or don't call setElevationProvider");

		if (!nodeAccess.is3D() && ElevationProvider.NOOP != eleProvider)
			throw new IllegalStateException(
					"Make sure you graph accepts 3D data");

		this.eleProvider = eleProvider;
		return this;
	}

	public OsItnReader setOSMFile(File osmFile) {
		this.routingFile = osmFile;
		return this;
	}

	private void printInfo(String str) {
		LoggerFactory.getLogger(getClass()).info(
				"finished " + str + " processing." + " nodes: "
						+ graphStorage.getNodes() + ", osmIdMap.size:"
						+ getNodeMap().getSize() + ", osmIdMap:"
						+ getNodeMap().getMemoryUsage() + "MB"
						+ ", nodeFlagsMap.size:" + getNodeFlagsMap().size()
						+ ", relFlagsMap.size:" + getRelFlagsMap().size() + " "
						+ Helper.getMemInfo());
	}

	@Override
	public String toString() {
		return getClass().getSimpleName();
	}

	public GraphStorage getGraphStorage() {
		return graphStorage;
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
			throw new IllegalArgumentException("No Matching Edges");
		}
		return -1;
	}
}
