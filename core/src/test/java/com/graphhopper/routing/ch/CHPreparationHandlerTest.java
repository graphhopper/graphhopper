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

import com.graphhopper.config.CHProfile;
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
    private CHConfig configNode1;
    private CHConfig configNode2;
    private CHConfig configNode3;
    private CHConfig configEdge1;
    private CHConfig configEdge2;
    private CHConfig configEdge3;
    private GraphHopperStorage ghStorage;

    @Before
    public void setup() {
        instance = new CHPreparationHandler();
        Directory dir = new RAMDirectory();
        FlagEncoder encoder = new CarFlagEncoder();
        EncodingManager encodingManager = EncodingManager.create(encoder);
        ghStorage = new GraphBuilder(encodingManager).setDir(dir).withTurnCosts(true)
                .setCHConfigStrings(
                        "p1|car|fastest|node",
                        "p2|car|shortest|node",
                        "p3|car|short_fastest|node",
                        "p4|car|fastest|edge|30",
                        "p5|car|shortest|edge|30",
                        "p6|car|short_fastest|edge|30"
                )
                .create();
        List<CHConfig> chConfigs = ghStorage.getCHConfigs();
        configNode1 = chConfigs.get(0);
        configNode2 = chConfigs.get(1);
        configNode3 = chConfigs.get(2);
        configEdge1 = chConfigs.get(3);
        configEdge2 = chConfigs.get(4);
        configEdge3 = chConfigs.get(5);
    }

    @Test
    public void testEnabled() {
        assertFalse(instance.isEnabled());
        instance.setCHProfiles(new CHProfile("myconfig"));
        assertTrue(instance.isEnabled());
    }

    @Test(expected = IllegalStateException.class)
    public void testAddingPreparationBeforeProfile_throws() {
        PrepareContractionHierarchies preparation = createPreparation(configNode1);
        instance.addPreparation(preparation);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddingPreparationWithWrongProfile_throws() {
        instance.addCHConfig(configNode1);
        PrepareContractionHierarchies preparation = createPreparation(configNode2);
        instance.addPreparation(preparation);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddingPreparationsInWrongOrder_throws() {
        instance.addCHConfig(configNode1);
        instance.addCHConfig(configNode2);
        instance.addPreparation(createPreparation(configNode2));
        instance.addPreparation(createPreparation(configNode1));
    }

    @Test
    public void testAddingPreparationsWithEdgeAndNodeBasedIntermixed_works() {
        instance.addCHConfig(configNode1);
        instance.addCHConfig(configEdge1);
        instance.addCHConfig(configNode2);
        instance.addPreparation(createPreparation(configNode1));
        instance.addPreparation(createPreparation(configEdge1));
        instance.addPreparation(createPreparation(configNode2));
    }

    @Test
    public void testAddingEdgeAndNodeBased_works() {
        instance.addCHConfig(configNode1);
        instance.addCHConfig(configNode2);
        instance.addCHConfig(configEdge1);
        instance.addCHConfig(configEdge2);
        instance.addCHConfig(configNode3);
        instance.addPreparation(createPreparation(configNode1));
        instance.addPreparation(createPreparation(configNode2));
        instance.addPreparation(createPreparation(configEdge1));
        instance.addPreparation(createPreparation(configEdge2));
        instance.addPreparation(createPreparation(configNode3));

        CHConfig[] expectedConfigs = new CHConfig[]{configNode1, configNode2, configEdge1, configEdge2, configNode3};
        List<PrepareContractionHierarchies> preparations = instance.getPreparations();
        for (int i = 0; i < preparations.size(); ++i) {
            assertSame(expectedConfigs[i], preparations.get(i).getCHConfig());
        }
    }

    private PrepareContractionHierarchies createPreparation(CHConfig chConfig) {
        return PrepareContractionHierarchies.fromGraphHopperStorage(ghStorage, chConfig);
    }

}
