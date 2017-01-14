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

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Contains SpatialRules
 *
 * @author Robin Boldt
 */
public class SpatialRuleContainer {

    private final Set<SpatialRule> rules = new LinkedHashSet<>();

    public SpatialRuleContainer addRule(SpatialRule rule) {
        rules.add(rule);
        return this;
    }

    public Set<SpatialRule> getRules() {
        return rules;
    }

    public int size(){
        return this.rules.size();
    }

    public SpatialRule first(){
        return this.rules.iterator().next();
    }

    public boolean represents(Set<SpatialRule> rules) {
        if(this.rules.size() != rules.size())
            return false;
        for (SpatialRule rule : rules) {
            if(!this.rules.contains(rule)){
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean equals(Object o){
        if(o instanceof SpatialRuleContainer){
            if(this.represents(((SpatialRuleContainer) o).getRules()))
                return true;
        }
        return false;
    }

    public SpatialRuleContainer copy(){
        SpatialRuleContainer container = new SpatialRuleContainer();
        for (SpatialRule rule: this.rules) {
            container.addRule(rule);
        }
        return container;
    }

}
