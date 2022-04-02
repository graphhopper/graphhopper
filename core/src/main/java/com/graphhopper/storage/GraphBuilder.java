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
package com.graphhopper.storage;

import com.graphhopper.routing.util.EncodingManager;

/**
 * Used to build {@link GraphHopperStorage}
 *
 * @author Peter Karich
 * @author easbar
 */
public class GraphBuilder {
    private final EncodingManager encodingManager;
    private Directory dir = new RAMDirectory();
    private boolean elevation;
    private boolean turnCosts;
    private long bytes = 100;
    private int segmentSize = -1;

    public static GraphBuilder start(EncodingManager encodingManager) {
        return new GraphBuilder(encodingManager);
    }

    public GraphBuilder(EncodingManager encodingManager) {
        this.encodingManager = encodingManager;
        this.turnCosts = encodingManager.needsTurnCostsSupport();
    }

    public GraphBuilder setDir(Directory dir) {
        this.dir = dir;
        return this;
    }

    public GraphBuilder setMMap(String location) {
        return setDir(new MMapDirectory(location));
    }

    public GraphBuilder setRAM() {
        return setDir(new RAMDirectory());
    }

    public GraphBuilder setRAM(String location) {
        return setDir(new RAMDirectory(location));
    }

    public GraphBuilder setRAM(String location, boolean store) {
        return setDir(new RAMDirectory(location, store));
    }

    public GraphBuilder set3D(boolean withElevation) {
        this.elevation = withElevation;
        return this;
    }

    public GraphBuilder withTurnCosts(boolean turnCosts) {
        this.turnCosts = turnCosts;
        return this;
    }

    public GraphBuilder setSegmentSize(int segmentSize) {
        this.segmentSize = segmentSize;
        return this;
    }

    /**
     * Default graph is a {@link GraphHopperStorage} with an in memory directory and disabled storing on flush.
     * Afterwards you'll need to call {@link GraphHopperStorage#create} to have a usable object. Better use
     * {@link #create} directly.
     */
    public GraphHopperStorage build() {
        return new GraphHopperStorage(dir, encodingManager, elevation, turnCosts, segmentSize);
    }

    /**
     * Default graph is a {@link GraphHopperStorage} with an in memory directory and disabled storing on flush.
     */
    public GraphHopperStorage create() {
        return build().create(bytes);
    }

}
