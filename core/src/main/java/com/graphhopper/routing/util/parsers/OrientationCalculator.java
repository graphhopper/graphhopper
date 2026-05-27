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
        if (pointList != null) {
            // Zero-length "barrier" edges have identical endpoints so their orientation cannot be
            // derived from the point_list alone. In that case WaySegmentParser passes the
            // surrounding way nodes (gh:barrier_prev/next_point) which we splice in here.
            GHPoint prev = way.getTag("gh:barrier_prev_point", null);
            GHPoint next = way.getTag("gh:barrier_next_point", null);
            if (prev != null || next != null) {
                if (next == null) next = prev;
                if (prev == null) prev = next;
                PointList replaced = new PointList(3, false);
                replaced.add(prev.getLat(), prev.getLon());
                replaced.add(pointList.getLat(0), pointList.getLon(0));
                replaced.add(next.getLat(), next.getLon());
                pointList = replaced;
            }

            // store orientation in degrees and use the end of the edge
            double azimuth = ANGLE_CALC.calcAzimuth(pointList.getLat(pointList.size() - 2), pointList.getLon(pointList.size() - 2),
                    pointList.getLat(pointList.size() - 1), pointList.getLon(pointList.size() - 1));
            // same for the opposite direction
            double revAzimuth = ANGLE_CALC.calcAzimuth(pointList.getLat(1), pointList.getLon(1),
                    pointList.getLat(0), pointList.getLon(0));

            // consecutive barrier edges or similar degenerated situations are now very unlikely
            // but if they occur fall back to a fixed pair of azimuths 180° apart => two such edges in a row yield change_angle = 0
            if (azimuth == revAzimuth) {
                azimuth = 0;
                revAzimuth = 180;
            }
            orientationEnc.setDecimal(false, edgeId, edgeIntAccess, azimuth);
            orientationEnc.setDecimal(true, edgeId, edgeIntAccess, revAzimuth);
        }
    }
}

