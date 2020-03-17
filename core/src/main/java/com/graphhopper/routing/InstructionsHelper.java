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
package com.graphhopper.routing;

import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.GHPoint;

/**
 * Simple helper class used during the instruction generation
 */
class InstructionsHelper {

    static double calculateOrientationDelta(double prevLatitude, double prevLongitude, double latitude, double longitude, double prevOrientation) {
        double orientation = Helper.ANGLE_CALC.calcOrientation(prevLatitude, prevLongitude, latitude, longitude, false);
        orientation = Helper.ANGLE_CALC.alignOrientation(prevOrientation, orientation);
        return orientation - prevOrientation;
    }

    static int calculateSign(double prevLatitude, double prevLongitude, double latitude, double longitude, double prevOrientation) {
        double delta = calculateOrientationDelta(prevLatitude, prevLongitude, latitude, longitude, prevOrientation);
        double absDelta = Math.abs(delta);

        if (absDelta < 0.2) {
            // 0.2 ~= 11°
            return Instruction.CONTINUE_ON_STREET;

        } else if (absDelta < 0.8) {
            // 0.8 ~= 40°
            if (delta > 0)
                return Instruction.TURN_SLIGHT_LEFT;
            else
                return Instruction.TURN_SLIGHT_RIGHT;

        } else if (absDelta < 1.8) {
            // 1.8 ~= 103°
            if (delta > 0)
                return Instruction.TURN_LEFT;
            else
                return Instruction.TURN_RIGHT;

        } else if (delta > 0)
            return Instruction.TURN_SHARP_LEFT;
        else
            return Instruction.TURN_SHARP_RIGHT;
    }

    static boolean isNameSimilar(String name1, String name2) {
        // We don't want two empty names to be similar
        // The idea is, if there are only a random tracks, they usually don't have names
        if (name1.isEmpty() && name2.isEmpty()) {
            return false;
        }
        if (name1.equals(name2)) {
            return true;
        }
        return false;
    }

    static GHPoint getPointForOrientationCalculation(EdgeIteratorState edgeIteratorState, NodeAccess nodeAccess) {
        double tmpLat;
        double tmpLon;
        PointList tmpWayGeo = edgeIteratorState.fetchWayGeometry(FetchMode.ALL);
        if (tmpWayGeo.getSize() <= 2) {
            tmpLat = nodeAccess.getLatitude(edgeIteratorState.getAdjNode());
            tmpLon = nodeAccess.getLongitude(edgeIteratorState.getAdjNode());
        } else {
            tmpLat = tmpWayGeo.getLatitude(1);
            tmpLon = tmpWayGeo.getLongitude(1);
        }
        return new GHPoint(tmpLat, tmpLon);
    }
}