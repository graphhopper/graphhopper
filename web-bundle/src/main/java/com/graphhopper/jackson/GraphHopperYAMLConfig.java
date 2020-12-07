package com.graphhopper.jackson;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.CustomArea;
import com.graphhopper.config.CustomAreaFile;
import com.graphhopper.config.LMProfile;

public class GraphHopperYAMLConfig extends GraphHopperConfig {

    @JsonProperty("profiles_ch")
    @Override
    public GraphHopperConfig setCHProfiles(List<CHProfile> chProfiles) {
        return super.setCHProfiles(chProfiles);
    }

    @JsonProperty("profiles_lm")
    @Override
    public GraphHopperConfig setLMProfiles(List<LMProfile> lmProfiles) {
        return super.setLMProfiles(lmProfiles);
    }

    @JsonProperty("custom_area_files")
    @Override
    public GraphHopperConfig setCustomAreaFiles(List<CustomAreaFile> customAreaFiles) {
        return super.setCustomAreaFiles(customAreaFiles);
    }
    
    @JsonIgnore
    @Override
    public GraphHopperConfig setCustomAreas(List<CustomArea> customAreas) {
        return super.setCustomAreas(customAreas);
    }

    // We can add explicit configuration properties to GraphHopperConfig (for example to allow lists or nested objects),
    // everything else is stored in a HashMap
    @JsonAnySetter
    @Override
    public GraphHopperConfig putObject(String key, Object value) {
        return super.putObject(key, value);
    }
}
