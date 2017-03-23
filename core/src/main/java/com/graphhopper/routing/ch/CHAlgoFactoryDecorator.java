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
import com.graphhopper.routing.weighting.GenericWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.*;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.Helper;
import com.graphhopper.util.Parameters.CH;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

import static com.graphhopper.util.Parameters.CH.DISABLE;

/**
 * This class implements the CH decorator and provides several helper methods related to CH
 * preparation and its vehicle profiles.
 *
 * @author Peter Karich
 */
public class CHAlgoFactoryDecorator implements RoutingAlgorithmFactoryDecorator {
    private final Logger LOGGER = LoggerFactory.getLogger(getClass());
    private final List<PrepareContractionHierarchies> preparations = new ArrayList<>();
    // we need to decouple weighting objects from the weighting list of strings 
    // as we need the strings to create the GraphHopperStorage and the GraphHopperStorage to create the preparations from the Weighting objects currently requiring the encoders
    private final List<Weighting> weightings = new ArrayList<>();
    private final Set<String> weightingsAsStrings = new LinkedHashSet<>();
    private boolean disablingAllowed = false;
    // for backward compatibility enable CH by default.
    private boolean enabled = true;
    private int preparationThreads;
    private ExecutorService threadPool;
    private int preparationPeriodicUpdates = -1;
    private int preparationLazyUpdates = -1;
    private int preparationNeighborUpdates = -1;
    private int preparationContractedNodes = -1;
    private double preparationLogMessages = -1;

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

        if ("no".equals(chWeightingsStr)) {
            // default is fastest and we need to clear this explicitely
            weightingsAsStrings.clear();
        } else if (!chWeightingsStr.isEmpty()) {
            List<String> tmpCHWeightingList = Arrays.asList(chWeightingsStr.split(","));
            setWeightingsAsStrings(tmpCHWeightingList);
        }

        boolean enableThis = !weightingsAsStrings.isEmpty();
        setEnabled(enableThis);
        if (enableThis)
            setDisablingAllowed(args.getBool(CH.INIT_DISABLING_ALLOWED, isDisablingAllowed()));

        setPreparationPeriodicUpdates(args.getInt(CH.PREPARE + "updates.periodic", getPreparationPeriodicUpdates()));
        setPreparationLazyUpdates(args.getInt(CH.PREPARE + "updates.lazy", getPreparationLazyUpdates()));
        setPreparationNeighborUpdates(args.getInt(CH.PREPARE + "updates.neighbor", getPreparationNeighborUpdates()));
        setPreparationContractedNodes(args.getInt(CH.PREPARE + "contracted_nodes", getPreparationContractedNodes()));
        setPreparationLogMessages(args.getDouble(CH.PREPARE + "log_messages", getPreparationLogMessages()));
    }

    public int getPreparationPeriodicUpdates() {
        return preparationPeriodicUpdates;
    }

    public CHAlgoFactoryDecorator setPreparationPeriodicUpdates(int preparePeriodicUpdates) {
        this.preparationPeriodicUpdates = preparePeriodicUpdates;
        return this;
    }

    public int getPreparationContractedNodes() {
        return preparationContractedNodes;
    }

    public CHAlgoFactoryDecorator setPreparationContractedNodes(int prepareContractedNodes) {
        this.preparationContractedNodes = prepareContractedNodes;
        return this;
    }

    public int getPreparationLazyUpdates() {
        return preparationLazyUpdates;
    }

    public CHAlgoFactoryDecorator setPreparationLazyUpdates(int prepareLazyUpdates) {
        this.preparationLazyUpdates = prepareLazyUpdates;
        return this;
    }

    public double getPreparationLogMessages() {
        return preparationLogMessages;
    }

    public CHAlgoFactoryDecorator setPreparationLogMessages(double prepareLogMessages) {
        this.preparationLogMessages = prepareLogMessages;
        return this;
    }

    public int getPreparationNeighborUpdates() {
        return preparationNeighborUpdates;
    }

    public CHAlgoFactoryDecorator setPreparationNeighborUpdates(int prepareNeighborUpdates) {
        this.preparationNeighborUpdates = prepareNeighborUpdates;
        return this;
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
     * Decouple weightings from PrepareContractionHierarchies as we need weightings for the
     * graphstorage and the graphstorage for the preparation.
     */
    public CHAlgoFactoryDecorator addWeighting(Weighting weighting) {
        weightings.add(weighting);
        return this;
    }

    public CHAlgoFactoryDecorator addWeighting(String weighting) {
        weightingsAsStrings.add(weighting);
        return this;
    }

    public CHAlgoFactoryDecorator addPreparation(PrepareContractionHierarchies pch) {
        preparations.add(pch);
        int lastIndex = preparations.size() - 1;
        if (lastIndex >= weightings.size())
            throw new IllegalStateException("Cannot access weighting for PrepareContractionHierarchies with " + pch.getWeighting()
                    + ". Call add(Weighting) before");

        if (preparations.get(lastIndex).getWeighting() != weightings.get(lastIndex))
            throw new IllegalArgumentException("Weighting of PrepareContractionHierarchies " + preparations.get(lastIndex).getWeighting()
                    + " needs to be identical to previously added " + weightings.get(lastIndex));
        return this;
    }

    public final boolean hasWeightings() {
        return !weightings.isEmpty();
    }

    public final List<Weighting> getWeightings() {
        return weightings;
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
            strWeighting = strWeighting.toLowerCase();
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
        boolean forceFlexMode = map.getBool(DISABLE, false);
        if (!isEnabled() || forceFlexMode)
            return defaultAlgoFactory;

        if (preparations.isEmpty())
            throw new IllegalStateException("No preparations added to this decorator");

        if (map.getWeighting().isEmpty())
            map.setWeighting(getDefaultWeighting());

        String entriesStr = "";
        for (PrepareContractionHierarchies p : preparations) {
            if (p.getWeighting().matches(map))
                return p;

            entriesStr += p.getWeighting() + ", ";
        }

        throw new IllegalArgumentException("Cannot find CH RoutingAlgorithmFactory for weighting map " + map + " in entries " + entriesStr);
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
            LOGGER.info((++counter) + "/" + getPreparations().size() + " calling CH prepare.doWork for " + prepare.getWeighting() + " ... (" + Helper.getMemInfo() + ")");
            final String name = AbstractWeighting.weightingToFileName(prepare.getWeighting());
            completionService.submit(new Runnable() {
                @Override
                public void run() {
                    String errorKey = CH.PREPARE + "error." + name;
                    try {
                        // toString is not taken into account so we need to cheat, see http://stackoverflow.com/q/6113746/194609 for other options
                        Thread.currentThread().setName(name);
                        properties.put(errorKey, "CH preparation incomplete");
                        prepare.doWork();
                        properties.remove(errorKey);
                        properties.put(CH.PREPARE + "date." + name, Helper.createFormatter().format(new Date()));
                    } catch (Exception ex) {
                        LOGGER.error("Problem while CH preparation " + name, ex);
                        properties.put(errorKey, ex.getMessage());
                        throw ex;
                    }
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

    public void createPreparations(GraphHopperStorage ghStorage, TraversalMode traversalMode) {
        if (!isEnabled() || !preparations.isEmpty())
            return;
        if (weightings.isEmpty())
            throw new IllegalStateException("No CH weightings found");

        traversalMode = getNodeBase();

        for (Weighting weighting : getWeightings()) {
            if (weighting instanceof GenericWeighting) {
                ((GenericWeighting) weighting).setGraph(ghStorage);
            }
            PrepareContractionHierarchies tmpPrepareCH = new PrepareContractionHierarchies(
                    new GHDirectory("", DAType.RAM_INT), ghStorage, ghStorage.getGraph(CHGraph.class, weighting),
                    weighting, traversalMode);
            tmpPrepareCH.setPeriodicUpdates(preparationPeriodicUpdates).
                    setLazyUpdates(preparationLazyUpdates).
                    setNeighborUpdates(preparationNeighborUpdates).
                    setLogMessages(preparationLogMessages);

            addPreparation(tmpPrepareCH);
        }
    }

    /**
     * For now only node based will work, later on we can easily find usage of this method to remove
     * it.
     */
    public TraversalMode getNodeBase() {
        return TraversalMode.NODE_BASED;
    }
}
