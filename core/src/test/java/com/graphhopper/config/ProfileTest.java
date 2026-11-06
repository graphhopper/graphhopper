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
package com.graphhopper.config;

import com.graphhopper.util.CustomModel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ProfileTest {

    private static Profile createProfile() {
        return new Profile("car").setCustomModel(new CustomModel().setParameter("width", 3)
                .setParameter("push", true).setParameter("rating", Map.of("value", 2, "min", 1, "max", 5)));
    }

    @Test
    public void testVersionWithParameters() {
        // the version of a profile with number and boolean parameters, as calculated before sets were supported.
        // It must not change as a stored graph would no longer match the profile.
        assertEquals(-923420087, createProfile().getVersion());

        // a set changes the version for every member, independent of the order and duplicates
        List<Integer> large = IntStream.range(0, 30).boxed().toList();
        int version = createProfile().setCustomModel(new CustomModel().setParameter("ways", large)).getVersion();
        assertEquals(version, createProfile().setCustomModel(new CustomModel().setParameter("ways", large.reversed())).getVersion());
        assertNotEquals(version, createProfile().setCustomModel(new CustomModel().setParameter("ways", large.subList(0, 29))).getVersion());
    }
}
