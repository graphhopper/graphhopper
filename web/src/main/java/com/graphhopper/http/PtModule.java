package com.graphhopper.http;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.graphhopper.GraphHopperAPI;
import com.graphhopper.json.GHJson;
import com.graphhopper.json.GHJsonBuilder;
import com.graphhopper.reader.gtfs.GraphHopperGtfs;
import com.graphhopper.reader.gtfs.GtfsStorage;
import com.graphhopper.reader.gtfs.PtFlagEncoder;
import com.graphhopper.reader.gtfs.RealtimeFeed;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.GHDirectory;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.TranslationMap;

import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Arrays;
import java.util.Collections;

public final class PtModule extends AbstractModule {

    private final CmdArgs args;

    public PtModule(CmdArgs args) {
        this.args = CmdArgs.readFromConfigAndMerge(args, "config", "graphhopper.config");
    }

    @Override
    protected void configure() {
        install(new CmdArgsModule(args));
        bind(GHJson.class).toInstance(new GHJsonBuilder().create());
    }

    @Provides
    @Singleton
    GraphHopperAPI createGraphHopper(PtFlagEncoder flagEncoder, TranslationMap translationMap, GraphHopperStorage graphHopperStorage, LocationIndex locationIndex, GtfsStorage gtfsStorage) {
        return new GraphHopperGtfs(flagEncoder, translationMap, graphHopperStorage, locationIndex, gtfsStorage, RealtimeFeed.empty());
    }

    @Provides
    @Singleton
    GHDirectory createGHDirectory(CmdArgs args) {
        return GraphHopperGtfs.createGHDirectory(args.get("graph.location", "target/tmp"));
    }

    @Provides
    @Singleton
    GraphHopperStorage createGraphHopperStorage(CmdArgs args, GHDirectory directory, EncodingManager encodingManager, PtFlagEncoder ptFlagEncoder, GtfsStorage gtfsStorage) {
        return GraphHopperGtfs.createOrLoad(directory, encodingManager, ptFlagEncoder, gtfsStorage,
                args.getBool("gtfs.createwalknetwork", false),
                args.has("gtfs.file") ? Arrays.asList(args.get("gtfs.file", "").split(",")) : Collections.emptyList(),
                args.has("datareader.file") ? Arrays.asList(args.get("datareader.file", "").split(",")) : Collections.emptyList());
    }

    @Provides
    @Singleton
    LocationIndex createLocationIndex(GraphHopperStorage graphHopperStorage, GHDirectory directory) {
        return GraphHopperGtfs.createOrLoadIndex(directory, graphHopperStorage);
    }

    @Provides
    @Singleton
    @Named("hasElevation")
    boolean hasElevation() {
        return false;
    }

    @Provides
    @Singleton
    GtfsStorage createGtfsStorage() {
        return GraphHopperGtfs.createGtfsStorage();
    }

    @Provides
    @Singleton
    EncodingManager createEncodingManager(PtFlagEncoder ptFlagEncoder) {
        return new EncodingManager(Arrays.asList(ptFlagEncoder), 8);
    }

    @Provides
    @Singleton
    PtFlagEncoder createPtFlagEncoder() {
        return new PtFlagEncoder();
    }

    @Provides
    @Singleton
    TranslationMap createTranslationMap() {
        return GraphHopperGtfs.createTranslationMap();
    }

    @Provides
    @Singleton
    RouteSerializer getRouteSerializer(GraphHopperStorage storage) {
        return new SimpleRouteSerializer(storage.getBounds());
    }

    @Provides
    GraphHopperService getGraphHopperService(GraphHopperStorage storage, LocationIndex locationIndex) {
        return new GraphHopperService() {
            @Override
            public void start() {

            }

            @Override
            public void close() throws Exception {
                storage.close();
                locationIndex.close();
            }
        };
    }

}
