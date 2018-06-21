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
import com.graphhopper.routing.util.spatialrules.countries.GreatBritainSpatialRule;
import com.graphhopper.util.shapes.Polygon;

import java.util.List;

/**
 * This class generates SpatialRules per Country
 */
public class CountriesSpatialRuleFactory implements SpatialRuleLookupBuilder.SpatialRuleFactory {
    @Override
    public SpatialRule createSpatialRule(String id, List<Polygon> polygons) {
        switch (id) {
            case "AUT":
                AustriaSpatialRule austriaSpatialRule = new AustriaSpatialRule();
                austriaSpatialRule.setBorders(polygons);
                return austriaSpatialRule;
            case "DEU":
                GermanySpatialRule germanySpatialRule = new GermanySpatialRule();
                germanySpatialRule.setBorders(polygons);
                return germanySpatialRule;
            case "GBR":
                GreatBritainSpatialRule greatBritainSpatialRule = new GreatBritainSpatialRule();
                greatBritainSpatialRule.setBorders(polygons);
                return greatBritainSpatialRule;
        }
        return SpatialRule.EMPTY;
    }
}
