package com.graphhopper.routing.lm;

import com.graphhopper.GraphHopperConfig;
import com.graphhopper.config.LMProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValueImpl;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.weighting.SpeedWeighting;
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
                new LMProfile("conf1").setMaximumLMWeight(650_000),
                new LMProfile("conf2").setMaximumLMWeight(200_000)
        );
        DecimalEncodedValue speedEnc = new DecimalEncodedValueImpl("speed", 5, 5, false);
        EncodingManager em = EncodingManager.start().add(speedEnc).build();
        List<LMConfig> lmConfigs = Arrays.asList(
                new LMConfig("conf1", new SpeedWeighting(speedEnc)),
                new LMConfig("conf2", new SpeedWeighting(speedEnc))
        );
        List<PrepareLandmarks> preparations = handler.createPreparations(lmConfigs, new BaseGraph.Builder(em).build(), em, null);
        assertEquals(10, preparations.get(0).getLandmarkStorage().getFactor(), .1);
        assertEquals(3, preparations.get(1).getLandmarkStorage().getFactor(), .1);
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
