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


public class LMAlgoFactoryDecoratorTest {

    @Test
    public void addWeighting() {
        LMAlgoFactoryDecorator dec = new LMAlgoFactoryDecorator().setEnabled(true);
        dec.addWeighting("fastest");
        assertEquals(Arrays.asList("fastest"), dec.getWeightingsAsStrings());

        // special parameters like the maximum weight
        dec = new LMAlgoFactoryDecorator().setEnabled(true);
        dec.addWeighting("fastest|maximum=65000");
        dec.addWeighting("shortest|maximum=20000");
        assertEquals(Arrays.asList("fastest", "shortest"), dec.getWeightingsAsStrings());

        FlagEncoder car = new CarFlagEncoder();
        EncodingManager em = EncodingManager.create(car);
        dec.addWeighting(new FastestWeighting(car)).addWeighting(new ShortestWeighting(car));
        dec.createPreparations(new GraphHopperStorage(new RAMDirectory(), em, false), null);
        assertEquals(1, dec.getPreparations().get(0).getLandmarkStorage().getFactor(), .1);
        assertEquals(0.3, dec.getPreparations().get(1).getLandmarkStorage().getFactor(), .1);
    }

    @Test
    public void testPrepareWeightingNo() {
        GraphHopperConfig ghConfig = new GraphHopperConfig();
        ghConfig.put(Parameters.Landmark.PREPARE + "weightings", "fastest");
        LMAlgoFactoryDecorator dec = new LMAlgoFactoryDecorator();
        dec.init(ghConfig);
        assertTrue(dec.isEnabled());

        // See #1076
        ghConfig.put(Parameters.Landmark.PREPARE + "weightings", "no");
        dec = new LMAlgoFactoryDecorator();
        dec.init(ghConfig);
        assertFalse(dec.isEnabled());

        ghConfig.put(Parameters.Landmark.PREPARE + "weightings", "false");
        dec = new LMAlgoFactoryDecorator();
        dec.init(ghConfig);
        assertFalse(dec.isEnabled());
    }
}