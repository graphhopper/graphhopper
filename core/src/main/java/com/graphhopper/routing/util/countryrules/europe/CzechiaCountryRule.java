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
package com.graphhopper.routing.util.countryrules.europe;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.RoadClass;
import com.graphhopper.routing.ev.Toll;
import com.graphhopper.routing.util.countryrules.CountryRule;

/**
 * Defines the default rules for the roads of the Czech Republic.
 *
 * @author Thomas Butz
 */
public class CzechiaCountryRule implements CountryRule {

    @Override
    public Toll getToll(ReaderWay readerWay, Toll currentToll) {
        if (currentToll != Toll.MISSING) {
            return currentToll;
        }

        RoadClass roadClass = RoadClass.find(readerWay.getTag("highway", ""));
        if (RoadClass.MOTORWAY == roadClass)
            return Toll.ALL;
        return currentToll;
    }
}
