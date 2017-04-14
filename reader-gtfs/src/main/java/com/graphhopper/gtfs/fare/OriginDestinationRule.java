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

final class OriginDestinationRule extends SanitizedFareRule {
    private final String origin_id;
    private final String destination_id;

    OriginDestinationRule(String origin_id, String destination_id) {
        this.origin_id = origin_id;
        this.destination_id = destination_id;
    }

    @Override
    boolean appliesTo(Trip.Segment segment) {
        return origin_id.equals(segment.getOriginId()) && destination_id.equals(segment.getDestinationId());
    }
}
