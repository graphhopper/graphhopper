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

package com.graphhopper.gtfs.fare;

import com.conveyal.gtfs.GTFSFeed;
import com.conveyal.gtfs.model.Fare;
import com.conveyal.gtfs.model.FareAttribute;

import java.io.IOException;
import java.util.Map;

/**
 * Workaround for an issue in gtfs-lib where a field (see below), if missing, is assumed set to be 0 while
 * the (as I understand the spec) more appropriate interpretation would be "practically infinite".
 *
 */
public class FixedFareAttributeLoader extends FareAttribute.Loader {
    private final Map<String, Fare> fares;

    public FixedFareAttributeLoader(GTFSFeed feed, Map<String, Fare> fares) {
        super(feed, fares);
        this.fares = fares;
    }

    @Override
    public void loadOneRow() throws IOException {
        super.loadOneRow();
        String fareId = getStringField("fare_id", true);
        final Fare fare = fares.get(fareId);
        fare.fare_attribute.transfers = getIntField("transfers", false, 0, Integer.MAX_VALUE, Integer.MAX_VALUE);
        fare.fare_attribute.transfer_duration = getIntField("transfer_duration", false, 0, 24*60*60, 24*60*60);
    }
}
