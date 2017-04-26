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

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.servlet.GuiceServletContextListener;
import com.graphhopper.util.CmdArgs;

/**
 * Replacement of web.xml used only for container deployment. Preferred method is to use GHServer.
 * <p>
 * http://code.google.com/p/google-guice/wiki/ServletModule
 * <p>
 *
 * @author Peter Karich
 */
public class GuiceServletConfig extends GuiceServletContextListener {
    private final CmdArgs args;

    public GuiceServletConfig() {
        try {
            args = CmdArgs.readFromConfig("config.properties", "graphhopper.config");
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    protected Injector getInjector() {
        return Guice.createInjector(createDefaultModule(), createServletModule());
    }

    protected Module createDefaultModule() {
        return new GraphHopperModule(args);
    }

    protected Module createServletModule() {
        return new GraphHopperServletModule(args);
    }
}
