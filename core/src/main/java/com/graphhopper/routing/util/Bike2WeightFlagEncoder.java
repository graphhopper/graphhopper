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
package com.graphhopper.routing.util;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PMap;
import com.graphhopper.util.FetchMode;
import com.graphhopper.util.PointList;

import static com.graphhopper.routing.util.PenaltyCode.*;
import static com.graphhopper.util.Helper.keepIn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

class GradeBoundary implements Comparable<GradeBoundary> {
    final Integer lowerBoundary;
    final Integer upperBoundary;

    GradeBoundary(Integer lowerBoundary, Integer upperBoundary) {
        this.lowerBoundary = lowerBoundary;
        this.upperBoundary = upperBoundary;
    }

    @Override
    public int compareTo(GradeBoundary o) {
        // Assume GradeBoundaries are inclusive boundaries, and do not overlap
        if (this.upperBoundary < o.lowerBoundary)
            return -1;
        if (this.lowerBoundary > o.upperBoundary)
            return 1;
        return 0;
    }
}

/**
 * Stores two speed values into an edge to support avoiding too much incline
 *
 * @author Peter Karich
 */
public class Bike2WeightFlagEncoder extends BikeFlagEncoder {
    // This map takes a GradeBoundary and a Penalty value, and returns a new Penalty
    Map<GradeBoundary, Function<Double, Double>> gradePenaltyMap = new HashMap<>();
    List<GradeBoundary> grades = new ArrayList<>(); // sorted

    GradeBoundary boundaryFor(int grade) {
        int index = Collections.binarySearch(grades, new GradeBoundary(grade, grade));
        return grades.get(index);
    }

    public Bike2WeightFlagEncoder() {
        this(new PMap());
    }

    public Bike2WeightFlagEncoder(PMap properties) {
        super(new PMap(properties).putObject("speed_two_directions", true).putObject("name",
                properties.getString("name", "bike2")));

        // Define grade boundaries. Order for `grades.add` matters.
        GradeBoundary extremeDownGrade = new GradeBoundary(-100, -16);
        grades.add(extremeDownGrade);
        GradeBoundary strongDownGrade = new GradeBoundary(-15, -12);
        grades.add(strongDownGrade);
        GradeBoundary mediumDownGrade = new GradeBoundary(-11, -8);
        grades.add(mediumDownGrade);
        GradeBoundary mildDownGrade = new GradeBoundary(-7, -4);
        grades.add(mildDownGrade);
        GradeBoundary neutralGrade = new GradeBoundary(-3, 3);
        grades.add(neutralGrade);
        GradeBoundary mildUpGrade = new GradeBoundary(4, 7);
        grades.add(mildUpGrade);
        GradeBoundary mediumUpGrade = new GradeBoundary(8, 11);
        grades.add(mediumUpGrade);
        GradeBoundary strongUpGrade = new GradeBoundary(12, 15);
        grades.add(strongUpGrade);
        GradeBoundary extremeUpGrade = new GradeBoundary(16, 100);
        grades.add(extremeUpGrade);

        // At downwards grades, the penalty is lessened
        gradePenaltyMap.put(strongDownGrade, (p) -> {
            return PenaltyCode.from(p).tickDownBy(3).getValue();
        });
        gradePenaltyMap.put(mediumDownGrade, (p) -> {
            return PenaltyCode.from(p).tickDownBy(2).getValue();
        });
        gradePenaltyMap.put(mildDownGrade, (p) -> {
            return PenaltyCode.from(p).tickDownBy(1).getValue();
        });

        // At a neutral grade, the penalty is unchanged
        gradePenaltyMap.put(neutralGrade, (p) -> p);

        // At upwards grades, the penalty is increased
        gradePenaltyMap.put(mildUpGrade, (p) -> {
            return PenaltyCode.from(p).tickUpBy(1).getValue();
        });
        gradePenaltyMap.put(mediumUpGrade, (p) -> {
            return PenaltyCode.from(p).tickUpBy(4).getValue();
        });
        gradePenaltyMap.put(strongUpGrade, (p) -> {
            return Math.max(VERY_BAD.getValue(), p);
        });

        // At extreme grades, the penalty is vastly increased
        gradePenaltyMap.put(extremeDownGrade, (p) -> {
            return Math.max(REACH_DESTINATION.getValue(), p);
        });
        gradePenaltyMap.put(extremeUpGrade, (p) -> {
            return Math.max(REACH_DESTINATION.getValue(), p);
        });
    }

    void applyWayTagsToPenalty(EdgeIteratorState edge) {
        int grade = edge.getGrade();
        IntsRef edgeFlags = edge.getFlags();
        Double forwardPenalty = penaltyEnc.getDecimal(false, edgeFlags);
        Double newForwardPenalty = gradePenaltyMap.get(boundaryFor(grade)).apply(forwardPenalty);
        penaltyEnc.setDecimal(false, edgeFlags, newForwardPenalty);

        Double backwardPenalty = penaltyEnc.getDecimal(true, edgeFlags);
        Double newBackwardPenalty = gradePenaltyMap.get(boundaryFor(-1 * grade)).apply(backwardPenalty);
        penaltyEnc.setDecimal(true, edgeFlags, newBackwardPenalty);
    }

    void applyWayTagsToSpeed(ReaderWay way, EdgeIteratorState edge) {
        PointList pl = edge.fetchWayGeometry(FetchMode.ALL);
        if (!pl.is3D())
            throw new IllegalStateException(getName()
                    + " requires elevation data to improve speed calculation based on it. Please enable it in config via e.g. graph.elevation.provider: srtm");

        IntsRef intsRef = edge.getFlags();
        if (way.hasTag("tunnel", "yes") || way.hasTag("bridge", "yes") || way.hasTag("highway", "steps"))
            // do not change speed
            // note: although tunnel can have a difference in elevation it is very unlikely
            // that the elevation data is correct for a tunnel
            return;

        // Decrease the speed for ele increase (incline), and decrease the speed for ele
        // decrease (decline). The speed-decrease
        // has to be bigger (compared to the speed-increase) for the same elevation
        // difference to simulate losing energy and avoiding hills.
        // For the reverse speed this has to be the opposite but again keeping in mind
        // that up+down difference.
        double incEleSum = 0, incDist2DSum = 0, decEleSum = 0, decDist2DSum = 0;
        // double prevLat = pl.getLat(0), prevLon = pl.getLon(0);
        double prevEle = pl.getEle(0);
        double fullDist2D = edge.getDistance();

        // for short edges an incline makes no sense and for 0 distances could lead to
        // NaN values for speed, see #432
        if (fullDist2D < 2)
            return;

        double eleDelta = pl.getEle(pl.size() - 1) - prevEle;
        if (eleDelta > 0.1) {
            incEleSum = eleDelta;
            incDist2DSum = fullDist2D;
        } else if (eleDelta < -0.1) {
            decEleSum = -eleDelta;
            decDist2DSum = fullDist2D;
        }

        // Calculate slop via tan(asin(height/distance)) but for rather smallish angles
        // where we can assume tan a=a and sin a=a.
        // Then calculate a factor which decreases or increases the speed.
        // Do this via a simple quadratic equation where y(0)=1 and y(0.3)=1/4 for
        // incline and y(0.3)=2 for decline
        double fwdIncline = incDist2DSum > 1 ? incEleSum / incDist2DSum : 0;
        double fwdDecline = decDist2DSum > 1 ? decEleSum / decDist2DSum : 0;
        double restDist2D = fullDist2D - incDist2DSum - decDist2DSum;
        double maxSpeed = getHighwaySpeed("cycleway");
        if (accessEnc.getBool(false, intsRef)) {
            // use weighted mean so that longer incline influences speed more than shorter
            double speed = avgSpeedEnc.getDecimal(false, intsRef);
            double fwdFaster = 1 + 2 * keepIn(fwdDecline, 0, 0.2);
            fwdFaster = fwdFaster * fwdFaster;
            double fwdSlower = 1 - 5 * keepIn(fwdIncline, 0, 0.2);
            fwdSlower = fwdSlower * fwdSlower;
            speed = speed * (fwdSlower * incDist2DSum + fwdFaster * decDist2DSum + 1 * restDist2D) / fullDist2D;
            setSpeed(false, intsRef, keepIn(speed, PUSHING_SECTION_SPEED / 2.0, maxSpeed));
        }

        if (accessEnc.getBool(true, intsRef)) {
            double speedReverse = avgSpeedEnc.getDecimal(true, intsRef);
            double bwFaster = 1 + 2 * keepIn(fwdIncline, 0, 0.2);
            bwFaster = bwFaster * bwFaster;
            double bwSlower = 1 - 5 * keepIn(fwdDecline, 0, 0.2);
            bwSlower = bwSlower * bwSlower;
            speedReverse = speedReverse * (bwFaster * incDist2DSum + bwSlower * decDist2DSum + 1 * restDist2D)
                    / fullDist2D;
            setSpeed(true, intsRef, keepIn(speedReverse, PUSHING_SECTION_SPEED / 2.0, maxSpeed));
        }
        edge.setFlags(intsRef);
    }

    @Override
    public void applyWayTags(ReaderWay way, EdgeIteratorState edge) {
        applyWayTagsToPenalty(edge);
        applyWayTagsToSpeed(way, edge);
    }
}
