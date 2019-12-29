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

import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.ShortFastestWeighting;
import com.graphhopper.routing.weighting.ShortestWeighting;
import com.graphhopper.storage.*;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
public class CHAlgoFactoryDecoratorTest {
    private CHAlgoFactoryDecorator instance;
    private CHProfile profileNode1;
    private CHProfile profileNode2;
    private CHProfile profileNode3;
    private CHProfile profileEdge1;
    private CHProfile profileEdge2;
    private CHProfile profileEdge3;
    private GraphHopperStorage ghStorage;

    @Before
    public void setup() {
        instance = new CHAlgoFactoryDecorator();
        Directory dir = new RAMDirectory();
        FlagEncoder encoder = new CarFlagEncoder();
        EncodingManager encodingManager = EncodingManager.create(encoder);
        profileNode1 = CHProfile.nodeBased(new FastestWeighting(encoder));
        profileNode2 = CHProfile.nodeBased(new ShortestWeighting(encoder));
        profileNode3 = CHProfile.nodeBased(new ShortFastestWeighting(encoder, 0.1));
        profileEdge1 = CHProfile.edgeBased(new FastestWeighting(encoder), 30);
        profileEdge2 = CHProfile.edgeBased(new ShortestWeighting(encoder), 30);
        profileEdge3 = CHProfile.edgeBased(new ShortFastestWeighting(encoder, 0.1), 30);
        ghStorage = new GraphBuilder(encodingManager)
                .setCHProfiles(profileNode1, profileNode2, profileNode3, profileEdge1, profileEdge2, profileEdge3)
                .setDir(dir)
                .set3D(false)
                .withTurnCosts(true)
                .create();
    }

    @Test
    public void testDisablingAllowed() {
        assertFalse(instance.isDisablingAllowed());
        instance.setEnabled(false);
        assertTrue(instance.isDisablingAllowed());
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
