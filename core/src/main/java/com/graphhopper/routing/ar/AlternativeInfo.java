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
package com.graphhopper.routing.ar;

import com.graphhopper.routing.Path;

/**
 * This class is used to compare alternative routes to the main route and each other
 *
 * @author Maximilian Sturm
 */
public class AlternativeInfo {
    private final Path path;
    private final double sortBy;
    private final int viaNode;

    public AlternativeInfo(Path path, double sortBy, int viaNode) {
        this.path = path;
        this.sortBy = sortBy;
        this.viaNode = viaNode;
    }

    public Path getPath() {
        return path;
    }

    public double getSortBy() {
        return sortBy;
    }

    public int getViaNode() {
        return viaNode;
    }
}
