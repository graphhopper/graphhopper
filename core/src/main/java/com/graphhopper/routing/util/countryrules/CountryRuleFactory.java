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

package com.graphhopper.routing.util.countryrules;


import com.graphhopper.routing.ev.Country;
import com.graphhopper.routing.util.countryrules.europe.*;

import java.util.EnumMap;
import java.util.Map;

import static com.graphhopper.routing.ev.Country.*;

public class CountryRuleFactory {

    private final Map<Country, CountryRule> rules = new EnumMap<>(Country.class);

    public CountryRuleFactory() {

        // Europe
        rules.put(AUT, new AustriaCountryRule());
        rules.put(DEU, new GermanyCountryRule());
        rules.put(HUN, new HungaryCountryRule());
    }

    public CountryRule getCountryRule(Country country) {
        return rules.get(country);
    }

    public Map<Country, CountryRule> getCountryToRuleMap() {
        return rules;
    }
}
