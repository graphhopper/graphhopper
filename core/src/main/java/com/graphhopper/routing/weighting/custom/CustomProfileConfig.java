package com.graphhopper.routing.weighting.custom;

import com.graphhopper.config.ProfileConfig;
import com.graphhopper.routing.util.CustomModel;

public class CustomProfileConfig extends ProfileConfig {

    public CustomProfileConfig(ProfileConfig profileConfig) {
        this(profileConfig.getName());
        setVehicle(profileConfig.getVehicle());
        getHints().putAll(profileConfig.getHints());
    }

    public CustomProfileConfig(String name) {
        super(name);
        setWeighting(CustomWeighting.NAME);
    }

    public CustomProfileConfig setCustomModel(CustomModel customModel) {
        getHints().putObject(CustomModel.KEY, customModel);
        getHints().putObject("custom_model_file", "empty");
        return this;
    }

    public CustomModel getCustomModel() {
        return getHints().getObject(CustomModel.KEY, null);
    }
}
