package com.graphhopper.routing.lm;

import com.graphhopper.GraphHopperConfig;
import com.graphhopper.config.LMProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.ShortestWeighting;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.RAMDirectory;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.*;


public class LMPreparationHandlerTest {

    @Test
    public void testEnabled() {
        LMPreparationHandler instance = new LMPreparationHandler();
        assertFalse(instance.isEnabled());
        instance.setLMProfiles(new LMProfile("myconfig"));
        assertTrue(instance.isEnabled());
    }

    @Test
    public void maximumLMWeight() {
        LMPreparationHandler handler = new LMPreparationHandler();
        handler.setLMProfiles(
                new LMProfile("conf1").setMaximumLMWeight(65_000),
                new LMProfile("conf2").setMaximumLMWeight(20_000)
        );
        FlagEncoder car = new CarFlagEncoder();
        EncodingManager em = EncodingManager.create(car);
        handler
                .addLMConfig(new LMConfig("conf1", new FastestWeighting(car)))
                .addLMConfig(new LMConfig("conf2", new ShortestWeighting(car)));
        handler.createPreparations(new GraphHopperStorage(new RAMDirectory(), em, false), null);
        assertEquals(1, handler.getPreparations().get(0).getLandmarkStorage().getFactor(), .1);
        assertEquals(0.3, handler.getPreparations().get(1).getLandmarkStorage().getFactor(), .1);
    }

    @Test
    public void testPrepareWeightingNo() {
        GraphHopperConfig ghConfig = new GraphHopperConfig();
        ghConfig.setProfiles(Collections.singletonList(new Profile("profile")));
        ghConfig.setLMProfiles(Collections.singletonList(new LMProfile("profile")));
        LMPreparationHandler handler = new LMPreparationHandler();
        handler.init(ghConfig);
        assertTrue(handler.isEnabled());

        // See #1076
        ghConfig.setLMProfiles(Collections.<LMProfile>emptyList());
        handler = new LMPreparationHandler();
        handler.init(ghConfig);
        assertFalse(handler.isEnabled());
    }
}