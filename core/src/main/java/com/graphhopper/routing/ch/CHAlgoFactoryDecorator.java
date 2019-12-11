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

import com.graphhopper.routing.RoutingAlgorithmFactory;
import com.graphhopper.routing.RoutingAlgorithmFactoryDecorator;
import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.storage.CHProfile;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.StorableProperties;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.PMap;
import com.graphhopper.util.Parameters.CH;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;

import static com.graphhopper.util.Helper.*;
import static com.graphhopper.util.Parameters.CH.DISABLE;

/**
 * This class implements the CH decorator for the routing algorithm factory and provides several
 * helper methods related to CH preparation and its vehicle profiles.
 *
 * @author Peter Karich
 */
public class CHAlgoFactoryDecorator implements RoutingAlgorithmFactoryDecorator {
    private final Logger LOGGER = LoggerFactory.getLogger(getClass());
    private final List<PrepareContractionHierarchies> preparations = new ArrayList<>();
    // we need to decouple the CH profile objects from the list of CH profile strings
    // as we need the strings to create the GraphHopperStorage and the GraphHopperStorage to create the preparations
    // from the CHProfile objects currently requiring the encoders
    private final List<CHProfile> chProfiles = new ArrayList<>();
    private final Set<String> chProfileStrings = new LinkedHashSet<>();
    private boolean disablingAllowed = false;
    // for backward compatibility enable CH by default.
    private boolean enabled = true;
    private EdgeBasedCHMode edgeBasedCHMode = EdgeBasedCHMode.OFF;
    private int preparationThreads;
    private ExecutorService threadPool;
    private PMap pMap = new PMap();

    public CHAlgoFactoryDecorator() {
        setPreparationThreads(1);
        // use fastest by default
        setCHProfilesAsStrings(Collections.singletonList("fastest"));
    }

    @Override
    public void init(CmdArgs args) {
        // throw explicit error for deprecated configs
        if (!args.get("prepare.threads", "").isEmpty())
            throw new IllegalStateException("Use " + CH.PREPARE + "threads instead of prepare.threads");
        if (!args.get("prepare.chWeighting", "").isEmpty() || !args.get("prepare.chWeightings", "").isEmpty())
            throw new IllegalStateException("Use " + CH.PREPARE + "weightings and a comma separated list instead of prepare.chWeighting or prepare.chWeightings");

        setPreparationThreads(args.getInt(CH.PREPARE + "threads", getPreparationThreads()));

        // default is enabled & fastest
        String chWeightingsStr = args.get(CH.PREPARE + "weightings", "");
        if (chWeightingsStr.contains("edge_based")) {
            throw new IllegalArgumentException("Adding 'edge_based` to " + (CH.PREPARE + "weightings") + " is not allowed, to enable edge-based CH use " + (CH.PREPARE + "edge_based") + " instead.");
        }

        if ("no".equals(chWeightingsStr) || "false".equals(chWeightingsStr)) {
            // default is fastest and we need to clear this explicitly
            chProfileStrings.clear();
        } else if (!chWeightingsStr.isEmpty()) {
            setCHProfilesAsStrings(Arrays.asList(chWeightingsStr.split(",")));
        }

        boolean enableThis = !chProfileStrings.isEmpty();
        setEnabled(enableThis);
        if (enableThis)
            setDisablingAllowed(args.getBool(CH.INIT_DISABLING_ALLOWED, isDisablingAllowed()));

        String edgeBasedCHStr = args.get(CH.PREPARE + "edge_based", "off").trim();
        edgeBasedCHStr = edgeBasedCHStr.equals("false") ? "off" : edgeBasedCHStr;
        edgeBasedCHMode = EdgeBasedCHMode.valueOf(edgeBasedCHStr.toUpperCase(Locale.ROOT));

        pMap = args;
    }

    @Override
    public final boolean isEnabled() {
        return enabled;
    }

    /**
     * Enables or disables contraction hierarchies (CH). This speed-up mode is enabled by default.
     */
    public final CHAlgoFactoryDecorator setEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public final boolean isDisablingAllowed() {
        return disablingAllowed || !isEnabled();
    }

    /**
     * This method specifies if it is allowed to disable CH routing at runtime via routing hints.
     */
    public final CHAlgoFactoryDecorator setDisablingAllowed(boolean disablingAllowed) {
        this.disablingAllowed = disablingAllowed;
        return this;
    }

    /**
     * This method specifies whether or not edge-based CH preparation (needed for turn costs) should be performed.
     *
     * @see EdgeBasedCHMode
     */
    public final CHAlgoFactoryDecorator setEdgeBasedCHMode(EdgeBasedCHMode edgeBasedCHMode) {
        this.edgeBasedCHMode = edgeBasedCHMode;
        return this;
    }

    /**
     * Decouple CH profiles from PrepareContractionHierarchies as we need CH profiles for the
     * graphstorage and the graphstorage for the preparation.
     */
    public CHAlgoFactoryDecorator addCHProfile(CHProfile chProfile) {
        chProfiles.add(chProfile);
        return this;
    }

    public CHAlgoFactoryDecorator addPreparation(PrepareContractionHierarchies pch) {
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

    public EdgeBasedCHMode getEdgeBasedCHMode() {
        return edgeBasedCHMode;
    }

    public List<String> getCHProfileStrings() {
        if (chProfileStrings.isEmpty())
            throw new IllegalStateException("Potential bug: chProfileStrings is empty");

        return new ArrayList<>(chProfileStrings);
    }

    public CHAlgoFactoryDecorator setCHProfileStrings(String... profileStrings) {
        return setCHProfilesAsStrings(Arrays.asList(profileStrings));
    }

    /**
     * @param profileStrings A list of multiple CH profile strings
     * @see #addCHProfileAsString(String)
     */
    public CHAlgoFactoryDecorator setCHProfilesAsStrings(List<String> profileStrings) {
        if (profileStrings.isEmpty())
            throw new IllegalArgumentException("It is not allowed to pass an empty list of CH profile strings");

        chProfileStrings.clear();
        for (String profileString : profileStrings) {
            profileString = toLowerCase(profileString);
            profileString = profileString.trim();
            addCHProfileAsString(profileString);
        }
        return this;
    }

    /**
     * Enables the use of contraction hierarchies to reduce query times. Enabled by default.
     *
     * @param profileString String representation of a CH profile like: "fastest", "shortest|edge_based=true",
     *                      "fastest|u_turn_costs=30 or your own weight-calculation type.
     */
    public CHAlgoFactoryDecorator addCHProfileAsString(String profileString) {
        chProfileStrings.add(profileString);
        return this;
    }

    public List<PrepareContractionHierarchies> getPreparations() {
        return preparations;
    }

    @Override
    public RoutingAlgorithmFactory getDecoratedAlgorithmFactory(RoutingAlgorithmFactory defaultAlgoFactory, HintsMap map) {
        boolean disableCH = map.getBool(DISABLE, false);
        if (!isEnabled() || disablingAllowed && disableCH)
            return defaultAlgoFactory;

        if (preparations.isEmpty())
            throw new IllegalStateException("No preparations added to this decorator");

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
        PrepareContractionHierarchies tmpPrepareCH = PrepareContractionHierarchies.fromGraphHopperStorage(ghStorage, chProfile);
        tmpPrepareCH.setParams(pMap);
        return tmpPrepareCH;
    }

    /**
     * Determines whether or not edge-based CH will be prepared for the different weightings/encoders.
     */
    public enum EdgeBasedCHMode {
        /**
         * no edge-based CH preparation will be performed
         */
        OFF,
        /**
         * for encoders with enabled turn costs edge-based CH and otherwise node-based CH preparation will be performed
         */
        EDGE_OR_NODE,
        /**
         * for encoders with enabled turn costs edge-based CH will be performed and node-based CH preparation will be
         * performed for all encoders
         */
        EDGE_AND_NODE
    }
}
