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
package com.graphhopper.routing.ar;

import com.graphhopper.routing.*;
import com.graphhopper.routing.ch.CHAlgoFactoryDecorator;
import com.graphhopper.routing.lm.LMAlgoFactoryDecorator;
import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.routing.weighting.AbstractWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.StorableProperties;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.PMap;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.graphhopper.util.Helper.createFormatter;
import static com.graphhopper.util.Helper.getMemInfo;
import static com.graphhopper.util.Helper.toLowerCase;

/**
 * This class implements the decorator for advanced alternative routes.
 *
 * @author Maximilian Sturm
 */
public class ARAlgoFactoryDecorator implements RoutingAlgorithmFactoryDecorator {
    private final Logger LOGGER = LoggerFactory.getLogger(getClass());
    private final List<PrepareAlternativeRoute> preparations = new ArrayList<>();

    // each weighting in this list should also be part of the CHAlgoFactoryDecorator's list. Otherwise AR will be
    // prepared for this weighting without using CH resulting in very long preparation times
    private final List<Weighting> weightings = new ArrayList<>();
    private final List<String> weightingsAsStrings = new ArrayList<>();
    private boolean disablingAllowed = false;
    private boolean enabled = true;
    private int preparationThreads;
    private ExecutorService threadPool;

    private double maxWeightFactor = 1.4;
    private double maxShareFactor = 0.6;
    private int maxPaths = 3;
    private int additionalPaths = 3;
    private int areas = -1;
    private PMap pMap = new PMap();

    public ARAlgoFactoryDecorator() {
        setPreparationThreads(1);
        setWeightingsAsStrings(Arrays.asList(getDefaultWeighting()));
    }

    @Override
    public void init(CmdArgs args) {
        setPreparationThreads(args.getInt(Parameters.AR.PREPARE + "threads", getPreparationThreads()));

        maxWeightFactor = args.getDouble(Parameters.AR.MAX_WEIGHT, maxWeightFactor);
        maxShareFactor = args.getDouble(Parameters.AR.MAX_SHARE, maxShareFactor);
        maxPaths = args.getInt(Parameters.AR.MAX_PATHS, maxPaths);
        additionalPaths = args.getInt(Parameters.AR.ADDITIONAL_PATHS, additionalPaths);

        // preparation for less than 16 areas doesn't make sense because too many areas would be directly connected to
        // each other
        areas = args.getInt(Parameters.AR.AREAS, areas);
        if (areas < 16 && areas != -1)
            throw new IllegalArgumentException("The graph has to be split into at least 16 areas. Use a value bigger" +
                    " or equal to 16 for " + Parameters.AR.AREAS + ". You can also use -1 to get the default value.");

        String arWeightingsStr = args.get(Parameters.AR.PREPARE + "weightings", "");
        if (!arWeightingsStr.isEmpty() && !arWeightingsStr.equalsIgnoreCase("no")) {
            List<String> tmpARWeightingList = Arrays.asList(arWeightingsStr.split(","));
            setWeightingsAsStrings(tmpARWeightingList);
        }

        boolean enableThis = !weightingsAsStrings.isEmpty();
        setEnabled(enableThis);
        if (enableThis)
            setDisablingAllowed(args.getBool(Parameters.AR.INIT_DISABLING_ALLOWED, isDisablingAllowed()));

        pMap = args;
    }

    @Override
    public final boolean isEnabled() {
        return enabled;
    }

    /**
     * Enables or disables advanced alternative routes. This speed-up mode is enabled by default. Disabling this
     * decorator will result in using the normal alternative route algorithm without preparation, taking about 50%-100%
     * longer and finding less alternative routes
     */
    public final ARAlgoFactoryDecorator setEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public final boolean isDisablingAllowed() {
        return disablingAllowed || !isEnabled();
    }

    /**
     * This method specifies if it is allowed to disable AR routing at runtime via routing hints.
     */
    public final ARAlgoFactoryDecorator setDisablingAllowed(boolean disablingAllowed) {
        this.disablingAllowed = disablingAllowed;
        return this;
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

    public final boolean hasWeightings() {
        return !weightings.isEmpty();
    }

    private String getDefaultWeighting() {
        return weightingsAsStrings.isEmpty() ? "fastest" : weightingsAsStrings.iterator().next();
    }

    public final List<Weighting> getWeightings() {
        return weightings;
    }

    public ARAlgoFactoryDecorator addWeighting(Weighting weighting) {
        weightings.add(weighting);
        return this;
    }

    public ARAlgoFactoryDecorator addWeighting(String weighting) {
        weightingsAsStrings.add(weighting);
        return this;
    }

    public List<String> getWeightingsAsStrings() {
        if (this.weightingsAsStrings.isEmpty())
            throw new IllegalStateException("Potential bug: weightingsAsStrings is empty");

        return this.weightingsAsStrings;
    }

    /**
     * Enables the use of advanced alternative routes to reduce query times. Enabled by default.
     *
     * @param weightingList A list containing multiple weightings like: "fastest", "shortest" or
     *                      your own weight-calculation type.
     */
    public ARAlgoFactoryDecorator setWeightingsAsStrings(List<String> weightingList) {
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

    public List<PrepareAlternativeRoute> getPreparations() {
        return preparations;
    }

    public ARAlgoFactoryDecorator addPreparation(PrepareAlternativeRoute par) {
        preparations.add(par);
        int lastIndex = preparations.size() - 1;
        if (lastIndex >= weightings.size())
            throw new IllegalStateException("Cannot access weighting for PrepareAlternativeRoute with " + par.getWeighting()
                    + ". Call add(Weighting) before");

        if (preparations.get(lastIndex).getWeighting() != weightings.get(lastIndex))
            throw new IllegalArgumentException("Weighting of PrepareAlternativeRoute " + preparations.get(lastIndex).getWeighting()
                    + " needs to be identical to previously added " + weightings.get(lastIndex));
        return this;
    }

    @Override
    public RoutingAlgorithmFactory getDecoratedAlgorithmFactory(RoutingAlgorithmFactory defaultAlgoFactory, HintsMap map) {
        boolean disableAR = map.getBool(Parameters.AR.DISABLE, false);
        if (!isEnabled() || disablingAllowed && disableAR)
            return defaultAlgoFactory;

        if (preparations.isEmpty())
            throw new IllegalStateException("No preparations added to this decorator");

        if (map.getWeighting().isEmpty())
            map.setWeighting(getDefaultWeighting());

        for (final PrepareAlternativeRoute p : preparations) {
            if (p.getWeighting().matches(map))
                return new ARAlgoFactoryDecorator.ARRAFactory(p, defaultAlgoFactory);
        }

        return defaultAlgoFactory;
    }

    /**
     * to enable the advanced algorithm, a ViaNodeSet must be added to an already created algorithm. This algorithm must
     * be either AlternativeRoute or AlternativeRouteCH created by one of the other factories (CH, LM, simple)
     */
    public class ARRAFactory implements RoutingAlgorithmFactory {
        private RoutingAlgorithmFactory baseAlgoFactory;
        private PrepareAlternativeRoute p;

        public ARRAFactory(PrepareAlternativeRoute p, RoutingAlgorithmFactory baseAlgoFactory) {
            this.baseAlgoFactory = baseAlgoFactory;
            this.p = p;
        }

        public RoutingAlgorithmFactory getBaseAlgoFactory() {
            return baseAlgoFactory;
        }

        @Override
        public RoutingAlgorithm createAlgo(Graph g, AlgorithmOptions opts) {
            RoutingAlgorithm algo = baseAlgoFactory.createAlgo(g, opts);
            return p.getDecoratedAlgorithm(algo);
        }
    }

    /**
     * This method creates the partition and ARStorage. Both, the CH and LM decorator are needed, in order to speed up
     * the AR preparation
     */
    public void createPreparations(GraphHopperStorage ghStorage,
                                   CHAlgoFactoryDecorator chDecorator,
                                   LMAlgoFactoryDecorator lmDecorator) {
        if (!isEnabled() || !preparations.isEmpty())
            return;
        if (weightings.isEmpty())
            throw new IllegalStateException("No AR weightings found");

        // if areas has not been set to a specific value before it will be set to the value that splits the graph into
        // areas containing about 250000 nodes each
        if (areas == -1)
            areas = ghStorage.getNodes() / 250000;
        if (areas < 16)
            areas = 16;
        if (areas > ghStorage.getNodes())
            areas = ghStorage.getNodes();
        GraphPartition partition = new GraphPartition(ghStorage, areas);
        if (!partition.loadExisting()) {
            StopWatch sw = new StopWatch().start();
            partition.doWork();
            LOGGER.info("partition took: " + (int) sw.stop().getSeconds() + " s, areas: " + partition.getAreas() + ", " + getMemInfo());
        }

        for (Weighting weighting : getWeightings()) {
            RoutingAlgorithmFactory routingAlgorithmFactory = new RoutingAlgorithmFactorySimple();
            HintsMap map = new HintsMap()
                    .setWeighting(weighting.getName())
                    .setVehicle(weighting.getFlagEncoder().toString());
            if (chDecorator.isEnabled())
                routingAlgorithmFactory = chDecorator.getDecoratedAlgorithmFactory(routingAlgorithmFactory, map);
            if (lmDecorator.isEnabled())
                routingAlgorithmFactory = lmDecorator.getDecoratedAlgorithmFactory(routingAlgorithmFactory, map);
            addPreparation(new PrepareAlternativeRoute(ghStorage, weighting, partition, routingAlgorithmFactory)
                    .setParams(pMap));
        }
    }

    /**
     * This method calculates the ViaNodeSets for every weighting and vehicle or - if already existing - loading it
     *
     * @return true if the preparation data for at least one weighting was calculated.
     */
    public boolean loadOrDoWork(final StorableProperties properties) {
        ExecutorCompletionService completionService = new ExecutorCompletionService<>(threadPool);
        int counter = 0;
        final AtomicBoolean prepared = new AtomicBoolean(false);
        for (final PrepareAlternativeRoute prepare : getPreparations()) {
            counter++;
            final int tmpCounter = counter;
            final String name = AbstractWeighting.weightingToFileName(prepare.getWeighting());
            completionService.submit(new Runnable() {
                @Override
                public void run() {
                    if (prepare.loadExisting())
                        return;

                    LOGGER.info((tmpCounter) + "/" + getPreparations().size() + " calling AR prepare.doWork for " + prepare.getWeighting() + " ... (" + getMemInfo() + ")");
                    prepared.set(true);
                    Thread.currentThread().setName(name);
                    prepare.doWork();
                    properties.put(Parameters.AR.PREPARE + "date." + name, createFormatter().format(new Date()));
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
        return prepared.get();
    }
}
