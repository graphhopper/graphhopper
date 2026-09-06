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
package com.graphhopper.routing.weighting.custom;

import com.graphhopper.json.MinMax;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.weighting.BikeClimbSpeedTable;
import com.graphhopper.routing.weighting.BikeRollingResistance;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.BBox;
import com.graphhopper.util.shapes.Polygon;

import java.util.Map;

/**
 * This class is for internal usage only. It is subclassed by Janino, then special expressions are
 * injected into init, getSpeed and getPriority. At the end an instance is created and used in CustomWeighting.
 */
public class CustomWeightingHelper {
    static double GLOBAL_MAX_SPEED = 999;
    static double GLOBAL_PRIORITY = 1;

    protected EncodedValueLookup lookup;
    protected CustomModel customModel;
    private BikeClimbSpeedTable bikeClimbTable, bikeSpeedTable;
    // the power balance ignores braking, so below this slope (in percent) bike_speed keeps the flat speed
    static final double BRAKING_SLOPE = -15;

    protected CustomWeightingHelper() {
    }

    public void init(CustomModel customModel, EncodedValueLookup lookup, Map<String, JsonFeature> areas) {
        this.lookup = lookup;
        this.customModel = customModel;
    }

    public double getPriority(EdgeIteratorState edge, boolean reverse) {
        return 1;
    }

    public double getSpeed(EdgeIteratorState edge, boolean reverse) {
        return 1;
    }

    public double getTurnPenalty(BaseGraph graph, EdgeIntAccess edgeIntAccess, int inEdge, int viaNode, int outEdge) {
        return 0;
    }

    public final double calcMaxSpeed() {
        MinMax minMaxSpeed = new MinMax(0, GLOBAL_MAX_SPEED);
        FindMinMax.findMinMax(minMaxSpeed, customModel.getSpeed(), customModel.getParameters(), lookup);
        if (minMaxSpeed.min < 0)
            throw new IllegalArgumentException("speed has to be >=0 but can be negative (" + minMaxSpeed.min + ")");
        if (minMaxSpeed.max <= 0)
            throw new IllegalArgumentException("maximum speed has to be >0 but was " + minMaxSpeed.max);
        if (minMaxSpeed.max == GLOBAL_MAX_SPEED)
            throw new IllegalArgumentException("The first statement for 'speed' must be unconditionally to set the speed. But it was " + customModel.getSpeed().get(0));

        return minMaxSpeed.max;
    }

    public final double calcMaxPriority() {
        MinMax minMaxPriority = new MinMax(0, GLOBAL_PRIORITY);
        FindMinMax.findMinMax(minMaxPriority, customModel.getPriority(), customModel.getParameters(), lookup);
        if (minMaxPriority.min < 0)
            throw new IllegalArgumentException("priority has to be >=0 but can be negative (" + minMaxPriority.min + ")");
        if (minMaxPriority.max < 0)
            throw new IllegalArgumentException("maximum priority has to be >=0 but was " + minMaxPriority.max);
        return minMaxPriority.max;
    }

    /**
     * This method calculates the slowdown factor based on the slope for the usage with 'multiply_by'.
     * This method is only used for findMinMax and parseValue, the generated getSpeed code calls
     * getBikeClimbFactor with the current speed instead, see CustomModelParser.parseValue.
     */
    public static double bike_climb_factor(double slope, double power, double mass, double cda, double crr) {
        BikeClimbSpeedTable table = new BikeClimbSpeedTable(power, mass, cda, crr);
        return table.getClimbFactor(slope, table.getFlatSpeed());
    }

    /**
     * Called per edge from the generated getSpeed for bike_climb_factor(average_slope, p_power, p_mass, p_cda, p_crr)
     * with the injected current speed. The arguments are parameters and the call is allowed only once
     * per custom model (see CustomModelParser), so the speed table can be created lazily.
     */
    protected double getBikeClimbFactor(double currentSpeed, double slope, double power, double mass, double cda, double crr) {
        // benign race if the instance is ever shared between threads: identical tables would be created and
        // as all fields of the table are final the reference can be published without synchronization
        if (bikeClimbTable == null)
            bikeClimbTable = new BikeClimbSpeedTable(power, mass, cda, crr);
        return bikeClimbTable.getClimbFactor(slope, currentSpeed);
    }

    /**
     * Called per edge from the generated getSpeed for bike_speed(average_slope, road_class, surface, track_type,
     * smoothness, bike_network, get_off_bike, p_power, p_mass, p_cda, p_crr): the speed in km/h from the power balance for
     * the slope and the rolling resistance of the surface (see BikeRollingResistance), or the walking speed
     * where the bike is pushed. It replaces the speed of the average speed parser, so that the speed on the
     * flat, on climbs and on rough surfaces follows from the same power. On descents steeper than
     * BRAKING_SLOPE the cyclist brakes and the flat speed is used.
     */
    protected double getBikeSpeed(double slope, RoadClass roadClass, Surface surface, TrackType trackType, Smoothness smoothness,
                                  RouteNetwork bikeNetwork, boolean getOffBike, double power, double mass, double cda, double crr) {
        if (getOffBike) return BikeClimbSpeedTable.walkingSpeedKmh(slope);
        if (bikeSpeedTable == null)
            bikeSpeedTable = new BikeClimbSpeedTable(power, mass, cda, crr);
        if (slope < BRAKING_SLOPE) slope = 0;
        return bikeSpeedTable.getSurfaceSpeed(slope, BikeRollingResistance.factor(roadClass, surface, trackType, smoothness, bikeNetwork));
    }

    /**
     * The bounds of bike_speed for findMinMax: the speed decreases with the slope and the rolling resistance
     * and the walking speed is below the flat speed of any valid parameters.
     */
    public static MinMax bikeSpeedMinMax(double minSlope, double maxSlope, double power, double mass, double cda, double crr) {
        BikeClimbSpeedTable table = new BikeClimbSpeedTable(power, mass, cda, crr);
        double min = Math.min(BikeClimbSpeedTable.walkingSpeedKmh(maxSlope), table.getSurfaceSpeed(maxSlope, BikeRollingResistance.PUSHING));
        return new MinMax(min, Math.max(BikeClimbSpeedTable.walkingSpeedKmh(minSlope), table.getSurfaceSpeed(minSlope, 1)));
    }

    public static boolean in(Polygon p, EdgeIteratorState edge) {
        BBox edgeBBox = GHUtility.createBBox(edge);
        BBox polyBBOX = p.getBounds();
        if (!polyBBOX.intersects(edgeBBox))
            return false;
        if (p.isRectangle() && polyBBOX.contains(edgeBBox))
            return true;
        return p.intersects(edge.fetchWayGeometry(FetchMode.ALL).makeImmutable()); // TODO PERF: cache bbox and edge wayGeometry for multiple area
    }

    public static double calcChangeAngle(EdgeIntAccess edgeIntAccess, DecimalEncodedValue orientationEnc,
                                         int inEdge, boolean inEdgeReverse, int outEdge, boolean outEdgeReverse) {
        double prevAzimuth = orientationEnc.getDecimal(inEdgeReverse, inEdge, edgeIntAccess);
        double azimuth = orientationEnc.getDecimal(outEdgeReverse, outEdge, edgeIntAccess);
        return calcChangeAngle(prevAzimuth, azimuth);
    }

    public static double calcChangeAngle(double prevAzimuth, double azimuth) {
        // bring parallel to prevOrientation
        azimuth = (azimuth + 180) % 360.0;

        double changeAngle = azimuth - prevAzimuth;

        // keep in [-180, 180]
        return (changeAngle + 540.0) % 360.0 - 180.0;
    }
}
