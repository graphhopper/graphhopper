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
package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.GHPoint;

import static com.graphhopper.util.AngleCalc.ANGLE_CALC;

public class OrientationCalculator implements TagParser {

    private final DecimalEncodedValue orientationEnc;

    public OrientationCalculator(DecimalEncodedValue orientationEnc) {
        this.orientationEnc = orientationEnc;
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way, IntsRef relationFlags) {
        PointList pointList = way.getTag("point_list", null);
        if (pointList == null)
            return;

        // points used to derive the forward (at end of edge) and reverse (at start of edge) azimuth
        double fwdFromLat = pointList.getLat(pointList.size() - 2);
        double fwdFromLon = pointList.getLon(pointList.size() - 2);
        double fwdToLat = pointList.getLat(pointList.size() - 1);
        double fwdToLon = pointList.getLon(pointList.size() - 1);
        double revFromLat = pointList.getLat(1);
        double revFromLon = pointList.getLon(1);
        double revToLat = pointList.getLat(0);
        double revToLon = pointList.getLon(0);

        // For zero-length "barrier" edges both endpoints are identical and the orientation cannot
        // be derived from the edge geometry alone. WaySegmentParser passes the coordinates of the
        // surrounding way nodes as transient tags in that case so we can still produce a sensible
        // orientation that is consistent with the road the barrier sits on.
        if (fwdFromLat == fwdToLat && fwdFromLon == fwdToLon) {
            GHPoint nextPoint = way.getTag("gh:barrier_next_point", null);
            GHPoint prevPoint = way.getTag("gh:barrier_prev_point", null);
            if (nextPoint == null) nextPoint = prevPoint;
            if (prevPoint == null) prevPoint = nextPoint;
            if (nextPoint != null) {
                // forward azimuth: from barrier point toward the next way node
                fwdFromLat = fwdToLat; // = barrier point
                fwdFromLon = fwdToLon;
                fwdToLat = nextPoint.getLat();
                fwdToLon = nextPoint.getLon();
                // reverse azimuth: from barrier point toward the previous way node
                revFromLat = revToLat; // = barrier point
                revFromLon = revToLon;
                revToLat = prevPoint.getLat();
                revToLon = prevPoint.getLon();
            }
        }

        // store orientation in degrees and use the end of the edge
        double azimuth = ANGLE_CALC.calcAzimuth(fwdFromLat, fwdFromLon, fwdToLat, fwdToLon);
        orientationEnc.setDecimal(false, edgeId, edgeIntAccess, azimuth);

        // same for the opposite direction
        double revAzimuth = ANGLE_CALC.calcAzimuth(revFromLat, revFromLon, revToLat, revToLon);
        orientationEnc.setDecimal(true, edgeId, edgeIntAccess, revAzimuth);
    }
}

