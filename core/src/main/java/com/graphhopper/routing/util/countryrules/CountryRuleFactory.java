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
        rules.put(AL, new AlbaniaCountryRule());
        rules.put(AD, new AndorraCountryRule());
        rules.put(AT, new AustriaCountryRule());
        rules.put(BE, new BelgiumCountryRule());
        rules.put(BG, new BulgariaCountryRule());
        rules.put(BA, new BosniaHerzegovinaCountryRule());
        rules.put(BY, new BelarusCountryRule());
        rules.put(CH, new SwitzerlandCountryRule());
        rules.put(CZ, new CzechiaCountryRule());
        rules.put(DE, new GermanyCountryRule());
        rules.put(DK, new DenmarkCountryRule());
        rules.put(ES, new SpainCountryRule());
        rules.put(EE, new EstoniaCountryRule());
        rules.put(FI, new FinlandCountryRule());
        rules.put(FR, new FranceCountryRule());
        rules.put(FO, new FaroeIslandsCountryRule());
        rules.put(GG, new GuernseyCountryRule());
        rules.put(GI, new GibraltarCountryRule());
        rules.put(GB, new UnitedKingdomCountryRule());
        rules.put(GR, new GreeceCountryRule());
        rules.put(HR, new CroatiaCountryRule());
        rules.put(HU, new HungaryCountryRule());
        rules.put(IM, new IsleOfManCountryRule());
        rules.put(IE, new IrelandCountryRule());
        rules.put(IS, new IcelandCountryRule());
        rules.put(IT, new ItalyCountryRule());
        rules.put(JE, new JerseyCountryRule());
        rules.put(LI, new LiechtensteinCountryRule());
        rules.put(LT, new LithuaniaCountryRule());
        rules.put(LU, new LuxembourgCountryRule());
        rules.put(LV, new LatviaCountryRule());
        rules.put(MC, new MonacoCountryRule());
        rules.put(MD, new MoldovaCountryRule());
        rules.put(MK, new NorthMacedoniaCountryRule());
        rules.put(ML, new MaltaCountryRule());
        rules.put(ME, new MontenegroCountryRule());
        rules.put(NL, new NetherlandsCountryRule());
        rules.put(NO, new NorwayCountryRule());
        rules.put(PL, new PolandCountryRule());
        rules.put(PT, new PortugalCountryRule());
        rules.put(RO, new RomaniaCountryRule());
        rules.put(RU, new RussiaCountryRule());
        rules.put(SM, new SanMarinoCountryRule());
        rules.put(RS, new SerbiaCountryRule());
        rules.put(SK, new SlovakiaCountryRule());
        rules.put(SI, new SloveniaCountryRule());
        rules.put(SE, new SwedenCountryRule());
        rules.put(UA, new UkraineCountryRule());
        rules.put(VA, new VaticanCityCountryRule());
    }

    public CountryRule getCountryRule(Country country) {
        return rules.get(country);
    }

    public Map<Country, CountryRule> getCountryToRuleMap() {
        return rules;
    }
}
