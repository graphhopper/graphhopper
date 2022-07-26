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
package com.graphhopper.routing.ch;

import com.graphhopper.GraphHopperConfig;
import com.graphhopper.config.CHProfile;
import com.graphhopper.storage.*;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.PMap;
import com.graphhopper.util.Parameters.CH;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import static com.graphhopper.util.Helper.createFormatter;
import static com.graphhopper.util.Helper.getMemInfo;

/**
 * This class handles the different CH preparations
 *
 * @author Peter Karich
 * @author easbar
 */
public class CHPreparationHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(CHPreparationHandler.class);
    // we first add the profiles and later read them to create the config objects (because they require
    // the actual Weightings)
    private final List<CHProfile> chProfiles = new ArrayList<>();
    private int preparationThreads;
    private PMap pMap = new PMap();

    public CHPreparationHandler() {
        setPreparationThreads(1);
    }

    public void init(GraphHopperConfig ghConfig) {
        // throw explicit error for deprecated configs
        if (ghConfig.has("prepare.threads"))
            throw new IllegalStateException("Use " + CH.PREPARE + "threads instead of prepare.threads");
        if (ghConfig.has("prepare.chWeighting") || ghConfig.has("prepare.chWeightings") || ghConfig.has("prepare.ch.weightings"))
            throw new IllegalStateException("Use profiles_ch instead of prepare.chWeighting, prepare.chWeightings or prepare.ch.weightings, see #1922 and docs/core/profiles.md");
        if (ghConfig.has("prepare.ch.edge_based"))
            throw new IllegalStateException("Use profiles_ch instead of prepare.ch.edge_based, see #1922 and docs/core/profiles.md");

        setPreparationThreads(ghConfig.getInt(CH.PREPARE + "threads", getPreparationThreads()));
        setCHProfiles(ghConfig.getCHProfiles());
        pMap = ghConfig.asPMap();
    }

    public final boolean isEnabled() {
        return !chProfiles.isEmpty();
    }

    public CHPreparationHandler setCHProfiles(CHProfile... chProfiles) {
        setCHProfiles(Arrays.asList(chProfiles));
        return this;
    }

    public CHPreparationHandler setCHProfiles(Collection<CHProfile> chProfiles) {
        this.chProfiles.clear();
        this.chProfiles.addAll(chProfiles);
        return this;
    }

    public List<CHProfile> getCHProfiles() {
        return chProfiles;
    }

    public int getPreparationThreads() {
        return preparationThreads;
    }

    /**
     * This method changes the number of threads used for preparation on import. Default is 1. Make
     * sure that you have enough memory when increasing this number!
     */
    public void setPreparationThreads(int preparationThreads) {
        this.preparationThreads = preparationThreads;
    }

    public Map<String, RoutingCHGraph> load(BaseGraph graph, List<CHConfig> chConfigs) {
        Map<String, RoutingCHGraph> loaded = Collections.synchronizedMap(new LinkedHashMap<>());
        List<Callable<String>> callables = chConfigs.stream()
                .map(c -> (Callable<String>) () -> {
                    CHStorage chStorage = new CHStorage(graph.getDirectory(), c.getName(), graph.getSegmentSize(), c.isEdgeBased());
                    if (chStorage.loadExisting())
                        loaded.put(c.getName(), RoutingCHGraphImpl.fromGraph(graph, chStorage, c));
                    else {
                        // todo: this is ugly, see comments in LMPreparationHandler
                        graph.getDirectory().remove("nodes_ch_" + c.getName());
                        graph.getDirectory().remove("shortcuts_" + c.getName());
                    }
                    return c.getName();
                })
                .collect(Collectors.toList());
        GHUtility.runConcurrently(callables, preparationThreads);
        return loaded;
    }

    public Map<String, PrepareContractionHierarchies.Result> prepare(BaseGraph baseGraph, StorableProperties properties, List<CHConfig> chConfigs, final boolean closeEarly) {
        if (chConfigs.isEmpty()) {
            LOGGER.info("There are no CHs to prepare");
            return Collections.emptyMap();
        }
        LOGGER.info("Creating CH preparations, {}", getMemInfo());
        List<PrepareContractionHierarchies> preparations = chConfigs.stream()
                .map(c -> createCHPreparation(baseGraph, c))
                .collect(Collectors.toList());
        Map<String, PrepareContractionHierarchies.Result> results = Collections.synchronizedMap(new LinkedHashMap<>());
        List<Callable<String>> callables = new ArrayList<>(preparations.size());
        for (int i = 0; i < preparations.size(); ++i) {
            PrepareContractionHierarchies prepare = preparations.get(i);
            LOGGER.info((i + 1) + "/" + preparations.size() + " calling " +
                    "CH prepare.doWork for profile '" + prepare.getCHConfig().getName() + "' " + prepare.getCHConfig().getTraversalMode() + " ... (" + getMemInfo() + ")");
            callables.add(() -> {
                final String name = prepare.getCHConfig().getName();
                // toString is not taken into account so we need to cheat, see http://stackoverflow.com/q/6113746/194609 for other options
                Thread.currentThread().setName(name);
                PrepareContractionHierarchies.Result result = prepare.doWork();
                results.put(name, result);
                prepare.flush();
                if (closeEarly)
                    prepare.close();
                properties.put(CH.PREPARE + "date." + name, createFormatter().format(new Date()));
                return name;
            });
        }
        GHUtility.runConcurrently(callables, preparationThreads);
        LOGGER.info("Finished CH preparation, {}", getMemInfo());
        return results;
    }

    private PrepareContractionHierarchies createCHPreparation(BaseGraph graph, CHConfig chConfig) {
        PrepareContractionHierarchies pch = PrepareContractionHierarchies.fromGraph(graph, chConfig);
        pch.setParams(pMap);
        return pch;
    }
}
