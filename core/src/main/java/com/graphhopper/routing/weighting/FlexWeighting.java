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
package com.graphhopper.routing.weighting;

import com.graphhopper.routing.flex.FlexModel;
import com.graphhopper.routing.profiles.*;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.Helper;

/**
 * Calculates the best route according to a configurable weighting.
 * The formula is:
 * <pre>
 * time_in_sec = CONST * distance / speed;
 * weight = factor * time_in_sec + timeOffset;
 * </pre>
 *
 * @author Peter Karich
 */
public class FlexWeighting implements Weighting {

    private final static double SPEED_CONV = 3600;
    private final BooleanEncodedValue accessEnc;
    private final DecimalEncodedValue avSpeedEnc;
    private final EnumEncodedValue<RoadEnvironment> roadEnvEnc;
    private final EnumEncodedValue<RoadClass> roadClassEnc;
    private final EnumEncodedValue<Toll> tollEnc;
    private final FlexModel model;
    private final double maxSpeed;
    private final FlagEncoder encoder;
    private final double distanceFactor;
    private final EdgeFilter edgeFilter;

    public FlexWeighting(EncodingManager encodingManager, FlexModel vehicleModel) {
        if (vehicleModel == null)
            throw new IllegalArgumentException("Vehicle model not found");
        this.model = vehicleModel;

        if (vehicleModel.getMaxSpeed() < 1)
            throw new IllegalArgumentException("max_speed too low: " + vehicleModel.getMaxSpeed());
        this.maxSpeed = vehicleModel.getMaxSpeed() / SPEED_CONV;

        // name can be empty if flex request
        String vehicle = vehicleModel.getName().isEmpty() ? vehicleModel.getBase() : vehicleModel.getName();

        if (Helper.isEmpty(vehicle))
            throw new IllegalArgumentException("No vehicle 'base' or 'name' was specified");

        // TODO deprecated. only used for getFlagEncoder method
        encoder = encodingManager.getEncoder(vehicle);

        accessEnc = encodingManager.getEncodedValue(vehicle + ".access", BooleanEncodedValue.class);
        avSpeedEnc = encodingManager.getEncodedValue(vehicle + ".average_speed", DecimalEncodedValue.class);
        roadEnvEnc = encodingManager.getEncodedValue(EncodingManager.ROAD_ENV, EnumEncodedValue.class);
        roadClassEnc = encodingManager.getEncodedValue(EncodingManager.ROAD_CLASS, EnumEncodedValue.class);
        tollEnc = encodingManager.getEncodedValue(EncodingManager.TOLL, EnumEncodedValue.class);

        edgeFilter = new EdgeFilter() {
            @Override
            public boolean accept(EdgeIteratorState edgeState) {
                if (model.getNoAccess().getRoadClass().contains(edgeState.get(roadClassEnc)))
                    return false;
                if (model.getNoAccess().getRoadEnvironment().contains(edgeState.get(roadEnvEnc)))
                    return false;
                return edgeState.get(accessEnc);
            }
        };
        distanceFactor = model.getDistanceFactor();
    }

    @Override
    public double getMinWeight(double distance) {
        return distance / maxSpeed + distance * distanceFactor;
    }

    @Override
    public double calcWeight(EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId) {
        // TODO combine with EdgeFilter => get rid of reverse somehow
        if (reverse) {
            if (!edgeState.getReverse(accessEnc))
                return Double.POSITIVE_INFINITY;
        } else if (!edgeState.get(accessEnc)) {
            return Double.POSITIVE_INFINITY;
        }
        if (model.getNoAccess().getRoadClass().contains(edgeState.get(roadClassEnc)))
            return Double.POSITIVE_INFINITY;
        if (model.getNoAccess().getRoadEnvironment().contains(edgeState.get(roadEnvEnc)))
            return Double.POSITIVE_INFINITY;
        if (model.getNoAccess().getToll().contains(edgeState.get(tollEnc)))
            return Double.POSITIVE_INFINITY;

//        if (edgeState.get(weightEnc) > model.getNoAccess().getMaxWeight())
//            return Double.POSITIVE_INFINITY;

        long time = calcMillis(edgeState, reverse, prevOrNextEdgeId);
        if (time == Long.MAX_VALUE)
            return Double.POSITIVE_INFINITY;

        // TODO make it pluggable like in a WeightingPipeline to avoid this calculation if non-existent and to make this method shorter
        // TODO avoid map access and use e.g. fast array
        // TODO do call edgeState.get only once, currently we do this here and in calcMillis
        Double tmp = model.getFactor().getRoadClass().get(edgeState.get(roadClassEnc).toString());
        if (tmp != null)
            time *= tmp;

        tmp = model.getFactor().getRoadEnvironment().get(edgeState.get(roadEnvEnc).toString());
        if (tmp != null)
            time *= tmp;

        tmp = model.getFactor().getToll().get(edgeState.get(tollEnc).toString());
        if (tmp != null)
            time *= tmp;

        if (distanceFactor > 0)
            return time + edgeState.getDistance() * distanceFactor;

        return time;
    }

    @Override
    public long calcMillis(EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId) {
        // TODO speed in request overwrites stored speed
        double speed = reverse ? edgeState.getReverse(avSpeedEnc) * 0.9 : edgeState.get(avSpeedEnc) * 0.9;
        if (speed == 0)
            return Long.MAX_VALUE;
        long timeInMillis = (long) (edgeState.getDistance() / speed * SPEED_CONV);

        Double tmp = model.getTimeOffset().getRoadClass().get(edgeState.get(roadClassEnc).toString());
        if (tmp != null)
            timeInMillis += tmp;

        tmp = model.getTimeOffset().getRoadEnvironment().get(edgeState.get(roadEnvEnc).toString());
        if (tmp != null)
            timeInMillis += tmp;

        tmp = model.getTimeOffset().getToll().get(edgeState.get(tollEnc).toString());
        if (tmp != null)
            timeInMillis += tmp;

        return timeInMillis;
    }

    @Override
    public FlagEncoder getFlagEncoder() {
        return encoder;
    }

    @Override
    public boolean matches(HintsMap reqMap) {
        return getName().equals(reqMap.getWeighting());
    }

    @Override
    public String getName() {
        return "flex";
    }
}
