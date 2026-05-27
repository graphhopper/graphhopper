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
        PointList points = way.getTag("point_list", null);
        if (points == null)
            return;

        // Zero-length "barrier" edges have identical endpoints so their orientation cannot be
        // derived from the edge geometry alone. In that case WaySegmentParser passes the
        // surrounding way nodes as transient tags; splice them in so the orientation is aligned
        // with the road the barrier sits on.
        GHPoint prev = way.getTag("gh:barrier_prev_point", null);
        GHPoint next = way.getTag("gh:barrier_next_point", null);
        if (prev != null || next != null) {
            if (next == null) next = prev;
            if (prev == null) prev = next;
            PointList replaced = new PointList(3, false);
            replaced.add(prev.getLat(), prev.getLon());
            replaced.add(points.getLat(0), points.getLon(0));
            replaced.add(next.getLat(), next.getLon());
            points = replaced;
        }

        int last = points.size() - 1;
        // store orientation in degrees and use the end of the edge
        orientationEnc.setDecimal(false, edgeId, edgeIntAccess, ANGLE_CALC.calcAzimuth(
                points.getLat(last - 1), points.getLon(last - 1), points.getLat(last), points.getLon(last)));
        // same for the opposite direction
        orientationEnc.setDecimal(true, edgeId, edgeIntAccess, ANGLE_CALC.calcAzimuth(
                points.getLat(1), points.getLon(1), points.getLat(0), points.getLon(0)));
    }
}

