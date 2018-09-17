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

package com.conveyal.gtfs;

import com.carrotsearch.hppc.IntArrayList;
import com.conveyal.gtfs.model.StopTime;

import java.util.ArrayList;
import java.util.List;

/**
 * Used as a map key when grouping trips by stop pattern. Note that this includes the routeId, so the same sequence of
 * stops on two different routes makes two different patterns.
 * These objects are not intended for use outside the grouping process.
 */
public class TripPatternKey {

    public String routeId;
    public List<String> stops = new ArrayList<>();
    public IntArrayList pickupTypes = new IntArrayList();
    public IntArrayList dropoffTypes = new IntArrayList();

    public TripPatternKey (String routeId) {
        this.routeId = routeId;
    }

    public void addStopTime (StopTime st) {
        stops.add(st.stop_id);
        pickupTypes.add(st.pickup_type);
        dropoffTypes.add(st.drop_off_type);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TripPatternKey that = (TripPatternKey) o;

        if (dropoffTypes != null ? !dropoffTypes.equals(that.dropoffTypes) : that.dropoffTypes != null) return false;
        if (pickupTypes != null ? !pickupTypes.equals(that.pickupTypes) : that.pickupTypes != null) return false;
        if (routeId != null ? !routeId.equals(that.routeId) : that.routeId != null) return false;
        if (stops != null ? !stops.equals(that.stops) : that.stops != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = routeId != null ? routeId.hashCode() : 0;
        result = 31 * result + (stops != null ? stops.hashCode() : 0);
        result = 31 * result + (pickupTypes != null ? pickupTypes.hashCode() : 0);
        result = 31 * result + (dropoffTypes != null ? dropoffTypes.hashCode() : 0);
        return result;
    }

}
