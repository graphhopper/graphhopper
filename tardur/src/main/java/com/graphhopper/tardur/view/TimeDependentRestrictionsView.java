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

import com.graphhopper.tardur.TimeDependentRestrictionsDAO;
import io.dropwizard.views.View;

import java.time.Instant;

public class TimeDependentRestrictionsView extends View {

    private final Instant linkEnterTime;
    private final TimeDependentRestrictionsDAO timeDependentRestrictionsDAO;
    private final Iterable<ConditionalRestrictionView> restrictions;

    public TimeDependentRestrictionsView(TimeDependentRestrictionsDAO timeDependentRestrictionsDAO, Iterable<ConditionalRestrictionView> restrictionViews) {
        super("/assets/wurst.ftl");
        linkEnterTime = Instant.now();
        this.timeDependentRestrictionsDAO = timeDependentRestrictionsDAO;
        this.restrictions = restrictionViews;
    }

    public Iterable<ConditionalRestrictionView> getRestrictions() {
        return restrictions;
    }

    public Instant getLinkEnterTime() {
        return linkEnterTime;
    }

}
