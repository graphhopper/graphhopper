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
package com.graphhopper.routing;

/**
 * This class stores an alternative route with its corresponding viaNode and sortBy value
 *
 * @author Maximilian Sturm
 */
class AlternativeInfo {
    private final Path path;
    private final double sortBy;
    private final int viaNode;

    protected AlternativeInfo(Path path, double sortBy, int viaNode) {
        this.path = path;
        this.sortBy = sortBy;
        this.viaNode = viaNode;
    }

    protected Path getPath() {
        return path;
    }

    protected double getSortBy() {
        return sortBy;
    }

    protected int getViaNode() {
        return viaNode;
    }
}
