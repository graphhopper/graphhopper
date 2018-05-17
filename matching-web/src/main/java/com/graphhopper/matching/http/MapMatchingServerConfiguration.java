package com.graphhopper.matching.http;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.graphhopper.http.GraphHopperBundleConfiguration;
import com.graphhopper.matching.MapMatchingBundleConfiguration;
import com.graphhopper.matching.MapMatchingConfiguration;
import com.graphhopper.util.CmdArgs;
import io.dropwizard.Configuration;
import io.dropwizard.bundles.assets.AssetsBundleConfiguration;
import io.dropwizard.bundles.assets.AssetsConfiguration;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

public class MapMatchingServerConfiguration extends Configuration implements GraphHopperBundleConfiguration, MapMatchingBundleConfiguration, AssetsBundleConfiguration {

    @NotNull
    @JsonProperty
    private final CmdArgs graphhopper = new CmdArgs();

    @NotNull
    @JsonProperty
    private final MapMatchingConfiguration mapMatching = new MapMatchingConfiguration();

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

    @Override
    public MapMatchingConfiguration getMapMatchingConfiguration() {
        return null;
    }
}
