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

import com.carrotsearch.hppc.IntArrayList;
import com.carrotsearch.hppc.cursors.LongCursor;
import com.graphhopper.reader.ReaderElement;
import com.graphhopper.reader.ReaderNode;
import com.graphhopper.reader.ReaderRelation;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.reader.dem.ElevationProvider;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PointAccess;
import com.graphhopper.util.PointList;
import com.graphhopper.util.StopWatch;
import com.graphhopper.util.shapes.GHPoint3D;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.impl.PackedCoordinateSequence;
import org.locationtech.jts.geom.prep.PreparedPolygon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLStreamException;
import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.IntUnaryOperator;
import java.util.function.LongToIntFunction;
import java.util.function.Predicate;

import static com.graphhopper.reader.osm.OSMNodeData.*;
import static com.graphhopper.util.Helper.nf;
import static java.util.Collections.emptyList;

/**
 * This class parses a given OSM file and splits OSM ways into 'segments' at all intersections (or 'junctions').
 * Intersections can be either crossings of different OSM ways or duplicate appearances of the same node within one
 * way (when the way contains a loop). Furthermore, this class creates artificial segments at certain nodes. This class
 * also provides several hooks/callbacks to customize the processing of nodes, ways and relations.
 * <p>
 * The OSM file is read twice. The first time we ignore OSM nodes and only determine the OSM node IDs at which accepted
 * ways are intersecting. During the second pass we split the OSM ways at intersections, introduce the artificial
 * segments and pass the way information along with the corresponding nodes to a given callback.
 * <p>
 * We assume a strict order of the OSM file: nodes, ways, then relations.
 * <p>
 * The main difficulty is that the OSM ID range is very large (64bit integers) and to be able to provide the full
 * node information for each segment we have to efficiently store the node data temporarily. This is addressed by
 * {@link OSMNodeData}.
 */
public class WaySegmentParser {
    private static final Logger LOGGER = LoggerFactory.getLogger(WaySegmentParser.class);
    private static final Set<String> INCLUDE_IF_NODE_TAGS = new HashSet<>(Arrays.asList("barrier", "highway", "railway", "crossing", "ford"));

    private final ElevationProvider eleProvider;
    private final WayProvider wayProvider;
    private final Predicate<ReaderWay> wayFilter;
    private final Predicate<ReaderNode> splitNodeFilter;
    private final WayPreprocessor wayPreprocessor;
    private final Consumer<ReaderRelation> relationPreprocessor;
    private final RelationProcessor relationProcessor;
    private final RelationPostProcessor relationPostProcessor;
    private final EdgeHandler edgeHandler;
    private final int workerThreads;

    private final OSMNodeData nodeData;
    private Date timestamp;

    private WaySegmentParser(PointAccess nodeAccess, Directory directory, ElevationProvider eleProvider,
                             WayProvider wayProvider, Predicate<ReaderWay> wayFilter,
                             Predicate<ReaderNode> splitNodeFilter, WayPreprocessor wayPreprocessor,
                             Consumer<ReaderRelation> relationPreprocessor, RelationProcessor relationProcessor,
                             RelationPostProcessor relationPostProcessor, EdgeHandler edgeHandler, int workerThreads) {
        this.eleProvider = eleProvider;
        this.wayProvider = wayProvider;
        this.wayFilter = wayFilter;
        this.splitNodeFilter = splitNodeFilter;
        this.wayPreprocessor = wayPreprocessor;
        this.relationPreprocessor = relationPreprocessor;
        this.relationProcessor = relationProcessor;
        this.relationPostProcessor = relationPostProcessor;
        this.edgeHandler = edgeHandler;
        this.workerThreads = workerThreads;

        this.nodeData = new OSMNodeData(nodeAccess, directory);
    }

    /**
     * @param osmFile the OSM file to parse, supported formats include .osm.xml, .osm.gz and .xml.pbf
     */
    public void readOSM(File osmFile) {
        if (nodeData.getNodeCount() > 0)
            throw new IllegalStateException("You can only run way segment parser once");

        LOGGER.info("Start reading OSM file: '" + osmFile + "'");
        LOGGER.info("pass1 - start");
        StopWatch sw1 = StopWatch.started();
        readOSM(osmFile, new Pass1Handler(), new SkipOptions(true, false, false));
        LOGGER.info("pass1 - finished, took: {}", sw1.stop().getTimeString());

        long nodes = nodeData.getNodeCount();

        LOGGER.info("Creating graph. Node count (pillar+tower): " + nodes + ", " + Helper.getMemInfo());

        LOGGER.info("pass2 - start");
        StopWatch sw2 = new StopWatch().start();
        readOSM(osmFile, new Pass2Handler(), SkipOptions.none());
        LOGGER.info("pass2 - finished, took: {}", sw2.stop().getTimeString());

        LOGGER.info("pass3 - start");
        StopWatch sw3 = new StopWatch().start();
        readOSM(osmFile, new Pass3Handler(), SkipOptions.none()); // todonow: skip something?
        LOGGER.info("pass3 - finished, took: {}", sw3.stop().getTimeString());

        nodeData.release();

        LOGGER.info("Finished reading OSM file." +
                " pass1: " + (int) sw1.getSeconds() + "s, " +
                " pass2: " + (int) sw2.getSeconds() + "s, " +
                " pass3: " + (int) sw3.getSeconds() + "s, " +
                " total: " + (int) (sw1.getSeconds() + sw2.getSeconds() + sw3.getSeconds()) + "s");
    }

    /**
     * @return the timestamp read from the OSM file, or null if nothing was read yet
     */
    public Date getTimeStamp() {
        return timestamp;
    }

    private class Pass1Handler implements ReaderElementHandler {
        private boolean handledWays;
        private boolean handledRelations;
        private long wayCounter = 0;
        private long acceptedWays = 0;
        private long relationsCounter = 0;

        @Override
        public void handleWay(ReaderWay way) {
            if (!handledWays) {
                LOGGER.info("pass1 - start reading OSM ways");
                handledWays = true;
            }
            if (handledRelations)
                throw new IllegalStateException("OSM way elements must be located before relation elements in OSM file");

            if (++wayCounter % 10_000_000 == 0)
                LOGGER.info("pass1 - processed ways: " + nf(wayCounter) + ", accepted ways: " + nf(acceptedWays) +
                        ", way nodes: " + nf(nodeData.getNodeCount()) + ", " + Helper.getMemInfo());

            if (!wayFilter.test(way))
                return;
            acceptedWays++;

            for (LongCursor node : way.getNodes()) {
                final boolean isEnd = node.index == 0 || node.index == way.getNodes().size() - 1;
                final long osmId = node.value;
                nodeData.setOrUpdateNodeType(osmId,
                        isEnd ? END_NODE : INTERMEDIATE_NODE,
                        // connection nodes are those where (only) two OSM ways are connected at their ends
                        prev -> prev == END_NODE && isEnd ? CONNECTION_NODE : JUNCTION_NODE);
            }
        }

        @Override
        public void handleRelation(ReaderRelation relation) {
            if (!handledRelations) {
                LOGGER.info("pass1 - start reading OSM relations");
                handledRelations = true;
            }

            if (++relationsCounter % 1_000_000 == 0)
                LOGGER.info("pass1 - processed relations: " + nf(relationsCounter) + ", " + Helper.getMemInfo());

            relationPreprocessor.accept(relation);
        }

        @Override
        public void handleFileHeader(OSMFileHeader fileHeader) throws ParseException {
            timestamp = Helper.createFormatter().parse(fileHeader.getTag("timestamp"));
        }

        @Override
        public void onFinish() {
            LOGGER.info("pass1 - finished, processed ways: " + nf(wayCounter) + ", accepted ways: " +
                    nf(acceptedWays) + ", way nodes: " + nf(nodeData.getNodeCount()) + ", relations: " +
                    nf(relationsCounter) + ", " + Helper.getMemInfo());
        }
    }

    private class Pass2Handler implements ReaderElementHandler {
        private boolean handledNodes;
        private boolean handledWays;
        private boolean handledRelations;
        private long nodeCounter = 0;
        private long acceptedNodes = 0;
        private long ignoredSplitNodes = 0;
        private long wayCounter = 0;

        @Override
        public void handleNode(ReaderNode node) {
            if (!handledNodes) {
                LOGGER.info("pass2 - start reading OSM nodes");
                handledNodes = true;
            }
            if (handledWays)
                throw new IllegalStateException("OSM node elements must be located before way elements in OSM file");
            if (handledRelations)
                throw new IllegalStateException("OSM node elements must be located before relation elements in OSM file");

            if (++nodeCounter % 10_000_000 == 0)
                LOGGER.info("pass2 - processed nodes: " + nf(nodeCounter) + ", accepted nodes: " + nf(acceptedNodes) +
                        ", " + Helper.getMemInfo());

            int nodeType = nodeData.addCoordinatesIfMapped(node.getId(), node.getLat(), node.getLon(), () -> eleProvider.getEle(node));
            if (nodeType == EMPTY_NODE)
                return;

            acceptedNodes++;

            // remember which nodes we want to split
            if (splitNodeFilter.test(node)) {
                if (nodeType == JUNCTION_NODE) {
                    LOGGER.debug("OSM node {} at {},{} is a barrier node at a junction. The barrier will be ignored",
                            node.getId(), Helper.round(node.getLat(), 7), Helper.round(node.getLon(), 7));
                    ignoredSplitNodes++;
                } else
                    nodeData.setSplitNode(node.getId());
            }

            // store node tags if at least one important tag is included and make this available for the edge handler
            for (Map.Entry<String, Object> e : node.getTags().entrySet()) {
                if (INCLUDE_IF_NODE_TAGS.contains(e.getKey())) {
                    node.removeTag("created_by");
                    node.removeTag("source");
                    node.removeTag("note");
                    node.removeTag("fixme");
                    nodeData.setTags(node);
                    break;
                }
            }
        }

        @Override
        public void handleWay(ReaderWay way) {
            if (!handledWays) {
                LOGGER.info("pass2 - start reading OSM ways");
                handledWays = true;
            }
            if (handledRelations)
                throw new IllegalStateException("OSM way elements must be located before relation elements in OSM file");

            if (++wayCounter % 10_000_000 == 0)
                LOGGER.info("pass2 - processed ways: " + nf(wayCounter) + ", " + Helper.getMemInfo());

            wayProvider.providerWay(way, nodeData::setOrUpdateNodeType);
            if (!wayFilter.test(way))
                return;
            List<SegmentNode> segment = new ArrayList<>(way.getNodes().size());
            for (LongCursor node : way.getNodes())
                segment.add(new SegmentNode(node.value, nodeData.getId(node.value), nodeData.getTags(node.value)));
            wayPreprocessor.preprocessWay(way, nodeData::getId, nodeData::getCoordinates);
            splitWayAtJunctionsAndEmptySections(segment, way);
        }

        private void splitWayAtJunctionsAndEmptySections(List<SegmentNode> fullSegment, ReaderWay way) {
            List<SegmentNode> segment = new ArrayList<>();
            for (SegmentNode node : fullSegment) {
                if (!isNodeId(node.id)) {
                    // this node exists in ways, but not in nodes. we ignore it, but we split the way when we encounter
                    // such a missing node. for example an OSM way might lead out of an area where nodes are available and
                    // back into it. we do not want to connect the exit/entry points using a straight line. this usually
                    // should only happen for OSM extracts
                    if (segment.size() > 1) {
                        splitLoopSegments(segment, way);
                        segment = new ArrayList<>();
                    }
                } else if (isTowerNode(node.id)) {
                    if (!segment.isEmpty()) {
                        segment.add(node);
                        splitLoopSegments(segment, way);
                        segment = new ArrayList<>();
                    }
                    segment.add(node);
                } else {
                    segment.add(node);
                }
            }
            // the last segment might end at the end of the way
            if (segment.size() > 1)
                splitLoopSegments(segment, way);
        }

        private void splitLoopSegments(List<SegmentNode> segment, ReaderWay way) {
            if (segment.size() < 2)
                throw new IllegalStateException("Segment size must be >= 2, but was: " + segment.size());

            boolean isLoop = segment.get(0).osmNodeId == segment.get(segment.size() - 1).osmNodeId;
            if (segment.size() == 2 && isLoop) {
                LOGGER.warn("Loop in OSM way: {}, will be ignored, duplicate node: {}", way.getId(), segment.get(0).osmNodeId);
            } else if (isLoop) {
                // split into two segments
                splitSegmentAtSplitNodes(segment.subList(0, segment.size() - 1), way);
                splitSegmentAtSplitNodes(segment.subList(segment.size() - 2, segment.size()), way);
            } else {
                splitSegmentAtSplitNodes(segment, way);
            }
        }

        private void splitSegmentAtSplitNodes(List<SegmentNode> parentSegment, ReaderWay way) {
            List<SegmentNode> segment = new ArrayList<>();
            for (int i = 0; i < parentSegment.size(); i++) {
                SegmentNode node = parentSegment.get(i);
                if (nodeData.isSplitNode(node.osmNodeId)) {
                    // do not split this node again. for example a barrier can be connecting two ways (appear in both
                    // ways) and we only want to add a barrier edge once (but we want to add one).
                    nodeData.unsetSplitNode(node.osmNodeId);

                    // this node is a barrier. we will copy it and add an extra edge
                    SegmentNode barrierFrom = node;
                    SegmentNode barrierTo = nodeData.addCopyOfNode(node);
                    if (i == parentSegment.size() - 1) {
                        // make sure the barrier node is always on the inside of the segment
                        SegmentNode tmp = barrierFrom;
                        barrierFrom = barrierTo;
                        barrierTo = tmp;
                    }
                    if (!segment.isEmpty()) {
                        segment.add(barrierFrom);
                        handleSegment(segment, way);
                        segment = new ArrayList<>();
                    }

                    // mark barrier edge
                    way.setTag("gh:barrier_edge", true);
                    segment.add(barrierFrom);
                    segment.add(barrierTo);
                    handleSegment(segment, way);
                    way.removeTag("gh:barrier_edge");

                    segment = new ArrayList<>();
                    segment.add(barrierTo);
                } else {
                    segment.add(node);
                }
            }
            if (segment.size() > 1)
                handleSegment(segment, way);
        }

        void handleSegment(List<SegmentNode> segment, ReaderWay way) {
            final PointList pointList = new PointList(segment.size(), nodeData.is3D());
            final List<Map<String, Object>> nodeTags = new ArrayList<>(segment.size());
            int from = -1;
            int to = -1;
            for (int i = 0; i < segment.size(); i++) {
                SegmentNode node = segment.get(i);
                int id = node.id;
                if (!isNodeId(id))
                    throw new IllegalStateException("Invalid id for node: " + node.osmNodeId + " when handling segment " + segment + " for way: " + way.getId());
                if (isPillarNode(id) && (i == 0 || i == segment.size() - 1)) {
                    id = nodeData.convertPillarToTowerNode(id, node.osmNodeId);
                    node.id = id;
                }

                if (i == 0)
                    from = nodeData.idToTowerNode(id);
                else if (i == segment.size() - 1)
                    to = nodeData.idToTowerNode(id);
                else if (isTowerNode(id))
                    throw new IllegalStateException("Tower nodes should only appear at the end of segments, way: " + way.getId());
                nodeData.addCoordinatesToPointList(id, pointList);
                nodeTags.add(node.tags);
            }
            if (from < 0 || to < 0)
                throw new IllegalStateException("The first and last nodes of a segment must be tower nodes, way: " + way.getId());
            edgeHandler.handleEdge(from, to, pointList, way, nodeTags);
        }

        @Override
        public void handleRelation(ReaderRelation relation) {
            if (!handledRelations) {
                LOGGER.info("pass2 - start reading OSM relations");
                handledRelations = true;
            }

            relationProcessor.processRelation(relation, this::getInternalNodeIdOfOSMNode);
        }

        @Override
        public void onFinish() {
            LOGGER.info("pass2 - finished, processed ways: {}, way nodes: {}, nodes with tags: {}, node tag capacity: {}, ignored barriers at junctions: {}",
                    nf(wayCounter), nf(acceptedNodes), nf(nodeData.getTaggedNodeCount()), nf(nodeData.getNodeTagCapacity()), nf(ignoredSplitNodes));
        }

        public int getInternalNodeIdOfOSMNode(long nodeOsmId) {
            int id = nodeData.getId(nodeOsmId);
            if (isTowerNode(id))
                return -id - 3;
            return -1;
        }
    }


    private class Pass3Handler implements ReaderElementHandler {
        int nodeCounter = -1;
        private long acceptedNodes = 0;

        @Override
        public void handleNode(ReaderNode node) {
            if (++nodeCounter % 10_000_000 == 0)
                LOGGER.info("pass3 - processed nodes: " + nf(nodeCounter) + ", accepted nodes: " + nf(acceptedNodes) +
                        ", " + Helper.getMemInfo());

            // PART 3 of 4 for multipolygon creation (move to OSMReader via postProcessNode!? but seems unnecessary)
            // nodePostProcessor.postProcessNode(node);
            if (isIntermediateNode(nodeData.getId(node.getId()))) {
                acceptedNodes++;
                nodeData.addPillarNode(node.getId(), node.getLat(), node.getLon(), eleProvider.getEle(node));
            }
        }

        @Override
        public void handleRelation(ReaderRelation relation) {
            relationPostProcessor.postProcessRelation(relation, nodeData::getId, nodeData::getCoordinates);
        }

        @Override
        public void onFinish() {
            LOGGER.info("pass3 - finished, way nodes: {}, with tags: {}",
                    nf(acceptedNodes), nf(nodeData.getTaggedNodeCount()));
        }
    }

    private void readOSM(File file, ReaderElementHandler handler, SkipOptions skipOptions) {
        try (OSMInput osmInput = openOsmInputFile(file, skipOptions)) {
            ReaderElement elem;
            while ((elem = osmInput.getNext()) != null)
                handler.handleElement(elem);
            handler.onFinish();
            if (osmInput.getUnprocessedElements() > 0)
                throw new IllegalStateException("There were some remaining elements in the reader queue " + osmInput.getUnprocessedElements());
        } catch (Exception e) {
            throw new RuntimeException("Could not parse OSM file: " + file.getAbsolutePath(), e);
        }
    }

    protected OSMInput openOsmInputFile(File osmFile, SkipOptions skipOptions) throws XMLStreamException, IOException {
        return new OSMInputFile(osmFile).setWorkerThreads(workerThreads).setSkipOptions(skipOptions).open();
    }

    public static class Builder {
        private final PointAccess nodeAccess;
        private Directory directory = new RAMDirectory();
        private ElevationProvider elevationProvider = ElevationProvider.NOOP;
        private Predicate<ReaderWay> wayFilter = way -> true;
        private Predicate<ReaderNode> splitNodeFilter = node -> false;
        private WayProvider wayProvider = (way, rememberNode) -> {
        };
        private WayPreprocessor wayPreprocessor = (way, map, coordinateSupplier) -> {
        };
        private Consumer<ReaderRelation> relationPreprocessor = (relation) -> {
        };
        private RelationProcessor relationProcessor = (relation, map) -> {
        };
        private RelationPostProcessor relationPostProcessor = (relation, map, coordinateSupplier) -> {
        };
        private EdgeHandler edgeHandler = (from, to, pointList, way, nodeTags) ->
                System.out.println("edge " + from + "->" + to + " (" + pointList.size() + " points)");
        private int workerThreads = 2;

        /**
         * @param nodeAccess used to store tower node coordinates while parsing the ways
         */
        public Builder(PointAccess nodeAccess) {
            // instead of requiring a PointAccess here we could also just use some temporary in-memory storage by default
            this.nodeAccess = nodeAccess;
        }

        /**
         * @param directory the directory to be used to store temporary data
         */
        public Builder setDirectory(Directory directory) {
            this.directory = directory;
            return this;
        }

        /**
         * @param elevationProvider used to determine the elevation of an OSM node
         */
        public Builder setElevationProvider(ElevationProvider elevationProvider) {
            this.elevationProvider = elevationProvider;
            return this;
        }

        /**
         * @param wayFilter return true for OSM ways that should be considered and false otherwise
         */
        public Builder setWayFilter(Predicate<ReaderWay> wayFilter) {
            this.wayFilter = wayFilter;
            return this;
        }

        /**
         * @param splitNodeFilter return true if the given OSM node should be duplicated to create an artificial edge
         */
        public Builder setSplitNodeFilter(Predicate<ReaderNode> splitNodeFilter) {
            this.splitNodeFilter = splitNodeFilter;
            return this;
        }

        public Builder setWayProvider(WayProvider wayProvider) {
            this.wayProvider = wayProvider;
            return this;
        }

        /**
         * @param wayPreprocessor callback function that is called for each accepted OSM way during the second pass
         */
        public Builder setWayPreprocessor(WayPreprocessor wayPreprocessor) {
            this.wayPreprocessor = wayPreprocessor;
            return this;
        }

        /**
         * @param relationPreprocessor callback function that receives OSM relations during the first pass
         */
        public Builder setRelationPreprocessor(Consumer<ReaderRelation> relationPreprocessor) {
            this.relationPreprocessor = relationPreprocessor;
            return this;
        }

        /**
         * @param relationProcessor callback function that receives OSM relations during the second pass
         */
        public Builder setRelationProcessor(RelationProcessor relationProcessor) {
            this.relationProcessor = relationProcessor;
            return this;
        }

        public Builder setRelationPostProcessor(RelationPostProcessor relationPostProcessor) {
            this.relationPostProcessor = relationPostProcessor;
            return this;
        }

        /**
         * @param edgeHandler callback function that is called for each edge (way segment)
         */
        public Builder setEdgeHandler(EdgeHandler edgeHandler) {
            this.edgeHandler = edgeHandler;
            return this;
        }

        /**
         * @param workerThreads the number of threads used for the low level reading of the OSM file
         */
        public Builder setWorkerThreads(int workerThreads) {
            this.workerThreads = workerThreads;
            return this;
        }

        public WaySegmentParser build() {
            return new WaySegmentParser(
                    nodeAccess, directory, elevationProvider, wayProvider, wayFilter, splitNodeFilter, wayPreprocessor,
                    relationPreprocessor, relationProcessor, relationPostProcessor, edgeHandler, workerThreads
            );
        }
    }

    private interface ReaderElementHandler {
        default void handleElement(ReaderElement elem) throws ParseException {
            switch (elem.getType()) {
                case NODE:
                    handleNode((ReaderNode) elem);
                    break;
                case WAY:
                    handleWay((ReaderWay) elem);
                    break;
                case RELATION:
                    handleRelation((ReaderRelation) elem);
                    break;
                case FILEHEADER:
                    handleFileHeader((OSMFileHeader) elem);
                    break;
                default:
                    throw new IllegalStateException("Unknown reader element type: " + elem.getType());
            }
        }

        default void handleNode(ReaderNode node) {
        }

        default void handleWay(ReaderWay way) {
        }

        default void handleRelation(ReaderRelation relation) {
        }

        default void handleFileHeader(OSMFileHeader fileHeader) throws ParseException {
        }

        default void onFinish() {
        }
    }

    public interface EdgeHandler {
        void handleEdge(int from, int to, PointList pointList, ReaderWay way, List<Map<String, Object>> nodeTags);
    }

    // todonow: use this instead of consumer?
//    public interface RelationPreprocessor {
//        void processRelation(ReaderRelation relation);
//    }

    public interface RelationProcessor {
        void processRelation(ReaderRelation relation, LongToIntFunction getNodeIdForOSMNodeId);
    }

    public interface RelationPostProcessor {
        void postProcessRelation(ReaderRelation relation, LongToIntFunction getInternalIdForOSMNodeId, CoordinateSupplier coordinateSupplier);
    }

    public interface WayProvider {
        void providerWay(ReaderWay readerWay, RememberNode rememberNode);
    }

    public interface RememberNode {
        void setOrUpdateNodeType(long osmNodeId, int newNodeType, IntUnaryOperator nodeTypeUpdate);
    }

    public interface WayPreprocessor {
        /**
         * @param coordinateSupplier maps an OSM node ID (as it can be obtained by way.getNodes()) to the coordinates
         *                           of this node. If elevation is disabled it will be NaN. Returns null if no such OSM
         *                           node exists.
         */
        void preprocessWay(ReaderWay way, LongToIntFunction getInternalIdForOSMNodeId, CoordinateSupplier coordinateSupplier);
    }

    public interface CoordinateSupplier {
        /**
         * Maps an internal ID (tower or pillar) to the coordinates of this node.
         * If elevation is disabled it will be NaN. Returns null if node is not tower or pillar.
         */
        GHPoint3D getCoordinate(int id);
    }

    static final GeometryFactory FACTORY = new GeometryFactory();
    private static final ReaderWay EMPTY_WAY = new ReaderWay(-1L);

    public static void createEdgesFromArea(IntArrayList ids, PointList outerPointList, PreparedPolygon prepPolygon,
                                           WaySegmentParser.EdgeHandler edgeHandler) {
        // skip the last (==first)
        for (int fromIdx = 0; fromIdx < ids.size() - 1; fromIdx++) {
            int fromNodeId = ids.get(fromIdx);
            // TODO NOW can we count the number of involved edges somehow? because areas are often mapped so that
            //  the start and end are only "unnecessary" tower nodes -> CONNECTION_NODE
            if (!isTowerNode(fromNodeId))
                continue;
            fromNodeId = idToTowerNode(fromNodeId);

            // we can skip the direct neighbor and the last (==first)
            for (int toIdx = fromIdx + 2; toIdx < ids.size() - 1; toIdx++) {
                int toNodeId = ids.get(toIdx);
                if (!isTowerNode(toNodeId))
                    continue;

                Coordinate[] lineCoordinates = new Coordinate[]{new Coordinate(outerPointList.getLon(fromIdx), outerPointList.getLat(fromIdx)),
                        new Coordinate(outerPointList.getLon(toIdx), outerPointList.getLat(toIdx))};
                if (prepPolygon.contains(FACTORY.createLineString(new PackedCoordinateSequence.Double(lineCoordinates, 2)))) {
                    toNodeId = idToTowerNode(toNodeId);
                    PointList pointList = new PointList(2, false);
                    pointList.add(outerPointList.getLat(fromIdx), outerPointList.getLon(fromIdx));
                    pointList.add(outerPointList.getLat(toIdx), outerPointList.getLon(toIdx));
                    edgeHandler.handleEdge(fromNodeId, toNodeId, pointList, EMPTY_WAY, emptyList());
                }
                // TODO NOW often the mapper created a connection for the router over the area now this can make a problem
                //  if they were already created (came before in OSM) we could skip them here via looping throught the graph
                //  but what about data after this -> somehow store these area ways and exclude these edges that are entirely contained in an area!?
//                        System.out.println("new:");
//                        System.out.println("https://www.openstreetmap.org/node/" + fromNode.osmNodeId);
//                        System.out.println("https://www.openstreetmap.org/node/" + toNode.osmNodeId);

            }
        }
    }
}
