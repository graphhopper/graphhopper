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
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.ShortFastestWeighting;
import com.graphhopper.routing.weighting.ShortestWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.GraphExtension;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.RAMDirectory;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static com.graphhopper.routing.util.TraversalMode.EDGE_BASED_2DIR;
import static com.graphhopper.routing.util.TraversalMode.NODE_BASED;
import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
public class CHAlgoFactoryDecoratorTest {
    private CHAlgoFactoryDecorator instance;
    private Weighting weighting1;
    private Weighting weighting2;
    private Weighting weighting3;
    private GraphHopperStorage ghStorage;

    @Before
    public void setup() {
        instance = new CHAlgoFactoryDecorator();
        Directory dir = new RAMDirectory();
        FlagEncoder encoder = new CarFlagEncoder();
        EncodingManager encodingManager = EncodingManager.create(encoder);
        weighting1 = new FastestWeighting(encoder);
        weighting2 = new ShortestWeighting(encoder);
        weighting3 = new ShortFastestWeighting(encoder, 0.1);
        ghStorage = new GraphHopperStorage(
                Arrays.asList(weighting1, weighting2, weighting3),
                Arrays.asList(weighting1, weighting2, weighting3),
                dir, encodingManager, false, new GraphExtension.NoOpExtension());
    }

    @Test
    public void testCreatePreparations() {
        assertFalse(instance.isDisablingAllowed());
        instance.setEnabled(false);
        assertTrue(instance.isDisablingAllowed());
    }

    @Test(expected = IllegalStateException.class)
    public void testAddingPreparationBeforeWeighting_throws() {
        PrepareContractionHierarchies preparation = createNodeBasedPreparation(weighting1);
        instance.addPreparation(preparation);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddingPreparationWithWrongWeighting_throws() {
        instance.addNodeBasedWeighting(weighting1);
        PrepareContractionHierarchies preparation = createNodeBasedPreparation(weighting2);
        instance.addPreparation(preparation);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddingPreparationsInWrongOrder_throws() {
        instance.addNodeBasedWeighting(weighting1);
        instance.addNodeBasedWeighting(weighting2);
        instance.addPreparation(createNodeBasedPreparation(weighting2));
        instance.addPreparation(createNodeBasedPreparation(weighting1));
    }

    @Test
    public void testAddingPreparationsWithEdgeAndNodeBasedIntermixed_works() {
        instance.addNodeBasedWeighting(weighting1);
        instance.addEdgeBasedWeighting(weighting1);
        instance.addPreparation(createEdgeBasedPreparation(weighting1));
        instance.addPreparation(createNodeBasedPreparation(weighting1));
    }

    @Test
    public void testAddingEdgeAndNodeBased_works() {
        instance.addEdgeBasedWeighting(weighting1);
        instance.addNodeBasedWeighting(weighting2);
        instance.addNodeBasedWeighting(weighting1);
        instance.addEdgeBasedWeighting(weighting2);
        instance.addEdgeBasedWeighting(weighting3);
        // we can change the order between edge and node based as long as within each group the weightings have the
        // right order
        instance.addPreparation(createEdgeBasedPreparation(weighting1));
        instance.addPreparation(createNodeBasedPreparation(weighting2));
        instance.addPreparation(createEdgeBasedPreparation(weighting2));
        instance.addPreparation(createEdgeBasedPreparation(weighting3));
        instance.addPreparation(createNodeBasedPreparation(weighting1));

        Weighting[] expectedWeightings = new Weighting[]{weighting1, weighting2, weighting2, weighting3, weighting1};
        boolean[] expectedEdgedBaseds = new boolean[]{true, false, true, true, false};

        List<PrepareContractionHierarchies> preparations = instance.getPreparations();
        for (int i = 0; i < preparations.size(); ++i) {
            assertSame(expectedWeightings[i], preparations.get(i).getWeighting());
            assertSame(expectedEdgedBaseds[i], preparations.get(i).isEdgeBased());
        }
    }

    private PrepareContractionHierarchies createNodeBasedPreparation(Weighting weighting) {
        return createPreparation(weighting, false);
    }

    private PrepareContractionHierarchies createEdgeBasedPreparation(Weighting weighting) {
        return createPreparation(weighting, true);
    }

    private PrepareContractionHierarchies createPreparation(Weighting weighting, boolean edgedBased) {
        TraversalMode traversalMode = edgedBased ? EDGE_BASED_2DIR : NODE_BASED;
        return PrepareContractionHierarchies.fromGraphHopperStorage(ghStorage, weighting, traversalMode);
    }

}
