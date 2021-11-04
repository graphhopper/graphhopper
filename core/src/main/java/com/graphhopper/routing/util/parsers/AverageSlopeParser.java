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
import com.graphhopper.routing.ev.AverageSlope;
import com.graphhopper.routing.ev.EncodedValue;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.ev.IntEncodedValue;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.FetchMode;
import com.graphhopper.util.PointList;

import java.util.List;

import static com.graphhopper.util.DistanceCalcEarth.DIST_EARTH;

// todonow: not really a tag parser in the strict sense, but we need to create the encoded value somewhere, see #2438
public class AverageSlopeParser implements TagParser {
    private final IntEncodedValue averageSlopeEnc;

    public AverageSlopeParser() {
        this.averageSlopeEnc = AverageSlope.create();
    }

    @Override
    public void createEncodedValues(EncodedValueLookup lookup, List<EncodedValue> registerNewEncodedValue) {
        registerNewEncodedValue.add(averageSlopeEnc);
    }

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay way, boolean ferry, IntsRef relationFlags) {
        return edgeFlags;
    }

    @Override
    public void applyWayTags(ReaderWay way, EdgeIteratorState edge) {
        PointList pointList = edge.fetchWayGeometry(FetchMode.ALL);
        if (pointList.is3D()) {
            double prevLat = Double.NaN;
            double prevLon = Double.NaN;
            double dist = 0;
            for (int i = 0; i < pointList.size(); i++) {
                if (i > 0)
                    dist += DIST_EARTH.calcDist(prevLat, prevLon, pointList.getLat(i), pointList.getLon(i));
                prevLat = pointList.getLat(i);
                prevLon = pointList.getLon(i);
            }
            int averageSlope = (int) Math.round(((pointList.getEle(pointList.size() - 1) - pointList.getEle(0)) / dist) * 100);
            averageSlope = Math.min(30, Math.max(-30, averageSlope));
            edge.set(averageSlopeEnc, averageSlope);
        } else {
            edge.set(averageSlopeEnc, 0);
        }
    }
}
