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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.gtfs.dropwizard.RealtimeBundleConfiguration;
import com.graphhopper.gtfs.dropwizard.RealtimeConfiguration;
import io.dropwizard.Configuration;
import io.dropwizard.bundles.assets.AssetsBundleConfiguration;
import io.dropwizard.bundles.assets.AssetsConfiguration;
import io.dropwizard.jetty.HttpConnectorFactory;
import io.dropwizard.server.DefaultServerFactory;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

public class GraphHopperServerConfiguration extends Configuration implements GraphHopperBundleConfiguration, RealtimeBundleConfiguration, AssetsBundleConfiguration {

    @NotNull
    @JsonProperty
    private final GraphHopperConfig graphhopper = new GraphHopperConfig();

    @Valid
    @JsonProperty
    private final AssetsConfiguration assets = AssetsConfiguration.builder().build();

    @JsonProperty
    private final RealtimeConfiguration gtfsRealtime = new RealtimeConfiguration();

    public GraphHopperServerConfiguration() {
        init();
    }
    
     private void init() {
        //see: https://stackoverflow.com/questions/20028451/change-dropwizard-default-ports
        // The following is to make sure it runs with a random port. parallel tests clash otherwise
        ((HttpConnectorFactory) ((DefaultServerFactory) getServerFactory()).getApplicationConnectors().get(0)).setPort(0);
        // this is for admin port
        ((HttpConnectorFactory) ((DefaultServerFactory) getServerFactory()).getAdminConnectors().get(0)).setPort(0);
    }

    @Override
    public GraphHopperConfig getGraphHopperConfiguration() {
        return graphhopper;
    }

    @Override
    public AssetsConfiguration getAssetsConfiguration() {
        return assets;
    }

    @Override
    public RealtimeConfiguration gtfsrealtime() {
        return gtfsRealtime;
    }
}
