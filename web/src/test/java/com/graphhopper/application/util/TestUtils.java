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

import io.dropwizard.core.Configuration;
import io.dropwizard.testing.junit5.DropwizardAppExtension;

import jakarta.ws.rs.client.WebTarget;

/**
 * @author thomas aulinger
 */
public class TestUtils {
    private TestUtils() {
    }

    public static WebTarget clientTarget(DropwizardAppExtension<? extends Configuration> app, String path) {
        path = prefixPathWithSlash(path);
        return app.client().target(clientUrl(app, path));
    }

    public static String clientUrl(DropwizardAppExtension<? extends Configuration> app, String path) {
        path = prefixPathWithSlash(path);
        return "http://localhost:" + app.getLocalPort() + path;
    }

    private static String prefixPathWithSlash(String path) {
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return path;
    }

}
