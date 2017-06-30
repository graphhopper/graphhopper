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

import com.graphhopper.util.CmdArgs;
import io.dropwizard.Configuration;
import io.dropwizard.server.DefaultServerFactory;

public class GraphHopperConfiguration extends Configuration {

    public CmdArgs cmdArgs = new CmdArgs();

    public GraphHopperConfiguration() {
        final DefaultServerFactory serverFactory = new DefaultServerFactory();
        // Move Jersey out of the way -- static assets (the web client)
        // and the API root cannot _both_ be mapped to "/".
        // We don't use Jersey services yet (instead: pure Servlets),
        // but once we do, we have to think of something.
        serverFactory.setJerseyRootPath("/api/");
        this.setServerFactory(serverFactory);
    }
}
