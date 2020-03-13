package com.graphhopper.routing.weighting.custom;

import com.graphhopper.config.ProfileConfig;
import com.graphhopper.routing.util.CustomModel;

public class CustomProfileConfig extends ProfileConfig {
    private CustomModel customModel;

    public CustomProfileConfig(ProfileConfig profileConfig) {
        this(profileConfig.getName());
        setVehicle(profileConfig.getVehicle());
        if (profileConfig instanceof CustomProfileConfig)
            setCustomModel(((CustomProfileConfig) profileConfig).getCustomModel());
    }

    public CustomProfileConfig(String name) {
        super(name);
        setWeighting(CustomWeighting.NAME);
    }

    public CustomProfileConfig setCustomModel(CustomModel customModel) {
        this.customModel = customModel;
        return this;
    }

    public CustomModel getCustomModel() {
        return customModel;
    }
}
