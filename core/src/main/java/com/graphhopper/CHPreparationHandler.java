package com.graphhopper;

import com.graphhopper.routing.RoutingAlgorithmFactory;
import com.graphhopper.routing.ch.CHProfileSelector;
import com.graphhopper.routing.ch.CHRoutingAlgorithmFactory;
import com.graphhopper.routing.ch.PrepareContractionHierarchies;
import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.storage.CHGraph;
import com.graphhopper.storage.CHGraphImpl;
import com.graphhopper.storage.CHProfile;
import com.graphhopper.storage.GraphHopperStorage;

import java.util.ArrayList;
import java.util.List;

public class CHPreparationHandler {
    private final List<CHGraph> chGraphs = new ArrayList<>();
    private final List<CHProfile> chProfiles = new ArrayList<>();
    private boolean enabled = true;

    public CHPreparationHandler() {
    }

    public static CHPreparationHandler read(GraphHopperStorage graphHopperStorage, List<CHProfile> chProfiles) {
        CHPreparationHandler handler = new CHPreparationHandler();
        for (CHProfile chProfile : chProfiles) {
            handler.chProfiles.add(chProfile);
            handler.chGraphs.add(graphHopperStorage.getCHGraph(chProfile));
        }
        return handler;
    }

    public CHPreparationHandler doPreparation(CHGraph chGraph, PrepareContractionHierarchies prep) {
        chProfiles.add(prep.getCHProfile());

        // TODO NOW improve API -> is the separation of "init" and "prepared" still necessary?
        CHGraphImpl chGraphImpl = ((CHGraphImpl) chGraph);
        chGraphs.add(chGraphImpl);
        chGraphImpl.create(1000);
        chGraphImpl.initStorage();
        chGraphImpl._prepareForContraction();

        // TODO NOW fake it until we make it async ;)
        prep.doWork();
        return this;
    }

    public CHPreparationHandler waitForCompletion() {
        for (CHGraph chGraph : chGraphs) {
            if (chGraph instanceof CHGraphImpl)
                ((CHGraphImpl) chGraph).flush();
        }
        return this;
    }

    public CHPreparationHandler setEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public RoutingAlgorithmFactory getDecoratedAlgorithmFactory(RoutingAlgorithmFactory routingAlgorithmFactory, HintsMap map) {
        if (chProfiles.isEmpty())
            return routingAlgorithmFactory;

        CHProfile chProfile = CHProfileSelector.select(chProfiles, map);
        for (CHGraph cg : chGraphs) {
            if (cg.getCHProfile().equals(chProfile))
                return new CHRoutingAlgorithmFactory(cg);
        }
        throw new IllegalStateException("Could not find CH preparation for profile: " + chProfile + ", profiles: " + chProfiles);
    }
}
