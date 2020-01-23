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
    private List<String> chProfileStrings = new ArrayList<>();
    private List<CHProfile> chProfiles = new ArrayList<>();

    public static GraphBuilder start(EncodingManager encodingManager) {
        return new GraphBuilder(encodingManager);
    }

    public GraphBuilder(EncodingManager encodingManager) {
        this.encodingManager = encodingManager;
        this.turnCosts = encodingManager.needsTurnCostsSupport();
    }

    /**
     * Convenience method to set the CH profiles using a string representation. This is will be convenient once the
     * turn cost handling is moved into {@link AbstractWeighting} and {@link TurnWeighting} is gone, see #1820.
     * attention: Currently this only supports a few weightings with limited extra options. The reason is that here the
     * same should happen as in the 'real' GraphHopper graph, reading the real config, but this is likely to change
     * soon, see #493
     */
    public GraphBuilder setCHProfileStrings(String... profileStrings) {
        this.chProfileStrings = Arrays.asList(profileStrings);
        return this;
    }

    public GraphBuilder setCHProfiles(List<CHProfile> chProfiles) {
        this.chProfiles = chProfiles;
        return this;
    }

    public GraphBuilder setCHProfiles(CHProfile... chProfiles) {
        return setCHProfiles(new ArrayList<>(Arrays.asList(chProfiles)));
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
        addCHProfilesFromStrings();
        ghStorage.addCHGraphs(chProfiles);
        return ghStorage;
    }

    /**
     * Default graph is a {@link GraphHopperStorage} with an in memory directory and disabled storing on flush.
     */
    public GraphHopperStorage create() {
        return build().create(bytes);
    }

    private void addCHProfilesFromStrings() {
        for (String profileString : chProfileStrings) {
            String[] split = profileString.split("\\|");
            if (split.length < 3) {
                throw new IllegalArgumentException("Invalid CH profile string: " + profileString + ". Expected something like: car|fastest|node or bike|shortest|edge|40");
            }
            FlagEncoder encoder = encodingManager.getEncoder(split[0]);
            String weightingStr = split[1];
            String edgeOrNode = split[2];
            int uTurnCostsInt = INFINITE_U_TURN_COSTS;
            if (split.length == 4) {
                uTurnCostsInt = Integer.parseInt(split[3]);
            }
            boolean edgeBased;
            if (edgeOrNode.equals("edge")) {
                edgeBased = true;
            } else if (edgeOrNode.equals("node")) {
                edgeBased = false;
            } else {
                throw new IllegalArgumentException("Invalid CH profile string: " + profileString);
            }
            Weighting weighting;
            if (weightingStr.equalsIgnoreCase("fastest")) {
                weighting = new FastestWeighting(encoder);
            } else if (weightingStr.equalsIgnoreCase("shortest")) {
                weighting = new ShortestWeighting(encoder);
            } else if (weightingStr.equalsIgnoreCase("short_fastest")) {
                weighting = new ShortFastestWeighting(encoder, 0.1);
            } else {
                throw new IllegalArgumentException("Weighting not supported using this method, maybe you can use setCHProfile instead: " + weightingStr);
            }
            chProfiles.add(new CHProfile(weighting, edgeBased, uTurnCostsInt));
        }

    }
}
