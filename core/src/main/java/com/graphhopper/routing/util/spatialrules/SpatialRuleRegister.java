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

import com.graphhopper.routing.util.spatialrules.countries.AustriaSpatialRule;
import com.graphhopper.routing.util.spatialrules.countries.GermanySpatialRule;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SpatialRules can be registered in this class.
 * Every rule gets a unique integer id assigned.
 * The conversion of rule name to id and id to name can be done fast and efficient.
 * <p>
 * ID 0 is the EMPTY rule, means there is no spatial rule.
 *
 * @author Robin Boldt
 */
public class SpatialRuleRegister {

    private final static SpatialRule[] DEFAULT_RULES = new SpatialRule[]{
            new GermanySpatialRule(),
            new AustriaSpatialRule()
    };

    private final List<SpatialRule> rules;

    private final Map<Integer, SpatialRule> toRule = new HashMap<>();
    private final Map<String, Integer> toInt = new HashMap<>();

    /**
     * The default constructer registers all rules of the DEFAULT_RULES array.
     */
    public SpatialRuleRegister() {
        this(DEFAULT_RULES);
    }

    /**
     * Registers the rule and create a unique id for these rules. The ids stay consistent as long as you pass the same
     * set of rules.
     */
    public SpatialRuleRegister(SpatialRule... rules) {
        this.rules = Arrays.asList(rules);

        toRule.put(0, SpatialRuleLookup.EMPTY_RULE);
        for (int i = 0; i < this.rules.size(); i++) {
            toRule.put(i + 1, this.rules.get(i));
            toInt.put(this.rules.get(i).getUniqueName(), i + 1);
        }
    }

    public List<SpatialRule> getRules() {
        return rules;
    }

    /**
     * Returns the id for a certain rule name.
     * If no such name is registered, return 0.
     */
    public int getIdForName(String name) {
        if (toInt.containsKey(name)) {
            return toInt.get(name);
        }
        return 0;
    }

    /**
     * Returns the rule for a certain id.
     * If no such id is registered, return "EMPTY".
     */
    public SpatialRule getRuleForId(int id) {
        if (toRule.containsKey(id)) {
            return toRule.get(id);
        }
        // TODO: Should we return something else? Maybe throw an Exception?
        return SpatialRuleLookup.EMPTY_RULE;
    }

    /**
     * Returns the highest id registered.
     */
    public int getMaxId() {
        return rules.size();
    }


}
