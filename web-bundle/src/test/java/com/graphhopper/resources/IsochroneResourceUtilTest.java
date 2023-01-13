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

package com.graphhopper.resources;

import com.graphhopper.core.util.DistanceCalcEarth;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class IsochroneResourceUtilTest {

    @Test
    public void testMetersToDegreeApproximation1() {
        DistanceCalcEarth distanceCalcEarth = new DistanceCalcEarth();
        Coordinate berlin = new Coordinate(13.2846508, 52.5069704);
        Coordinate hamburg = new Coordinate(9.7877407, 53.5586941);
        double berlinToHamburgInDegrees = berlin.distance(hamburg);
        double berlinToHamburgInMeters = distanceCalcEarth.calcDist(berlin.y, berlin.x, hamburg.y, hamburg.x);
        double berlinToHamburgInDegreesAccordingToTestee = IsochroneResource.degreesFromMeters(berlinToHamburgInMeters);
        // We only expect this to be in the right order of magnitude
        assertEquals(berlinToHamburgInDegrees, berlinToHamburgInDegreesAccordingToTestee, berlinToHamburgInDegrees * 0.5);
    }

    @Test
    public void testMetersToDegreeApproximation2() {
        DistanceCalcEarth distanceCalcEarth = new DistanceCalcEarth();
        Coordinate sanFrancisco = new Coordinate(-122.4726193,37.7577627);
        Coordinate losAngeles = new Coordinate(-118.6919116,34.0207305);
        double sanFranciscoToLosAngelesInDegrees = sanFrancisco.distance(losAngeles);
        double sanFranciscoToLosAngelesInMeters = distanceCalcEarth.calcDist(sanFrancisco.y, sanFrancisco.x, losAngeles.y, losAngeles.x);
        double sanFranciscoToLosAngelesInDegreesAccordingToTestee = IsochroneResource.degreesFromMeters(sanFranciscoToLosAngelesInMeters);
        // We only expect this to be in the right order of magnitude
        assertEquals(sanFranciscoToLosAngelesInDegrees, sanFranciscoToLosAngelesInDegreesAccordingToTestee, sanFranciscoToLosAngelesInDegrees * 0.5);
    }

}
