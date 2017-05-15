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

package com.graphhopper.reader.gtfs;

import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.QueryResult;

class EmptyLocationIndex implements LocationIndex {
    @Override
    public LocationIndex setResolution(int resolution) {
        return this;
    }

    @Override
    public LocationIndex prepareIndex() {
        return this;
    }

    @Override
    public QueryResult findClosest(double lat, double lon, EdgeFilter edgeFilter) {
        return new QueryResult(lat, lon);
    }

    @Override
    public LocationIndex setApproximation(boolean approxDist) {
        return this;
    }

    @Override
    public void setSegmentSize(int bytes) {

    }

    @Override
    public boolean loadExisting() {
        return false;
    }

    @Override
    public LocationIndex create(long byteCount) {
        return this;
    }

    @Override
    public void flush() {

    }

    @Override
    public void close() {

    }

    @Override
    public boolean isClosed() {
        return false;
    }

    @Override
    public long getCapacity() {
        return 0;
    }
}
