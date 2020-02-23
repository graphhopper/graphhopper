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
import com.graphhopper.config.CHProfileConfig;
import com.graphhopper.routing.RoutingAlgorithmFactory;
import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.storage.CHProfile;
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
 * This class handles the different CH preparations and serves the corresponding {@link RoutingAlgorithmFactory}
 *
 * @author Peter Karich
 * @author easbar
 */
public class CHPreparationHandler {
    private final Logger LOGGER = LoggerFactory.getLogger(getClass());
    private final List<PrepareContractionHierarchies> preparations = new ArrayList<>();
    // we first add the profile configs and later read them to create the actual profile objects (because they require
    // the actual Weightings)
    private final List<CHProfileConfig> chProfileConfigs = new ArrayList<>();
    private final List<CHProfile> chProfiles = new ArrayList<>();
    private boolean disablingAllowed = false;
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
            throw new IllegalStateException("Use profiles_ch instead of prepare.chWeighting, prepare.chWeightings or prepare.ch.weightings, see #1922");
        if (ghConfig.has("prepare.ch.edge_based"))
            throw new IllegalStateException("Use profiles_ch instead of prepare.ch.edge_based, see #1922");

        setPreparationThreads(ghConfig.getInt(CH.PREPARE + "threads", getPreparationThreads()));
        setDisablingAllowed(ghConfig.getBool(CH.INIT_DISABLING_ALLOWED, isDisablingAllowed()));
        setCHProfileConfigs(ghConfig.getCHProfiles());
        pMap = ghConfig.asPMap();
    }

    public final boolean isEnabled() {
        return !chProfileConfigs.isEmpty() || !chProfiles.isEmpty() || !preparations.isEmpty();
    }

    public final boolean isDisablingAllowed() {
        return disablingAllowed;
    }

    /**
     * This method specifies if it is allowed to disable CH routing at runtime via routing hints.
     */
    public final CHPreparationHandler setDisablingAllowed(boolean disablingAllowed) {
        this.disablingAllowed = disablingAllowed;
        return this;
    }

    /**
     * Decouple CH profiles from PrepareContractionHierarchies as we need CH profiles for the
     * graphstorage and the graphstorage for the preparation.
     */
    public CHPreparationHandler addCHProfile(CHProfile chProfile) {
        chProfiles.add(chProfile);
        return this;
    }

    public CHPreparationHandler addPreparation(PrepareContractionHierarchies pch) {
        // we want to make sure that CH preparations are added in the same order as their corresponding profiles
        if (preparations.size() >= chProfiles.size()) {
            throw new IllegalStateException("You need to add the corresponding CH profiles before adding preparations.");
        }
        CHProfile expectedProfile = chProfiles.get(preparations.size());
        if (!pch.getCHProfile().equals(expectedProfile)) {
            throw new IllegalArgumentException("CH profile of preparation: " + pch + " needs to be identical to previously added CH profile: " + expectedProfile);
        }
        preparations.add(pch);
        return this;
    }

    public final boolean hasCHProfiles() {
        return !chProfiles.isEmpty();
    }

    public List<CHProfile> getCHProfiles() {
        return chProfiles;
    }

    public List<CHProfile> getNodeBasedCHProfiles() {
        List<CHProfile> result = new ArrayList<>();
        for (CHProfile chProfile : chProfiles) {
            if (!chProfile.getTraversalMode().isEdgeBased()) {
                result.add(chProfile);
            }
        }
        return result;
    }

    public List<CHProfile> getEdgeBasedCHProfiles() {
        List<CHProfile> result = new ArrayList<>();
        for (CHProfile chProfile : chProfiles) {
            if (chProfile.getTraversalMode().isEdgeBased()) {
                result.add(chProfile);
            }
        }
        return result;
    }

    public CHPreparationHandler setCHProfileConfigs(CHProfileConfig... chProfileConfigs) {
        setCHProfileConfigs(Arrays.asList(chProfileConfigs));
        return this;
    }

    /**
     * Enables the use of contraction hierarchies to reduce query times.
     * "fastest|u_turn_costs=30 or your own weight-calculation type.
     */
    public CHPreparationHandler setCHProfileConfigs(Collection<CHProfileConfig> chProfileConfigs) {
        this.chProfileConfigs.clear();
        this.chProfileConfigs.addAll(chProfileConfigs);
        return this;
    }

    public List<CHProfileConfig> getCHProfileConfigs() {
        return chProfileConfigs;
    }

    public List<PrepareContractionHierarchies> getPreparations() {
        return preparations;
    }

    /**
     * @return a {@link RoutingAlgorithmFactory} for CH or throw an error if no preparation is available for the given
     * hints
     */
    public RoutingAlgorithmFactory getAlgorithmFactory(HintsMap map) {
        if (preparations.isEmpty())
            throw new IllegalStateException("No CH preparations added yet");
        return getPreparation(map).getRoutingAlgorithmFactory();
    }

    public PrepareContractionHierarchies getPreparation(HintsMap map) {
        CHProfile selectedProfile = selectProfile(map);
        return getPreparation(selectedProfile);
    }

    public PrepareContractionHierarchies getPreparation(CHProfile chProfile) {
        for (PrepareContractionHierarchies p : getPreparations()) {
            if (p.getCHProfile().equals(chProfile)) {
                return p;
            }
        }
        throw new IllegalStateException("Could not find CH preparation for profile: " + chProfile);
    }

    private CHProfile selectProfile(HintsMap map) {
        try {
            return CHProfileSelector.select(chProfiles, map);
        } catch (CHProfileSelectionException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
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
        for (final PrepareContractionHierarchies prepare : getPreparations()) {
            LOGGER.info((++counter) + "/" + getPreparations().size() + " calling " +
                    "CH prepare.doWork for " + prepare.getCHProfile() + " ... (" + getMemInfo() + ")");
            final String name = prepare.getCHProfile().toFileName();
            completionService.submit(new Runnable() {
                @Override
                public void run() {
                    // toString is not taken into account so we need to cheat, see http://stackoverflow.com/q/6113746/194609 for other options
                    Thread.currentThread().setName(name);
                    prepare.doWork();
                    if (closeEarly)
                        prepare.close();

                    properties.put(CH.PREPARE + "date." + name, createFormatter().format(new Date()));
                }
            }, name);
        }

        threadPool.shutdown();

        try {
            for (int i = 0; i < getPreparations().size(); i++) {
                completionService.take().get();
            }
        } catch (Exception e) {
            threadPool.shutdownNow();
            throw new RuntimeException(e);
        }
    }

    public void createPreparations(GraphHopperStorage ghStorage) {
        if (!isEnabled() || !getPreparations().isEmpty())
            return;
        if (!hasCHProfiles())
            throw new IllegalStateException("No CH profiles found");

        for (CHProfile chProfile : chProfiles) {
            addPreparation(createCHPreparation(ghStorage, chProfile));
        }
    }

    private PrepareContractionHierarchies createCHPreparation(GraphHopperStorage ghStorage, CHProfile chProfile) {
        PrepareContractionHierarchies pch = PrepareContractionHierarchies.fromGraphHopperStorage(ghStorage, chProfile);
        pch.setParams(pMap);
        return pch;
    }
}
