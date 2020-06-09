package com.graphhopper.routing.weighting.custom;

import com.graphhopper.api.config.Profile;
import com.graphhopper.api.routing.util.CustomModel;

public class CustomProfile extends Profile {

    public CustomProfile(Profile profile) {
        this(profile.getName());
        setVehicle(profile.getVehicle());
        getHints().putAll(profile.getHints());
    }

    public CustomProfile(String name) {
        super(name);
        setWeighting(CustomWeighting.NAME);
    }

    public CustomProfile setCustomModel(CustomModel customModel) {
        getHints().putObject(CustomModel.KEY, customModel);
        getHints().putObject("custom_model_file", "empty");
        return this;
    }

    public CustomModel getCustomModel() {
        return getHints().getObject(CustomModel.KEY, null);
    }
}
