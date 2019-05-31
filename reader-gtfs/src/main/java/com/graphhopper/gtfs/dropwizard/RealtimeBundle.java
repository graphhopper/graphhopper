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

import com.graphhopper.reader.gtfs.RealtimeFeed;
import io.dropwizard.ConfiguredBundle;
import io.dropwizard.client.HttpClientBuilder;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.apache.http.client.HttpClient;
import org.glassfish.hk2.utilities.binding.AbstractBinder;

import javax.inject.Singleton;

public class RealtimeBundle implements ConfiguredBundle<RealtimeBundleConfiguration> {

    @Override
    public void initialize(Bootstrap<?> bootstrap) {
    }

    @Override
    public void run(RealtimeBundleConfiguration configuration, Environment environment) {
        final HttpClient httpClient = new HttpClientBuilder(environment)
                .using(configuration.gtfsrealtime().getHttpClientConfiguration())
                .build("gtfs-realtime-feed-loader");
        environment.jersey().register(new AbstractBinder() {
            @Override
            protected void configure() {
                bind(httpClient).to(HttpClient.class);
                bind(configuration).to(RealtimeBundleConfiguration.class);
                bindFactory(RealtimeFeedLoadingCache.class, Singleton.class).to(RealtimeFeed.class);
            }
        });
    }

}
