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

package com.graphhopper.tardur.view;

import ch.poole.openinghoursparser.Rule;
import com.graphhopper.tardur.TimeDependentAccessRestriction;
import com.graphhopper.timezone.core.TimeZones;
import org.locationtech.jts.geom.Coordinate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;

public class ConditionalRestrictionView {

    private final TimeZones timeZones;
    public long osmid;
    public Map<String, Object> tags;
    public Coordinate from;
    public Coordinate to;
    private TimeDependentAccessRestriction timeDependentAccessRestriction;

    public ConditionalRestrictionView(TimeDependentAccessRestriction timeDependentAccessRestriction, TimeZones timeZones) {
        this.timeDependentAccessRestriction = timeDependentAccessRestriction;
        this.timeZones = timeZones;
    }

    public List<TimeDependentAccessRestriction.ConditionalTagData> getRestrictionData() {
        return restrictionData;
    }

    public List<TimeDependentAccessRestriction.ConditionalTagData> restrictionData;

    public Optional<Boolean> accessible(Instant linkEnterTime) {
        TimeZone timeZone = timeZones.getTimeZone(from.y, from.x);
        return timeDependentAccessRestriction.accessible(tags, linkEnterTime.atZone(timeZone.toZoneId()));
    }

    public boolean matches(Instant linkEnterTime, Rule rule) {
        TimeZone timeZone = timeZones.getTimeZone(from.y, from.x);
        return timeDependentAccessRestriction.matches(linkEnterTime.atZone(timeZone.toZoneId()), rule);
    }

}
