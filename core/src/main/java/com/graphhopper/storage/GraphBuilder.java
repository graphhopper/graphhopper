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
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.weighting.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.graphhopper.routing.weighting.TurnCostProvider.NO_TURN_COST_PROVIDER;
import static com.graphhopper.routing.weighting.Weighting.INFINITE_U_TURN_COSTS;

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
    private List<String> chConfigStrings = new ArrayList<>();
    private List<CHConfig> chConfigs = new ArrayList<>();

    public static GraphBuilder start(EncodingManager encodingManager) {
        return new GraphBuilder(encodingManager);
    }

    public GraphBuilder(EncodingManager encodingManager) {
        this.encodingManager = encodingManager;
        this.turnCosts = encodingManager.needsTurnCostsSupport();
    }

    /**
     * Convenience method to set the CH profiles using a string representation. This is convenient if you want to add
     * edge-based {@link CHConfig}s, because otherwise when using {@link #setCHConfigs} you first have to
     * {@link #build()} the {@link GraphHopperStorage} to obtain a {@link TurnCostStorage} to be able to create the
     * {@link Weighting} you need for the {@link CHConfig} to be added...
     * todo: Currently this only supports a few weightings with limited extra options. The reason is that here the
     * same should happen as in the 'real' GraphHopper graph, reading the real config, but this is likely to change
     * soon.
     */
    public GraphBuilder setCHConfigStrings(String... profileStrings) {
        this.chConfigStrings = Arrays.asList(profileStrings);
        return this;
    }

    public GraphBuilder setCHConfigs(List<CHConfig> chConfigs) {
        this.chConfigs = chConfigs;
        return this;
    }

    public GraphBuilder setCHConfigs(CHConfig... chConfigs) {
        return setCHConfigs(new ArrayList<>(Arrays.asList(chConfigs)));
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

    public GraphBuilder setBytes(long bytes) {
        this.bytes = bytes;
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
        GraphHopperStorage ghStorage = new GraphHopperStorage(dir, encodingManager, elevation, turnCosts, segmentSize);
        addCHProfilesFromStrings(ghStorage.getTurnCostStorage());
        ghStorage.addCHGraphs(chConfigs);
        return ghStorage;
    }

    /**
     * Default graph is a {@link GraphHopperStorage} with an in memory directory and disabled storing on flush.
     */
    public GraphHopperStorage create() {
        return build().create(bytes);
    }

    private void addCHProfilesFromStrings(TurnCostStorage turnCostStorage) {
        for (String profileString : chConfigStrings) {
            String[] split = profileString.split("\\|");
            if (split.length < 4) {
                throw new IllegalArgumentException("Invalid CH profile string: " + profileString + ". " +
                        "Expected something like: 'my_profile|car|fastest|node' or 'your_profile|bike|shortest|edge|40'");
            }
            String profileName = split[0];
            FlagEncoder encoder = encodingManager.getEncoder(split[1]);
            String weightingStr = split[2];
            String edgeOrNode = split[3];
            int uTurnCostsInt = INFINITE_U_TURN_COSTS;
            if (split.length == 5) {
                uTurnCostsInt = Integer.parseInt(split[4]);
            }
            TurnCostProvider turnCostProvider;
            boolean edgeBased = false;
            if (edgeOrNode.equals("edge")) {
                if (turnCostStorage == null) {
                    throw new IllegalArgumentException("For edge-based CH profiles you need a turn cost storage");
                }
                turnCostProvider = new DefaultTurnCostProvider(encoder, turnCostStorage, uTurnCostsInt);
                edgeBased = true;
            } else if (edgeOrNode.equals("node")) {
                turnCostProvider = NO_TURN_COST_PROVIDER;
            } else {
                throw new IllegalArgumentException("Invalid CH profile string: " + profileString);
            }
            Weighting weighting;
            if (weightingStr.equalsIgnoreCase("fastest")) {
                weighting = new FastestWeighting(encoder, turnCostProvider);
            } else if (weightingStr.equalsIgnoreCase("shortest")) {
                weighting = new ShortestWeighting(encoder, turnCostProvider);
            } else if (weightingStr.equalsIgnoreCase("short_fastest")) {
                weighting = new ShortFastestWeighting(encoder, 0.1, turnCostProvider);
            } else {
                throw new IllegalArgumentException("Weighting not supported using this method, maybe you can use setCHProfile instead: " + weightingStr);
            }
            chConfigs.add(new CHConfig(profileName, weighting, edgeBased));
        }

    }
}
