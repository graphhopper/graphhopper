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
package com.graphhopper.routing.lm

import com.carrotsearch.hppc.IntArrayList
import com.carrotsearch.hppc.IntHashSet
import com.carrotsearch.hppc.predicates.IntObjectPredicate
import com.carrotsearch.hppc.procedures.IntObjectProcedure
import com.graphhopper.coll.MapEntry
import com.graphhopper.routing.DijkstraBidirectionRef
import com.graphhopper.routing.SPTEntry
import com.graphhopper.routing.ev.EncodedValueLookup
import com.graphhopper.routing.ev.Subnetwork
import com.graphhopper.routing.subnetwork.SubnetworkStorage
import com.graphhopper.routing.subnetwork.TarjanSCC
import com.graphhopper.routing.util.AreaIndex
import com.graphhopper.routing.util.EdgeFilter
import com.graphhopper.routing.util.TraversalMode
import com.graphhopper.routing.weighting.AbstractAdjustedWeighting
import com.graphhopper.routing.weighting.Weighting
import com.graphhopper.storage.BaseGraph
import com.graphhopper.storage.DataAccess
import com.graphhopper.storage.Directory
import com.graphhopper.storage.Graph
import com.graphhopper.storage.NodeAccess
import com.graphhopper.util.EdgeIteratorState
import com.graphhopper.util.GHUtility
import com.graphhopper.util.Helper
import com.graphhopper.util.StopWatch
import com.graphhopper.util.exceptions.ConnectionNotFoundException
import com.graphhopper.util.shapes.GHPoint
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.Arrays
import java.util.Collections
import java.util.Comparator
import java.util.Random

/**
 * This class stores the landmark nodes and the weights from and to all other nodes in every
 * subnetwork. This data is created to apply a speed-up for path calculation but at the same times
 * stays flexible to per-request changes. The class is safe for usage from multiple reading threads
 * across algorithms.
 *
 * @author Peter Karich
 */
class LandmarkStorage(graph: BaseGraph, encodedValueLookup: EncodedValueLookup, dir: Directory, lmConfig: LMConfig, landmarks: Int) {

    // one node has an associated landmark information ('one landmark row'): the forward and backward weight
    private var LM_ROW_LENGTH: Long
    private var landmarks: Int
    private val FROM_OFFSET: Int
    private val TO_OFFSET: Int
    private val landmarkWeightDA: DataAccess

    // every subnetwork has its own landmark mapping but the count of landmarks is always the same
    private val landmarkIDs: MutableList<IntArray>
    private var factor = -1.0
    private val graph: BaseGraph
    private val na: NodeAccess
    private val encodedValueLookup: EncodedValueLookup
    private val weighting: Weighting
    private val lmConfig: LMConfig
    private var lmSelectionWeighting: Weighting
    private val traversalMode: TraversalMode
    private var initialized = false
    private var minimumNodes: Int
    private val subnetworkStorage: SubnetworkStorage
    private var landmarkSuggestions: List<LandmarkSuggestion> = Collections.emptyList()
    private var areaIndex: AreaIndex<SplitArea>? = null
    private var logDetails = false

    init {
        this.graph = graph
        this.encodedValueLookup = encodedValueLookup
        this.na = graph.nodeAccess
        this.minimumNodes = Math.min(graph.nodes / 2, 500_000)
        this.lmConfig = lmConfig
        this.weighting = lmConfig.getWeighting()
        if (weighting.hasTurnCosts()) {
            throw IllegalArgumentException("Landmark preparation cannot be used with weightings returning turn costs, because this can lead to wrong results during the (node-based) landmark calculation, see #1960")
        }
        // allowing arbitrary weighting is too dangerous
        this.lmSelectionWeighting = object : AbstractAdjustedWeighting(weighting) {
            override fun calcEdgeWeight(edgeState: EdgeIteratorState, reverse: Boolean): Double {
                // make accessibility of shortest identical to the provided weighting to avoid problems like shown in testWeightingConsistence
                val res = weighting.calcEdgeWeight(edgeState, reverse)
                if (res >= Double.MAX_VALUE)
                    return Double.POSITIVE_INFINITY

                // returning the time or distance leads to strange landmark positions (ferries -> slow&very long) and BFS is more what we want
                return 1.0
            }

            override val name: String
                get() = "LM_BFS|" + weighting.name
        }

        // Edge based is not really necessary because when adding turn costs while routing we can still
        // use the node based traversal as this is a smaller weight approximation and will still produce correct results
        // In this sense its even 'better' to use node-based.
        this.traversalMode = TraversalMode.NODE_BASED
        this.landmarkWeightDA = dir.create("landmarks_" + lmConfig.getName())

        this.landmarks = landmarks
        // one short per landmark and two directions => 2*2 byte
        this.LM_ROW_LENGTH = (landmarks * 4).toLong()
        this.FROM_OFFSET = 0
        this.TO_OFFSET = 2
        this.landmarkIDs = ArrayList()
        this.subnetworkStorage = SubnetworkStorage(dir.create("landmarks_subnetwork_" + lmConfig.getName()))
    }

    /**
     * Specify the maximum possible value for your used area. With this maximum weight value you can influence the storage
     * precision for your weights that help A* finding its way to the goal. The same value is used for all subnetworks.
     * Note, if you pick this value too big then too similar weights are stored
     * (some bits of the storage capability will be left unused).
     * If too low then far away values will have the same maximum value associated ("maxed out").
     * Both will lead to bad performance.
     *
     * @param maxWeight use a negative value to automatically determine this value.
     */
    fun setMaximumWeight(maxWeight: Double): LandmarkStorage {
        if (maxWeight > 0) {
            this.factor = maxWeight / PRECISION
            if (factor.isInfinite() || factor.isNaN())
                throw IllegalStateException("Illegal factor " + factor + " calculated from maximum weight " + maxWeight)
        }
        return this
    }

    /**
     * By default do not log many details.
     */
    fun setLogDetails(logDetails: Boolean) {
        this.logDetails = logDetails
    }

    /**
     * This method forces the landmark preparation to skip the landmark search and uses the specified landmark list instead.
     * Useful for manual tuning of larger areas to safe import time or improve quality.
     */
    fun setLandmarkSuggestions(landmarkSuggestions: List<LandmarkSuggestion>?): LandmarkStorage {
        if (landmarkSuggestions == null)
            throw IllegalArgumentException("landmark suggestions cannot be null")

        this.landmarkSuggestions = landmarkSuggestions
        return this
    }

    /**
     * This method sets the required number of nodes of a subnetwork for which landmarks should be calculated. Every
     * subnetwork below this count will be ignored.
     */
    fun setMinimumNodes(minimumNodes: Int) {
        this.minimumNodes = minimumNodes
    }

    /**
     * @see setMinimumNodes
     */
    fun getMinimumNodes(): Int = minimumNodes

    /**
     * This weighting is used for the selection heuristic and is per default not the weighting specified in the constructor.
     * The special weighting leads to a much better distribution of the landmarks and results in better response times.
     */
    fun setLMSelectionWeighting(lmSelectionWeighting: Weighting) {
        this.lmSelectionWeighting = lmSelectionWeighting
    }

    fun getLmSelectionWeighting(): Weighting = lmSelectionWeighting

    /**
     * This method returns the weighting for which the landmarks are originally created
     */
    fun getWeighting(): Weighting = weighting

    fun getLMConfig(): LMConfig = lmConfig

    @JvmName("isInitialized")
    internal fun isInitialized(): Boolean = initialized

    /**
     * This method calculates the landmarks and initial weightings to &amp; from them.
     */
    fun createLandmarks() {
        if (isInitialized())
            throw IllegalStateException("Initialize the landmark storage only once!")

        // fill 'from' and 'to' weights with maximum value
        val maxBytes = graph.nodes.toLong() * LM_ROW_LENGTH
        this.landmarkWeightDA.create(2000)
        this.landmarkWeightDA.ensureCapacity(maxBytes)

        var pointer = 0L
        while (pointer < maxBytes) {
            landmarkWeightDA.setShort(pointer, SHORT_INFINITY.toShort())
            pointer += 2
        }

        val empty = IntArray(landmarks)
        Arrays.fill(empty, UNSET_SUBNETWORK)
        landmarkIDs.add(empty)

        val subnetworks = ByteArray(graph.nodes)
        Arrays.fill(subnetworks, UNSET_SUBNETWORK.toByte())

        val snKey = Subnetwork.key(lmConfig.getName())
        // TODO We could use EdgeBasedTarjanSCC instead of node-based TarjanSCC here to get the small networks directly,
        //  instead of using the subnetworkEnc from PrepareRoutingSubnetworks.
        if (!encodedValueLookup.hasEncodedValue(snKey))
            throw IllegalArgumentException("EncodedValue '" + snKey + "' does not exist. For Landmarks this is " +
                    "currently required (also used in PrepareRoutingSubnetworks). See #2256")

        // Exclude edges that we previously marked in PrepareRoutingSubnetworks to avoid problems like "connection not found".
        val edgeInSubnetworkEnc = encodedValueLookup.getBooleanEncodedValue(snKey)
        val blockedEdges: IntHashSet
        // We use the areaIndex to split certain areas from each other but do not permanently change the base graph
        // so that other algorithms still can route through these regions. This is done to increase the density of
        // landmarks for an area like Europe+Asia, which improves the query speed.
        val areaIndex = this.areaIndex
        if (areaIndex != null) {
            val sw = StopWatch().start()
            blockedEdges = findBorderEdgeIds(areaIndex)
            if (logDetails)
                LOGGER.info("Made " + blockedEdges.size() + " edges inaccessible. Calculated country cut in " + sw.stop().getSeconds() + "s, " + Helper.getMemInfo())
        } else {
            blockedEdges = IntHashSet()
        }

        val accessFilter = EdgeFilter { edge -> !edge.get(edgeInSubnetworkEnc) && !blockedEdges.contains(edge.edge) }
        val tarjanFilter = EdgeFilter { edge -> accessFilter.accept(edge) && weighting.calcEdgeWeight(edge, false).isFinite() }

        val sw = StopWatch().start()
        val graphComponents = TarjanSCC.findComponents(graph, tarjanFilter, true)
        if (logDetails)
            LOGGER.info("Calculated " + graphComponents.components.size + " subnetworks via tarjan in " + sw.stop().getSeconds() + "s, " + Helper.getMemInfo())

        var additionalInfo = ""
        // guess the factor
        if (factor <= 0) {
            // A 'factor' is necessary to store the weight in just a short value but without losing too much precision.
            // This factor is rather delicate to pick, we estimate it from an exploration with some "test landmarks",
            // see estimateMaxWeight. If we pick the distance too big for small areas this could lead to (slightly)
            // suboptimal routes as there will be too big rounding errors. But picking it too small is bad for performance
            // e.g. for Germany at least 1500km is very important otherwise speed is at least twice as slow e.g. for 1000km
            val maxWeight = estimateMaxWeight(graphComponents.components, accessFilter)
            setMaximumWeight(maxWeight)
            additionalInfo = ", maxWeight:" + maxWeight + " from quick estimation"
        }

        if (logDetails)
            LOGGER.info("init landmarks for subnetworks with node count greater than " + minimumNodes + " with factor:" + factor + additionalInfo)

        var nodes = 0
        for (subnetworkIds in graphComponents.components) {
            nodes += subnetworkIds.size()
            if (subnetworkIds.size() < minimumNodes)
                continue
            if (factor <= 0)
                throw IllegalStateException("factor wasn't initialized " + factor + ", subnetworks:"
                        + graphComponents.components.size + ", minimumNodes:" + minimumNodes + ", current size:" + subnetworkIds.size())

            var index = subnetworkIds.size() - 1
            // ensure start node is reachable from both sides and no subnetwork is associated
            while (index >= 0) {
                val nextStartNode = subnetworkIds.get(index)
                if (subnetworks[nextStartNode].toInt() == UNSET_SUBNETWORK) {
                    if (logDetails) {
                        val p = createPoint(graph, nextStartNode)
                        LOGGER.info("start node: " + nextStartNode + " (" + p + ") subnetwork " + index + ", subnetwork size: " + subnetworkIds.size()
                                + ", " + Helper.getMemInfo() + (if (this.areaIndex == null) "" else " area:" + this.areaIndex!!.query(p.lat, p.lon)))
                    }

                    if (createLandmarksForSubnetwork(nextStartNode, subnetworks, accessFilter))
                        break
                }
                index--
            }
            if (index < 0)
                LOGGER.warn("next start node not found in big enough network of size " + subnetworkIds.size() + ", first element is " + subnetworkIds.get(0) + ", " + createPoint(graph, subnetworkIds.get(0)))
        }

        val subnetworkCount = landmarkIDs.size
        // store all landmark node IDs and one int for the factor itself.
        this.landmarkWeightDA.ensureCapacity(maxBytes /* landmark weights */ + subnetworkCount.toLong() * landmarks /* landmark mapping per subnetwork */ + 4)

        // calculate offset to point into landmark mapping
        var bytePos = maxBytes
        for (lms in landmarkIDs) {
            for (lmNodeId in lms) {
                landmarkWeightDA.setInt(bytePos, lmNodeId)
                bytePos += 4L
            }
        }

        landmarkWeightDA.setHeader(0 * 4, graph.nodes)
        landmarkWeightDA.setHeader(1 * 4, landmarks)
        landmarkWeightDA.setHeader(2 * 4, subnetworkCount)
        if (factor * DOUBLE_MLTPL > Int.MAX_VALUE)
            throw UnsupportedOperationException("landmark weight factor cannot be bigger than Integer.MAX_VALUE " + factor * DOUBLE_MLTPL)
        landmarkWeightDA.setHeader(3 * 4, Math.round(factor * DOUBLE_MLTPL).toInt())

        // serialize fast byte[] into DataAccess
        subnetworkStorage.create(graph.nodes.toLong())
        for (nodeId in subnetworks.indices) {
            subnetworkStorage.setSubnetwork(nodeId, subnetworks[nodeId].toInt())
        }

        if (logDetails)
            LOGGER.info("Finished landmark creation. Subnetwork node count sum " + nodes + " vs. nodes " + graph.nodes)
        initialized = true
    }

    /**
     * This method returns the maximum weight for the graph starting from the landmarks
     */
    private fun estimateMaxWeight(graphComponents: List<IntArrayList>, accessFilter: EdgeFilter): Double {
        var maxWeight = 0.0
        var searchedSubnetworks = 0
        val random = Random(0)
        // the maximum weight can only be an approximation so there is only a tiny improvement when we would do this for
        // all landmarks. See #2027 (1st commit) where only 1 landmark was sufficient when multiplied with 1.01 at the end
        // TODO instead of calculating the landmarks again here we could store them in landmarkIDs and do this for all here
        val tmpLandmarkNodeIds = IntArray(3)
        for (subnetworkIds in graphComponents) {
            if (subnetworkIds.size() < minimumNodes)
                continue

            searchedSubnetworks++
            val maxRetries = Math.max(subnetworkIds.size(), 100)
            for (retry in 0 until maxRetries) {
                val index = random.nextInt(subnetworkIds.size())
                val nextStartNode = subnetworkIds.get(index)
                var explorer = findLandmarks(tmpLandmarkNodeIds, nextStartNode, accessFilter, "estimate $index")
                if (explorer.getFromCount() < minimumNodes) {
                    LOGGER.error("method findLandmarks for " + createPoint(graph, nextStartNode) + " (" + nextStartNode + ")"
                            + " resulted in too few visited nodes: " + explorer.getFromCount() + " vs expected minimum " + minimumNodes + ", see #2256")
                    continue
                }

                // starting
                for (lmIdx in tmpLandmarkNodeIds.indices) {
                    val lmNodeId = tmpLandmarkNodeIds[lmIdx]
                    explorer = LandmarkExplorer(graph, this, weighting, traversalMode, accessFilter, false)
                    explorer.setStartNode(lmNodeId)
                    explorer.runAlgo()
                    maxWeight = Math.max(maxWeight, explorer.getLastEntry().weight)
                }
                break
            }
        }

        if (maxWeight <= 0 && searchedSubnetworks > 0)
            throw IllegalStateException("max weight wasn't set although " + searchedSubnetworks + " subnetworks were searched (total " + graphComponents.size + "), minimumNodes:" + minimumNodes)

        // we have to increase maxWeight slightly as it is only an approximation towards the maximum weight,
        // especially when external landmarks are provided, but also because we do not traverse all landmarks
        return maxWeight * 1.008
    }

    /**
     * This method creates landmarks for the specified subnetwork (integer list)
     *
     * @return landmark mapping
     */
    private fun createLandmarksForSubnetwork(startNode: Int, subnetworks: ByteArray, accessFilter: EdgeFilter): Boolean {
        val subnetworkId = landmarkIDs.size
        val tmpLandmarkNodeIds = IntArray(landmarks)
        val logOffset = Math.max(1, landmarks / 2)
        var pickedPrecalculatedLandmarks = false

        if (!landmarkSuggestions.isEmpty()) {
            val lat = na.getLat(startNode)
            val lon = na.getLon(startNode)
            var selectedSuggestion: LandmarkSuggestion? = null
            for (lmsugg in landmarkSuggestions) {
                if (lmsugg.getBox().contains(lat, lon)) {
                    selectedSuggestion = lmsugg
                    break
                }
            }

            if (selectedSuggestion != null) {
                if (selectedSuggestion.getNodeIds().size < tmpLandmarkNodeIds.size)
                    throw IllegalArgumentException("landmark suggestions are too few " + selectedSuggestion.getNodeIds().size + " for requested landmarks " + landmarks)

                pickedPrecalculatedLandmarks = true
                for (i in tmpLandmarkNodeIds.indices) {
                    val lmNodeId = selectedSuggestion.getNodeIds()[i]
                    tmpLandmarkNodeIds[i] = lmNodeId
                }
            }
        }

        if (pickedPrecalculatedLandmarks) {
            LOGGER.info("Picked " + tmpLandmarkNodeIds.size + " landmark suggestions, skip finding landmarks")
        } else {
            val explorer = findLandmarks(tmpLandmarkNodeIds, startNode, accessFilter, "create")
            if (explorer.getFromCount() < minimumNodes) {
                // too small subnetworks are initialized with special id==0
                explorer.setSubnetworks(subnetworks, UNCLEAR_SUBNETWORK)
                return false
            }
            if (logDetails)
                LOGGER.info("Finished searching landmarks for subnetwork " + subnetworkId + " of size " + explorer.getVisitedNodes())
        }

        // 2) calculate weights for all landmarks -> 'from' and 'to' weight
        for (lmIdx in tmpLandmarkNodeIds.indices) {
            if (Thread.currentThread().isInterrupted) {
                throw RuntimeException("Thread was interrupted for landmark $lmIdx")
            }
            val lmNodeId = tmpLandmarkNodeIds[lmIdx]
            var explorer = LandmarkExplorer(graph, this, weighting, traversalMode, accessFilter, false)
            explorer.setStartNode(lmNodeId)
            explorer.runAlgo()
            explorer.initLandmarkWeights(lmIdx, lmNodeId, LM_ROW_LENGTH, FROM_OFFSET)

            // set subnetwork id to all explored nodes, but do this only for the first landmark
            if (lmIdx == 0) {
                if (explorer.setSubnetworks(subnetworks, subnetworkId))
                    return false
            }

            explorer = LandmarkExplorer(graph, this, weighting, traversalMode, accessFilter, true)
            explorer.setStartNode(lmNodeId)
            explorer.runAlgo()
            explorer.initLandmarkWeights(lmIdx, lmNodeId, LM_ROW_LENGTH, TO_OFFSET)

            if (lmIdx == 0) {
                if (explorer.setSubnetworks(subnetworks, subnetworkId))
                    return false
            }

            if (logDetails && lmIdx % logOffset == 0)
                LOGGER.info("Set landmarks weights [" + weighting + "]. "
                        + "Progress " + (100.0 * lmIdx / tmpLandmarkNodeIds.size).toInt() + "%")
        }

        // TODO set weight to SHORT_MAX if entry has either no 'from' or no 'to' entry
        landmarkIDs.add(tmpLandmarkNodeIds)
        return true
    }

    /**
     * This method specifies the polygons which should be used to split the world wide area to improve performance and
     * quality in this scenario.
     */
    fun setAreaIndex(areaIndex: AreaIndex<SplitArea>) {
        this.areaIndex = areaIndex
    }

    /**
     * This method makes edges crossing the specified border inaccessible to split a bigger area into smaller subnetworks.
     * This is important for the world wide use case to limit the maximum distance and also to detect unreasonable routes faster.
     */
    protected fun findBorderEdgeIds(areaIndex: AreaIndex<SplitArea>): IntHashSet {
        val allEdgesIterator = graph.allEdges
        val inaccessible = IntHashSet()
        while (allEdgesIterator.next()) {
            val adjNode = allEdgesIterator.adjNode
            var areas = areaIndex.query(na.getLat(adjNode), na.getLon(adjNode))
            val areaAdj = if (areas.isEmpty()) null else areas[0]

            val baseNode = allEdgesIterator.baseNode
            areas = areaIndex.query(na.getLat(baseNode), na.getLon(baseNode))
            val areaBase = if (areas.isEmpty()) null else areas[0]
            if (areaAdj !== areaBase) {
                inaccessible.add(allEdgesIterator.edge)
            }
        }
        return inaccessible
    }

    /**
     * The factor is used to convert double values into more compact int values.
     */
    @JvmName("getFactor")
    internal fun getFactor(): Double = factor

    /**
     * @return the weight from the landmark to the specified node. Where the landmark integer is not
     * a node ID but the internal index of the landmark array.
     */
    @JvmName("getFromWeight")
    internal fun getFromWeight(landmarkIndex: Int, node: Int): Int {
        val res = landmarkWeightDA.getShort(node.toLong() * LM_ROW_LENGTH + landmarkIndex * 4L + FROM_OFFSET).toInt() and 0x0000FFFF
        if (res == SHORT_INFINITY)
            // TODO can happen if endstanding oneway
            // we should set a 'from' value to SHORT_MAX if the 'to' value was already set to find real bugs
            // and what to return? Integer.MAX_VALUE i.e. convert to Double.pos_infinity upstream?
            return SHORT_MAX
        // throw new IllegalStateException("Do not call getFromWeight for wrong landmark[" + landmarkIndex + "]=" + landmarkIDs[landmarkIndex] + " and node " + node);
        // TODO if(res == MAX) fallback to beeline approximation!?

        return res
    }

    /**
     * @return the weight from the specified node to the landmark (specified *as index*)
     */
    @JvmName("getToWeight")
    internal fun getToWeight(landmarkIndex: Int, node: Int): Int {
        val res = landmarkWeightDA.getShort(node.toLong() * LM_ROW_LENGTH + landmarkIndex * 4 + TO_OFFSET).toInt() and 0x0000FFFF
        if (res == SHORT_INFINITY)
            return SHORT_MAX

        return res
    }

    /**
     * @return false if the value capacity was reached and instead of the real value the SHORT_MAX was stored.
     */
    @JvmName("setWeight")
    internal fun setWeight(pointer: Long, value: Double): Boolean {
        val tmpVal = value / factor
        if (tmpVal > Int.MAX_VALUE)
            throw UnsupportedOperationException("Cannot store infinity explicitly, pointer=" + pointer + ", value=" + value + ", factor=" + factor)

        return if (tmpVal >= SHORT_MAX) {
            landmarkWeightDA.setShort(pointer, SHORT_MAX.toShort())
            false
        } else {
            landmarkWeightDA.setShort(pointer, tmpVal.toInt().toShort())
            true
        }
    }

    @JvmName("isInfinity")
    internal fun isInfinity(pointer: Long): Boolean {
        return (landmarkWeightDA.getShort(pointer).toInt() and 0x0000FFFF) == SHORT_INFINITY
    }

    // From all available landmarks pick just a few active ones
    @JvmName("chooseActiveLandmarks")
    internal fun chooseActiveLandmarks(fromNode: Int, toNode: Int, activeLandmarkIndices: IntArray, reverse: Boolean): Boolean {
        if (fromNode < 0 || toNode < 0)
            throw IllegalStateException("from " + fromNode + " and to "
                    + toNode + " nodes have to be 0 or positive to init landmarks")

        val subnetworkFrom = subnetworkStorage.getSubnetwork(fromNode)
        val subnetworkTo = subnetworkStorage.getSubnetwork(toNode)
        if (subnetworkFrom <= UNCLEAR_SUBNETWORK || subnetworkTo <= UNCLEAR_SUBNETWORK)
            return false
        if (subnetworkFrom != subnetworkTo) {
            throw ConnectionNotFoundException("Connection between locations not found. Different subnetworks " + subnetworkFrom
                    + " vs. " + subnetworkTo, HashMap<String, Any>())
        }

        // See the similar formula in LMApproximator.approximateForLandmark
        val list = ArrayList<Map.Entry<Int, Int>>(landmarks)
        for (lmIndex in 0 until landmarks) {
            val fromWeight = getFromWeight(lmIndex, toNode) - getFromWeight(lmIndex, fromNode)
            val toWeight = getToWeight(lmIndex, fromNode) - getToWeight(lmIndex, toNode)

            list.add(MapEntry(if (reverse)
                Math.max(-fromWeight, -toWeight)
            else
                Math.max(fromWeight, toWeight), lmIndex))
        }

        Collections.sort(list, SORT_BY_WEIGHT)

        if (activeLandmarkIndices[0] >= 0) {
            val set = IntHashSet(activeLandmarkIndices.size)
            set.addAll(*activeLandmarkIndices)
            var existingLandmarkCounter = 0
            val COUNT = Math.min(activeLandmarkIndices.size - 2, 2)
            for (i in activeLandmarkIndices.indices) {
                if (i >= activeLandmarkIndices.size - COUNT + existingLandmarkCounter) {
                    // keep at least two of the previous landmarks (pick the best)
                    break
                } else {
                    activeLandmarkIndices[i] = list[i].value
                    if (set.contains(activeLandmarkIndices[i]))
                        existingLandmarkCounter++
                }
            }

        } else {
            for (i in activeLandmarkIndices.indices) {
                activeLandmarkIndices[i] = list[i].value
            }
        }

        return true
    }

    fun getLandmarkCount(): Int = landmarks

    fun getLandmarks(subnetwork: Int): IntArray = landmarkIDs[subnetwork]

    /**
     * @return the number of subnetworks that have landmarks
     */
    fun getSubnetworksWithLandmarks(): Int = landmarkIDs.size

    fun isEmpty(): Boolean = landmarkIDs.size < 2

    override fun toString(): String {
        var str = ""
        for (ints in landmarkIDs) {
            if (!str.isEmpty())
                str += ", "
            str += Arrays.toString(ints)
        }
        return str
    }

    /**
     * @return the calculated landmarks as GeoJSON string.
     */
    @JvmName("getLandmarksAsGeoJSON")
    internal fun getLandmarksAsGeoJSON(): String {
        var str = ""
        for (subnetwork in 1 until landmarkIDs.size) {
            val lmArray = landmarkIDs[subnetwork]
            for (lmIdx in lmArray.indices) {
                val index = lmArray[lmIdx]
                if (!str.isEmpty())
                    str += ","

                str += ("{ \"type\": \"Feature\", \"geometry\": {\"type\": \"Point\", \"coordinates\": ["
                        + na.getLon(index) + ", " + na.getLat(index) + "]},")
                str += ("  \"properties\":{\"node_index\":" + index + ","
                        + "\"subnetwork\":" + subnetwork + ","
                        + "\"lm_index\":" + lmIdx + "}"
                        + "}")
            }
        }

        return "{ \"type\": \"FeatureCollection\", \"features\": [" + str + "]}"
    }

    fun loadExisting(): Boolean {
        if (isInitialized())
            throw IllegalStateException("Cannot call PrepareLandmarks.loadExisting if already initialized")
        if (landmarkWeightDA.loadExisting()) {
            if (!subnetworkStorage.loadExisting())
                throw IllegalStateException("landmark weights loaded but not the subnetworks!?")

            val nodes = landmarkWeightDA.getHeader(0 * 4)
            if (nodes != graph.nodes)
                throw IllegalArgumentException("Cannot load landmark data as written for different graph storage with " + nodes + " nodes, not " + graph.nodes)
            landmarks = landmarkWeightDA.getHeader(1 * 4)
            val subnetworks = landmarkWeightDA.getHeader(2 * 4)
            factor = landmarkWeightDA.getHeader(3 * 4) / DOUBLE_MLTPL
            LM_ROW_LENGTH = (landmarks * 4).toLong()
            val maxBytes = LM_ROW_LENGTH * nodes
            var bytePos = maxBytes

            // in the first subnetwork 0 there are no landmark IDs stored
            for (j in 0 until subnetworks) {
                val tmpLandmarks = IntArray(landmarks)
                for (i in tmpLandmarks.indices) {
                    tmpLandmarks[i] = landmarkWeightDA.getInt(bytePos)
                    bytePos += 4
                }
                landmarkIDs.add(tmpLandmarks)
            }

            initialized = true
            return true
        }
        return false
    }

    fun flush() {
        landmarkWeightDA.flush()
        subnetworkStorage.flush()
    }

    fun close() {
        landmarkWeightDA.close()
        subnetworkStorage.close()
    }

    fun isClosed(): Boolean = landmarkWeightDA.isClosed

    fun getCapacity(): Long = landmarkWeightDA.capacity + subnetworkStorage.capacity

    @JvmName("getBaseNodes")
    internal fun getBaseNodes(): Int = graph.nodes

    private fun findLandmarks(landmarkNodeIdsToReturn: IntArray, startNode: Int, accessFilter: EdgeFilter, info: String): LandmarkExplorer {
        val logOffset = Math.max(1, landmarkNodeIdsToReturn.size / 2)
        // 1a) pick landmarks via special weighting for a better geographical spreading
        val initWeighting = lmSelectionWeighting
        var explorer = LandmarkExplorer(graph, this, initWeighting, traversalMode, accessFilter, false)
        explorer.setStartNode(startNode)
        explorer.runAlgo()

        if (explorer.getFromCount() >= minimumNodes) {
            // 1b) we have one landmark, now determine the other landmarks
            landmarkNodeIdsToReturn[0] = explorer.getLastEntry().adjNode
            for (lmIdx in 0 until landmarkNodeIdsToReturn.size - 1) {
                explorer = LandmarkExplorer(graph, this, initWeighting, traversalMode, accessFilter, false)
                // set all current landmarks as start so that the next getLastNode is hopefully a "far away" node
                for (j in 0 until lmIdx + 1) {
                    explorer.setStartNode(landmarkNodeIdsToReturn[j])
                }
                explorer.runAlgo()
                landmarkNodeIdsToReturn[lmIdx + 1] = explorer.getLastEntry().adjNode
                if (logDetails && lmIdx % logOffset == 0)
                    LOGGER.info("Finding landmarks [" + lmConfig + "] in network [" + explorer.getVisitedNodes() + "] for " + info + ". "
                            + "Start node:" + startNode + " (" + createPoint(graph, startNode) + ")"
                            + "Progress " + (100.0 * lmIdx / landmarkNodeIdsToReturn.size).toInt() + "%, " + Helper.getMemInfo())
            }
        }
        return explorer
    }

    /**
     * For testing only
     */
    @JvmName("_getInternalDA")
    internal fun _getInternalDA(): DataAccess = landmarkWeightDA

    /**
     * This class is used to calculate landmark location (equally distributed).
     * It derives from DijkstraBidirectionRef, but is only used as forward or backward search.
     */
    private class LandmarkExplorer(
        g: Graph, private val lms: LandmarkStorage, weighting: Weighting, tMode: TraversalMode,
        private val accessFilter: EdgeFilter, private val reverse: Boolean
    ) : DijkstraBidirectionRef(g, weighting, tMode) {

        private var lastEntry: SPTEntry? = null

        init {
            // set one of the bi directions as already finished
            if (reverse)
                finishedFrom = true
            else
                finishedTo = true

            // no path should be calculated
            setUpdateBestPath(false)
        }

        fun setStartNode(startNode: Int) {
            if (reverse)
                initTo(startNode, 0.0)
            else
                initFrom(startNode, 0.0)
        }

        override fun calcWeight(iter: EdgeIteratorState, currEdge: SPTEntry, reverse: Boolean): Double {
            if (!accessFilter.accept(iter))
                return Double.POSITIVE_INFINITY
            return GHUtility.calcWeightWithTurnWeight(weighting, iter, reverse, currEdge.edge) + currEdge.getWeightOfVisitedPath()
        }

        fun getFromCount(): Int = bestWeightMapFrom.size()

        public override fun runAlgo() {
            super.runAlgo()
        }

        fun getLastEntry(): SPTEntry {
            if (!finished())
                throw IllegalStateException("Cannot get max weight if not yet finished")
            return lastEntry!!
        }

        public override fun finished(): Boolean {
            return if (reverse) {
                lastEntry = currTo
                finishedTo
            } else {
                lastEntry = currFrom
                finishedFrom
            }
        }

        fun setSubnetworks(subnetworks: ByteArray, subnetworkId: Int): Boolean {
            if (subnetworkId > 127)
                throw IllegalStateException("Too many subnetworks " + subnetworkId)

            var failed = false
            val map = if (reverse) bestWeightMapTo else bestWeightMapFrom
            map.forEach(IntObjectPredicate<SPTEntry> { nodeId, _ ->
                val sn = subnetworks[nodeId].toInt()
                if (sn != subnetworkId) {
                    if (sn != UNSET_SUBNETWORK && sn != UNCLEAR_SUBNETWORK) {
                        // this is ugly but can happen in real world, see testWithOnewaySubnetworks
                        LOGGER.error("subnetworkId for node " + nodeId
                                + " (" + createPoint(graph, nodeId) + ") already set (" + sn + "). " + "Cannot change to " + subnetworkId)

                        failed = true
                        return@IntObjectPredicate false
                    }

                    subnetworks[nodeId] = subnetworkId.toByte()
                }
                true
            })
            return failed
        }

        fun initLandmarkWeights(lmIdx: Int, lmNodeId: Int, rowSize: Long, offset: Int) {
            val map = if (reverse) bestWeightMapTo else bestWeightMapFrom
            var maxedout = 0
            var finalMaxWeight = 0.0

            map.forEach(IntObjectProcedure<SPTEntry> { nodeId, b ->
                if (!lms.setWeight(nodeId * rowSize + lmIdx * 4 + offset, b.weight)) {
                    maxedout++
                    finalMaxWeight = Math.max(b.weight, finalMaxWeight)
                }
            })

            if (maxedout.toDouble() / map.size() > 0.1) {
                LOGGER.warn("landmark " + lmIdx + " (" + nodeAccess.getLat(lmNodeId) + "," + nodeAccess.getLon(lmNodeId) + "): " +
                        "too many weights were maxed out (" + maxedout + "/" + map.size() + "). Use a bigger factor than " + lms.factor
                        + ". For example use maximum_lm_weight: " + finalMaxWeight * 1.2 + " in your LM profile definition")
            }
        }
    }

    companion object {
        // Short.MAX_VALUE = 2^15-1 but we have unsigned short so we need 2^16-1
        private const val SHORT_INFINITY = Short.MAX_VALUE * 2 + 1

        // We have large values that do not fit into a short, use a specific maximum value
        internal const val SHORT_MAX = SHORT_INFINITY - 1

        private val LOGGER: Logger = LoggerFactory.getLogger(LandmarkStorage::class.java)

        // This value is used to identify nodes where no subnetwork is associated
        private const val UNSET_SUBNETWORK = -1

        // This value should only be used if subnetwork is too small to be explicitly stored
        private const val UNCLEAR_SUBNETWORK = 0

        private const val DOUBLE_MLTPL = 1e6

        /**
         * 'to' and 'from' fit into 32 bit => 16 bit for each of them => 65536
         */
        internal const val PRECISION: Long = 1L shl 16

        /**
         * Sort landmark by weight and let maximum weight come first, to pick best active landmarks.
         */
        internal val SORT_BY_WEIGHT: Comparator<Map.Entry<Int, Int>> =
            Comparator { o1, o2 -> Integer.compare(o2.key, o1.key) }

        @JvmStatic
        internal fun createPoint(graph: Graph, nodeId: Int): GHPoint {
            return GHPoint(graph.nodeAccess.getLat(nodeId), graph.nodeAccess.getLon(nodeId))
        }
    }
}
