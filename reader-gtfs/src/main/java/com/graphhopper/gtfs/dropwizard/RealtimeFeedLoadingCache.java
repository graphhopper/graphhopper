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

package com.graphhopper.gtfs.dropwizard;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableFutureTask;
import com.google.transit.realtime.GtfsRealtime;
import com.graphhopper.reader.gtfs.GtfsStorage;
import com.graphhopper.reader.gtfs.PtEncodedValues;
import com.graphhopper.reader.gtfs.RealtimeFeed;
import com.graphhopper.storage.GraphHopperStorage;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.glassfish.hk2.api.Factory;

import javax.inject.Inject;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class RealtimeFeedLoadingCache implements Factory<RealtimeFeed> {

    private final HttpClient httpClient;
    private final GraphHopperStorage graphHopperStorage;
    private final GtfsStorage gtfsStorage;
    private final RealtimeBundleConfiguration bundleConfiguration;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final LoadingCache<String, RealtimeFeed> cache;

    @Inject
    RealtimeFeedLoadingCache(GraphHopperStorage graphHopperStorage, GtfsStorage gtfsStorage, HttpClient httpClient, RealtimeBundleConfiguration bundleConfiguration) {
        this.graphHopperStorage = graphHopperStorage;
        this.gtfsStorage = gtfsStorage;
        this.bundleConfiguration = bundleConfiguration;
        this.httpClient = httpClient;
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
            return RealtimeFeed.empty(gtfsStorage);
        }
    }

    @Override
    public void dispose(RealtimeFeed instance) {

    }

    private RealtimeFeed fetchFeedsAndCreateGraph() {
        Map<String, GtfsRealtime.FeedMessage> feedMessageMap = new HashMap<>();
        for (FeedConfiguration configuration : bundleConfiguration.gtfsrealtime().getFeeds()) {
            try {
                GtfsRealtime.FeedMessage feedMessage = GtfsRealtime.FeedMessage.parseFrom(httpClient.execute(new HttpGet(configuration.getUrl().toURI())).getEntity().getContent());
                feedMessageMap.put(configuration.getFeedId(), feedMessage);
            } catch (IOException | URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }
        return RealtimeFeed.fromProtobuf(graphHopperStorage, gtfsStorage, PtEncodedValues.fromEncodingManager(graphHopperStorage.getEncodingManager()), feedMessageMap);
    }

}
