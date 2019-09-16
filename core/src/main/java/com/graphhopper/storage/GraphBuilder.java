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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/**
 * For now this is just a helper class to quickly create a {@link GraphHopperStorage}
 * <p>
 *
 * @author Peter Karich
 */
public class GraphBuilder {
    private final EncodingManager encodingManager;
    private String location;
    private boolean mmap;
    private boolean store;
    private boolean elevation;
    private boolean turnCosts;
    private long byteCapacity = 100;
    private List<CHProfile> chProfiles = Collections.emptyList();

    public GraphBuilder(EncodingManager encodingManager) {
        this.encodingManager = encodingManager;
    }

    /**
     * This method enables creating CHGraphs with the specified CHProfiles
     */
    public GraphBuilder setCHProfiles(List<CHProfile> chProfiles) {
        if (chProfiles.size() != new HashSet<>(chProfiles).size()) {
            throw new IllegalArgumentException("Given CH profiles contain duplicates, given: " + chProfiles);
        }
        this.chProfiles = chProfiles;
        return this;
    }

    public GraphBuilder setCHProfiles(CHProfile... chProfiles) {
        return setCHProfiles(Arrays.asList(chProfiles));
    }

    public GraphBuilder setLocation(String location) {
        this.location = location;
        return this;
    }

    public GraphBuilder setStore(boolean store) {
        this.store = store;
        return this;
    }

    public GraphBuilder setMmap(boolean mmap) {
        this.mmap = mmap;
        return this;
    }

    public GraphBuilder setExpectedSize(byte cap) {
        this.byteCapacity = cap;
        return this;
    }

    public GraphBuilder set3D(boolean withElevation) {
        this.elevation = withElevation;
        return this;
    }

    public GraphBuilder withTurnCosts(boolean turnCosts) {
        this.turnCosts = turnCosts;
        return this;
    }

    public boolean hasElevation() {
        return elevation;
    }

    /**
     * Creates a CHGraph
     */
    public CHGraph chGraphCreate(CHProfile chProfile) {
        return setCHProfiles(chProfile).create().getCHGraph();
    }

    /**
     * Default graph is a {@link GraphHopperStorage} with an in memory directory and disabled storing on flush.
     * Afterwards you'll need to call {@link GraphHopperStorage#create} to have a usable object. Better use
     * {@link #create} directly.
     */
    public GraphHopperStorage build() {
        Directory dir = mmap ?
                new MMapDirectory(location) :
                new RAMDirectory(location, store);

        GraphExtension graphExtension = encodingManager.needsTurnCostsSupport() || turnCosts ?
                new TurnCostExtension() :
                new TurnCostExtension.NoOpExtension();

        return chProfiles.isEmpty() ?
                new GraphHopperStorage(dir, encodingManager, elevation, graphExtension) :
                new GraphHopperStorage(chProfiles, dir, encodingManager, elevation, graphExtension);
    }

    /**
     * Default graph is a {@link GraphHopperStorage} with an in memory directory and disabled storing on flush.
     */
    public GraphHopperStorage create() {
        return build().create(byteCapacity);
    }

    /**
     * @throws IllegalStateException if not loadable.
     */
    public GraphHopperStorage load() {
        GraphHopperStorage gs = build();
        if (!gs.loadExisting()) {
            throw new IllegalStateException("Cannot load graph " + location);
        }
        return gs;
    }
}
