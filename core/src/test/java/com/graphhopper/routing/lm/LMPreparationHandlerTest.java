package com.graphhopper.routing.lm;

import com.graphhopper.GraphHopperConfig;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.ShortestWeighting;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.util.Parameters;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;


public class LMPreparationHandlerTest {

    @Test
    public void addWeighting() {
        LMPreparationHandler handler = new LMPreparationHandler();
        handler.addLMProfileAsString("fastest");
        assertEquals(Arrays.asList("fastest"), handler.getLMProfileStrings());

        // special parameters like the maximum weight
        handler = new LMPreparationHandler();
        handler.addLMProfileAsString("fastest|maximum=65000");
        handler.addLMProfileAsString("shortest|maximum=20000");
        assertEquals(Arrays.asList("fastest", "shortest"), handler.getLMProfileStrings());

        FlagEncoder car = new CarFlagEncoder();
        EncodingManager em = EncodingManager.create(car);
        handler.addLMProfile(new LMProfile(new FastestWeighting(car))).addLMProfile(new LMProfile(new ShortestWeighting(car)));
        handler.createPreparations(new GraphHopperStorage(new RAMDirectory(), em, false), null);
        assertEquals(1, handler.getPreparations().get(0).getLandmarkStorage().getFactor(), .1);
        assertEquals(0.3, handler.getPreparations().get(1).getLandmarkStorage().getFactor(), .1);
    }

    @Test
    public void testPrepareWeightingNo() {
        GraphHopperConfig ghConfig = new GraphHopperConfig();
        ghConfig.put(Parameters.Landmark.PREPARE + "weightings", "fastest");
        LMPreparationHandler handler = new LMPreparationHandler();
        handler.init(ghConfig);
        assertTrue(handler.isEnabled());

        // See #1076
        ghConfig.put(Parameters.Landmark.PREPARE + "weightings", "no");
        handler = new LMPreparationHandler();
        handler.init(ghConfig);
        assertFalse(handler.isEnabled());

        ghConfig.put(Parameters.Landmark.PREPARE + "weightings", "false");
        handler = new LMPreparationHandler();
        handler.init(ghConfig);
        assertFalse(handler.isEnabled());
    }
}