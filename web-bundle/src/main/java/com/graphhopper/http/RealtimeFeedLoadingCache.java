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

package com.graphhopper.http;

import com.conveyal.gtfs.GTFSFeed;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableFutureTask;
import com.google.transit.realtime.GtfsRealtime;
import com.graphhopper.config.Profile;
import com.graphhopper.gtfs.*;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.util.PMap;
import io.dropwizard.lifecycle.Managed;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.glassfish.hk2.api.Factory;

import javax.inject.Inject;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class RealtimeFeedLoadingCache implements Factory<RealtimeFeed>, Managed {

    private final HttpClient httpClient;
    private final GraphHopperGtfs graphHopper;
    private final GraphHopperBundleConfiguration bundleConfiguration;
    private ExecutorService executor;
    private LoadingCache<String, RealtimeFeed> cache;
    private Map<String, Transfers> transfers;

    @Inject
    RealtimeFeedLoadingCache(GraphHopperGtfs graphHopper, HttpClient httpClient, GraphHopperBundleConfiguration bundleConfiguration) {
        this.graphHopper = graphHopper;
        this.bundleConfiguration = bundleConfiguration;
        this.httpClient = httpClient;
    }

    @Override
    public void start() {
        this.transfers = new HashMap<>();
        for (Map.Entry<String, GTFSFeed> entry : this.graphHopper.getGtfsStorage().getGtfsFeeds().entrySet()) {
            this.transfers.put(entry.getKey(), new Transfers(entry.getValue()));
        }
        this.executor = Executors.newSingleThreadExecutor();
        this.cache = CacheBuilder.newBuilder()
                .maximumSize(1)
                .refreshAfterWrite(1, TimeUnit.MINUTES)
                .build(new CacheLoader<String, RealtimeFeed>() {
                    public RealtimeFeed load(String key) {
                        return fetchFeedsAndCreateGraph();
                    }

                    @Override
                    public ListenableFuture<RealtimeFeed> reload(String key, RealtimeFeed oldValue) {
                        ListenableFutureTask<RealtimeFeed> task = ListenableFutureTask.create(() -> fetchFeedsAndCreateGraph());
                        executor.execute(task);
                        return task;
                    }
                });
    }

    @Override
    public RealtimeFeed provide() {
        try {
            return cache.get("pups");
        } catch (ExecutionException | RuntimeException e) {
            e.printStackTrace();
            return RealtimeFeed.empty();
        }
    }

    @Override
    public void dispose(RealtimeFeed instance) {
        this.executor.shutdown();
    }

    @Override
    public void stop() {
    }

    private RealtimeFeed fetchFeedsAndCreateGraph() {
        Map<String, GtfsRealtime.FeedMessage> feedMessageMap = new HashMap<>();
        for (FeedConfiguration configuration : bundleConfiguration.gtfsrealtime().getFeeds()) {
            try {
                switch (configuration.getUrl().getProtocol()) {
                    case "http": {
                        GtfsRealtime.FeedMessage feedMessage = httpClient.execute(new HttpGet(configuration.getUrl().toURI()),
                                response -> GtfsRealtime.FeedMessage.parseFrom(response.getEntity().getContent()));
                        feedMessageMap.put(configuration.getFeedId(), feedMessage);
                        break;
                    }
                    case "file": {
                        GtfsRealtime.FeedMessage feedMessage = GtfsRealtime.FeedMessage.parseFrom(configuration.getUrl().openStream());
                        feedMessageMap.put(configuration.getFeedId(), feedMessage);
                        break;
                    }
                }
            } catch (IOException | URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }
        return RealtimeFeed.fromProtobuf(graphHopper.getGtfsStorage(), this.transfers, feedMessageMap);
    }

    private void validate(RealtimeFeed realtimeFeed) {
        Profile foot = graphHopper.getProfile("foot");
        Weighting weighting = graphHopper.createWeighting(foot, new PMap(), false);
        GraphExplorer graphExplorer = new GraphExplorer(graphHopper.getBaseGraph(), graphHopper.getGtfsStorage().getPtGraph(), weighting, graphHopper.getGtfsStorage(), realtimeFeed, false, false, false, 6.0, true, 0);
        EnumSet<GtfsStorage.EdgeType> edgeTypes = EnumSet.noneOf(GtfsStorage.EdgeType.class);
        for (int streetNode = 0; streetNode < graphHopper.getBaseGraph().getNodes(); streetNode++) {
            int ptNode = graphHopper.getGtfsStorage().getStreetToPt().getOrDefault(streetNode, -1);
            if (ptNode != -1) {
                for (GraphExplorer.MultiModalEdge multiModalEdge : graphExplorer.ptEdgeStream(ptNode, 0)) {
                    edgeTypes.add(multiModalEdge.getType());
                }
            }
        }
        System.out.println(edgeTypes);
    }

}
