package com.graphhopper.routing.lm;

import com.graphhopper.GraphHopperConfig;
import com.graphhopper.config.LMProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.ShortestWeighting;
import com.graphhopper.storage.BaseGraph;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;


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
        CarFlagEncoder car = new CarFlagEncoder();
        EncodingManager em = EncodingManager.create(car);
        List<LMConfig> lmConfigs = Arrays.asList(
                new LMConfig("conf1", new FastestWeighting(car)),
                new LMConfig("conf2", new ShortestWeighting(car))
        );
        List<PrepareLandmarks> preparations = handler.createPreparations(lmConfigs, new BaseGraph.Builder(em).build(), null);
        assertEquals(1, preparations.get(0).getLandmarkStorage().getFactor(), .1);
        assertEquals(0.3, preparations.get(1).getLandmarkStorage().getFactor(), .1);
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
        ghConfig.setLMProfiles(Collections.emptyList());
        handler = new LMPreparationHandler();
        handler.init(ghConfig);
        assertFalse(handler.isEnabled());
    }
}