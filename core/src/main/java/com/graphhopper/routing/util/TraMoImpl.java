package com.graphhopper.routing.util;

import java.util.Collections;
import java.util.List;

class TraMoImpl implements TransportationMode {
    List<String> restrictions;
    boolean motorVehicle;
    String name;

    TraMoImpl(String name, List<String> restrictions, boolean motorVehicle) {
        this.name = name;
        this.restrictions = Collections.unmodifiableList(restrictions);
        this.motorVehicle = motorVehicle;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public List<String> getRestrictions() {
        return restrictions;
    }

    @Override
    public boolean isMotorVehicle() {
        return motorVehicle;
    }
}
