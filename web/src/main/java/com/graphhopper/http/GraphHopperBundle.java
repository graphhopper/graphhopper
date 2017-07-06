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

package com.graphhopper.http;

import ru.vyarus.dropwizard.guice.module.installer.bundle.GuiceyBootstrap;
import ru.vyarus.dropwizard.guice.module.installer.bundle.GuiceyBundle;

public class GraphHopperBundle implements GuiceyBundle {
    @Override
    public void initialize(GuiceyBootstrap bootstrap) {
        final GraphHopperConfiguration configuration = bootstrap.configuration();
        if (configuration.cmdArgs.has("gtfs.file")) {
            // switch to different API implementation when using Pt
            bootstrap.modules(new PtModule(configuration.cmdArgs));
        } else {
            bootstrap.modules(new GraphHopperModule());
            bootstrap.extensions(GraphHopperService.class, Nearest.class);
        }
    }
}
