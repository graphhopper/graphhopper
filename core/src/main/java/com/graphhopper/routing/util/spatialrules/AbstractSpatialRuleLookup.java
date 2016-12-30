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


import com.graphhopper.reader.ReaderWay;

/**
 * @author Robin Boldt
 */
public abstract class AbstractSpatialRuleLookup implements SpatialRuleLookup {

    public static final SpatialRule EMPTY_RULE = new AbstractSpatialRule() {
        @Override
        public int getMaxSpeed(ReaderWay readerWay, String transportationMode) {
            return -1;
        }

        @Override
        public AccessValue isAccessible(ReaderWay readerWay, String transportationMode) {
            return AccessValue.ACCESSIBLE;
        }

        @Override
        public String getCountryIsoA3Name() {
            return "";
        }
    };

    public static final SpatialRuleContainer EMPTY_RULE_CONTAINER = new SpatialRuleContainer().addRule(EMPTY_RULE);

    public SpatialRule getEmptyRule() {
        return EMPTY_RULE;
    }

}
