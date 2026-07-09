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

package com.graphhopper.reader.osm

import com.graphhopper.reader.ReaderElement
import com.graphhopper.reader.ReaderNode
import com.graphhopper.reader.ReaderRelation
import com.graphhopper.reader.ReaderWay
import com.graphhopper.reader.osm.OSMNodeData.Companion.CONNECTION_NODE
import com.graphhopper.reader.osm.OSMNodeData.Companion.EMPTY_NODE
import com.graphhopper.reader.osm.OSMNodeData.Companion.END_NODE
import com.graphhopper.reader.osm.OSMNodeData.Companion.INTERMEDIATE_NODE
import com.graphhopper.reader.osm.OSMNodeData.Companion.JUNCTION_NODE
import com.graphhopper.reader.osm.OSMNodeData.Companion.isNodeId
import com.graphhopper.reader.osm.OSMNodeData.Companion.isPillarNode
import com.graphhopper.reader.osm.OSMNodeData.Companion.isTowerNode
import com.graphhopper.storage.Directory
import com.graphhopper.util.Helper
import com.graphhopper.util.Helper.nf
import com.graphhopper.util.PointAccess
import com.graphhopper.util.PointList
import com.graphhopper.util.StopWatch
import com.graphhopper.util.shapes.GHPoint3D
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.text.ParseException
import java.util.Date
import java.util.function.Consumer
import java.util.function.LongToIntFunction
import java.util.function.Predicate
import javax.xml.stream.XMLStreamException

/**
 * This class parses a given OSM file and splits OSM ways into 'segments' at all intersections (or 'junctions').
 * Intersections can be either crossings of different OSM ways or duplicate appearances of the same node within one
 * way (when the way contains a loop). Furthermore, this class creates artificial segments at certain nodes. It
 * also provides several hooks/callbacks to customize the processing of nodes, ways and relations.
 *
 * The OSM file is read twice. The first time we ignore OSM nodes and only determine the OSM node IDs at which accepted
 * ways are intersecting. During the second pass we split the OSM ways at intersections, introduce the artificial
 * segments and pass the way information along with the corresponding nodes to a given callback.
 *
 * We assume a strict order of the OSM file: nodes, ways, then relations.
 *
 * The main difficulty is that the OSM ID range is very large (64bit integers) and to be able to provide the full
 * node information for each segment we have to efficiently store the node data temporarily. This is addressed by
 * [OSMNodeData].
 */
class WaySegmentParser private constructor(private val nodeData: OSMNodeData) {

    private var wayFilter: Predicate<ReaderWay> = Predicate { true }
    private var splitNodeFilter: Predicate<ReaderNode> = Predicate { false }
    private var wayPreprocessor: WayPreprocessor = WayPreprocessor { _, _, _ -> }
    private var relationPreprocessor: Consumer<ReaderRelation> = Consumer { }
    private var relationProcessor: RelationProcessor = RelationProcessor { _, _ -> }
    private var edgeHandler: EdgeHandler = EdgeHandler { from, to, pointList, _, _ ->
        println("edge $from->$to (${pointList.size()} points)")
    }
    private var workerThreads = 2

    private var timestamp: Date? = null

    /**
     * @param osmFile the OSM file to parse, supported formats include .osm.xml, .osm.gz and .xml.pbf
     */
    fun readOSM(osmFile: File) {
        if (nodeData.getNodeCount() > 0)
            throw IllegalStateException("You can only run way segment parser once")

        LOGGER.info("Start reading OSM file: '$osmFile'")
        LOGGER.info("pass1 - start")
        val sw1 = StopWatch.started()
        readOSM(osmFile, Pass1Handler(), SkipOptions(true, false, false))
        LOGGER.info("pass1 - finished, took: {}", sw1.stop().getTimeString())

        val nodes = nodeData.getNodeCount()

        LOGGER.info("Creating graph. Node count (pillar+tower): " + nodes + ", " + Helper.getMemInfo())

        // the OSM-node-id key set is now fixed: switch to a read-optimal layout for the lookup-heavy pass2
        nodeData.freeze()

        LOGGER.info("pass2 - start")
        val sw2 = StopWatch().start()
        readOSM(osmFile, Pass2Handler(), SkipOptions.none())
        LOGGER.info("pass2 - finished, took: {}", sw2.stop().getTimeString())

        nodeData.release()

        LOGGER.info("Finished reading OSM file." +
                " pass1: " + sw1.getSeconds().toInt() + "s, " +
                " pass2: " + sw2.getSeconds().toInt() + "s, " +
                " total: " + (sw1.getSeconds() + sw2.getSeconds()).toInt() + "s" +
                " memory: " + Helper.getMemInfo())
    }

    /**
     * @return the timestamp read from the OSM file, or null if nothing was read yet
     */
    fun getTimestamp(): Date? = timestamp

    private inner class Pass1Handler : ReaderElementHandler {
        private var handledWays = false
        private var handledRelations = false
        private var wayCounter = 0L
        private var acceptedWays = 0L
        private var relationsCounter = 0L

        override fun handleWay(way: ReaderWay) {
            if (!handledWays) {
                LOGGER.info("pass1 - start reading OSM ways")
                handledWays = true
            }
            if (handledRelations)
                throw IllegalStateException("OSM way elements must be located before relation elements in OSM file")

            if (++wayCounter % 10_000_000 == 0L)
                LOGGER.info("pass1 - processed ways: " + nf(wayCounter) + ", accepted ways: " + nf(acceptedWays) +
                        ", way nodes: " + nf(nodeData.getNodeCount()) + ", " + Helper.getMemInfo())

            if (!wayFilter.test(way))
                return
            acceptedWays++

            for (node in way.nodes) {
                val isEnd = node.index == 0 || node.index == way.nodes.size() - 1
                val osmId = node.value
                nodeData.setOrUpdateNodeType(osmId,
                    if (isEnd) END_NODE else INTERMEDIATE_NODE,
                    // connection nodes are those where (only) two OSM ways are connected at their ends
                    { prev -> if (prev == END_NODE && isEnd) CONNECTION_NODE else JUNCTION_NODE })
            }
        }

        override fun handleRelation(relation: ReaderRelation) {
            if (!handledRelations) {
                LOGGER.info("pass1 - start reading OSM relations")
                handledRelations = true
            }

            if (++relationsCounter % 1_000_000 == 0L)
                LOGGER.info("pass1 - processed relations: " + nf(relationsCounter) + ", " + Helper.getMemInfo())

            relationPreprocessor.accept(relation)
        }

        @Throws(ParseException::class)
        override fun handleFileHeader(fileHeader: OSMFileHeader) {
            timestamp = Helper.createFormatter().parse(fileHeader.getTag("timestamp"))
        }

        override fun onFinish() {
            LOGGER.info("pass1 - finished, processed ways: " + nf(wayCounter) + ", accepted ways: " +
                    nf(acceptedWays) + ", way nodes: " + nf(nodeData.getNodeCount()) + ", relations: " +
                    nf(relationsCounter) + ", " + Helper.getMemInfo())
        }
    }

    private inner class Pass2Handler : ReaderElementHandler {
        private var handledNodes = false
        private var handledWays = false
        private var handledRelations = false
        private var nodeCounter = 0L
        private var acceptedNodes = 0L
        private var ignoredSplitNodes = 0L
        private var wayCounter = 0L

        override fun handleNode(node: ReaderNode) {
            if (!handledNodes) {
                LOGGER.info("pass2 - start reading OSM nodes")
                handledNodes = true
            }
            if (handledWays)
                throw IllegalStateException("OSM node elements must be located before way elements in OSM file")
            if (handledRelations)
                throw IllegalStateException("OSM node elements must be located before relation elements in OSM file")

            if (++nodeCounter % 10_000_000 == 0L)
                LOGGER.info("pass2 - processed nodes: " + nf(nodeCounter) + ", accepted nodes: " + nf(acceptedNodes) +
                        ", " + Helper.getMemInfo())

            val nodeType = nodeData.addCoordinatesIfMapped(node.id, node.lat, node.lon)
            if (nodeType == EMPTY_NODE)
                return

            acceptedNodes++

            // remember which nodes we want to split
            if (splitNodeFilter.test(node)) {
                if (nodeType == JUNCTION_NODE) {
                    LOGGER.debug("OSM node {} at {},{} is a barrier node at a junction. The barrier will be ignored",
                        node.id, Helper.round(node.lat, 7), Helper.round(node.lon, 7))
                    ignoredSplitNodes++
                } else
                    nodeData.setSplitNode(node.id)
            }

            // store node tags if at least one important tag is included and make this available for the edge handler
            for (e in node.getTags().entries) {
                if (INCLUDE_IF_NODE_TAGS.contains(e.key)) {
                    node.removeTag("created_by")
                    node.removeTag("source")
                    node.removeTag("note")
                    node.removeTag("fixme")
                    nodeData.setTags(node)
                    break
                }
            }
        }

        override fun handleWay(way: ReaderWay) {
            if (!handledWays) {
                LOGGER.info("pass2 - start reading OSM ways")
                handledWays = true
            }
            if (handledRelations)
                throw IllegalStateException("OSM way elements must be located before relation elements in OSM file")

            if (++wayCounter % 10_000_000 == 0L)
                LOGGER.info("pass2 - processed ways: " + nf(wayCounter) + ", " + Helper.getMemInfo())

            if (!wayFilter.test(way))
                return
            val segment = ArrayList<SegmentNode>(way.nodes.size())
            for (node in way.nodes)
                segment.add(SegmentNode(node.value, nodeData.getId(node.value), nodeData.getTags(node.value)))
            wayPreprocessor.preprocessWay(way,
                { osmNodeId -> nodeData.getCoordinates(nodeData.getId(osmNodeId)) },
                { osmNodeId -> nodeData.getTags(osmNodeId) })
            splitWayAtJunctionsAndEmptySections(segment, way)
        }

        private fun splitWayAtJunctionsAndEmptySections(fullSegment: List<SegmentNode>, way: ReaderWay) {
            var segment = ArrayList<SegmentNode>()
            for (node in fullSegment) {
                if (!isNodeId(node.id)) {
                    // this node exists in ways, but not in nodes. we ignore it, but we split the way when we encounter
                    // such a missing node. for example an OSM way might lead out of an area where nodes are available and
                    // back into it. we do not want to connect the exit/entry points using a straight line. this usually
                    // should only happen for OSM extracts
                    if (segment.size > 1) {
                        splitLoopSegments(segment, way)
                        segment = ArrayList()
                    }
                } else if (isTowerNode(node.id)) {
                    if (!segment.isEmpty()) {
                        segment.add(node)
                        splitLoopSegments(segment, way)
                        segment = ArrayList()
                    }
                    segment.add(node)
                } else {
                    segment.add(node)
                }
            }
            // the last segment might end at the end of the way
            if (segment.size > 1)
                splitLoopSegments(segment, way)
        }

        private fun splitLoopSegments(segment: List<SegmentNode>, way: ReaderWay) {
            if (segment.size < 2)
                throw IllegalStateException("Segment size must be >= 2, but was: " + segment.size)

            val isLoop = segment[0].osmNodeId == segment[segment.size - 1].osmNodeId
            if (segment.size == 2 && isLoop) {
                LOGGER.warn("Loop in OSM way: {}, will be ignored, duplicate node: {}", way.id, segment[0].osmNodeId)
            } else if (isLoop) {
                // split into two segments
                splitSegmentAtSplitNodes(segment.subList(0, segment.size - 1), way)
                splitSegmentAtSplitNodes(segment.subList(segment.size - 2, segment.size), way)
            } else {
                splitSegmentAtSplitNodes(segment, way)
            }
        }

        private fun splitSegmentAtSplitNodes(parentSegment: List<SegmentNode>, way: ReaderWay) {
            var segment = ArrayList<SegmentNode>()
            for (i in parentSegment.indices) {
                val node = parentSegment[i]
                if (nodeData.isSplitNode(node.osmNodeId)) {
                    // do not split this node again. for example a barrier can be connecting two ways (appear in both
                    // ways) and we only want to add a barrier edge once (but we want to add one).
                    nodeData.unsetSplitNode(node.osmNodeId)

                    // this node is a barrier. we will copy it and add an extra edge
                    var barrierFrom = node
                    var barrierTo = nodeData.addCopyOfNode(node)
                    if (i == parentSegment.size - 1) {
                        // make sure the barrier node is always on the inside of the segment
                        val tmp = barrierFrom
                        barrierFrom = barrierTo
                        barrierTo = tmp
                    }
                    if (!segment.isEmpty()) {
                        segment.add(barrierFrom)
                        handleSegment(segment, way)
                        segment = ArrayList()
                    }

                    // mark barrier edge
                    way.setTag("gh:barrier_edge", true)
                    // a barrier edge has two identical endpoints, so its geometry alone is not
                    // enough to derive an orientation. Pass the coordinates of the surrounding
                    // way nodes as transient tags so OrientationCalculator can use them.
                    if (i > 0)
                        way.setTag("gh:barrier_prev_point", nodeData.getCoordinates(parentSegment[i - 1].id)!!)
                    if (i < parentSegment.size - 1)
                        way.setTag("gh:barrier_next_point", nodeData.getCoordinates(parentSegment[i + 1].id)!!)
                    segment.add(barrierFrom)
                    segment.add(barrierTo)
                    handleSegment(segment, way)
                    way.removeTag("gh:barrier_edge")
                    way.removeTag("gh:barrier_prev_point")
                    way.removeTag("gh:barrier_next_point")

                    segment = ArrayList()
                    segment.add(barrierTo)
                } else {
                    segment.add(node)
                }
            }
            if (segment.size > 1)
                handleSegment(segment, way)
        }

        fun handleSegment(segment: List<SegmentNode>, way: ReaderWay) {
            val pointList = PointList(segment.size, nodeData.is3D())
            val nodeTags = ArrayList<Map<String, Any>>(segment.size)
            var from = -1
            var to = -1
            for (i in segment.indices) {
                val node = segment[i]
                var id = node.id
                if (!isNodeId(id))
                    throw IllegalStateException("Invalid id for node: " + node.osmNodeId + " when handling segment " + segment + " for way: " + way.id)
                if (isPillarNode(id) && (i == 0 || i == segment.size - 1)) {
                    id = nodeData.convertPillarToTowerNode(id, node.osmNodeId)
                    node.id = id
                }

                if (i == 0)
                    from = nodeData.idToTowerNode(id)
                else if (i == segment.size - 1)
                    to = nodeData.idToTowerNode(id)
                else if (isTowerNode(id))
                    throw IllegalStateException("Tower nodes should only appear at the end of segments, way: " + way.id)
                nodeData.addCoordinatesToPointList(id, pointList)
                nodeTags.add(node.tags)
            }
            if (from < 0 || to < 0)
                throw IllegalStateException("The first and last nodes of a segment must be tower nodes, way: " + way.id)
            edgeHandler.handleEdge(from, to, pointList, way, nodeTags)
        }

        override fun handleRelation(relation: ReaderRelation) {
            if (!handledRelations) {
                LOGGER.info("pass2 - start reading OSM relations")
                handledRelations = true
            }

            relationProcessor.processRelation(relation) { nodeOsmId -> getInternalNodeIdOfOSMNode(nodeOsmId) }
        }

        override fun onFinish() {
            LOGGER.info("pass2 - finished, processed ways: {}, way nodes: {}, nodes with tags: {}, node tag capacity: {}, ignored barriers at junctions: {}",
                nf(wayCounter), nf(acceptedNodes), nf(nodeData.getTaggedNodeCount()), nf(nodeData.getNodeTagCapacity()), nf(ignoredSplitNodes))
        }

        fun getInternalNodeIdOfOSMNode(nodeOsmId: Long): Int {
            val id = nodeData.getId(nodeOsmId)
            if (isTowerNode(id))
                return -id.toInt() - 3
            return -1
        }
    }

    private fun readOSM(file: File, handler: ReaderElementHandler, skipOptions: SkipOptions) {
        try {
            openOsmInputFile(file, skipOptions).use { osmInput ->
                var elem: ReaderElement?
                while (osmInput.getNext().also { elem = it } != null)
                    handler.handleElement(elem!!)
                handler.onFinish()
            }
        } catch (e: Exception) {
            throw RuntimeException("Could not parse OSM file: " + file.absolutePath, e)
        }
    }

    @Throws(XMLStreamException::class, IOException::class)
    protected fun openOsmInputFile(osmFile: File, skipOptions: SkipOptions): OSMInput =
        OSMInput.open(osmFile, workerThreads, skipOptions)

    class Builder(pointAccess: PointAccess, directory: Directory) {
        private val waySegmentParser: WaySegmentParser =
            WaySegmentParser(OSMNodeData(pointAccess, directory))

        /**
         * @param wayFilter return true for OSM ways that should be considered and false otherwise
         */
        fun setWayFilter(wayFilter: Predicate<ReaderWay>): Builder {
            waySegmentParser.wayFilter = wayFilter
            return this
        }

        /**
         * @param splitNodeFilter return true if the given OSM node should be duplicated to create an artificial edge
         */
        fun setSplitNodeFilter(splitNodeFilter: Predicate<ReaderNode>): Builder {
            waySegmentParser.splitNodeFilter = splitNodeFilter
            return this
        }

        /**
         * @param wayPreprocessor callback function that is called for each accepted OSM way during the second pass
         */
        fun setWayPreprocessor(wayPreprocessor: WayPreprocessor): Builder {
            waySegmentParser.wayPreprocessor = wayPreprocessor
            return this
        }

        /**
         * @param relationPreprocessor callback function that receives OSM relations during the first pass
         */
        fun setRelationPreprocessor(relationPreprocessor: Consumer<ReaderRelation>): Builder {
            waySegmentParser.relationPreprocessor = relationPreprocessor
            return this
        }

        /**
         * @param relationProcessor callback function that receives OSM relations during the second pass
         */
        fun setRelationProcessor(relationProcessor: RelationProcessor): Builder {
            waySegmentParser.relationProcessor = relationProcessor
            return this
        }

        /**
         * @param edgeHandler callback function that is called for each edge (way segment)
         */
        fun setEdgeHandler(edgeHandler: EdgeHandler): Builder {
            waySegmentParser.edgeHandler = edgeHandler
            return this
        }

        /**
         * @param workerThreads the number of threads used for the low level reading of the OSM file
         */
        fun setWorkerThreads(workerThreads: Int): Builder {
            waySegmentParser.workerThreads = workerThreads
            return this
        }

        fun build(): WaySegmentParser = waySegmentParser
    }

    private interface ReaderElementHandler {
        @Throws(ParseException::class)
        fun handleElement(elem: ReaderElement) {
            when (elem.type) {
                ReaderElement.Type.NODE -> handleNode(elem as ReaderNode)
                ReaderElement.Type.WAY -> handleWay(elem as ReaderWay)
                ReaderElement.Type.RELATION -> handleRelation(elem as ReaderRelation)
                ReaderElement.Type.FILEHEADER -> handleFileHeader(elem as OSMFileHeader)
                else -> throw IllegalStateException("Unknown reader element type: " + elem.type)
            }
        }

        fun handleNode(node: ReaderNode) {
        }

        fun handleWay(way: ReaderWay) {
        }

        fun handleRelation(relation: ReaderRelation) {
        }

        @Throws(ParseException::class)
        fun handleFileHeader(fileHeader: OSMFileHeader) {
        }

        fun onFinish() {
        }
    }

    fun interface EdgeHandler {
        fun handleEdge(from: Int, to: Int, pointList: PointList, way: ReaderWay, nodeTags: List<Map<String, Any>>)
    }

    fun interface RelationProcessor {
        fun processRelation(relation: ReaderRelation, getNodeIdForOSMNodeId: LongToIntFunction)
    }

    fun interface WayPreprocessor {
        /**
         * @param coordinateSupplier maps an OSM node ID (as it can be obtained by way.getNodes()) to the coordinates
         *                           of this node. If elevation is disabled it will be NaN. Returns null if no such OSM
         *                           node exists.
         */
        fun preprocessWay(way: ReaderWay, coordinateSupplier: CoordinateSupplier, nodeTagSupplier: NodeTagSupplier)
    }

    fun interface CoordinateSupplier {
        fun getCoordinate(osmNodeId: Long): GHPoint3D?
    }

    fun interface NodeTagSupplier {
        fun getTags(osmNodeId: Long): Map<String, Any>
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(WaySegmentParser::class.java)
        private val INCLUDE_IF_NODE_TAGS = setOf("barrier", "highway", "railway", "crossing", "ford")
    }
}
