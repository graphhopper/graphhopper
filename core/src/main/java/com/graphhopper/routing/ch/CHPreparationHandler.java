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
import com.graphhopper.storage.CHConfig;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.StorableProperties;
import com.graphhopper.util.PMap;
import com.graphhopper.util.Parameters.CH;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;

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
    private final List<PrepareContractionHierarchies> preparations = new ArrayList<>();
    // we first add the profiles and later read them to create the config objects (because they require
    // the actual Weightings)
    private final List<CHProfile> chProfiles = new ArrayList<>();
    private int preparationThreads;
    private ExecutorService threadPool;
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
        return !chProfiles.isEmpty() || !preparations.isEmpty();
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

    public List<PrepareContractionHierarchies> getPreparations() {
        return preparations;
    }

    public PrepareContractionHierarchies getPreparation(String chGraphName) {
        if (preparations.isEmpty())
            throw new IllegalStateException("No CH preparations added yet");
        List<String> profileNames = new ArrayList<>(preparations.size());
        for (PrepareContractionHierarchies preparation : preparations) {
            profileNames.add(preparation.getCHConfig().getName());
            if (preparation.getCHConfig().getName().equalsIgnoreCase(chGraphName)) {
                return preparation;
            }
        }
        throw new IllegalArgumentException("Cannot find CH preparation for the requested profile: '" + chGraphName + "'" +
                "\nYou can try disabling CH using " + CH.DISABLE + "=true" +
                "\navailable CH profiles: " + profileNames);
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
        this.threadPool = java.util.concurrent.Executors.newFixedThreadPool(preparationThreads);
    }

    public void prepare(final StorableProperties properties, final boolean closeEarly) {
        ExecutorCompletionService<String> completionService = new ExecutorCompletionService<>(threadPool);
        int counter = 0;
        for (final PrepareContractionHierarchies prepare : preparations) {
            LOGGER.info((++counter) + "/" + preparations.size() + " calling " +
                    "CH prepare.doWork for profile '" + prepare.getCHConfig().getName() + "' " + prepare.getCHConfig().getTraversalMode() + " ... (" + getMemInfo() + ")");
            final String name = prepare.getCHConfig().getName();
            completionService.submit(() -> {
                // toString is not taken into account so we need to cheat, see http://stackoverflow.com/q/6113746/194609 for other options
                Thread.currentThread().setName(name);
                prepare.doWork();
                if (closeEarly)
                    prepare.close();

                properties.put(CH.PREPARE + "date." + name, createFormatter().format(new Date()));
            }, name);
        }

        threadPool.shutdown();

        try {
            for (int i = 0; i < preparations.size(); i++) {
                completionService.take().get();
            }
        } catch (Exception e) {
            threadPool.shutdownNow();
            throw new RuntimeException(e);
        }
        LOGGER.info("Finished CH preparation, {}", getMemInfo());
    }

    public void createPreparations(GraphHopperStorage ghStorage) {
        if (!preparations.isEmpty())
            throw new IllegalStateException("CH preparations were created already");
        LOGGER.info("Creating CH preparations, {}", getMemInfo());
        for (CHConfig chConfig : ghStorage.getCHConfigs()) {
            preparations.add(createCHPreparation(ghStorage, chConfig));
        }
    }

    private PrepareContractionHierarchies createCHPreparation(GraphHopperStorage ghStorage, CHConfig chConfig) {
        PrepareContractionHierarchies pch = PrepareContractionHierarchies.fromGraphHopperStorage(ghStorage, chConfig);
        pch.setParams(pMap);
        return pch;
    }
}
