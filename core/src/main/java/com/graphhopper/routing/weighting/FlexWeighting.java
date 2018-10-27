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
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.Helper;

import java.util.ArrayList;

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

    private final static double SPEED_CONV = 3600; // 3.6 <=> meter/s and 3.6*1000 <=> meter/ms
    private final String name;
    private final FlexModel model;
    private final double maxSpeed;
    private final ArrayList<TimeOffsetCalc> timeOffsetCalcs;
    private final ArrayList<FactorCalc> factorCalcs;
    private final ArrayList<NoAccessCalc> noAccessCalcs;
    private BooleanEncodedValue accessEnc;
    private DecimalEncodedValue avSpeedEnc;
    private EnumEncodedValue<RoadEnvironment> roadEnvEnc;
    private EnumEncodedValue<RoadClass> roadClassEnc;
    private EnumEncodedValue<Toll> tollEnc;
    private FlagEncoder encoder;
    private double distanceFactor;

    public FlexWeighting(FlexModel vehicleModel) {
        if (vehicleModel == null)
            throw new IllegalArgumentException("Vehicle model not found");
        this.model = vehicleModel;

        if (vehicleModel.getMaxSpeed() < 1)
            throw new IllegalArgumentException("max_speed too low: " + vehicleModel.getMaxSpeed());
        this.maxSpeed = vehicleModel.getMaxSpeed() / SPEED_CONV;

        // TODO how can we make this true for "car fastest" so that landmark preparation is used if just base matches? p.getWeighting().matches(map)
        name = vehicleModel.getName().isEmpty() ? "flex" : vehicleModel.getName();

        // TODO avoid map access and use e.g. fast array => roadClasses[edgeState.get(roadClassIntEnc)]
        // TODO do call edgeState.get(roadClassEnc) only once, currently we do this for all hooks if non-empty
        timeOffsetCalcs = new ArrayList<>();
        if (!model.getTimeOffset().getRoadClass().isEmpty())
            timeOffsetCalcs.add(new TimeOffsetCalc() {
                @Override
                public double calcSeconds(EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId) {
                    Double tmp = model.getTimeOffset().getRoadClass().get(edgeState.get(roadClassEnc).toString());
                    return tmp != null ? tmp : 1;
                }
            });


        if (!model.getTimeOffset().getRoadEnvironment().isEmpty())
            timeOffsetCalcs.add(new TimeOffsetCalc() {
                @Override
                public double calcSeconds(EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId) {
                    Double tmp = model.getTimeOffset().getRoadEnvironment().get(edgeState.get(roadEnvEnc).toString());
                    return tmp != null ? tmp : 1;
                }
            });

        if (!model.getTimeOffset().getToll().isEmpty())
            timeOffsetCalcs.add(new TimeOffsetCalc() {
                @Override
                public double calcSeconds(EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId) {
                    Double tmp = model.getTimeOffset().getToll().get(edgeState.get(tollEnc).toString());
                    return tmp != null ? tmp : 1;
                }
            });

        noAccessCalcs = new ArrayList<>();
        if (!model.getNoAccess().getRoadClass().isEmpty())
            noAccessCalcs.add(new NoAccessCalc() {
                @Override
                public boolean blocked(EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId) {
                    return model.getNoAccess().getRoadClass().contains(edgeState.get(roadClassEnc));
                }
            });

        if (!model.getNoAccess().getRoadEnvironment().isEmpty())
            noAccessCalcs.add(new NoAccessCalc() {
                @Override
                public boolean blocked(EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId) {
                    return model.getNoAccess().getRoadEnvironment().contains(edgeState.get(roadEnvEnc));
                }
            });

        if (!model.getNoAccess().getToll().isEmpty())
            noAccessCalcs.add(new NoAccessCalc() {
                @Override
                public boolean blocked(EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId) {
                    return model.getNoAccess().getToll().contains(edgeState.get(tollEnc));
                }
            });

        factorCalcs = new ArrayList<>();
        if (!model.getFactor().getRoadClass().isEmpty())
            factorCalcs.add(new FactorCalc() {
                @Override
                public double calcWeight(EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId) {
                    Double tmp = model.getFactor().getRoadClass().get(edgeState.get(roadClassEnc).toString());
                    return tmp != null ? tmp : 1;
                }
            });

        if (!model.getFactor().getRoadEnvironment().isEmpty())
            factorCalcs.add(new FactorCalc() {
                @Override
                public double calcWeight(EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId) {
                    Double tmp = model.getFactor().getRoadEnvironment().get(edgeState.get(roadEnvEnc).toString());
                    return tmp != null ? tmp : 1;
                }
            });

        if (!model.getFactor().getToll().isEmpty()) {
            factorCalcs.add(new FactorCalc() {
                @Override
                public double calcWeight(EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId) {
                    Double tmp = model.getFactor().getToll().get(edgeState.get(tollEnc).toString());
                    return tmp != null ? tmp : 1;
                }
            });
        }
    }

    public FlexWeighting init(EncodingManager encodingManager) {
        // vehicleModel.name can be empty if flex request
        String vehicle = model.getName().isEmpty() ? model.getBase() : model.getName();

        if (Helper.isEmpty(vehicle))
            throw new IllegalArgumentException("No vehicle 'base' or 'name' was specified");

        // TODO deprecated. only used for getFlagEncoder method
        encoder = encodingManager.getEncoder(vehicle);

        accessEnc = encodingManager.getEncodedValue(vehicle + ".access", BooleanEncodedValue.class);
        avSpeedEnc = encodingManager.getEncodedValue(vehicle + ".average_speed", DecimalEncodedValue.class);
        roadEnvEnc = encodingManager.getEncodedValue(EncodingManager.ROAD_ENV, EnumEncodedValue.class);
        roadClassEnc = encodingManager.getEncodedValue(EncodingManager.ROAD_CLASS, EnumEncodedValue.class);
        tollEnc = encodingManager.getEncodedValue(EncodingManager.TOLL, EnumEncodedValue.class);

        distanceFactor = model.getDistanceFactor();
        return this;
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

        for (int i = 0; i < noAccessCalcs.size(); i++) {
            if (noAccessCalcs.get(i).blocked(edgeState, reverse, prevOrNextEdgeId))
                return Double.POSITIVE_INFINITY;
        }

        long timeInMillis = calcMillis(edgeState, reverse, prevOrNextEdgeId);
        if (timeInMillis == Long.MAX_VALUE)
            return Double.POSITIVE_INFINITY;

        for (int i = 0; i < factorCalcs.size(); i++) {
            timeInMillis *= factorCalcs.get(i).calcWeight(edgeState, reverse, prevOrNextEdgeId);
        }

        if (distanceFactor > 0)
            return timeInMillis + edgeState.getDistance() * distanceFactor;

        return timeInMillis;
    }

    @Override
    public long calcMillis(EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId) {
        // TODO speed in request overwrites stored speed
        double speed = reverse ? edgeState.getReverse(avSpeedEnc) * 0.9 : edgeState.get(avSpeedEnc) * 0.9;
        if (speed == 0)
            return Long.MAX_VALUE;
        long timeInMillis = (long) (edgeState.getDistance() / speed * SPEED_CONV);

        for (int i = 0; i < timeOffsetCalcs.size(); i++) {
            timeInMillis += timeOffsetCalcs.get(i).calcSeconds(edgeState, reverse, prevOrNextEdgeId) * 1000;
        }

        return timeInMillis;
    }

    interface TimeOffsetCalc {
        double calcSeconds(EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId);
    }

    interface NoAccessCalc {
        boolean blocked(EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId);
    }

    interface FactorCalc {
        double calcWeight(EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId);
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
        return name;
    }

    @Override
    public String toString() {
        return name;
    }
}
