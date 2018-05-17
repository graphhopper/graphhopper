package com.graphhopper.matching.http;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.graphhopper.http.GraphHopperBundleConfiguration;
import com.graphhopper.util.CmdArgs;
import io.dropwizard.Configuration;
import io.dropwizard.bundles.assets.AssetsBundleConfiguration;
import io.dropwizard.bundles.assets.AssetsConfiguration;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

public class MapMatchingServerConfiguration extends Configuration implements GraphHopperBundleConfiguration, AssetsBundleConfiguration {

    @NotNull
    @JsonProperty
    private final CmdArgs graphhopper = new CmdArgs();

    @Override
    public CmdArgs getGraphHopperConfiguration() {
        return graphhopper;
    }

    @Valid
    @JsonProperty
    private final AssetsConfiguration assets = AssetsConfiguration.builder().build();

    @Override
    public AssetsConfiguration getAssetsConfiguration() {
        return assets;
    }

}
