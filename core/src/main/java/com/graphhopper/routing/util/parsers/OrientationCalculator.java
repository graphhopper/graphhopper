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
            // store orientation in degrees and use the end of the edge
            double azimuth = ANGLE_CALC.calcAzimuth(pointList.getLat(pointList.size() - 2), pointList.getLon(pointList.size() - 2),
                    pointList.getLat(pointList.size() - 1), pointList.getLon(pointList.size() - 1));
            orientationEnc.setDecimal(false, edgeId, edgeIntAccess, azimuth);

            // same for the opposite direction
            double revAzimuth = ANGLE_CALC.calcAzimuth(pointList.getLat(1), pointList.getLon(1),
                    pointList.getLat(0), pointList.getLon(0));
            orientationEnc.setDecimal(true, edgeId, edgeIntAccess, revAzimuth);
        }
    }
}

