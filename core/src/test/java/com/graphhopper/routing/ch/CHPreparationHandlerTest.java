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
package com.graphhopper.routing.ch;

import com.graphhopper.config.CHProfileConfig;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.*;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
public class CHPreparationHandlerTest {
    private CHPreparationHandler instance;
    private CHProfile profileNode1;
    private CHProfile profileNode2;
    private CHProfile profileNode3;
    private CHProfile profileEdge1;
    private CHProfile profileEdge2;
    private CHProfile profileEdge3;
    private GraphHopperStorage ghStorage;

    @Before
    public void setup() {
        instance = new CHPreparationHandler();
        Directory dir = new RAMDirectory();
        FlagEncoder encoder = new CarFlagEncoder();
        EncodingManager encodingManager = EncodingManager.create(encoder);
        ghStorage = new GraphBuilder(encodingManager).setDir(dir).withTurnCosts(true)
                .setCHProfileStrings(
                        "car|fastest|node",
                        "car|shortest|node",
                        "car|short_fastest|node",
                        "car|fastest|edge|30",
                        "car|shortest|edge|30",
                        "car|short_fastest|edge|30"
                )
                .create();
        List<CHProfile> chProfiles = ghStorage.getCHProfiles();
        profileNode1 = chProfiles.get(0);
        profileNode2 = chProfiles.get(1);
        profileNode3 = chProfiles.get(2);
        profileEdge1 = chProfiles.get(3);
        profileEdge2 = chProfiles.get(4);
        profileEdge3 = chProfiles.get(5);
    }

    @Test
    public void testEnabled() {
        assertFalse(instance.isEnabled());
        instance.setCHProfileConfigs(new CHProfileConfig("myconfig"));
        assertTrue(instance.isEnabled());
    }

    @Test(expected = IllegalStateException.class)
    public void testAddingPreparationBeforeProfile_throws() {
        PrepareContractionHierarchies preparation = createPreparation(profileNode1);
        instance.addPreparation(preparation);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddingPreparationWithWrongProfile_throws() {
        instance.addCHProfile(profileNode1);
        PrepareContractionHierarchies preparation = createPreparation(profileNode2);
        instance.addPreparation(preparation);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddingPreparationsInWrongOrder_throws() {
        instance.addCHProfile(profileNode1);
        instance.addCHProfile(profileNode2);
        instance.addPreparation(createPreparation(profileNode2));
        instance.addPreparation(createPreparation(profileNode1));
    }

    @Test
    public void testAddingPreparationsWithEdgeAndNodeBasedIntermixed_works() {
        instance.addCHProfile(profileNode1);
        instance.addCHProfile(profileEdge1);
        instance.addCHProfile(profileNode2);
        instance.addPreparation(createPreparation(profileNode1));
        instance.addPreparation(createPreparation(profileEdge1));
        instance.addPreparation(createPreparation(profileNode2));
    }

    @Test
    public void testAddingEdgeAndNodeBased_works() {
        instance.addCHProfile(profileNode1);
        instance.addCHProfile(profileNode2);
        instance.addCHProfile(profileEdge1);
        instance.addCHProfile(profileEdge2);
        instance.addCHProfile(profileNode3);
        instance.addPreparation(createPreparation(profileNode1));
        instance.addPreparation(createPreparation(profileNode2));
        instance.addPreparation(createPreparation(profileEdge1));
        instance.addPreparation(createPreparation(profileEdge2));
        instance.addPreparation(createPreparation(profileNode3));

        CHProfile[] expectedProfiles = new CHProfile[]{profileNode1, profileNode2, profileEdge1, profileEdge2, profileNode3};
        List<PrepareContractionHierarchies> preparations = instance.getPreparations();
        for (int i = 0; i < preparations.size(); ++i) {
            assertSame(expectedProfiles[i], preparations.get(i).getCHProfile());
        }
    }

    private PrepareContractionHierarchies createPreparation(CHProfile chProfile) {
        return PrepareContractionHierarchies.fromGraphHopperStorage(ghStorage, chProfile);
    }

}
