/*
 *  Licensed to Peter Karich under one or more contributor license 
 *  agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  Peter Karich licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except 
 *  in compliance with the License. You may obtain a copy of the 
 *  License at
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

import com.graphhopper.search.Geocoding;
import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import com.graphhopper.GraphHopper;
import com.graphhopper.util.CmdArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Peter Karich, pkarich@pannous.info
 */
public class DefaultModule extends AbstractModule {

    private Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    protected void configure() {
        try {
            CmdArgs args = CmdArgs.readFromConfig("config.properties", "graphhopper.config");
            GraphHopper hopper = new GraphHopper().init(args)
                    .forServer();
            hopper.importOrLoad();
            logger.info("loaded graph at:" + hopper.graphHopperLocation()
                    + ", source:" + hopper.osmFile()
                    + ", acceptWay:" + hopper.acceptWay()
                    + ", class:" + hopper.graph().getClass().getSimpleName());

            bind(GraphHopper.class).toInstance(hopper);

            String algo = args.get("routing.defaultAlgorithm", "dijkstrabi");
            bind(String.class).annotatedWith(Names.named("defaultAlgorithm")).toInstance(algo);

            long timeout = args.getLong("web.timeout", 3000);
            bind(Long.class).annotatedWith(Names.named("timeout")).toInstance(timeout);
            bind(Geocoding.class).toInstance(new NominatimGeocoder().timeout((int) timeout).
                    bounds(hopper.graph().bounds()));
            bind(GHThreadPool.class).toInstance(new GHThreadPool(1000, 50).startService());
        } catch (Exception ex) {
            throw new IllegalStateException("Couldn't load graph", ex);
        }
    }
}
