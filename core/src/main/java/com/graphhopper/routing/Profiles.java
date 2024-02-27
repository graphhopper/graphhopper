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

import com.graphhopper.config.Profile;
import com.graphhopper.util.GHUtility;

public class Profiles {

    public static Profile bike(String name) {
        return new Profile(name).setCustomModel(GHUtility.loadCustomModelFromJar("bike.json"));
    }

    public static Profile bus(String name) {
        return new Profile(name).setCustomModel(GHUtility.loadCustomModelFromJar("bus.json"));
    }

    public static Profile car(String name) {
        return new Profile(name).setCustomModel(GHUtility.loadCustomModelFromJar("car.json"));
    }

    public static Profile car4wd(String name) {
        return new Profile(name).setCustomModel(GHUtility.loadCustomModelFromJar("car4wd.json"));
    }

    public static Profile cargoBike(String name) {
        return new Profile(name).setCustomModel(GHUtility.loadCustomModelFromJar("cargo_bike.json"));
    }

    public static Profile foot(String name) {
        return new Profile(name).setCustomModel(GHUtility.loadCustomModelFromJar("foot.json"));
    }

    public static Profile hike(String name) {
        return new Profile(name).setCustomModel(GHUtility.loadCustomModelFromJar("hike.json"));
    }

    public static Profile motorcycle(String name) {
        return new Profile(name).setCustomModel(GHUtility.loadCustomModelFromJar("motorcycle.json"));
    }

    public static Profile mtb(String name) {
        return new Profile(name).setCustomModel(GHUtility.loadCustomModelFromJar("mtb.json"));
    }

    public static Profile racingbike(String name) {
        return new Profile(name).setCustomModel(GHUtility.loadCustomModelFromJar("racingbike.json"));
    }

    public static Profile truck(String name) {
        return new Profile(name).setCustomModel(GHUtility.loadCustomModelFromJar("truck.json"));
    }


}
