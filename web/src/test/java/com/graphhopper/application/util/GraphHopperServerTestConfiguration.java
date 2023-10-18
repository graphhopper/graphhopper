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
package com.graphhopper.application.util;

import com.graphhopper.application.GraphHopperServerConfiguration;
import io.dropwizard.jetty.HttpConnectorFactory;
import io.dropwizard.server.DefaultServerFactory;

/**
 * @author thomas aulinger
 */
public class GraphHopperServerTestConfiguration extends GraphHopperServerConfiguration {

    public GraphHopperServerTestConfiguration() {
        init();
    }

    private void init() {
        // The following is to make sure it runs with a random port
        ((HttpConnectorFactory) ((DefaultServerFactory) getServerFactory()).getApplicationConnectors().get(0)).setPort(0);
        // this is for admin port
        ((HttpConnectorFactory) ((DefaultServerFactory) getServerFactory()).getAdminConnectors().get(0)).setPort(0);
    }

}
