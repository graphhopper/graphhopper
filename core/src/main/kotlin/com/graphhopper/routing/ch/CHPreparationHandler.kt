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
package com.graphhopper.routing.ch

import com.graphhopper.GraphHopperConfig
import com.graphhopper.config.CHProfile
import com.graphhopper.storage.BaseGraph
import com.graphhopper.storage.CHConfig
import com.graphhopper.storage.CHStorage
import com.graphhopper.storage.RoutingCHGraph
import com.graphhopper.storage.RoutingCHGraphImpl
import com.graphhopper.storage.StorableProperties
import com.graphhopper.util.GHUtility
import com.graphhopper.util.Helper.createFormatter
import com.graphhopper.util.Helper.getMemInfo
import com.graphhopper.util.PMap
import com.graphhopper.util.Parameters.CH
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.Collections
import java.util.Date
import java.util.LinkedHashMap
import java.util.stream.Stream

/**
 * This class handles the different CH preparations
 *
 * @author Peter Karich
 * @author easbar
 */
class CHPreparationHandler {
    // we first add the profiles and later read them to create the config objects (because they require
    // the actual Weightings)
    private val chProfiles = ArrayList<CHProfile>()
    private var preparationThreads = 0
    private var pMap = PMap()

    init {
        setPreparationThreads(1)
    }

    fun init(ghConfig: GraphHopperConfig) {
        // throw explicit error for deprecated configs
        if (ghConfig.has("prepare.threads"))
            throw IllegalStateException("Use " + CH.PREPARE + "threads instead of prepare.threads")
        if (ghConfig.has("prepare.chWeighting") || ghConfig.has("prepare.chWeightings") || ghConfig.has("prepare.ch.weightings"))
            throw IllegalStateException("Use profiles_ch instead of prepare.chWeighting, prepare.chWeightings or prepare.ch.weightings, see #1922 and docs/core/profiles.md")
        if (ghConfig.has("prepare.ch.edge_based"))
            throw IllegalStateException("Use profiles_ch instead of prepare.ch.edge_based, see #1922 and docs/core/profiles.md")

        setPreparationThreads(ghConfig.getInt(CH.PREPARE + "threads", getPreparationThreads()))
        setCHProfiles(ghConfig.getCHProfiles())
        pMap = ghConfig.asPMap()
    }

    fun isEnabled(): Boolean = !chProfiles.isEmpty()

    fun setCHProfiles(vararg chProfiles: CHProfile): CHPreparationHandler {
        setCHProfiles(listOf(*chProfiles))
        return this
    }

    fun setCHProfiles(chProfiles: Collection<CHProfile>): CHPreparationHandler {
        this.chProfiles.clear()
        this.chProfiles.addAll(chProfiles)
        return this
    }

    fun getCHProfiles(): List<CHProfile> = chProfiles

    fun getPreparationThreads(): Int = preparationThreads

    /**
     * This method changes the number of threads used for preparation on import. Default is 1. Make
     * sure that you have enough memory when increasing this number!
     */
    fun setPreparationThreads(preparationThreads: Int) {
        this.preparationThreads = preparationThreads
    }

    fun load(graph: BaseGraph, chConfigs: List<CHConfig>): Map<String, RoutingCHGraph> {
        val loaded = Collections.synchronizedMap(LinkedHashMap<String, RoutingCHGraph>())
        val runnables: Stream<Runnable> = chConfigs.stream()
                .map { c ->
                    Runnable {
                        val chStorage = CHStorage(graph.directory, c.name, c.isEdgeBased)
                        if (chStorage.loadExisting())
                            loaded[c.name] = RoutingCHGraphImpl.fromGraph(graph, chStorage, c)
                        else {
                            // todo: this is ugly, see comments in LMPreparationHandler
                            graph.directory.remove("nodes_ch_" + c.name)
                            graph.directory.remove("shortcuts_" + c.name)
                        }
                    }
                }
        GHUtility.runConcurrently(runnables, preparationThreads)
        return loaded
    }

    fun prepare(baseGraph: BaseGraph, properties: StorableProperties, chConfigs: List<CHConfig>, closeEarly: Boolean): Map<String, PrepareContractionHierarchies.Result> {
        if (chConfigs.isEmpty()) {
            LOGGER.info("There are no CHs to prepare")
            return Collections.emptyMap()
        }
        LOGGER.info("Creating CH preparations, {}", getMemInfo())
        val results = Collections.synchronizedMap(LinkedHashMap<String, PrepareContractionHierarchies.Result>())
        val runnables = ArrayList<Runnable>(chConfigs.size)
        for (i in chConfigs.indices) {
            val chConfig = chConfigs[i]
            LOGGER.info((i + 1).toString() + "/" + chConfigs.size + " Setting up CH preparation for profile " +
                    "'" + chConfig.name + "' " + chConfig.traversalMode + " ... (" + getMemInfo() + ")")
            runnables.add(Runnable {
                val name = chConfig.name
                // toString is not taken into account so we need to cheat, see http://stackoverflow.com/q/6113746/194609 for other options
                Thread.currentThread().name = name
                val prepare = PrepareContractionHierarchies.fromGraph(baseGraph, chConfig)
                prepare.setParams(pMap)
                val result = prepare.doWork()
                results[name] = result
                prepare.flush()
                if (closeEarly)
                    prepare.close()
                properties.put(CH.PREPARE + "date." + name, createFormatter().format(Date()))
            })
        }
        GHUtility.runConcurrently(runnables.stream(), preparationThreads)
        LOGGER.info("Finished CH preparation, {}", getMemInfo())
        return results
    }

    private fun createCHPreparation(graph: BaseGraph, chConfig: CHConfig): PrepareContractionHierarchies {
        val pch = PrepareContractionHierarchies.fromGraph(graph, chConfig)
        pch.setParams(pMap)
        return pch
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(CHPreparationHandler::class.java)
    }
}
