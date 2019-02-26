package com.graphhopper.routing.lm;

import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.ShortestWeighting;
import com.graphhopper.storage.GraphExtension;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.Parameters;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;


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
        dec.createPreparations(new GraphHopperStorage(new RAMDirectory(), em, false, new GraphExtension.NoOpExtension()), null);
        assertEquals(1, dec.getPreparations().get(0).getLandmarkStorage().getFactor(), .1);
        assertEquals(0.3, dec.getPreparations().get(1).getLandmarkStorage().getFactor(), .1);
    }

    @Test
    public void testPrepareWeightingNo() {
        CmdArgs args = new CmdArgs();
        args.put(Parameters.Landmark.PREPARE + "weightings", "fastest");
        LMAlgoFactoryDecorator dec = new LMAlgoFactoryDecorator();
        dec.init(args);
        assertTrue(dec.isEnabled());

        // See #1076
        args.put(Parameters.Landmark.PREPARE + "weightings", "no");
        dec = new LMAlgoFactoryDecorator();
        dec.init(args);
        assertFalse(dec.isEnabled());
    }
}