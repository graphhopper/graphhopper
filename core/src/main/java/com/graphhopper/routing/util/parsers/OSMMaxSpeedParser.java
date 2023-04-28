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
import com.graphhopper.routing.ev.Country;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.ev.MaxSpeed;
import com.graphhopper.routing.util.parsers.helpers.OSMValueExtractor;
import com.graphhopper.storage.IntsRef;
import de.westnordost.osm_legal_default_speeds.LegalDefaultSpeeds;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.graphhopper.routing.ev.MaxSpeed.UNSET_SPEED;

public class OSMMaxSpeedParser implements TagParser {

    private final DecimalEncodedValue carMaxSpeedEnc;
    private final LegalDefaultSpeeds speeds;

    public OSMMaxSpeedParser(DecimalEncodedValue carMaxSpeedEnc, LegalDefaultSpeeds speeds) {
        if (!carMaxSpeedEnc.isStoreTwoDirections())
            throw new IllegalArgumentException("EncodedValue for maxSpeed must be able to store two directions");

        this.carMaxSpeedEnc = carMaxSpeedEnc;
        this.speeds = speeds;
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way, IntsRef relationFlags) {
        double maxSpeed = OSMValueExtractor.stringToKmh(way.getTag("maxspeed"));

        if (Double.isNaN(maxSpeed)) {
            Country country = way.getTag("country", null);
            if (country != null) {
                LegalDefaultSpeeds.Result result = speeds.getSpeedLimits(country.getAlpha2(),
                        fixType(way.getTags()), Collections.emptyList(), (name, eval) -> eval.invoke());
                if (result != null)
                    maxSpeed = OSMValueExtractor.stringToKmh(result.getTags().get("maxspeed"));
            }
        }

        double fwdSpeed = OSMValueExtractor.stringToKmh(way.getTag("maxspeed:forward"));
        if (!isValidSpeed(fwdSpeed) && isValidSpeed(maxSpeed))
            fwdSpeed = maxSpeed;
        double maxPossibleSpeed = MaxSpeed.UNLIMITED_SIGN_SPEED;
        if (isValidSpeed(fwdSpeed) && fwdSpeed > maxPossibleSpeed)
            fwdSpeed = maxPossibleSpeed;

        double bwdSpeed = OSMValueExtractor.stringToKmh(way.getTag("maxspeed:backward"));
        if (!isValidSpeed(bwdSpeed) && isValidSpeed(maxSpeed))
            bwdSpeed = maxSpeed;
        if (isValidSpeed(bwdSpeed) && bwdSpeed > maxPossibleSpeed)
            bwdSpeed = maxPossibleSpeed;

        if (!isValidSpeed(fwdSpeed))
            fwdSpeed = UNSET_SPEED;
        carMaxSpeedEnc.setDecimal(false, edgeId, edgeIntAccess, fwdSpeed);

        if (!isValidSpeed(bwdSpeed))
            bwdSpeed = UNSET_SPEED;
        carMaxSpeedEnc.setDecimal(true, edgeId, edgeIntAccess, bwdSpeed);
    }

    /**
     * @return <i>true</i> if the given speed is not {@link Double#NaN}
     */
    private boolean isValidSpeed(double speed) {
        return !Double.isNaN(speed);
    }

    Map<String, String> fixType(Map<String, Object> tags) {
        Map<String, String> map = new HashMap<>(tags.size());
        for (Map.Entry<String, Object> entry : tags.entrySet()) {
            if (entry.getValue() instanceof String)
                map.put(entry.getKey(), (String) entry.getValue());
        }
        return map;
    }
}
