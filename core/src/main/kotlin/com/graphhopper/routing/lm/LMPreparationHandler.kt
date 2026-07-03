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

import com.bedatadriven.jackson.datatype.jts.JtsModule
import com.fasterxml.jackson.databind.ObjectMapper
import com.graphhopper.GraphHopperConfig
import com.graphhopper.config.LMProfile
import com.graphhopper.routing.ev.EncodedValueLookup
import com.graphhopper.routing.util.AreaIndex
import com.graphhopper.routing.util.EncodingManager
import com.graphhopper.storage.BaseGraph
import com.graphhopper.storage.StorableProperties
import com.graphhopper.util.GHUtility
import com.graphhopper.util.Helper.UTF_CS
import com.graphhopper.util.Helper.createFormatter
import com.graphhopper.util.Helper.getMemInfo
import com.graphhopper.util.JsonFeatureCollection
import com.graphhopper.util.Parameters
import com.graphhopper.util.Parameters.Landmark
import com.graphhopper.storage.index.LocationIndex
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.Reader
import java.util.Collections
import java.util.Date
import java.util.stream.Stream

/**
 * This class deals with the A*, landmark and triangulation (ALT) preparations.
 *
 * @author Peter Karich
 */
class LMPreparationHandler {

    private var landmarkCount = 16
    private val lmProfiles = ArrayList<LMProfile>()
    private val maximumWeights = HashMap<String, Double>()
    private var minNodes = -1
    private val lmSuggestionsLocations = ArrayList<String>(5)
    private var preparationThreads = 0
    private var logDetails = false
    private var areaIndex: AreaIndex<SplitArea>? = null

    init {
        setPreparationThreads(1)
    }

    fun init(ghConfig: GraphHopperConfig) {
        // throw explicit error for deprecated configs
        if (ghConfig.has("prepare.lm.weightings")) {
            throw IllegalStateException("Use profiles_lm instead of prepare.lm.weightings, see #1922 and docs/core/profiles.md")
        }

        setPreparationThreads(ghConfig.getInt(Parameters.Landmark.PREPARE + "threads", getPreparationThreads()))
        setLMProfiles(ghConfig.getLMProfiles())

        landmarkCount = ghConfig.getInt(Parameters.Landmark.COUNT, landmarkCount)
        logDetails = ghConfig.getBool(Landmark.PREPARE + "log_details", false)
        minNodes = ghConfig.getInt(Landmark.PREPARE + "min_network_size", -1)

        for (loc in ghConfig.getString(Landmark.PREPARE + "suggestions_location", "")!!.split(",")) {
            if (!loc.trim().isEmpty())
                lmSuggestionsLocations.add(loc.trim())
        }

        if (!isEnabled())
            return

        val splitAreaLocation = ghConfig.getString(Landmark.PREPARE + "split_area_location", "")!!
        val landmarkSplittingFeatureCollection = loadLandmarkSplittingFeatureCollection(splitAreaLocation)
        if (landmarkSplittingFeatureCollection != null && !landmarkSplittingFeatureCollection.features.isEmpty()) {
            val splitAreas = landmarkSplittingFeatureCollection.features
                .map { SplitArea.fromJsonFeature(it) }
            areaIndex = AreaIndex(splitAreas)
        }
    }

    fun getLandmarks(): Int = landmarkCount

    fun isEnabled(): Boolean = !lmProfiles.isEmpty()

    fun getPreparationThreads(): Int = preparationThreads

    /**
     * This method changes the number of threads used for preparation on import. Default is 1. Make
     * sure that you have enough memory when increasing this number!
     */
    fun setPreparationThreads(preparationThreads: Int) {
        this.preparationThreads = preparationThreads
    }

    fun setLMProfiles(vararg lmProfiles: LMProfile): LMPreparationHandler {
        return setLMProfiles(listOf(*lmProfiles))
    }

    /**
     * Enables the use of landmarks to reduce query times.
     */
    fun setLMProfiles(lmProfiles: Collection<LMProfile>): LMPreparationHandler {
        this.lmProfiles.clear()
        this.maximumWeights.clear()
        for (profile in lmProfiles) {
            if (profile.usesOtherPreparation())
                continue
            maximumWeights[profile.getProfile()] = profile.getMaximumLMWeight()
        }
        this.lmProfiles.addAll(lmProfiles)
        return this
    }

    fun getLMProfiles(): List<LMProfile> = lmProfiles

    /**
     * Loads the landmark data for all given configs if available.
     *
     * @return the loaded landmark storages
     */
    fun load(lmConfigs: List<LMConfig>, baseGraph: BaseGraph, encodedValueLookup: EncodedValueLookup): List<LandmarkStorage> {
        val loaded = Collections.synchronizedList(ArrayList<LandmarkStorage>())
        val loadingRunnables: Stream<Runnable> = lmConfigs.stream()
                .map { lmConfig ->
                    Runnable {
                        // todo: specifying ghStorage and landmarkCount should not be necessary, because all we want to do
                        //       is load the landmark data and these parameters are only needed to calculate the landmarks.
                        //       we should also work towards a separation of the storage and preparation related code in
                        //       landmark storage
                        val lms = LandmarkStorage(baseGraph, encodedValueLookup, baseGraph.directory, lmConfig, landmarkCount)
                        if (lms.loadExisting())
                            loaded.add(lms)
                        else {
                            // todo: this is very ugly. all we wanted to do was see if the landmarks exist already, but now
                            //       we need to remove the DAs from the directory. This is because otherwise we cannot
                            //       create these DataAccess again when we actually prepare the landmarks that don't exist
                            //       yet.
                            baseGraph.directory.remove("landmarks_" + lmConfig.getName())
                            baseGraph.directory.remove("landmarks_subnetwork_" + lmConfig.getName())
                        }
                    }
                }
        GHUtility.runConcurrently(loadingRunnables, preparationThreads)
        return loaded
    }

    /**
     * Prepares the landmark data for all given configs
     */
    fun prepare(lmConfigs: List<LMConfig>, baseGraph: BaseGraph, encodingManager: EncodingManager, properties: StorableProperties, locationIndex: LocationIndex, closeEarly: Boolean): List<PrepareLandmarks> {
        if (lmConfigs.isEmpty()) {
            LOGGER.info("There are no LMs to prepare")
            return Collections.emptyList()
        }
        val preparations = createPreparations(lmConfigs, baseGraph, encodingManager, locationIndex)
        val prepareRunnables = ArrayList<Runnable>()
        for (i in preparations.indices) {
            val prepare = preparations[i]
            val count = i + 1
            val name = prepare.getLMConfig().getName()
            prepareRunnables.add(Runnable {
                LOGGER.info(count.toString() + "/" + lmConfigs.size + " calling LM prepare.doWork for " + prepare.getLMConfig().getName() + " ... (" + getMemInfo() + ")")
                Thread.currentThread().name = name
                prepare.doWork()
                if (closeEarly)
                    prepare.close()
                LOGGER.info("LM {} finished {}", name, getMemInfo())
                properties.put(Landmark.PREPARE + "date." + name, createFormatter().format(Date()))
            })
        }
        GHUtility.runConcurrently(prepareRunnables.stream(), preparationThreads)
        LOGGER.info("Finished LM preparation, {}", getMemInfo())
        return preparations
    }

    /**
     * This method creates the landmark storages ready for landmark creation.
     */
    @JvmName("createPreparations")
    internal fun createPreparations(lmConfigs: List<LMConfig>, graph: BaseGraph, encodedValueLookup: EncodedValueLookup, locationIndex: LocationIndex?): List<PrepareLandmarks> {
        LOGGER.info("Creating LM preparations, {}", getMemInfo())
        val lmSuggestions = ArrayList<LandmarkSuggestion>(lmSuggestionsLocations.size)
        if (!lmSuggestionsLocations.isEmpty()) {
            try {
                for (loc in lmSuggestionsLocations) {
                    lmSuggestions.add(LandmarkSuggestion.readLandmarks(loc, locationIndex!!))
                }
            } catch (ex: IOException) {
                throw RuntimeException(ex)
            }
        }

        val preparations = ArrayList<PrepareLandmarks>()
        for (lmConfig in lmConfigs) {
            val maximumWeight = maximumWeights[lmConfig.getName()]
                ?: throw IllegalStateException("maximumWeight cannot be null. Default should be just negative. " +
                        "Couldn't find " + lmConfig.getName() + " in " + maximumWeights)

            val prepareLandmarks = PrepareLandmarks(graph.directory, graph, encodedValueLookup,
                    lmConfig, landmarkCount)
                    .setLandmarkSuggestions(lmSuggestions)
                    .setMaximumWeight(maximumWeight)
                    .setLogDetails(logDetails)
            if (minNodes > 1)
                prepareLandmarks.setMinimumNodes(minNodes)
            // using the area index we separate certain areas from each other but we do not change the base graph for this
            // so that other algorithms still can route between these areas
            val areaIndex = this.areaIndex
            if (areaIndex != null)
                prepareLandmarks.setAreaIndex(areaIndex)
            preparations.add(prepareLandmarks)
        }
        return preparations
    }

    private fun loadLandmarkSplittingFeatureCollection(splitAreaLocation: String): JsonFeatureCollection? {
        val objectMapper = ObjectMapper()
        objectMapper.registerModule(JtsModule())
        val builtinSplittingFile = LandmarkStorage::class.java.getResource("map.geo.json")
        try {
            val reader: Reader = if (splitAreaLocation.isEmpty())
                InputStreamReader(builtinSplittingFile!!.openStream(), UTF_CS)
            else
                InputStreamReader(FileInputStream(splitAreaLocation), UTF_CS)
            reader.use { r ->
                val result = objectMapper.readValue(r, JsonFeatureCollection::class.java)
                if (splitAreaLocation.isEmpty()) {
                    LOGGER.info("Loaded built-in landmark splitting collection from {}", builtinSplittingFile)
                } else {
                    LOGGER.info("Loaded landmark splitting collection from {}", splitAreaLocation)
                }
                return result
            }
        } catch (e: IOException) {
            LOGGER.error("Problem while reading border map GeoJSON. Skipping this.", e)
            return null
        }
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(LMPreparationHandler::class.java)
    }
}
