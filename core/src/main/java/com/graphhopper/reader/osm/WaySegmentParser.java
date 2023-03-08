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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLStreamException;
import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.LongToIntFunction;
import java.util.function.Predicate;

import static com.graphhopper.reader.osm.OSMNodeData.*;
import static com.graphhopper.util.Helper.nf;
import static java.util.Collections.emptyMap;

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

    private final ElevationProvider eleProvider;

    private final Consumer<ReaderRelation> pass0RelationHook;
    private final Consumer<ReaderWay> pass1WayPreHook;
    private final Predicate<ReaderWay> wayFilter;
    private final Consumer<ReaderNode> pass2NodePreHook;
    private final Runnable pass2AfterNodesHook;
    private final Predicate<ReaderNode> splitNodeFilter;
    private final WayPreprocessor wayPreprocessor;
    private final Consumer<ReaderRelation> relationPreprocessor;
    private final RelationProcessor relationProcessor;
    private final EdgeHandler edgeHandler;
    private final int workerThreads;

    private final OSMNodeData nodeData;
    private Date timestamp;

    private WaySegmentParser(PointAccess nodeAccess, Directory directory, ElevationProvider eleProvider,
                             Consumer<ReaderRelation> pass0RelationHook,
                             Consumer<ReaderWay> pass1WayPreHook,
                             Predicate<ReaderWay> wayFilter,
                             Consumer<ReaderNode> pass2NodePreHook,
                             Runnable pass2AfterNodesHook,
                             Predicate<ReaderNode> splitNodeFilter, WayPreprocessor wayPreprocessor,
                             Consumer<ReaderRelation> relationPreprocessor, RelationProcessor relationProcessor,
                             EdgeHandler edgeHandler, int workerThreads) {
        this.eleProvider = eleProvider;
        this.pass0RelationHook = pass0RelationHook;
        this.pass1WayPreHook = pass1WayPreHook;
        this.wayFilter = wayFilter;
        this.pass2NodePreHook = pass2NodePreHook;
        this.pass2AfterNodesHook = pass2AfterNodesHook;
        this.splitNodeFilter = splitNodeFilter;
        this.wayPreprocessor = wayPreprocessor;
        this.relationPreprocessor = relationPreprocessor;
        this.relationProcessor = relationProcessor;
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
        LOGGER.info("pass0 - start");
        StopWatch sw0 = StopWatch.started();
        readOSM(osmFile, new Pass0Handler(), new SkipOptions(true, true, false));
        LOGGER.info("pass0 - finished, took: {}", sw0.stop().getTimeString());

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

        nodeData.release();

        LOGGER.info("Finished reading OSM file." +
                " pass0: " + (int) sw0.getSeconds() + "s, " +
                " pass1: " + (int) sw1.getSeconds() + "s, " +
                " pass2: " + (int) sw2.getSeconds() + "s, " +
                " total: " + (int) (sw0.getSeconds() + sw1.getSeconds() + sw2.getSeconds()) + "s");
    }

    /**
     * @return the timestamp read from the OSM file, or null if nothing was read yet
     */
    public Date getTimeStamp() {
        return timestamp;
    }

    private class Pass0Handler implements ReaderElementHandler {
        @Override
        public void handleRelation(ReaderRelation relation) {
            pass0RelationHook.accept(relation);
        }
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

            pass1WayPreHook.accept(way);

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

            pass2NodePreHook.accept(node);

            int nodeType = nodeData.addCoordinatesIfMapped(node.getId(), node.getLat(), node.getLon(), () -> eleProvider.getEle(node));
            if (nodeType == EMPTY_NODE)
                return;

            acceptedNodes++;

            // we keep node tags for barrier nodes
            if (splitNodeFilter.test(node)) {
                if (nodeType == JUNCTION_NODE) {
                    LOGGER.debug("OSM node {} at {},{} is a barrier node at a junction. The barrier will be ignored",
                            node.getId(), Helper.round(node.getLat(), 7), Helper.round(node.getLon(), 7));
                    ignoredSplitNodes++;
                } else
                    nodeData.setTags(node);
            }
        }

        @Override
        public void handleWay(ReaderWay way) {
            if (!handledWays) {
                pass2AfterNodesHook.run();
                LOGGER.info("pass2 - start reading OSM ways");
                handledWays = true;
            }
            if (handledRelations)
                throw new IllegalStateException("OSM way elements must be located before relation elements in OSM file");

            if (++wayCounter % 10_000_000 == 0)
                LOGGER.info("pass2 - processed ways: " + nf(wayCounter) + ", " + Helper.getMemInfo());

            if (!wayFilter.test(way))
                return;
            List<SegmentNode> segment = new ArrayList<>(way.getNodes().size());
            for (LongCursor node : way.getNodes())
                segment.add(new SegmentNode(node.value, nodeData.getId(node.value)));
            wayPreprocessor.preprocessWay(way, osmNodeId -> nodeData.getCoordinates(nodeData.getId(osmNodeId)));
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
                Map<String, Object> nodeTags = nodeData.getTags(node.osmNodeId);
                // so far we only consider node tags of split nodes, so if there are node tags we split the node
                if (!nodeTags.isEmpty()) {
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
                        handleSegment(segment, way, emptyMap());
                        segment = new ArrayList<>();
                    }
                    segment.add(barrierFrom);
                    segment.add(barrierTo);
                    handleSegment(segment, way, nodeTags);
                    segment = new ArrayList<>();
                    segment.add(barrierTo);

                    // ignore this barrier node from now. for example a barrier can be connecting two ways (appear in both
                    // ways) and we only want to add a barrier edge once (but we want to add one).
                    nodeData.removeTags(node.osmNodeId);
                } else {
                    segment.add(node);
                }
            }
            if (segment.size() > 1)
                handleSegment(segment, way, emptyMap());
        }

        void handleSegment(List<SegmentNode> segment, ReaderWay way, Map<String, Object> nodeTags) {
            final PointList pointList = new PointList(segment.size(), nodeData.is3D());
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
            LOGGER.info("pass2 - finished, processed ways: {}, way nodes: {}, with tags: {}, ignored barriers at junctions: {}",
                    nf(wayCounter), nf(acceptedNodes), nf(nodeData.getTaggedNodeCount()), nf(ignoredSplitNodes));
        }

        public int getInternalNodeIdOfOSMNode(long nodeOsmId) {
            int id = nodeData.getId(nodeOsmId);
            if (isTowerNode(id))
                return -id - 3;
            return -1;
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
        private Consumer<ReaderRelation> pass0RelationHook = rel -> {
        };
        private Consumer<ReaderWay> pass1WayPreHook = way -> {
        };
        private Predicate<ReaderWay> wayFilter = way -> true;
        private Consumer<ReaderNode> pass2NodePreHook = node -> {
        };
        private Runnable pass2AfterNodesHook = () -> {
        };
        private Predicate<ReaderNode> splitNodeFilter = node -> false;
        private WayPreprocessor wayPreprocessor = (way, supplier) -> {
        };
        private Consumer<ReaderRelation> relationPreprocessor = relation -> {
        };
        private RelationProcessor relationProcessor = (relation, map) -> {
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

        public Builder setPass0RelationHook(Consumer<ReaderRelation> pass0RelationHook) {
            this.pass0RelationHook = pass0RelationHook;
            return this;
        }

        public Builder setPass1WayPreHook(Consumer<ReaderWay> pass1WayPreHook) {
            this.pass1WayPreHook = pass1WayPreHook;
            return this;
        }

        /**
         * @param wayFilter return true for OSM ways that should be considered and false otherwise
         */
        public Builder setWayFilter(Predicate<ReaderWay> wayFilter) {
            this.wayFilter = wayFilter;
            return this;
        }

        public Builder setPass2NodePreHook(Consumer<ReaderNode> pass2NodePreHook) {
            this.pass2NodePreHook = pass2NodePreHook;
            return this;
        }

        public Builder setPass2AfterNodesHook(Runnable pass2AfterNodesHook) {
            this.pass2AfterNodesHook = pass2AfterNodesHook;
            return this;
        }

        /**
         * @param splitNodeFilter return true if the given OSM node should be duplicated to create an artificial edge
         */
        public Builder setSplitNodeFilter(Predicate<ReaderNode> splitNodeFilter) {
            this.splitNodeFilter = splitNodeFilter;
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
                    nodeAccess, directory, elevationProvider, pass0RelationHook, pass1WayPreHook, wayFilter, pass2NodePreHook, pass2AfterNodesHook, splitNodeFilter, wayPreprocessor, relationPreprocessor, relationProcessor,
                    edgeHandler, workerThreads
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
        void handleEdge(int from, int to, PointList pointList, ReaderWay way, Map<String, Object> nodeTags);
    }

    public interface RelationProcessor {
        void processRelation(ReaderRelation relation, LongToIntFunction getNodeIdForOSMNodeId);
    }

    public interface WayPreprocessor {
        /**
         * @param coordinateSupplier maps an OSM node ID (as it can be obtained by way.getNodes()) to the coordinates
         *                           of this node. If elevation is disabled it will be NaN. Returns null if no such OSM
         *                           node exists.
         */
        void preprocessWay(ReaderWay way, CoordinateSupplier coordinateSupplier);
    }

    public interface CoordinateSupplier {
        GHPoint3D getCoordinate(long osmNodeId);
    }
}
