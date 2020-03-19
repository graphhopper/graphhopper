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

package com.graphhopper.routing;

import com.graphhopper.config.CHProfileConfig;
import com.graphhopper.config.LMProfileConfig;
import com.graphhopper.config.ProfileConfig;
import com.graphhopper.routing.ch.CHProfileSelectorTest;
import com.graphhopper.routing.lm.LMProfileSelectorTest;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.util.Parameters;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * So far this test is only testing the profile selection in the absence of CH/LM profiles. For CH/LM profile selection
 *
 * @see CHProfileSelectorTest
 * @see LMProfileSelectorTest
 */
public class ProfileResolverTest {
    @Test
    public void defaultVehicle() {
        ProfileResolver profileResolver = new ProfileResolver(
                EncodingManager.create("car,foot,bike"),
                Arrays.asList(
                        new ProfileConfig("my_bike").setVehicle("bike"),
                        new ProfileConfig("your_car").setVehicle("car")
                ),
                Collections.<CHProfileConfig>emptyList(), Collections.<LMProfileConfig>emptyList());
        // first encoder represents default vehicle
        assertEquals("your_car", profileResolver.resolveProfile(new HintsMap()).getName());
        // use vehicle to specify profile
        assertEquals("your_car", profileResolver.resolveProfile(new HintsMap().setVehicle("car")).getName());
        assertEquals("my_bike", profileResolver.resolveProfile(new HintsMap().setVehicle("bike")).getName());
    }

    @Test
    public void defaultWeighting() {
        ProfileResolver profileResolver = new ProfileResolver(
                EncodingManager.create("bike,car,foot"),
                Arrays.asList(
                        new ProfileConfig("fast_bike").setVehicle("bike").setWeighting("fastest"),
                        new ProfileConfig("short_bike").setVehicle("bike").setWeighting("shortest")
                ),
                Collections.<CHProfileConfig>emptyList(), Collections.<LMProfileConfig>emptyList());
        // use "fastest" weighting by default
        assertEquals("fast_bike", profileResolver.resolveProfile(new HintsMap()).getName());
        // use weighting to specify profile
        assertEquals("short_bike", profileResolver.resolveProfile(new HintsMap().setWeighting("shortest")).getName());
        assertEquals("fast_bike", profileResolver.resolveProfile(new HintsMap().setWeighting("fastest")).getName());
    }

    @Test
    public void missingProfiles() {
        ProfileResolver profileResolver = new ProfileResolver(
                EncodingManager.create("car,bike"),
                Arrays.asList(
                        new ProfileConfig("fast_bike").setVehicle("bike").setWeighting("fastest"),
                        new ProfileConfig("short_bike").setVehicle("bike").setWeighting("shortest")
                ),
                Collections.<CHProfileConfig>emptyList(), Collections.<LMProfileConfig>emptyList());
        // since 'car' is the default vehicle and there are no car profiles we get an error unless the vehicle is set to 'bike'
        assertProfileNotFound(profileResolver, new HintsMap());
        assertProfileNotFound(profileResolver, new HintsMap().setVehicle("car"));
        assertProfileNotFound(profileResolver, new HintsMap().setWeighting("shortest"));

        // now set vehicle to 'bike'
        assertEquals("fast_bike", profileResolver.resolveProfile(new HintsMap().setVehicle("bike")).getName());
        assertEquals("fast_bike", profileResolver.resolveProfile(new HintsMap().setVehicle("bike").setWeighting("fastest")).getName());
        assertEquals("short_bike", profileResolver.resolveProfile(new HintsMap().setVehicle("bike").setWeighting("shortest")).getName());
    }

    @Test
    public void edgeBasedRequiresTurnCostSupport() {
        ProfileResolver profileResolver = new ProfileResolver(
                EncodingManager.create("foot"),
                Collections.singletonList(new ProfileConfig("profile").setVehicle("foot").setWeighting("fastest")),
                Collections.<CHProfileConfig>emptyList(), Collections.<LMProfileConfig>emptyList());

        HintsMap hints = new HintsMap().putObject(Parameters.Routing.EDGE_BASED, true);
        try {
            profileResolver.resolveProfile(hints);
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("You need to set up a turn cost storage to make use of edge_based=true, e.g. use car|turn_costs=true"),
                    "using edge_based=true for encoder without turncost support should be an error, but got:\n" + e.getMessage());
        }
    }

    private void assertProfileNotFound(ProfileResolver profileResolver, HintsMap hints) {
        try {
            profileResolver.resolveProfile(hints);
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("No profile could be found"), e.getMessage());
        }
    }

}