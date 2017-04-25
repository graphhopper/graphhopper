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
package com.graphhopper.matching.http;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.servlet.GuiceFilter;
import com.graphhopper.http.GHServer;
import com.graphhopper.http.GraphHopperModule;
import com.graphhopper.http.GraphHopperServletModule;
import com.graphhopper.util.CmdArgs;

/**
 * @author Peter Karich
 */
public class MatchServer extends GHServer {

    public static void main(String[] argsStr) throws Exception {
        CmdArgs args = CmdArgs.read(argsStr);
        args.put("prepare.ch.weightings", "no");
        new MatchServer(args).start();
    }

    private final CmdArgs args;

    public MatchServer(CmdArgs args) {
        super(args);
        this.args = args;
    }

    @Override
    protected Module createModule() {
        return new AbstractModule() {
            @Override
            protected void configure() {
                binder().requireExplicitBindings();

                install(new GraphHopperModule(args));
                install(new GraphHopperServletModule(args));

                install(new MapMatchingModule(args));
                install(new MapMatchingServletModule(args));

                bind(GuiceFilter.class);
            }
        };
    }
}
