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


import static com.graphhopper.routing.ev.Country.*;

import java.util.EnumMap;
import java.util.Map;

import com.graphhopper.routing.ev.Country;
import com.graphhopper.routing.util.countryrules.europe.*;

public class CountryRuleFactory {
    
    private final Map<Country, CountryRule> rules = new EnumMap<>(Country.class);
    
    public CountryRuleFactory() {
        
        // Europe
        rules.put(ALB, null);
        rules.put(AND, null);
        rules.put(AUT, new AustriaCountryRule());
        rules.put(BEL, new BelgiumCountryRule());
        rules.put(BGR, new BulgariaCountryRule());
        rules.put(BIH, null);
        rules.put(BLR, null);
        rules.put(CHE, new SwitzerlandCountryRule());
        rules.put(CZE, new CzechiaCountryRule());
        rules.put(DEU, new GermanyCountryRule());
        rules.put(DNK, new DenmarkCountryRule());
        rules.put(ESP, new SpainCountryRule());
        rules.put(EST, null);
        rules.put(FIN, null);
        rules.put(FRA, new FranceCountryRule());
        rules.put(FRO, null);
        rules.put(GGY, null);
        rules.put(GIB, null);
        rules.put(GBR, null);
        rules.put(GRC, null);
        rules.put(HRV, new CroatiaCountryRule());
        rules.put(HUN, new HungaryCountryRule());
        rules.put(IMN, null);
        rules.put(IRL, null);
        rules.put(ISL, null);
        rules.put(ITA, new ItalyCountryRule());
        rules.put(JEY, null);
        rules.put(LIE, null);
        rules.put(LTU, null);
        rules.put(LUX, new LuxembourgCountryRule());
        rules.put(LVA, null);
        rules.put(MCO, null);
        rules.put(MDA, null);
        rules.put(MKD, null);
        rules.put(MLT, null);
        rules.put(MNE, null);
        rules.put(NLD, new NetherlandsCountryRule());
        rules.put(NOR, null);
        rules.put(POL, new PolandCountryRule());
        rules.put(PRT, new PortugalCountryRule());
        rules.put(ROU, null);
        rules.put(RUS, null);
        rules.put(SMR, null);
        rules.put(SRB, new SerbiaCountryRule());
        rules.put(SVK, new SlovakiaCountryRule());
        rules.put(SVN, new SloveniaCountryRule());
        rules.put(SWE, new SwedenCountryRule());
        rules.put(UKR, null);
        rules.put(VAT, null);
    }

    public CountryRule getCountryRule(Country country) {
        return rules.get(country);
    }
    
    public Map<Country, CountryRule> getCountryToRuleMap() {
        return rules;
    }
}
