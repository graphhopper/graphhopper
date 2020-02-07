package com.graphhopper;

import com.graphhopper.routing.RoutingAlgorithmFactory;
import com.graphhopper.routing.ch.CHProfileSelector;
import com.graphhopper.routing.ch.CHRoutingAlgorithmFactory;
import com.graphhopper.routing.ch.PrepareContractionHierarchies;
import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.storage.CHGraphImpl;
import com.graphhopper.storage.CHProfile;
import com.graphhopper.storage.GraphHopperStorage;

import java.util.ArrayList;
import java.util.List;

public class CHPreparationHandler {
    private final GraphHopperStorage graphHopperStorage;
    private final List<CHGraphImpl> chGraphs = new ArrayList<>();
    private final List<CHProfile> chProfiles = new ArrayList<>();
    private final List<PrepareContractionHierarchies> preparations = new ArrayList<>();
    private boolean enabled = true;

    public CHPreparationHandler(GraphHopperStorage graphHopperStorage) {
        this.graphHopperStorage = graphHopperStorage;
    }

    public CHPreparationHandler(GraphHopperStorage graphHopperStorage, List<CHProfile> chProfiles) {
        this.graphHopperStorage = graphHopperStorage;
        this.chProfiles.addAll(chProfiles);
        this.graphHopperStorage.addCHGraphs(chProfiles);
    }

    public void doPreparation(CHProfile chProfile, CHProfileConfig chConfig) {
        graphHopperStorage.addCHGraph(chProfile);
        PrepareContractionHierarchies prep = PrepareContractionHierarchies.fromGraphHopperStorage(graphHopperStorage, chProfile);
        prep.setParams(chConfig.asMap());

        chProfiles.add(chProfile);
        preparations.add(prep);

        // TODO NOW improve API -> is the separation of "init" and "prepared" still necessary?
        CHGraphImpl chGraphImpl = ((CHGraphImpl) graphHopperStorage.getCHGraph(chProfile));
        chGraphs.add(chGraphImpl);
        chGraphImpl.create(1000);
        chGraphImpl.initStorage();
        chGraphImpl._prepareForContraction();

        // TODO NOW fake it until we make it async ;)
        prep.doWork();
    }

    public void waitForCompletion() {
        graphHopperStorage.flush();

        for (CHGraphImpl chGraph : chGraphs) {
            chGraph.flush();
        }
    }

    public CHPreparationHandler setEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public RoutingAlgorithmFactory getDecoratedAlgorithmFactory(RoutingAlgorithmFactory routingAlgorithmFactory, HintsMap map) {
        CHProfile chProfile = CHProfileSelector.select(chProfiles, map);
        for (CHProfile existingCHProfile : chProfiles) {
            if (existingCHProfile.equals(chProfile))
                // this is nice: no need to have PrepareContractionHierarchies.getRoutingAlgorithmFactory
                return new CHRoutingAlgorithmFactory(graphHopperStorage.getCHGraph(chProfile));
        }
        throw new IllegalStateException("Could not find CH preparation for profile: " + chProfile + ", preparations:" + preparations);
    }
}
