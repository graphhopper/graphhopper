package com.graphhopper.routing.weighting.custom;

import com.graphhopper.config.Profile;
import com.graphhopper.routing.util.CustomModel;

public class CustomProfile extends Profile {

    public CustomProfile(Profile profile) {
        this(profile.getName());
        setVehicle(profile.getVehicle());
        setTurnCosts(profile.isTurnCosts());
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
