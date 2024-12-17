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
        rules.put(ALB, new AlbaniaCountryRule());
        rules.put(AND, new AndorraCountryRule());
        rules.put(AUT, new AustriaCountryRule());
        rules.put(BEL, new BelgiumCountryRule());
        rules.put(BGR, new BulgariaCountryRule());
        rules.put(BIH, new BosniaHerzegovinaCountryRule());
        rules.put(BLR, new BelarusCountryRule());
        rules.put(CHE, new SwitzerlandCountryRule());
        rules.put(CZE, new CzechiaCountryRule());
        rules.put(DEU, new GermanyCountryRule());
        rules.put(DNK, new DenmarkCountryRule());
        rules.put(ESP, new SpainCountryRule());
        rules.put(EST, new EstoniaCountryRule());
        rules.put(FIN, new FinlandCountryRule());
        rules.put(FRA, new FranceCountryRule());
        rules.put(FRO, new FaroeIslandsCountryRule());
        rules.put(GGY, new GuernseyCountryRule());
        rules.put(GIB, new GibraltarCountryRule());
        rules.put(GBR, new UnitedKingdomCountryRule());
        rules.put(GRC, new GreeceCountryRule());
        rules.put(HRV, new CroatiaCountryRule());
        rules.put(HUN, new HungaryCountryRule());
        rules.put(IMN, new IsleOfManCountryRule());
        rules.put(IRL, new IrelandCountryRule());
        rules.put(ISL, new IcelandCountryRule());
        rules.put(ITA, new ItalyCountryRule());
        rules.put(JEY, new JerseyCountryRule());
        rules.put(LIE, new LiechtensteinCountryRule());
        rules.put(LTU, new LithuaniaCountryRule());
        rules.put(LUX, new LuxembourgCountryRule());
        rules.put(LVA, new LatviaCountryRule());
        rules.put(MCO, new MonacoCountryRule());
        rules.put(MDA, new MoldovaCountryRule());
        rules.put(MKD, new NorthMacedoniaCountryRule());
        rules.put(MLT, new MaltaCountryRule());
        rules.put(MNE, new MontenegroCountryRule());
        rules.put(NLD, new NetherlandsCountryRule());
        rules.put(NOR, new NorwayCountryRule());
        rules.put(POL, new PolandCountryRule());
        rules.put(PRT, new PortugalCountryRule());
        rules.put(ROU, new RomaniaCountryRule());
        rules.put(RUS, new RussiaCountryRule());
        rules.put(SMR, new SanMarinoCountryRule());
        rules.put(SRB, new SerbiaCountryRule());
        rules.put(SVK, new SlovakiaCountryRule());
        rules.put(SVN, new SloveniaCountryRule());
        rules.put(SWE, new SwedenCountryRule());
        rules.put(UKR, new UkraineCountryRule());
        rules.put(VAT, new VaticanCityCountryRule());
    }

    public CountryRule getCountryRule(Country country) {
        return rules.get(country);
    }

    public Map<Country, CountryRule> getCountryToRuleMap() {
        return rules;
    }
}
