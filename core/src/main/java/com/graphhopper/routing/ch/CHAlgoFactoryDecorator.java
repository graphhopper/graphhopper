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
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.AbstractWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.StorableProperties;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.PMap;
import com.graphhopper.util.Parameters;
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
    // we need to decouple weighting objects from the weighting list of strings 
    // as we need the strings to create the GraphHopperStorage and the GraphHopperStorage to create the preparations from the Weighting objects currently requiring the encoders
    private final List<Weighting> nodeBasedWeightings = new ArrayList<>();
    private final List<Weighting> edgeBasedWeightings = new ArrayList<>();
    private final Set<String> weightingsAsStrings = new LinkedHashSet<>();
    private boolean disablingAllowed = false;
    // for backward compatibility enable CH by default.
    private boolean enabled = true;
    private EdgeBasedCHMode edgeBasedCHMode = EdgeBasedCHMode.OFF;
    private int preparationThreads;
    private ExecutorService threadPool;
    private PMap pMap = new PMap();

    public CHAlgoFactoryDecorator() {
        setPreparationThreads(1);
        setWeightingsAsStrings(Arrays.asList(getDefaultWeighting()));
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

        if ("no".equals(chWeightingsStr) || "false".equals(chWeightingsStr)) {
            // default is fastest and we need to clear this explicitly
            weightingsAsStrings.clear();
        } else if (!chWeightingsStr.isEmpty()) {
            List<String> tmpCHWeightingList = Arrays.asList(chWeightingsStr.split(","));
            setWeightingsAsStrings(tmpCHWeightingList);
        }

        boolean enableThis = !weightingsAsStrings.isEmpty();
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
     * Decouple weightings from PrepareContractionHierarchies as we need weightings for the
     * graphstorage and the graphstorage for the preparation.
     */
    public CHAlgoFactoryDecorator addNodeBasedWeighting(Weighting weighting) {
        nodeBasedWeightings.add(weighting);
        return this;
    }

    public CHAlgoFactoryDecorator addEdgeBasedWeighting(Weighting weighting) {
        edgeBasedWeightings.add(weighting);
        return this;
    }

    public CHAlgoFactoryDecorator addWeighting(String weighting) {
        weightingsAsStrings.add(weighting);
        return this;
    }

    public CHAlgoFactoryDecorator addPreparation(PrepareContractionHierarchies pch) {
        // we want to make sure that edge- and node-based preparations are added in the same order as their corresponding
        // weightings, but changing the order between edge- and node-based preparations is accepted
        int index = 0;
        for (PrepareContractionHierarchies p : preparations) {
            if (p.isEdgeBased() == pch.isEdgeBased()) {
                index++;
            }
        }
        List<Weighting> weightings = pch.isEdgeBased() ? edgeBasedWeightings : nodeBasedWeightings;
        if (index >= weightings.size())
            throw new IllegalStateException("Cannot access weighting for PrepareContractionHierarchies with " + pch.getWeighting()
                    + ". Call add(Weighting) before");

        Weighting expectedWeighting = weightings.get(index);
        if (pch.getWeighting() != expectedWeighting)
            throw new IllegalArgumentException("Weighting of PrepareContractionHierarchies " + pch
                    + " needs to be identical to previously added " + expectedWeighting);

        preparations.add(pch);
        return this;
    }

    public final boolean hasWeightings() {
        return !nodeBasedWeightings.isEmpty() || !edgeBasedWeightings.isEmpty();
    }

    public final List<Weighting> getNodeBasedWeightings() {
        return nodeBasedWeightings;
    }

    public final List<Weighting> getEdgeBasedWeightings() {
        return edgeBasedWeightings;
    }

    public EdgeBasedCHMode getEdgeBasedCHMode() {
        return edgeBasedCHMode;
    }

    public CHAlgoFactoryDecorator setWeightingsAsStrings(String... weightingNames) {
        return setWeightingsAsStrings(Arrays.asList(weightingNames));
    }

    public List<String> getWeightingsAsStrings() {
        if (this.weightingsAsStrings.isEmpty())
            throw new IllegalStateException("Potential bug: weightingsAsStrings is empty");

        return new ArrayList<>(this.weightingsAsStrings);
    }

    /**
     * Enables the use of contraction hierarchies to reduce query times. Enabled by default.
     *
     * @param weightingList A list containing multiple weightings like: "fastest", "shortest" or
     *                      your own weight-calculation type.
     */
    public CHAlgoFactoryDecorator setWeightingsAsStrings(List<String> weightingList) {
        if (weightingList.isEmpty())
            throw new IllegalArgumentException("It is not allowed to pass an emtpy weightingList");

        weightingsAsStrings.clear();
        for (String strWeighting : weightingList) {
            strWeighting = toLowerCase(strWeighting);
            strWeighting = strWeighting.trim();
            addWeighting(strWeighting);
        }
        return this;
    }

    private String getDefaultWeighting() {
        return weightingsAsStrings.isEmpty() ? "fastest" : weightingsAsStrings.iterator().next();
    }

    public List<PrepareContractionHierarchies> getPreparations() {
        return preparations;
    }

    @Override
    public RoutingAlgorithmFactory getDecoratedAlgorithmFactory(RoutingAlgorithmFactory defaultAlgoFactory, HintsMap map) {
        boolean disableCH = map.getBool(DISABLE, false);
        if (!isEnabled() || disablingAllowed && disableCH)
            return defaultAlgoFactory;

        List<PrepareContractionHierarchies> allPreparations = getPreparations();
        if (allPreparations.isEmpty())
            throw new IllegalStateException("No preparations added to this decorator");

        if (map.getWeighting().isEmpty())
            map.setWeighting(getDefaultWeighting());

        return getPreparation(map);
    }

    public PrepareContractionHierarchies getPreparation(HintsMap map) {
        boolean edgeBased = map.getBool(Parameters.Routing.EDGE_BASED, false);
        List<String> entriesStrs = new ArrayList<>();
        boolean weightingMatchesButNotEdgeBased = false;
        for (PrepareContractionHierarchies p : getPreparations()) {
            boolean weightingMatches = p.getWeighting().matches(map);
            if (p.isEdgeBased() == edgeBased && weightingMatches)
                return p;
            else if (weightingMatches)
                weightingMatchesButNotEdgeBased = true;

            entriesStrs.add(p.getWeighting() + "|" + (p.isEdgeBased() ? "edge" : "node"));
        }

        String hint = weightingMatchesButNotEdgeBased
                ? " The '" + Parameters.Routing.EDGE_BASED + "' url parameter is missing or does not fit the weightings. Its value was: '" + edgeBased + "'"
                : "";
        throw new IllegalArgumentException("Cannot find CH RoutingAlgorithmFactory for weighting map " + map + " in entries: " + entriesStrs + "." + hint);
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

    public void prepare(final StorableProperties properties) {
        ExecutorCompletionService completionService = new ExecutorCompletionService<>(threadPool);
        int counter = 0;
        for (final PrepareContractionHierarchies prepare : getPreparations()) {
            LOGGER.info((++counter) + "/" + getPreparations().size() + " calling " +
                    (prepare.isEdgeBased() ? "edge" : "node") + "-based CH prepare.doWork for " + prepare.getWeighting() + " ... (" + getMemInfo() + ")");
            final String name = AbstractWeighting.weightingToFileName(prepare.getWeighting(), prepare.isEdgeBased());
            completionService.submit(new Runnable() {
                @Override
                public void run() {
                    // toString is not taken into account so we need to cheat, see http://stackoverflow.com/q/6113746/194609 for other options
                    Thread.currentThread().setName(name);
                    prepare.doWork();
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
        if (!hasWeightings())
            throw new IllegalStateException("No CH weightings found");

        for (Weighting weighting : nodeBasedWeightings) {
            addPreparation(createCHPreparation(ghStorage, weighting, TraversalMode.NODE_BASED));
        }
        for (Weighting weighting : edgeBasedWeightings) {
            addPreparation(createCHPreparation(ghStorage, weighting, TraversalMode.EDGE_BASED_2DIR));
        }
    }

    private PrepareContractionHierarchies createCHPreparation(GraphHopperStorage ghStorage, Weighting weighting,
                                                              TraversalMode traversalMode) {
        PrepareContractionHierarchies tmpPrepareCH = PrepareContractionHierarchies.fromGraphHopperStorage(
                ghStorage, weighting, traversalMode);
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
