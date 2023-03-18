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

package com.graphhopper.reader.osm;

public class SkipOptions {
    private final boolean skipNodes;
    private final boolean skipWays;
    private final boolean skipRelations;

    public static SkipOptions none() {
        return new SkipOptions(false, false, false);
    }

    public SkipOptions(boolean skipNodes, boolean skipWays, boolean skipRelations) {
        this.skipNodes = skipNodes;
        this.skipWays = skipWays;
        this.skipRelations = skipRelations;
    }

    public boolean isSkipNodes() {
        return skipNodes;
    }

    public boolean isSkipWays() {
        return skipWays;
    }

    public boolean isSkipRelations() {
        return skipRelations;
    }
}
