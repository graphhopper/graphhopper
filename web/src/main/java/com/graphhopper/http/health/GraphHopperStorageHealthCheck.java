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

package com.graphhopper.http.health;

import com.codahale.metrics.health.HealthCheck;
import com.graphhopper.storage.GraphHopperStorage;

public class GraphHopperStorageHealthCheck extends HealthCheck {

    private final GraphHopperStorage graphHopperStorage;

    public GraphHopperStorageHealthCheck(GraphHopperStorage graphHopperStorage) {
        this.graphHopperStorage = graphHopperStorage;
    }

    @Override
    protected Result check() {
        boolean valid = graphHopperStorage.getBounds().isValid();
        if (valid) {
            return Result.healthy();
        } else {
            return Result.unhealthy("GraphHopperStorage has invalid bounds.");
        }
    }
}
