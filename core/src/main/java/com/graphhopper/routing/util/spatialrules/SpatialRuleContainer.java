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
package com.graphhopper.routing.util.spatialrules;

import java.util.*;

/**
 * This class contains a collection of SpatialRule and is used for the implementation SpatialRuleLookupArray.
 *
 * @author Robin Boldt
 */
class SpatialRuleContainer {

    final Set<SpatialRule> rules = new LinkedHashSet<>();

    public SpatialRuleContainer addRule(SpatialRule spatialRule) {
        rules.add(spatialRule);
        return this;
    }

    public SpatialRuleContainer addRules(Collection<SpatialRule> rules) {
        this.rules.addAll(rules);
        return this;
    }

    /**
     * Returns a list of all spatial rules including the EMPTY one.
     */
    Collection<SpatialRule> getRules() {
        return rules;
    }

    public int size() {
        return this.rules.size();
    }

    SpatialRule first() {
        return this.rules.iterator().next();
    }

    @Override
    public int hashCode() {
        return rules.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof SpatialRuleContainer) {
            if (this.rules.equals(((SpatialRuleContainer) o).getRules()))
                return true;
        }
        return false;
    }

    public SpatialRuleContainer copy() {
        SpatialRuleContainer container = new SpatialRuleContainer();
        container.addRules(this.rules);
        return container;
    }
}
